#ifndef _GNU_SOURCE
#define _GNU_SOURCE
#endif

#include <stddef.h>
#include <poll.h>
#include <sys/ioctl.h>
#include <time.h>

#include "net.h"

#ifndef NDEBUG
#define DEBUG_LOG(fmt, arg...) printf("<%s(%s:%d)> " fmt, __FUNCTION__, __FILE__, __LINE__, ##arg)
#else
#define DEBUG_LOG(fmt, arg...)
#endif

void dumpMemStatus()
{
#ifdef _MALLOC_H_
	struct mallinfo info = mallinfo ();
	printf("   arena = %d\n", info.arena);
	printf(" ordblks = %d\n", info.ordblks);
	printf("  smblks = %d\n", info.smblks);
	printf("   hblks = %d\n", info.hblks);
	printf("  hblkhd = %d\n", info.hblkhd);
	printf(" usmblks = %d\n", info.usmblks);
	printf(" fsmblks = %d\n", info.fsmblks);
	printf("uordblks = %d\n", info.uordblks);
	printf("fordblks = %d\n", info.fordblks);
	printf("keepcost = %d\n", info.keepcost);
#else
	printf("unsupport struct mallinfo\n");
#endif
}

void dumpHex(char *buf, int len)
{
	char dumpBin[49];
	char dumpTxt[17];
	int j = 0;
	do {
		int i;
		for (i=0; i<16; ++i) {
			if (i<len) {
				sprintf(dumpBin+i*3, "%02X ", (unsigned char)buf[i]);
				if (buf[i] < 0x20) {
					dumpTxt[i] = '.';
				} else {
					dumpTxt[i] = buf[i];
				}
			} else {
				sprintf(dumpBin+i*3, "   ");
				dumpTxt[i] = ' ';
			}
		}
		dumpBin[47]='\0';
		dumpTxt[16]='\0';
		fprintf(stdout, "%06X\t%s\t%s\n", j, dumpBin, dumpTxt);
		j += 16;
		buf += 16;
		len -= 16;
	} while (len > 0);
}

int setnonblocking(int fd)
{
	unsigned long ul = 1;
	ioctl(fd, FIONBIO, &ul);
	return 0;
}

const char *ip_check(const char *ip, char *dst, socklen_t size)
{
	struct in_addr saddr_v4;
	int ret = inet_pton(AF_INET, ip, &saddr_v4);
	if (ret > 0) {
		return inet_ntop(AF_INET, (void *)&saddr_v4, dst, size);
	}
	struct in6_addr saddr_v6;
	ret = inet_pton(AF_INET6, ip, &saddr_v6);
	if (ret > 0) {
		return inet_ntop(AF_INET6, (void *)&saddr_v6, dst, size);
	}
	errno = EAFNOSUPPORT;
	return NULL;
}

int lingering_close(int fd)
{
	char buf[256];
	int rv;
	shutdown(fd, SHUT_WR);
	setnonblocking(fd);
	do {
		rv = read(fd, buf, 256);
	} while (rv == -1 && errno == EINTR);
	return close(fd);
}

int socket_tcplisten_port(const char *ip, const int port, int backlog)
{
	int fd = socket(AF_INET, SOCK_STREAM, 0);
	if ( fd == -1 ) {
		return -1;
	}

	int optval = -1;
	optval = 1;
	setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &optval, sizeof(optval));

	optval = 1;
	setsockopt(fd, IPPROTO_TCP, TCP_NODELAY, &optval, sizeof(optval));

	struct sockaddr_in al;
	memset(&al, 0, sizeof(al));

	al.sin_family = AF_INET;
	al.sin_addr.s_addr = (ip==NULL)?INADDR_ANY:inet_addr(ip);
	al.sin_port = htons( port );

	if (bind(fd, (struct sockaddr *)&al, sizeof(al)) < 0) {
		close(fd);
		return -1;
	}

	if (listen(fd, backlog) < 0) {
		close(fd);
		return -1;
	}

	return fd;
}

int socket_udp_bind_port(const char *ip, const int port)
{
	int fd = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
	if ( fd == -1 ) {
		return -1;
	}

	struct sockaddr_in al;
	memset(&al, 0, sizeof(al));

	al.sin_family = AF_INET;
	al.sin_addr.s_addr = (ip==NULL) ? INADDR_ANY : inet_addr(ip);
	al.sin_port = htons( port );

	int optval = 1;
	setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &optval, sizeof(optval));

	if (bind(fd, (struct sockaddr *)&al, sizeof(al)) < 0) {
		close(fd);
		return -1;
	}

	return fd;
}

int socket_domain_listen(const char *path, int backlog)
{
	int fd = socket(AF_UNIX, SOCK_STREAM, 0);
	if ( fd == -1 ) {
		return -1;
	}

	int optval = -1;

	optval = 1;
	setsockopt(fd, IPPROTO_TCP, TCP_NODELAY, &optval, sizeof(optval));

	struct sockaddr_un al;
	memset(&al, 0, sizeof(al));

	al.sun_family = AF_UNIX;
	snprintf(al.sun_path, sizeof(al.sun_path), "%s", path);
	unlink(al.sun_path);
	int len = strlen(al.sun_path) + sizeof(al.sun_family);

	if (bind(fd, (struct sockaddr *)&al, len) < 0) {
		close(fd);
		return -1;
	}

	if (listen(fd, backlog) < 0) {
		close(fd);
		return -1;
	}

	return fd;
}

int socket_sendv(int fd, struct iovec *vec, int nvec)
{
	int i = 0, bytes = 0, rv = 0;

	while (i < nvec) {
		do {
			rv = writev(fd, &vec[i], nvec - i);
		} while (rv == -1 && (errno == EINTR || errno == EAGAIN));

		if (rv == -1) {
			return -1;
		}
		bytes += rv;
		/* recalculate vec to deal with partial writes */
		while (rv > 0) {
			if (rv < (int)vec[i].iov_len) {
				vec[i].iov_base = (char *) vec[i].iov_base + rv;
				vec[i].iov_len -= rv;
				rv = 0;
			} else {
				rv -= vec[i].iov_len;
				++i;
			}
		}
	}

	/* We should get here only after we write out everything */
	return bytes;
}

int socket_send(int fd, const void *buf, int len)
{
	int rv;
	int nwrite = len;
	int wlen = 0;
	const char *pbuf = (const char *)buf;

	do {
		do {
			rv = write(fd, pbuf + wlen, nwrite);
		} while (rv == -1 && errno == EINTR);
		switch(rv) {
			case -1:
				if (errno == EAGAIN) {
					if (wlen != 0) {
						return wlen;
					}
				}
				return -1;
				break;
			case 0:
				return wlen;
				break;
			default:
				nwrite -= rv;
				wlen += rv;
				break;
		}
	} while(nwrite > 0);

	return wlen;
}

int socket_recv(int fd, void *buf, int len)
{
	int rv;
	int nread = len;
	int rlen = 0;
	char *pbuf = (char *)buf;

	do {
		do {
			rv = read(fd, pbuf + rlen, nread);
		} while (rv == -1 && errno == EINTR);
		switch (rv) {
			case -1:
				if (errno == EAGAIN) {
					if (rlen != 0) {
						return rlen;
					}
				}
				return -1;
				break;
			case 0:
				return rlen;
			default:
				nread -= rv;
				rlen += rv;
				break;
		}
	} while (nread > 0);
	return rlen;
}

int socket_recvo(int fd, void *buf, int len, int timeout_ms)
{
	int rv;
	int nread = len;
	int rlen = 0;
	char *pbuf = (char *)buf;

	do {
		rv = read(fd, pbuf + rlen, nread);
	} while (rv == -1 && errno == EINTR);
	if (rv > 0) {
		nread -= rv;
		rlen += rv;
	} else if (rv == 0) {
		if (rlen > 0) {
			return rlen;
		}
		return -1;
	}

	do {
		rv = wait_for_io(fd, 1, timeout_ms, NULL);
		do {
			rv = read(fd, pbuf + rlen, nread);
		} while (rv == -1 && errno == EINTR);
		switch (rv) {
			case -1:
				if (errno == EAGAIN) {
					if (rlen > 0) {
						return rlen;
					}
				}
				return -1;
				break;
			case 0:
				return rlen;
			default:
				nread -= rv;
				rlen += rv;
				break;
		}
	} while (nread > 0);
	return rlen;
}

int socket_send_all(int fd, const void *buf, int len)
{
	int l = len, rv;
	const char *p = (const char *)buf;
	do {
		rv = socket_send(fd, p, l);
		if (rv == -1 && errno != EAGAIN) {
			return -1;
		}
		p += rv;
		l -= rv;
	} while (l>0);
	return len;
}

int connect_timeout(int fd, const struct sockaddr *addr, socklen_t len, int timeout_ms)
{
	if (connect(fd, addr, len) == -1) {
		switch (errno) {
			case EISCONN:
				break;
			case EINPROGRESS:
			case EINTR:
				switch (wait_for_io(fd, 0, timeout_ms, NULL)) {
					case 0:
						errno = ETIMEDOUT;
						break;
					case -1:
						break;
					default:
						{
							int error;
							len = sizeof(int);
							if (getsockopt(fd, SOL_SOCKET, SO_ERROR, &error, &len)==0) {
								if (error==0) {
									return 0;
								}
							}
							errno = ETIMEDOUT;
						}
						break;
				}
				return -1;
			default:
				return -1;
		}
	}
	return 0;
}

int socket_tcpconnect4(const char *ip, const int port, int timeout_ms)
{
	int fd = socket(AF_INET, SOCK_STREAM, 0);
	if (fd < 0) {
		return -1;
	}
	unsigned long ul = 1;
	ioctl(fd, FIONBIO, &ul);

	struct sockaddr_in addr;
	bzero(&addr, sizeof(struct sockaddr));
	addr.sin_family = AF_INET;
	addr.sin_port = htons(port);
	addr.sin_addr.s_addr = inet_addr(ip);
	if (connect_timeout(fd, (struct sockaddr *)&addr, sizeof(struct sockaddr), timeout_ms) == -1) {
		close(fd);
		return -1;
	}
	return fd;
}

int socket_udpconnect4(const char *ip, const int port, int timeout_ms)
{
	int fd = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
	if ( fd == -1 ) {
		return -1;
	}
	unsigned long ul = 1;
	ioctl(fd, FIONBIO, &ul);

	struct sockaddr_in addr;
	memset(&addr, 0, sizeof(struct sockaddr_in));
	addr.sin_family = AF_INET;
	addr.sin_addr.s_addr = inet_addr(ip);
	addr.sin_port = htons(port);
	if (connect_timeout(fd, (struct sockaddr *)&addr, sizeof(struct sockaddr), timeout_ms) == -1) {
		close(fd);
		return -1;
	}
	return fd;
}

int socket_connect_unix(const char *path, int timeout_ms)
{
	int fd = socket(AF_UNIX, SOCK_STREAM, 0);
	if (fd < 0) {
		return -1;
	}
	unsigned long ul = 1;
	ioctl(fd, FIONBIO, &ul);

	struct sockaddr_un addr;
	bzero(&addr, sizeof(struct sockaddr));
	addr.sun_family = AF_UNIX;
	strncpy(addr.sun_path, path, sizeof(addr.sun_path));
	addr.sun_path[ sizeof(addr.sun_path) - 1 ] = '\0';
	if (connect_timeout(fd, (struct sockaddr *)&addr, SUN_LEN(&addr), timeout_ms) < 0 ) {
		close(fd);
		return -1;
	}
	return fd;
}

int wait_for_io(int socket, int for_read, int timeout_ms, int *revents)
{
	struct pollfd pfd;
	int rv;

	for_read = for_read ? POLLIN : POLLOUT;
	pfd.fd     = socket;
	pfd.events = for_read;
	pfd.revents = 0;

	do {
		errno = 0;
		rv = poll(&pfd, 1, timeout_ms);
		if (rv == -1) {
			if (errno == EINTR) {
				continue;
			} else if (errno == EAGAIN) {
				if (pfd.revents & for_read) {
					rv = 1;
					break;
				}
			}
		} else {
			break;
		}
	} while (1);

	if (revents) {
		*revents = pfd.revents;
	}
	return rv;
}

epoll_util_t *eu_new(int epoll_size)
{
	epoll_util_t *u = (epoll_util_t *)malloc(sizeof(epoll_util_t));
	if (u == NULL) {
		return NULL;
	}
	memset(u, 0, sizeof(epoll_util_t));
	u->epoll_fd = epoll_create (epoll_size);
	if (-1 == u->epoll_fd) {
		free(u);
		return NULL;
	}
	return u;
}

int eu_add_listenfd(epoll_util_t *u, int fd, int istcp)
{
	if (fd < 0 || fd >= MAX_EU_FD_NUM) {
		return -1;
	}
	struct epoll_event ev;
	ev.events = EPOLLIN;
	ev.data.fd = fd;
	u->fds[fd] = istcp ? EU_accept_tcp : EU_accept_udp;
	if (epoll_ctl(u->epoll_fd, EPOLL_CTL_ADD, fd, &ev) < 0) {
		if (errno != EEXIST) {
			return -1;
		}
	}
	return 0;
}

int eu_add_readfd(epoll_util_t *u, int fd, int istcp)
{
	if (fd < 0 || fd >= MAX_EU_FD_NUM) {
		return -1;
	}
	struct epoll_event ev;
	ev.events = EPOLLIN | POLLRDHUP | POLLHUP | POLLERR;
	ev.data.fd = fd;
	u->fds[fd] = istcp ? EU_read_tcp : EU_read_udp;
	if (epoll_ctl(u->epoll_fd, EPOLL_CTL_ADD, fd, &ev) < 0) {
		if (errno != EEXIST) {
			return -1;
		}
	}
	return 0;
}

int eu_del_fd(epoll_util_t *u, int fd)
{
	int ret = epoll_ctl(u->epoll_fd, EPOLL_CTL_DEL, fd, NULL);
	lingering_close(fd);
	if (fd > -1 && fd < MAX_EU_FD_NUM) {
		u->fds[fd] = 0;
	}
	return ret;
}

void eu_free(epoll_util_t *u)
{
	int i = 0;
	for (i=0; i<MAX_EU_FD_NUM; ++i) {
		if (u->fds[i] > 0 && u->fds[i] < EU_MAX_UNUSE_VAL) {
			eu_del_fd(u, i);
		}
	}

	if (u->epoll_fd > -1) {
		close(u->epoll_fd);
	}
	free(u);
}

int eu_once(epoll_util_t *u, int timeout)
{
	struct epoll_event events[20];
	int num = epoll_wait(u->epoll_fd, events, 20, timeout);
	int i;
	for (i=0; i<num; ++i) {
		if (events[i].data.fd > -1 && events[i].data.fd < MAX_EU_FD_NUM) {
			switch (u->fds[events[i].data.fd]) {
			case EU_accept_udp:
				u->on_accept_udp(u, events[i].data.fd, events[i].events);
				break;
			case EU_accept_tcp:
				u->on_accept_tcp(u, events[i].data.fd, events[i].events);
				break;
			case EU_read_udp:
				u->on_read_udp(u, events[i].data.fd, events[i].events);
				break;
			case EU_read_tcp:
				u->on_read_tcp(u, events[i].data.fd, events[i].events);
				break;
			default:
				eu_del_fd(u, events[i].data.fd);
				break;
			}
		} else {
			eu_del_fd(u, events[i].data.fd);
		}
	}
	return num;
}

void eu_set_onaccept_tcp(epoll_util_t *u, eu_on_callback on_func)
{
	u->on_accept_tcp = on_func;
}

void eu_set_onread_tcp(epoll_util_t *u, eu_on_callback on_func)
{
	u->on_read_tcp = on_func;
}

void eu_set_onaccept_udp(epoll_util_t *u, eu_on_callback on_func)
{
	u->on_accept_udp = on_func;
}

void eu_set_onread_udp(epoll_util_t *u, eu_on_callback on_func)
{
	u->on_read_udp = on_func;
}

void eu_set_userdata(epoll_util_t *u, void *userdata)
{
	u->userdata = userdata;
}

void *eu_get_userdata(epoll_util_t *u)
{
	return u->userdata;
}
