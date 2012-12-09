#ifndef __DNS_SOCKET_H_
#define __DNS_SOCKET_H_

#include <stdio.h>
#include <stdlib.h>
#include <errno.h>
#include <string.h>
#include <unistd.h>

#include <sys/socket.h>
#include <sys/uio.h>
#include <arpa/inet.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <sys/un.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>

#ifdef __cplusplus
extern "C" {
#endif

#ifndef EPOLLRDHUP
#define EPOLLRDHUP 0x2000
#endif

void dumpMemStatus();
void dumpHex(char *buf, int len);

int setnonblocking(int fd);
int lingering_close(int fd);

const char *ip_check(const char *ip, char *dst, socklen_t size);

int socket_tcplisten_port(const char *ip, const int port, int backlog);
int socket_domain_listen(const char *path, int backlog);
int socket_udp_bind_port(const char *ip, const int port);

int socket_tcpconnect4(const char *ip, const int port, int timeout_ms);
int socket_connect_unix(const char *path, int timeout_ms);
int socket_udpconnect4(const char *ip, const int port, int timeout_ms);

int wait_for_io(int socket, int for_read, int timeout_ms, int *revents);

int socket_sendv(int fd, struct iovec *vec, int nvec);
int socket_send(int fd, const void *buf, int len);
int socket_send_all(int fd, const void *buf, int len);

int socket_recv(int fd, void *buf, int len);
int socket_recvo(int fd, void *buf, int len, int timeout_ms);

typedef struct epoll_util_t epoll_util_t;

typedef void (*eu_on_callback)(epoll_util_t*, int fd, uint32_t events);

/*
void eu_on_accept(epoll_util_t *u, int fd, uint32_t events);
void eu_on_read(epoll_util_t *u, int fd, uint32_t events);
*/

enum e_callback_type{
	EU_unknow_type = 0,
	EU_accept_udp,
	EU_accept_tcp,
	EU_read_udp,
	EU_read_tcp,
	EU_MAX_UNUSE_VAL
};

#define MAX_EU_FD_NUM 1024

struct epoll_util_t {
	int epoll_fd;
	unsigned char fds[MAX_EU_FD_NUM];
	eu_on_callback on_accept_udp;
	eu_on_callback on_accept_tcp;
	eu_on_callback on_read_udp;
	eu_on_callback on_read_tcp;
	void *userdata;
};

epoll_util_t *eu_new(int epoll_size);
void eu_free(epoll_util_t *u);

void eu_set_onaccept_tcp(epoll_util_t *u, eu_on_callback on_func);
void eu_set_onread_tcp(epoll_util_t *u, eu_on_callback on_func);
void eu_set_onaccept_udp(epoll_util_t *u, eu_on_callback on_func);
void eu_set_onread_udp(epoll_util_t *u, eu_on_callback on_func);

void eu_set_userdata(epoll_util_t *u, void *userdata);
void *eu_get_userdata(epoll_util_t *u);

int eu_once(epoll_util_t *u, int timeout);

int eu_add_listenfd(epoll_util_t *u, int fd, int istcp);
int eu_add_readfd(epoll_util_t *u, int fd, int istcp);

int eu_del_fd(epoll_util_t *u, int fd);

#ifdef __cplusplus
}
#endif

#endif /* __DNS_SOCKET_H_ */
