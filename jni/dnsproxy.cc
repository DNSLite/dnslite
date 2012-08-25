#include "dnsproxy.h"
#include    <map>
#include    <string>
using namespace std;

#ifdef ANDROID
#define APP_NAME "me.xu.DNSLite"
#include <sys/system_properties.h>
//#include <android/log.h>
#endif

#ifdef _ANDROID_LOG_H
# define  LOG_TAG    "libdnslite.so"
# define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
# define  LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG,LOG_TAG,__VA_ARGS__)
# define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)
#else
# define  LOGI(...)
# define  LOGD(...)
# define  LOGE(...)
#endif

#define ns_t_a    1
#define ns_t_ptr  12
#define ns_t_aaaa 28

#define	MAXSOCKET    512
#define	MAXDELAY     2000000

#define GetTimeCurrent(tv) gettimeofday(&tv, NULL)
#define SetTimeUsed(tused, tv1, tv2) { \
	tused  = (tv2.tv_sec-tv1.tv_sec) * 1000000; \
	tused += (tv2.tv_usec-tv1.tv_usec); \
	if (tused == 0){ tused+=1; } \
}

#define MIN_CLEAN_CACHE_GAP 86400

#define MAX_NAMESERVER_NUM 2
#define MAX_IP_LEN 16
#define MAX_DOMAIN_LEN 65

std::map <string, string> static_cache;
std::map <string, string> dnsa_cache;
std::map <string, string> dnsaaaa_cache;

typedef struct conf_t {
	int useTcp;
	int nameserver_num;
	char nameservers[MAX_NAMESERVER_NUM][MAX_IP_LEN];
	udp_sock_t UDPSock[MAXSOCKET];
	struct timeval tmnow;
	time_t last_serv;
	time_t last_clean_cache;
	unsigned int clean_cache_gap;
	unsigned int max_idle_time;
	int listen_udpfd;
	int logfd;
	epoll_util_t *eu;
	int set_system_dns;
#ifdef ANDROID
	char net_dns[2][PROP_VALUE_MAX];
#endif
	const char *db_filename;
	struct in_addr eth0;
	char host_name[MAXHOSTNAMELEN];
	const char *fix_system_dns;
} conf_t;

#define ENABLE_CACHE (gconf->clean_cache_gap)
#define NEED_CLEAN_CACHE (ENABLE_CACHE && (gconf->tmnow.tv_sec - gconf->last_clean_cache > (int)gconf->clean_cache_gap))
#define ENABLE_AUTO_EXIT (gconf->max_idle_time)
#define IDLE_TOO_LONG (ENABLE_AUTO_EXIT && gconf->tmnow.tv_sec - gconf->last_serv > (int)gconf->max_idle_time)

static conf_t *gconf;

#ifndef ANDROID
static int find_user_group(const char *user, const char *group, uid_t *uid, gid_t *gid, const char **username);
#endif

static int do_send_response(epoll_util_t *u, udp_sock_t *c);
static int do_send_0response(epoll_util_t *u, udp_sock_t *c);
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

static void logs(const char *fmt, ...)
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

static int init_nameserver(const char *arg)
{
	memset(gconf->nameservers, 0, sizeof(gconf->nameservers));
	gconf->nameserver_num = 0;
	if (arg) {
		const char *start = arg;
		while (gconf->nameserver_num < MAX_NAMESERVER_NUM) {
			while (*start) {
				if (*start == ' ' || *start == ',') {
					++start;
					continue;
				}
				break;
			}
			const char *end = start;
			while (*end) {
				if (*end == ' ' || *end == ',') {
					break;
				}
				++end;
			}
			if (end == start) {
				break;
			}
			if (end - start < MAX_IP_LEN) {
				strncpy(gconf->nameservers[ gconf->nameserver_num ], start, end-start);
				gconf->nameservers[ gconf->nameserver_num ][end-start] = '\0';
				if (ip_check(gconf->nameservers[ gconf->nameserver_num ], 
							gconf->nameservers[ gconf->nameserver_num ], MAX_IP_LEN) != NULL) {
					++ gconf->nameserver_num;
				}
			}
			start = end;
		}
	}
	if (gconf->nameserver_num < 1) {
		snprintf(gconf->nameservers[0], MAX_IP_LEN, "8.8.8.8");
		snprintf(gconf->nameservers[1], MAX_IP_LEN, "8.8.4.4");
		gconf->nameserver_num = 2;
	}
	return 0;
}

static const char *get_rand_nameserver_ip()
{
	static int t = 0;
	assert (gconf->nameserver_num > 0);
	++t;
	t %= gconf->nameserver_num;
	return gconf->nameservers[t];
}

static int queryDNS(epoll_util_t *u, udp_sock_t *c)
{
	int ret = 0;
	int wlen = 0;
	if (gconf->useTcp) {
		c->fd = socket_tcpconnect4(get_rand_nameserver_ip(), 53, 1000);
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
		c->fd = socket_udpconnect4(get_rand_nameserver_ip(), 53, 1000);
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
	int catch_sig[] = {SIGILL,SIGQUIT,SIGFPE,SIGBUS,SIGSEGV,SIGSYS,SIGPWR,SIGTERM,
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

void eu_on_accept_tcp(epoll_util_t *u, int fd, uint32_t)
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

static int ptr_info_set(const char *domain, char *ans_cache, int *size)
{
	const int len = strlen(domain);
#define PTR_RES_LEN 12
	unsigned char buf[PTR_RES_LEN] = {0xc0, 0x0c, 0, 12, 0, 1, 0, 0, 0x1c, 0x20, 0, (unsigned char)(len+2)};

	if (PTR_RES_LEN + len + 2 < *size) {
		memcpy(ans_cache, buf, PTR_RES_LEN);
		*size = PTR_RES_LEN + len + 2;
		ans_cache += PTR_RES_LEN;
		char *prev = ans_cache;
		++ans_cache;
		int i = 0;
		for (i=0; i<len; ++i) {
			if (domain[i] == '.') {
				*prev = ans_cache + i - prev - 1;
				ans_cache[i] = 0;
				prev = ans_cache + i;
			} else {
				ans_cache[i] = domain[i];
			}
		}
		*prev = ans_cache + i - prev - 1;
		ans_cache[i] = 0;
		return i+1;
	}
	return 0;
}

static int cname2_info_set(char *ans_cache, int *size)
{
#define CNAME_RES_LEN 14
	unsigned char buf[CNAME_RES_LEN] = {0xc0, 0x0c, 0, 5, 0, 1, 0, 0, 0x1c, 0x20, 0, 2, 0xc0, 0x0c};
	if (CNAME_RES_LEN < *size) {
		memcpy(ans_cache, buf, CNAME_RES_LEN);
		*size = CNAME_RES_LEN;
		return CNAME_RES_LEN;
	}
	return 0;
}

#ifdef USE_SET_TRUE_CNAME
static int cname_info_set(const char *cname, char *ans_cache, int *size)
{
	const int len = strlen(cname);
	unsigned char buf[CNAME_RES_LEN] = {0xc0, 0x0c, 0, 5, 0, 1, 0, 0, 0x1c, 0x20, 0, (unsigned char)(len+2), 0, 0};

	if (CNAME_RES_LEN + len < *size) {
		memcpy(ans_cache, buf, CNAME_RES_LEN - 2);
		ans_cache += CNAME_RES_LEN - 2;
		*size = CNAME_RES_LEN + len;
		char *prev = ans_cache;
		++ans_cache;
		int i = 0;
		for (i=0; i<len; ++i) {
			if (cname[i] == '.') {
				*prev = ans_cache + i - prev - 1;
				ans_cache[i] = 0;
				prev = ans_cache + i;
			} else {
				ans_cache[i] = cname[i];
			}
		}
		*prev = ans_cache + i - prev - 1;
		ans_cache[i] = 0;
		return i+1;
	}
	return 0;
}
#endif

static int a_info_set(const char *addr4, char *ans_cache, int *size)
{
#define A_RES_LEN 16
	unsigned char buf[A_RES_LEN] = {0xC0, 0x0C, 0, 1, 0, 1, 0, 0, 0x1C, 0x20, 0, sizeof(struct in_addr)};
	if (A_RES_LEN < *size) {
		memcpy(buf+12, addr4, 4);
		memcpy(ans_cache, buf, A_RES_LEN);
		*size = A_RES_LEN;
		//dumpHex(UDPSock[sockNo].buf, bytecount);
		return 1;
	}
	return 0;
}

static int aaaa_info_set(const char *addr6, char *ans_cache, int *size)
{
#define AAAA_RES_LEN 28
	unsigned char buf[AAAA_RES_LEN] = {0xC0, 0x0C, 0, 28, 0, 1, 0, 0, 0x1C, 0x20, 0, sizeof(struct in6_addr)};
	if (AAAA_RES_LEN < *size) {
		memcpy(buf+12, addr6, 16);
		memcpy(ans_cache, buf, AAAA_RES_LEN);
		*size = AAAA_RES_LEN;
		//dumpHex(UDPSock[sockNo].buf, bytecount);
		return 1;
	}
	return 0;
}

static int cache_hit_ptr(const char *domain, char *ans_cache, int *size)
{
	const int len = strlen(domain);
	/* .in-addr.arpa 13 */
	if (len < 14) {
		return -1;
	}
	if (strncasecmp(domain + len - 13, ".in-addr.arpa", 13)) {
		return -1;
	}
	struct in_addr ptr_addr = {0};
	int i = 0;
	int y = 0;
	int num = 0;
	int k = 0;
	int w = 1;
	for (i=len-14; i>-1; --i) {
		if (domain[i] == '.') {
			ptr_addr.s_addr |= (num << y);
			y += 8;
			num = 0;
			w = 1;
			if (y > 24) {
				break;
			}
		} else {
			k = domain[i] - '0';
			if (k < 0 || k > 9) {
				return -1;
			}
			num += k * w;
			w *= 10;
		}
	}
	if (y != 24) {
		return -1;
	}
	ptr_addr.s_addr |= (num << y);

	if (gconf->eth0.s_addr == ptr_addr.s_addr) {
		logs("PTR: %s %s\n", domain, gconf->host_name);
		return ptr_info_set(gconf->host_name, ans_cache, size);
	} else if (16777343 == ptr_addr.s_addr) {
		logs("PTR: %s %s\n", domain, gconf->host_name);
		return ptr_info_set(gconf->host_name, ans_cache, size);
	}

	return -1;
}

static void do_request(epoll_util_t *u, udp_sock_t *c)
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

static int do_send_0response(epoll_util_t *u, udp_sock_t *c)
{
	struct  DNS_HEADER *dns = (struct DNS_HEADER *)(c->buf);
	dns->qr = 1;
	dns->ra = 1;
	dns->ans_count_h  = 0;
	dns->ans_count_l  = 0;
	c->status = ST_IDLE;

	return do_send_response(u, c);
}

static int do_send_response(epoll_util_t *u, udp_sock_t *c)
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

void eu_on_accept_udp(epoll_util_t *u, int fd, uint32_t)
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

void eu_on_read_tcp(epoll_util_t *u, int fd, uint32_t events)
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
				socket_send(fd, "SUCC\n", 5);
				// for re-set netdns1
				if (gconf->set_system_dns) {
					set_system_dns();
				}
				// end
				break;
			case 'L':
				if (gconf->logfd > -1) {
					eu_del_fd(u, gconf->logfd);
				}
				gconf->logfd = fd;
				GetTimeCurrent(gconf->tmnow);
				bytecount = snprintf(buf, sizeof(buf), "LOGS\nstatic cache:%ld idle:%ld host:%s ip:%s\n", 
						static_cache.size(), gconf->tmnow.tv_sec - gconf->last_serv, gconf->host_name, inet_ntoa(gconf->eth0));
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

void eu_on_read_dns(epoll_util_t *u, int fd, uint32_t events)
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
	int rv = 0;
	int i = 0;
	for (i=0; i<2; ++i) {
		sprintf(line, "net.dns%d", i+1);
		rv = __system_property_get(line, gconf->net_dns[i]);
		if (rv < 0) {
			rv = 0;
		}
		gconf->net_dns[i][rv] = 0;
		logs("%s %s\n", line, gconf->net_dns[i]);
	}

	if (!strcmp(gconf->net_dns[0], "127.0.0.1")) {
		return;
	}

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

void static_cache_add_line(char *line)
{
	if (line[0] == '#') {
		return;
	}
	int ip_len = 0;
	char *ip = line;
	char *domain = line;
	while (*domain) {
		if (*domain != ' ' && *domain != '\t') {
			++domain;
		} else {
			break;
		}
	}
	if (*domain) {
		*domain = 0;
		ip_len = domain - ip;
		++domain;
		while (*domain) {
			if (*domain == ' ' || *domain == '\t') {
				++domain;
			} else {
				break;
			}
		}
		char *p = domain;
		while (*p) {
			if (*p == '\r' || *p == '\n') {
				*p = 0;
				break;
			}
			++p;
		}
		int domain_len = p - domain;
		if (domain_len < 1 || ip_len < 1) {
			return;
		}
		LOGD("[%s] [%s]", ip, domain);
		static_cache.insert(pair<string,string>(domain, ip));
	}
}

int load_db_dnscache()
{
	FILE *fp = fopen (gconf->db_filename, "r");
	if (fp == NULL) {
		return -1;
	}

	char line[256];
	while (fgets(line, sizeof(line), fp) != NULL) {
		static_cache_add_line(line);
	}
	fclose(fp);
	return static_cache.size();
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
	while ((ch = getopt(argc, argv, "r:a:d:i:g:f:U:G:Dts?vh"))!= -1) {
		switch (ch) {
			case 'a':
				listen_addr = optarg;
				break;
			case 'd':
				gconf->db_filename = optarg;
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

	if (!nodaemon) {
		if (daemon(1, 0)) {
			logs("Error: daemon %d\n", errno);
			setsid();
		}
	}

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
	if (gconf->max_idle_time < 0) {
		gconf->max_idle_time = 0;
	}

	GetTimeCurrent(gconf->tmnow);
	gconf->last_serv = gconf->tmnow.tv_sec;
	gconf->last_clean_cache = gconf->tmnow.tv_sec;

	init_nameserver(remote_dns);

	ret = load_db_dnscache();
	logs("pid:%d load_db_dnscache %d\n", getpid(), ret);
	atexit(uninit_conf);
}

int main(int argc, char * const *argv)
{
	signal(SIGPIPE, SIG_IGN);
	init_conf (argc, argv);
	signalsetup();
	udp_sock_t *UDPSock = gconf->UDPSock;
	epoll_util_t *eu = gconf->eu;

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

int getQDomain(const char *query, const int qlen, char *domain, int dsize)
{
	int i, j;

	if (qlen < 14) {
		return -1;
	}
	i=13;
	j=0;
	while (query[i] != 0 && j < dsize) {
		if (query[i] < 0x20) {
			domain[j] = '.';
		} else {
			domain[j] = query[i];
		}
		++i;
		++j;
	}
	if (j == dsize) {
		return -1;
	}
	domain[j]='\0';
	return j;
}

int getQueryDomain(const char *query, const int qlen, char *domain, int dsize, int *dot, int *type)
{
	int i, j;

	*dot = 0;
	*type = 0;
	if (qlen < 14) {
		return -1;
	}
	i=13;
	j=0;
	while (query[i] != 0 && j < dsize) {
		if (query[i] < 0x20) {
			domain[j] = '.';
			++(*dot);
		} else {
			domain[j] = query[i];
		}
		++i;
		++j;
	}
	if (j == dsize) {
		return -1;
	}
	if (query[i] != 0) {
		return -1;
	}
	++i;
	if (i+4 > qlen) {
		return -1;
	}
	*type = ((query[i] & 0xFF) << 8) | (query[i+1] & 0xFF);
	domain[j]='\0';
	return j;
}

int getResAAAAAAns(const char *ans, const int len, int *type, char *domain, int dmsize, char *dst, int dst_size)
{
	int i, j;

	if (len < 14) {
		return -1;
	}
	i=13;
	j=0;
	while (ans[i] != 0 && j < dmsize) {
		if (ans[i] < 0x20) {
			domain[j] = '.';
		} else {
			domain[j] = ans[i];
		}
		++i;
		++j;
	}
	if (j == dmsize) {
		return -1;
	}
	domain[j]='\0';
	++i;
	if (i+1 < len) {
		*type = ((ans[i] & 0xFF) << 8)|(ans[i+1] & 0xFF);
	} else {
		return -1;
	}
	i+=4;
	dst[0] = ans[6];
	dst[1] = ans[7];
	if (dst_size > len - i) {
		memcpy(dst+2, ans+i, len - i);
		return len - i + 2;
	}
	return -1;
}

#ifdef ONLY_CACHA_IPRES
static int getResAAAAAAns_ip(const char *ans, const int len, int *type, char *domain, int dmsize, char *dst, int dst_size)
{
	int i, j;

	if (len < 14) {
		return -1;
	}
	//int ans_count = ((ans[6] & 0xFF) << 8)|(ans[7] & 0xFF);
	i=13;
	j=0;
	while (ans[i] != 0 && j < dmsize) {
		if (ans[i] < 0x20) {
			domain[j] = '.';
		} else {
			domain[j] = ans[i];
		}
		++i;
		++j;
	}
	if (j == dmsize) {
		return -1;
	}
	domain[j]='\0';
	++i;
	if (i+1 < len) {
		*type = ((ans[i] & 0xFF) << 8)|(ans[i+1] & 0xFF);
	} else {
		return -1;
	}
	i+=4;
	int find_count = 0;
	j = 2;
	dst[0] = 0;
	dst[1] = 0;
	while (i < len) {
		if ((ans[i]&0xFF) == 0xC0 && i+3<len) {
			if (ans[i+2] == 0 && (ans[i+3] == ns_t_a || ans[i+3] == ns_t_aaaa)) {
				if (i + 11 < len) {
					int dlen = 12 + (((ans[i+10] & 0xFF) << 8)|(ans[i+11] & 0xFF));
					if (i+dlen-1 < len) {
						if (j + dlen < dst_size) {
							memcpy(dst+j, ans + i, dlen);
							dst[j+1] = 0x0C;
							j+=dlen;
							find_count++;
						}
						i+=dlen;
					} else {
						break;
					}
				} else {
					break;
				}
			} else {
				i+=10;
				if (i+1 < len) {
					int skip = ((ans[i] & 0xFF) << 8)|(ans[i+1] & 0xFF);
					i+=2;
					while (i<len && skip > 0) {
						if ((ans[i]&0xFF) == 0xC0) {
							++i;
							--skip;
						}
						++i;
						--skip;
					}
				}
			}
		} else {
			break;
		}
	}
	dst[0] = (find_count >> 8) & 0xFF;
	dst[1] = find_count & 0xFF;

	return j;
}
#endif

int cache_static_hit(const char *domain, int dot, char *ans_cache, int *size, const int query_type)
{
	map<string, string>::iterator it;

	char addr[100];
	strncpy(addr, domain, 100);
	addr[99] = 0;

	if (addr[0] != '.') {
		++dot;
	}
	int num = -1;
	int i = 0;
	do {
		it = static_cache.find(addr+i);
		if (it != static_cache.end()) {
			int addr_type = -1;
			int wlen = 0;
			struct in6_addr addr;

			int ret = inet_pton(AF_INET, (*it).second.c_str(), (struct in_addr*)&addr);
			if (ret > 0) {
				addr_type = AF_INET;
			} else {
				ret = inet_pton(AF_INET6, (*it).second.c_str(), &addr);
				if (ret > 0) {
					addr_type = AF_INET6;
				} else {
					logs("E: inet_pton: %d\n", errno);
					break;
				}
			}
			int cnlen = *size;
			ret = cname2_info_set(ans_cache, &cnlen);
			if (ret > 0) {
				ans_cache += cnlen;
				wlen = cnlen;
			} else {
				wlen = 0;
			}
			cnlen = *size - wlen;
			if (addr_type == AF_INET) {
				ret = a_info_set((const char *)&addr, ans_cache, &cnlen);
			} else {
				ret = aaaa_info_set((const char *)&addr, ans_cache, &cnlen);
			}
			if (ret > 0) {
				*size = cnlen + wlen;
				num = 2;
				//dumpHex(UDPSock[sockNo].buf, bytecount);
			}
			break;
		} else {
			while (addr[i] == '*') ++i;
			while (addr[i] == '.') ++i;
			while (addr[i] && addr[i] != '.') ++i;
			if (addr[i] && i > 0) {
				--i;
				addr[i] = '*';
				--dot;
			} else {
				break;
			}
		}
	} while (dot > 1);

	return num;
}

int cache_add(int is_dnsa, const char *domain, const char *ans, int len)
{
	if (len < 1) {
		return -1;
	}
	string binAns(ans, len);
	if (is_dnsa) {
		if (dnsa_cache.size() > 10000) {
			dnsa_cache.clear();
		}
		dnsa_cache.insert(pair<string,string>(domain, binAns));
	} else {
		if (dnsaaaa_cache.size() > 10000) {
			dnsaaaa_cache.clear();
		}
		dnsaaaa_cache.insert(pair<string,string>(domain, binAns));
	}
	return 0;
}

int cache_hit(int is_dnsa, const char *domain, char *ans_cache, int *size)
{
	map<string, string>::iterator it;
	map<string, string>::iterator end;
	if (is_dnsa) {
		it = dnsa_cache.find(domain);
		end = dnsa_cache.end();
	} else {
		it = dnsaaaa_cache.find(domain);
		end = dnsaaaa_cache.end();
	}
	int num = -1;
	if (it != end) {
		int blob_size = (*it).second.length();
		if (*size > blob_size - 2 && blob_size > 1) {
			const char *ans = (*it).second.c_str();
			num = (((ans[0] & 0xFF) << 8)|(ans[1]&0xFF));
			memcpy(ans_cache, ans+2, blob_size - 2);
			*size = blob_size - 2;
		}
	}
	//logs("cache hit %s %d\n", domain, num);
	return num;
}

void cache_clean()
{
	dnsa_cache.clear();
	dnsaaaa_cache.clear();
	gconf->last_clean_cache = gconf->tmnow.tv_sec;
	logs("cache clear\n");
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

