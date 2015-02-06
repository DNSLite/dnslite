#ifndef _GNU_SOURCE
#define _GNU_SOURCE
#endif

#include <stddef.h>
#include <poll.h>
#include <sys/ioctl.h>
#include <time.h>

#if defined(__APPLE__)
# include <malloc/malloc.h>
# include <sys/event.h>
# define eu_event kevent
# define _eu_fd_create(_eu_size) kqueue()
#else
# include <malloc.h>
# include <sys/epoll.h>
# define eu_event epoll_event
# define _eu_fd_create epoll_create
#endif

#include <sys/socket.h>
#include <sys/types.h>
#include <netinet/in.h>
#include <netdb.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <unistd.h>
#include <errno.h>

#if defined(_ENABLE_SSL)
#include <openssl/rand.h>
#include <openssl/ssl.h>
#include <openssl/err.h>
#endif

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
	const char *p = ip;
	int v6 = 0;
	while (*p) {
		if (':' == *p) {
			v6 = 1;
			break;
		}
		if ('.' == *p) {
			v6 = 0;
			break;
		}
		p++;
	}

	if (v6) {
		struct in6_addr saddr_v6;
		int ret = inet_pton(AF_INET6, ip, &saddr_v6);
		if (ret > 0) {
			return inet_ntop(AF_INET6, (void *)&saddr_v6, dst, size);
		}
	} else {
		char *nip = (char *)ip;
		p = strchr(ip, ':');
		if (p) {
			char buf[64];
			if (sizeof(buf) - (p - ip) > 0) {
				strncpy(buf, ip, p - ip);
				buf[p - ip] = '\0';
				nip = buf;
			}
		}

		struct in_addr saddr_v4;
		int ret = inet_pton(AF_INET, nip, &saddr_v4);
		if (ret > 0) {
			return inet_ntop(AF_INET, (void *)&saddr_v4, dst, size);
		}
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

struct sockaddr_in get_inet(const char *_ip, const int _port)
{
	char *ip = (char *)_ip;
	int port = _port;
	struct sockaddr_in al;
	memset(&al, 0, sizeof(al));
	char *p = strchr(ip, ':');
	if (p) {
		int n = atoi(p + 1);
		if (n > 0 && n < 65535) {
			port = n;
		}
		char buf[128];
		if (p - ip < 128) {
			strncpy(buf, ip, p - ip);
			buf[p-ip] = '\0';
			ip = buf;
		}
	}

	al.sin_family = AF_INET;
	al.sin_addr.s_addr = (ip==NULL) ? INADDR_ANY : inet_addr(ip);
	al.sin_port = htons( port );
	return al;
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

	struct sockaddr_in al = get_inet(ip, port);

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

	struct sockaddr_in al = get_inet(ip, port);

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

	struct sockaddr_in addr = get_inet(ip, port);
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

	struct sockaddr_in addr = get_inet(ip, port);
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

#if defined(_ENABLE_SSL)
connection *ssl_connect(int tcp_fd)
{
    connection *c;

    c = malloc (sizeof (connection));
    if (c == NULL) {
        return NULL;
    }

    c->socket = tcp_fd;
    c->ssl_handle = NULL;
    c->ssl_context = NULL;

    // Register the error strings for libcrypto & libssl
    SSL_load_error_strings ();
    // Register the available ciphers and digests
    SSL_library_init ();

    // New context saying we are a client, and using SSL 2 or 3
    c->ssl_context = SSL_CTX_new (SSLv23_client_method ());
    if (c->ssl_context == NULL) {
        ERR_print_errors_fp (stderr);
    }

    // Create an SSL struct for the connection
    c->ssl_handle = SSL_new (c->ssl_context);
    if (c->ssl_handle == NULL) {
        ERR_print_errors_fp (stderr);
    }

    // Connect the SSL struct to our connection
    if (!SSL_set_fd (c->ssl_handle, c->socket)) {
        ERR_print_errors_fp (stderr);
    }

    // Initiate SSL handshake
    if (SSL_connect (c->ssl_handle) != 1) {
        ERR_print_errors_fp (stderr);
    }

    return c;
}

void ssl_disconnect(connection *c)
{
    if (c->socket) {
        close (c->socket);
    }

    if (c->ssl_handle) {
        SSL_shutdown (c->ssl_handle);
        SSL_free (c->ssl_handle);
    }
    if (c->ssl_context) {
        SSL_CTX_free (c->ssl_context);
    }

    free (c);
}
#endif

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

event_util_t *eu_new(int epoll_size)
{
	event_util_t *u = (event_util_t *)malloc(sizeof(event_util_t));
	if (u == NULL) {
		return NULL;
	}
	memset(u, 0, sizeof(event_util_t));
	u->epoll_fd = _eu_fd_create (epoll_size);
	if (-1 == u->epoll_fd) {
		free(u);
		return NULL;
	}
	return u;
}

int eu_add_listenfd(event_util_t *u, int fd, int istcp)
{
	if (fd < 0 || fd >= MAX_EU_FD_NUM) {
		return -1;
	}
	struct eu_event ev;
	u->fds[fd] = istcp ? EU_accept_tcp : EU_accept_udp;
#if defined(__APPLE__)
	EV_SET(&ev, fd, EVFILT_READ, EV_ADD, 0, 0, NULL);
	int ret = kevent(u->epoll_fd, &ev, 1, NULL, 0, NULL);
#else
	ev.events = EPOLLIN;
	ev.data.fd = fd;
	int ret = epoll_ctl(u->epoll_fd, EPOLL_CTL_ADD, fd, &ev);
#endif
	if (ret < 0) {
		if (errno != EEXIST) {
			return -1;
		}
	}
	return 0;
}

int eu_add_readfd(event_util_t *u, int fd, int istcp)
{
	if (fd < 0 || fd >= MAX_EU_FD_NUM) {
		return -1;
	}
	struct eu_event ev;
	u->fds[fd] = istcp ? EU_read_tcp : EU_read_udp;
#if defined(__APPLE__)
	EV_SET(&ev, fd, EVFILT_READ, EV_ADD, 0, 0, NULL);
	int ret = kevent(u->epoll_fd, &ev, 1, NULL, 0, NULL);
#else
	ev.events = EPOLLIN | POLLRDHUP | POLLHUP | POLLERR;
	ev.data.fd = fd;
	int ret = epoll_ctl(u->epoll_fd, EPOLL_CTL_ADD, fd, &ev);
#endif
	if (ret < 0) {
		if (errno != EEXIST) {
			return -1;
		}
	}
	return 0;
}

int eu_del_fd(event_util_t *u, int fd)
{
#if defined(__APPLE__)
	struct eu_event ev;
	EV_SET(&ev, fd, EVFILT_READ, EV_DELETE, 0, 0, NULL);
	int ret = kevent(u->epoll_fd, &ev, 1, NULL, 0, NULL);
#else
	int ret = epoll_ctl(u->epoll_fd, EPOLL_CTL_DEL, fd, NULL);
#endif
	lingering_close(fd);
	if (fd > -1 && fd < MAX_EU_FD_NUM) {
		u->fds[fd] = 0;
	}
	return ret;
}

void eu_free(event_util_t *u)
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

#if defined(__APPLE__)
# define get_event_fd(_event) (_event).ident
# define get_event_events(_event) (_event).filter
#else
# define get_event_fd(_event) (_event).data.fd
# define get_event_events(_event) (_event).events
#endif

#define MAX_READ_EVENT_SIZE 20

int eu_once(event_util_t *u, int timeout)
{
	struct eu_event events[MAX_READ_EVENT_SIZE];
	int num = 0;
#if defined(__APPLE__)
	{
		struct timespec *ptspec = NULL;
		struct timespec tspec;
		if (timeout > 0) {
			tspec.tv_sec = timeout / 1000;
			tspec.tv_nsec = timeout * 1000 % 1000000;
			ptspec = &tspec;
		}
		num = kevent(u->epoll_fd, NULL, 0, events, MAX_READ_EVENT_SIZE, ptspec);
	}
#else
	num = epoll_wait(u->epoll_fd, events, MAX_READ_EVENT_SIZE, timeout);
#endif
	int i;
	for (i=0; i<num; ++i) {
		int fd = get_event_fd(events[i]);
		int event = get_event_events(events[i]);
		if (fd > -1 && fd < MAX_EU_FD_NUM) {
			switch (u->fds[fd]) {
			case EU_accept_udp:
				u->on_accept_udp(u, fd, event);
				break;
			case EU_accept_tcp:
				u->on_accept_tcp(u, fd, event);
				break;
			case EU_read_udp:
				u->on_read_udp(u, fd, event);
				break;
			case EU_read_tcp:
				u->on_read_tcp(u, fd, event);
				break;
			default:
				eu_del_fd(u, fd);
				break;
			}
		} else {
			eu_del_fd(u, fd);
		}
	}
	return num;
}

void eu_set_onaccept_tcp(event_util_t *u, eu_on_callback on_func)
{
	u->on_accept_tcp = on_func;
}

void eu_set_onread_tcp(event_util_t *u, eu_on_callback on_func)
{
	u->on_read_tcp = on_func;
}

void eu_set_onaccept_udp(event_util_t *u, eu_on_callback on_func)
{
	u->on_accept_udp = on_func;
}

void eu_set_onread_udp(event_util_t *u, eu_on_callback on_func)
{
	u->on_read_udp = on_func;
}

void eu_set_userdata(event_util_t *u, void *userdata)
{
	u->userdata = userdata;
}

void *eu_get_userdata(event_util_t *u)
{
	return u->userdata;
}

#if defined(_ENABLE_SSL)
int ssl_read(connection *c, void *buf, int num)
{
    if (!c) {
        return -1;
    }

    return SSL_read (c->ssl_handle, buf, num);
}

int ssl_write(connection *c, const void *buf, int num)
{
    if (!c) {
        return -1;
    }
    return SSL_write (c->ssl_handle, buf, num);
}
#endif
