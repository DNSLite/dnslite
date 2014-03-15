#include "dnsproxy.h"
#include "define.h"
#include "conf.h"
#include "cache.h"
#include "dns.h"
#include "nameserver.h"

conf_t *gconf;

#ifndef ANDROID
static int find_user_group(const char *user, const char *group, uid_t *uid, gid_t *gid, const char **username);
#endif

static int do_send_response(event_util_t *u, udp_sock_t *c);
static int do_send_0response(event_util_t *u, udp_sock_t *c);
static void uninit_conf();
static void set_system_dns();

void printversion()
{
	puts("Version: 0.1 xudejian2008@gmail.com\n");
}

void printHelp()
{
	puts("Usage: bin [options]\n"
			"\t-s set system dns setting\n"
			"\t-a listen_ip\n"
			"\t-d static_cache file\n"
			"\t-D no daemon\n"
			"\t-i max_idle_time\n"
			"\t-g clean_cache_gap 0:disable cache\n"
			"\t-U user ANDROID not support\n"
			"\t-G group ANDROID not support\n"
			"\t-t usetcp\n"
			"\t-r remote dns eg:8.8.8.8,8.8.4.4\n"
			"\t-v verion\n"
			"\th? this info\n");
	printversion();
}

void logs(const char *fmt, ...)
{
	int fd = -1;
	if (!gconf || gconf->logfd == -2) {
		fd = STDOUT_FILENO;
	} else if (gconf->logfd == -1) {
		return;
	} else {
		fd = gconf->logfd;
	}
	char buf[1024];
	int n;
	va_list ap;
	va_start(ap, fmt);
	n = vsnprintf(buf, sizeof(buf), fmt, ap);
	va_end(ap);
	if (n > -1 && n < (int)sizeof(buf)) {
		socket_send(fd, buf, n);
	}
}

static int queryDNS(event_util_t *u, udp_sock_t *c)
{
	int ret = 0;
	int wlen = 0;
    const char *nameserver = get_rand_nameserver_ip();
    if (*(c->buf + c->bufLen) == '@') {
        nameserver = c->buf + c->bufLen + 1;
    } else {
        nameserver = get_rand_nameserver_ip();
    }

	if (gconf->useTcp) {
		c->fd = socket_tcpconnect4(nameserver, 53, 1000);
		if (c->fd == -1) {
			c->status = ST_IDLE;
			logs("E: socket_tcpconnect4, %d\n", errno);
			return -1;
		}

		ret = eu_add_readfd(u, c->fd, 0);
		if (ret == -1) {
			logs("E: eu_add_readfd: %d\n", errno);
			exit(1);
		}

		c->tcpbuf[0] = (c->bufLen >> 8) & 0xFF;
		c->tcpbuf[1] = c->bufLen & 0xFF;
		wlen = c->bufLen+2;
		ret = socket_send(c->fd, c->tcpbuf, wlen);
	} else {
		c->fd = socket_udpconnect4(nameserver, 53, 1000);
		if (c->fd == -1) {
			c->status = ST_IDLE;
			logs("E: socket_udpconnect4, %d\n", errno);
			return -1;
		}

		ret = eu_add_readfd(u, c->fd, 0);
		if (ret == -1) {
			logs("E: eu_add_readfd: %d\n", errno);
			exit(1);
		}

		wlen = c->bufLen;
		ret = socket_send(c->fd, c->buf, wlen);
	}
	if (ret != wlen) {
		logs("E: socket_send: %d.\n", errno);
		eu_del_fd(u, c->fd);
		c->fd = -1;
		c->status = ST_IDLE;
		do_send_0response(u, c);
		return -1;
	} else {
		c->status = ST_WAIT_ANS;
	}
	return 0;
}

#ifdef _EXECINFO_H
void print_trace (int sig, siginfo_t *info)
{
	void *array[32];
	size_t size;
	size_t i;

	size = backtrace (array, 32);

	int fd = open("core.txt", O_WRONLY | O_CREAT, 0644);
	if (fd == -1) {
		return;
	}
	char buf[8*1024];
	int len = snprintf(buf, sizeof(buf), "si_signo=%d si_errno=%d si_code=%d\n",
			info->si_signo, info->si_errno, info->si_code);
	switch(sig) {
		case SIGABRT:
		case SIGSEGV:
		case SIGBUS:
		case SIGFPE:
		case SIGILL:
			len += snprintf(buf + len, sizeof(buf) - len, "si_addr=%p\n", info->si_addr);
			break;
	}
	write(fd, buf, len);
	backtrace_symbols_fd(array, size, fd);
	len = snprintf(buf, sizeof(buf), "addr2line -C -e ./dnsproxy -f -i");
	write(fd, buf, len);
	for (i = 0; i < size; i++) {
		len = snprintf(buf, sizeof(buf), " %p", array[i]);
		write(fd, buf, len);
	}

	write(fd, "\n\n", 2);
	close(fd);
}
#endif

void unexpectedsignal(int sig, siginfo_t * info, void *)
{
	logs("unexpected exit signal %d code=%d\n", sig, info->si_code);
#ifdef _EXECINFO_H
	if (sig != SIGTERM) print_trace(sig, info);
#endif
	uninit_conf();
	exit(2);
}

void signalsetup()
{
	int catch_sig[] = {SIGILL,SIGQUIT,SIGFPE,SIGBUS,SIGSEGV,SIGSYS,SIGTERM,
#ifdef SIGPWR
		SIGPWR,
#endif
#ifdef SIGEMT
	SIGEMT,
#endif
#ifdef SIGSTKFLT
	SIGSTKFLT,
#endif
	0
	};

	int i = 0;
	struct sigaction act;
	act.sa_flags = SA_SIGINFO;
	act.sa_sigaction = unexpectedsignal;
	for (i=0;catch_sig[i];++i) {
		sigaction(catch_sig[i], &act, NULL);
	}
	return;
}

void eu_on_accept_tcp(event_util_t *u, int fd, uint32_t)
{
	while(1) {
		int s = accept(fd, NULL, NULL);
		if (s < 0) {
			if (errno == EINTR) {
				continue;
			}
			return;
		}
		setnonblocking(s);
		if (eu_add_readfd(u, s, 1) == -1) {
			logs("eu_add_readfd fail : %d\n", errno);
			exit(1);
		}
	}
}

static void do_request(event_util_t *u, udp_sock_t *c)
{
	if (c->bufLen < 1) {
		return;
	}

	GetTimeCurrent( c->start );
	gconf->last_serv = c->start.tv_sec;
	char domain[100];
	int dot = 0;
	int type = 0;
	int ret = getQueryDomain(c->buf, c->bufLen, domain, sizeof(domain), &dot, &type);
	if ((type == ns_t_a || type == ns_t_aaaa)) {
		//dumpHex(c->buf, c->bufLen);
		int wlen = BUF_SIZE - c->bufLen;
		if (ENABLE_CACHE) {
			if (type == ns_t_a){
				ret = cache_hit(1, domain, c->buf + c->bufLen, &wlen);
			} else {
				ret = cache_hit(0, domain, c->buf + c->bufLen, &wlen);
			}
		} else {
			ret = -1;
		}

		if (ret > -1) {
			struct  DNS_HEADER *dns = (struct DNS_HEADER *)(c->buf);
			dns->qr = 1;
			dns->ra = 1;
			dns->ans_count_h  = (ret >> 8) & 0xFF;
			dns->ans_count_l  = ret & 0xFF;
			c->bufLen += wlen;
			c->status = ST_IDLE;
			c->fd = -1;

			ret = do_send_response(u, c);
			if (ret == -1) {
				logs("E: do_send_response: %d\n", errno);
			}
			GetTimeCurrent(gconf->tmnow);
			SetTimeUsed(ret, c->start, gconf->tmnow);
			logs("C:%d %s %dus\n", type, domain, ret);
		} else {
			int wlen = BUF_SIZE - c->bufLen;
			ret = cache_static_hit(domain, dot, c->buf + c->bufLen, &wlen, type);
			if (ret > 0) {
				struct  DNS_HEADER *dns = (struct DNS_HEADER *)(c->buf);
				dns->qr = 1;
				dns->ra = 1;
				dns->ans_count_h  = 0;
				dns->ans_count_l  = ret & 0xFF;

				c->bufLen += wlen;
				c->status = ST_IDLE;

				ret = do_send_response(u, c);
				if (ret == -1) {
					logs("E: do_send_response %d\n", errno);
				}

				GetTimeCurrent(gconf->tmnow);
				SetTimeUsed(ret, c->start, gconf->tmnow);
				logs("S:%d %s %dus\n", type, domain, ret);
			} else {
                if (ret != -2) {
                    *(c->buf + c->bufLen) = 0;
                }
				c->status = ST_QUERY;
			}
		}
	} else if (type == ns_t_ptr) {
		if (strstr(domain, "._dns-sd.")) {
			do_send_0response(u, c);
			c->status = ST_IDLE;
			c->fd = -1;
			logs("E: reject PTR %s\n", domain);
		} else {
			int wlen = BUF_SIZE - c->bufLen;
			ret = cache_hit_ptr(domain, c->buf + c->bufLen, &wlen);
			if (ret == 0) {
				struct  DNS_HEADER *dns = (struct DNS_HEADER *)(c->buf);
				dns->qr = 1;
				dns->ra = 1;
				dns->ans_count_h  = 0;
				dns->ans_count_l  = 1;
				c->bufLen += wlen;
				c->status = ST_IDLE;
				c->fd = -1;
				do_send_response(u, c);
				//dumpHex(c->buf, c->bufLen);
			} else {
				c->status = ST_QUERY;
			}
		}
	} else {
		c->status = ST_QUERY;
	}

	if (c->status == ST_QUERY) {
		queryDNS(u, c);
	}
}

static int do_send_0response(event_util_t *u, udp_sock_t *c)
{
	struct  DNS_HEADER *dns = (struct DNS_HEADER *)(c->buf);
	dns->qr = 1;
	dns->ra = 1;
	dns->ans_count_h  = 0;
	dns->ans_count_l  = 0;
	c->status = ST_IDLE;

	return do_send_response(u, c);
}

static int do_send_response(event_util_t *u, udp_sock_t *c)
{
	int ret = 0;
	if (c->istcp) {
		c->tcpbuf[0] = (c->bufLen >> 8) & 0xFF;
		c->tcpbuf[1] = c->bufLen & 0xFF;
		ret = socket_send(c->reqfd, c->tcpbuf, c->bufLen + 2);
		eu_del_fd(u, c->reqfd);
		c->reqfd = -1;
	} else {
		ret = sendto(gconf->listen_udpfd, c->buf, c->bufLen, 0, (struct sockaddr*)&c->from_addr, sizeof(struct sockaddr_in));
	}
	return ret;
}

void eu_on_accept_udp(event_util_t *u, int fd, uint32_t)
{
	int i;
	udp_sock_t *UDPSock = (udp_sock_t *)eu_get_userdata(u);
	for (i=0; i<MAXSOCKET; i++) {
		if (UDPSock[i].status == ST_IDLE) {
			UDPSock[i].istcp = 0;
			socklen_t inlen = sizeof(struct sockaddr_in);
			int bytecount = recvfrom(fd, UDPSock[i].buf, BUF_SIZE, 0, (struct sockaddr*)&UDPSock[i].from_addr, &inlen);
			UDPSock[i].bufLen = bytecount;
			//.from_addr.sin_family, inet_ntoa(.from_addr.sin_addr)
			return do_request(u, UDPSock + i);
			break;
		}
	}
}

void eu_on_read_tcp(event_util_t *u, int fd, uint32_t events)
{
	if (events & EPOLLRDHUP) {
		if (gconf->logfd == fd) {
			gconf->logfd = -2;
		}
		eu_del_fd(u, fd);
		return;
	}

	char buf[256];
	int bytecount = recv(fd, buf, 2, MSG_PEEK);
	if (bytecount < 2) {
		if (gconf->logfd == fd) {
			gconf->logfd = -2;
		}
		eu_del_fd(u, fd);
		return;
	}

	bytecount = ((buf[0] & 0xff) << 8) | (buf[1] & 0xff);
	if (bytecount == 1025) {
		memset(buf, 0, 12);
		int bytecount = socket_recv(fd, buf, 256);
		if (bytecount != 11 || strncmp(buf+2, "xudejian", 8)) {
			if (gconf->logfd == fd) {
				gconf->logfd = -2;
			}
			eu_del_fd(u, fd);
			return;
		}

		switch (buf[10]) {
			case 'R':
				// for re-set netdns1
				if (gconf->set_system_dns) {
					set_system_dns();
				}
				// end
				socket_send(fd, "SUCC\n", 5);
				break;
			case 'L':
				if (gconf->logfd > -1) {
					eu_del_fd(u, gconf->logfd);
				}
				gconf->logfd = fd;
				GetTimeCurrent(gconf->tmnow);
				bytecount = snprintf(buf, sizeof(buf), "LOGS\nstatic cache:%ld idle:%ld host:%s ip:%s\n",
						static_cache_count(), gconf->tmnow.tv_sec - gconf->last_serv, gconf->host_name, inet_ntoa(gconf->eth0));
				socket_send(fd, buf, bytecount);
				break;
			case 'Q':
				socket_send(fd, "QUIT\n", 5);
				exit(2);
				break;
			case 'P':
				bytecount = snprintf(buf, sizeof(buf), "PID=%d\n", getpid());
				socket_send(fd, buf, bytecount);
				break;
			case 'S':
				socket_send(fd, "SUCC\n", 5);
				break;
			default:
				if (gconf->logfd == fd) {
					gconf->logfd = -2;
				}
				eu_del_fd(u, fd);
				break;
		}
		return;
	} else {
		int i;
		udp_sock_t *UDPSock = (udp_sock_t *)eu_get_userdata(u);
		for (i=0; i<MAXSOCKET; i++) {
			if (UDPSock[i].status == ST_IDLE) {
				UDPSock[i].istcp = 1;
				UDPSock[i].reqfd = fd;
				int bytecount = socket_recv(fd, UDPSock[i].tcpbuf, BUF_SIZE+2);
				UDPSock[i].bufLen = bytecount - 2;
				//.from_addr.sin_family, inet_ntoa(.from_addr.sin_addr)
				return do_request(u, UDPSock + i);
				break;
			}
		}
	}
}

void eu_on_read_dns(event_util_t *u, int fd, uint32_t events)
{
	int i;
	udp_sock_t *UDPSock = (udp_sock_t *)eu_get_userdata(u);
	for (i=0; i<MAXSOCKET; ++i) {
		if (UDPSock[i].fd == fd) {
			break;
		}
	}
	if (i >= MAXSOCKET) {
		logs("can't seek fd=%d in UDPSock events=%#x\n", fd, events);
		eu_del_fd(u, fd);
		return;
	}
	int bytecount = 0;
	if (gconf->useTcp || UDPSock[i].istcp) {
		bytecount = socket_recv(UDPSock[i].fd, UDPSock[i].tcpbuf, BUF_SIZE+2);
	} else {
		bytecount = socket_recv(UDPSock[i].fd, UDPSock[i].buf, BUF_SIZE);
	}
	//printf("Read from DNS server! byte:%d\n", bytecount);
	if(bytecount>0) {
		if (gconf->useTcp) {
			UDPSock[i].bufLen = bytecount - 2;
		} else {
			UDPSock[i].bufLen = bytecount;
		}
		eu_del_fd(u, fd);
		UDPSock[i].fd = -1;
		UDPSock[i].status = ST_WAIT_CACHE_ADD;
		int ret = do_send_response(u, UDPSock + i);
		if (ret == -1) {
			logs("E: do_send_response fail : %d\n", errno);
		}
	} else {
		logs("E: read from DNS server errno=%d\n", errno);
		eu_del_fd(u, fd);
		UDPSock[i].fd = -1;
		UDPSock[i].status = ST_IDLE;
		do_send_0response(u, UDPSock + i);
	}
}

static void set_system_dns()
{
#ifdef ANDROID
	char line[512];
	char net_dns[2][PROP_VALUE_MAX];
	int rv = 0;
	int i = 0;
	for (i=0; i<2; ++i) {
		sprintf(line, "net.dns%d", i+1);
		rv = __system_property_get(line, net_dns[i]);
		if (rv < 0) {
			rv = 0;
		}
		net_dns[i][rv] = 0;
		logs("%s %s\n", line, net_dns[i]);
	}

	if (!strcmp(net_dns[0], "127.0.0.1")) {
		return;
	}
	memcpy(gconf->net_dns, net_dns, sizeof(net_dns));

	rv = snprintf(line, sizeof(line), "setprop net.dns1 127.0.0.1");
	logs("%s\n", line);
	rv = system(line);
	logs("setprop ret:%d\n", rv);
#endif
}

static void reset_system_dns()
{
#ifdef ANDROID
	char line[512];
	char net_dns[PROP_VALUE_MAX];
	int rv = 0;
	int i = 0;
	if (NULL != gconf->fix_system_dns) {
		const char *pstart = gconf->fix_system_dns;
		i = 1;
		do {
			const char *p = strchr(pstart, ',');
			if (NULL == p) {
				p = pstart + strlen(pstart);
			}
			rv = snprintf(line, sizeof(line), "setprop net.dns%d %.*s", i, p - pstart, pstart);
			if (rv > 0) {
				rv = system(line);
				logs("re %s ret:%d\n", line, rv);
			}
			if ('\0' == *p) {
				break;
			}
			if (++i > 2) {
				break;
			}
			pstart = p + 1;
		} while (*pstart);
	}

	if (gconf->net_dns[0][0] == 0) {
		return;
	}
	if (!strcmp(gconf->net_dns[0], "127.0.0.1")) {
		return;
	}
	rv = __system_property_get("net.dns1", net_dns);
	if (rv < 0) {
		rv = 0;
	}
	net_dns[rv] = 0;
	logs("net.dns1 %s\n", net_dns);

	if (strcmp(net_dns, "127.0.0.1")) {
		logs("need not reset net.dns1=%s\n", net_dns);
		return;
	}

	rv = snprintf(line, sizeof(line), "setprop net.dns1 %s", gconf->net_dns[0]);
	if (rv > 0) {
		rv = system(line);
		logs("re %s ret:%d\n", line, rv);
	}
#endif
}

static void uninit_conf()
{
	if (gconf) {
#ifndef ANDROID
		dumpMemStatus();
#endif
		if (gconf->set_system_dns) {
			reset_system_dns();
		}
		eu_free(gconf->eu);
		free (gconf);
		gconf = NULL;
	}
}

void init_conf(int argc, char * const *argv)
{
	if (gconf) return;

	gconf = (conf_t *) malloc (sizeof(conf_t));
	if (gconf == NULL) {
		logs("malloc conf_t fail\n");
		exit (EXIT_FAILURE);
	}
	memset(gconf, 0, sizeof(conf_t));
    gconf->clean_cache_gap = MIN_CLEAN_CACHE_GAP;
	gconf->logfd = -2;
#ifndef ANDROID
	logs("sizeof(conf_t)=%zd\n", sizeof(conf_t));
#endif

#ifndef ANDROID
	const char *username = NULL;
	const char *usergroup = NULL;
#endif
	int nodaemon = 0;
	int ch = 0;
	const char *listen_addr = NULL;
	const char *remote_dns = NULL;
	while ((ch = getopt(argc, argv, "C:r:a:d:i:g:f:U:G:Dts?vh"))!= -1) {
		switch (ch) {
			case 'a':
				listen_addr = optarg;
				break;
			case 'd':
				gconf->db_filename = optarg;
				break;
			case 'C':
				gconf->confd = optarg;
				break;
			case 'D':
				nodaemon = 1;
				break;
			case 'i':
				gconf->max_idle_time = atoi(optarg);
				break;
			case 'g':
				gconf->clean_cache_gap = atoi(optarg);
				break;
			case 's':
				gconf->set_system_dns = 1;
				break;
			case 't':
				gconf->useTcp = 1;
				break;
			case 'r':
				remote_dns = optarg;
				break;
			case 'f':
				gconf->fix_system_dns = optarg;
				break;
#ifndef ANDROID
			case 'U':
				username = optarg;
				break;
			case 'G':
				usergroup = optarg;
				break;
#endif
			case 'v':
				printversion();
				exit(EXIT_SUCCESS);
				break;
			case '?':case 'h':
				printHelp();
				exit(EXIT_SUCCESS);
				break;
			default:
				break;
		}
	}

	cache_clean();
	if (gconf->set_system_dns) {
		set_system_dns();
	}

#ifdef ANDROID
	if (!strstr(argv[0], APP_NAME)) {
		char cwd[256];
		memset(cwd, 0, sizeof(cwd));
		getcwd(cwd, sizeof(cwd));
		if (!strstr(cwd, APP_NAME)) {
			LOGE("Name fail\n");
			logs("Name fail\n");
			exit(1);
		}
	}
#endif

	gconf->eu = eu_new(MAXSOCKET);
	if (gconf->eu == NULL) {
		logs("Error: %d eu_new\n", errno);
		free(gconf);
		gconf = NULL;
		exit (EXIT_FAILURE);
	}

	eu_set_userdata(gconf->eu, gconf->UDPSock);
	eu_set_onaccept_tcp(gconf->eu, eu_on_accept_tcp);
	eu_set_onread_tcp(gconf->eu, eu_on_read_tcp);
	eu_set_onaccept_udp(gconf->eu, eu_on_accept_udp);
	eu_set_onread_udp(gconf->eu, eu_on_read_dns);

	int ret = 0;
	int fd = socket_tcplisten_port("127.0.0.1", 53, 5);
	if (fd == -1) {
		logs("Server: tcp bind() failed! Error: %d.\n", errno);
		exit(EXIT_FAILURE);
	}
	setnonblocking(fd);

	ret = eu_add_listenfd(gconf->eu, fd, 1);
	if (ret) {
		exit(EXIT_FAILURE);
	}

	fd = socket_udp_bind_port(listen_addr, 53);
	if (fd == -1) {
		logs("Server: udp bind() failed! Error: %d.\n", errno);
		exit(EXIT_FAILURE);
	}
	setnonblocking(fd);

	ret = eu_add_listenfd(gconf->eu, fd, 0);
	if (ret) {
		exit(EXIT_FAILURE);
	}

	gconf->listen_udpfd = fd;

	{
		gconf->host_name[0] = 0;
		gconf->eth0.s_addr = 0;
		if (!gethostname(gconf->host_name, sizeof(gconf->host_name))) {
		} else {
			strcpy(gconf->host_name, "localhost");
		}
		struct ifreq ifr;
		int inet_sock = socket(AF_INET, SOCK_DGRAM, 0);
		strcpy(ifr.ifr_name, "eth0");
		if (ioctl(inet_sock, SIOCGIFADDR, &ifr) < 0) {
			gconf->eth0.s_addr = 0;
		} else {
			gconf->eth0.s_addr = ((struct sockaddr_in*)&(ifr.ifr_addr))->sin_addr.s_addr;
		}
		close (inet_sock);
		logs("host:%s ip:%s\n", gconf->host_name, inet_ntoa(gconf->eth0));
	}

	logs("Success. DNS Proxy.\n");

#if !defined(__APPLE__)
	if (!nodaemon) {
		if (daemon(1, 0)) {
			logs("Error: daemon %d\n", errno);
			setsid();
		}
	}
#endif

#ifndef ANDROID
	if (geteuid() == 0) {
		uid_t uid;
		gid_t gid;
		const char *tusername = NULL;
		ch = find_user_group(username, usergroup, &uid, &gid, &tusername);
		if (ch == 0) {
			if (setgid(gid) == -1) {
			}

			if (tusername) {
				if (initgroups(tusername, gid) == -1) {
				}
			}

			if (setuid(uid) == -1) {
			}
		}
	}
#endif

	if (ENABLE_CACHE && gconf->clean_cache_gap < MIN_CLEAN_CACHE_GAP) {
		gconf->clean_cache_gap = MIN_CLEAN_CACHE_GAP;
	}

	GetTimeCurrent(gconf->tmnow);
	gconf->last_serv = gconf->tmnow.tv_sec;
	gconf->last_clean_cache = gconf->tmnow.tv_sec;

	init_nameserver(remote_dns);

    load_db_dnscache();
    logs("pid:%d load_db_dnscache %d\n", getpid(), static_cache_count());
	atexit(uninit_conf);
}

int main(int argc, char * const *argv)
{
	signal(SIGPIPE, SIG_IGN);
	init_conf (argc, argv);
	signalsetup();
	udp_sock_t *UDPSock = gconf->UDPSock;
	event_util_t *eu = gconf->eu;

	int i;
	for (i=0; i<MAXSOCKET; i++) {
		UDPSock[i].fd = -1;
	}

	while (1) {
		int delay = 0;
		int ret = eu_once(eu, MAXDELAY/1000);
		if (ret == -1) {
			if (errno == EINTR) {
				continue;
			}
		}
		switch (ret) {
			case 0:
				for (i=0; i<MAXSOCKET; ++i) {
					switch (UDPSock[i].status) {
						case ST_WAIT_ANS:
							SetTimeUsed(delay, UDPSock[i].start, gconf->tmnow);
							if ( delay > MAXDELAY ) {
								eu_del_fd(eu, UDPSock[i].fd);
								UDPSock[i].fd = -1;
								UDPSock[i].status = ST_IDLE;
								char domain[100];
								ret = getQDomain(UDPSock[i].buf, UDPSock[i].bufLen, domain, sizeof(domain));
								if (ret > 0) {
									logs("O: %s\n", domain);
								}
								do_send_0response(eu, UDPSock+i);
							}
							break;
						default:
							break;
					}
				}
				break;

			case -1:
				logs("E: %d eu_once\n", errno);
				break;

			default:
				break;
		}
		GetTimeCurrent(gconf->tmnow);

		int cache_add_count = 0;
		for (i=0; i<MAXSOCKET; ++i) {
			if (UDPSock[i].status == ST_WAIT_CACHE_ADD) {
				UDPSock[i].status = ST_IDLE;
				char ans[BUF_SIZE];
				char domain[100];
				int type = 0;
				ret = getResAAAAAAns(UDPSock[i].buf, UDPSock[i].bufLen, &type, domain, sizeof(domain), ans, BUF_SIZE);
				if (ret > 0) {
					if (type == ns_t_a) {
						if (ENABLE_CACHE) {
							if (cache_add(1, domain, ans, ret) == 0) {
								++cache_add_count;
							}
						}
						SetTimeUsed(delay, UDPSock[i].start, gconf->tmnow);
						logs("A:4 %s %dus\n", domain, delay);
					} else if (type == ns_t_aaaa) {
						if (ENABLE_CACHE) {
							if (cache_add(0, domain, ans, ret) == 0) {
								++cache_add_count;
							}
						}
						SetTimeUsed(delay, UDPSock[i].start, gconf->tmnow);
						logs("A:6 %s %dus\n", domain, delay);
					} else {
						logs("Q:%d %s\n", type, domain);
					}
				}
			}
		}
/*
		if (cache_add_count) {
			GetTimeCurrent(gconf->tmnow);
			SetTimeUsed(delay, tmnow, gconf->tmnow);
			logs("cache %d %dus\n", cache_add_count, delay);
		}
*/
		if (IDLE_TOO_LONG) {
			logs("idle timeout\n");
#ifdef ANDROID
			system("am startservice -n me.xu.DNSLite/me.xu.DNSLite.DNSService -e _idle_exit 1");
#endif
			break;
		}

		if (NEED_CLEAN_CACHE) {
			cache_clean();
		}
	}

	logs("Normally Exit\n");
	return EXIT_SUCCESS;
}

#ifndef ANDROID
static int find_user_group(const char *user, const char *group, uid_t *uid, gid_t *gid, const char **username)
{
	uid_t my_uid = 0;
	gid_t my_gid = 0;
	struct passwd *my_pwd = NULL;
	struct group *my_grp = NULL;
	char *endptr = NULL;
	*uid = 0; *gid = 0;
	if (username) *username = NULL;

	if (user) {
		my_uid = strtol(user, &endptr, 10);

		if (my_uid <= 0 || *endptr) {
			if (NULL == (my_pwd = getpwnam(user))) {
				logs("can't find user name %s\n", user);
				return -1;
			}
			my_uid = my_pwd->pw_uid;

			if (my_uid == 0) {
				logs("I will not set uid to 0\n");
				return -1;
			}

			if (username) *username = user;
		} else {
			my_pwd = getpwuid(my_uid);
			if (username && my_pwd) *username = my_pwd->pw_name;
		}
	}

	if (group) {
		my_gid = strtol(group, &endptr, 10);

		if (my_gid <= 0 || *endptr) {
			if (NULL == (my_grp = getgrnam(group))) {
				logs("can't find group name %s\n", group);
				return -1;
			}
			my_gid = my_grp->gr_gid;

			if (my_gid == 0) {
				logs("I will not set gid to 0\n");
				return -1;
			}
		}
	} else if (my_pwd) {
		my_gid = my_pwd->pw_gid;

		if (my_gid == 0) {
			logs("I will not set gid to 0\n");
			return -1;
		}
	}

	*uid = my_uid;
	*gid = my_gid;
	return 0;
}
#endif
