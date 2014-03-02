#ifndef _DNSPROXY_INCLUDE_H_
#define _DNSPROXY_INCLUDE_H_

#include <stdio.h>
#include <stdlib.h>
#include <assert.h>
#include <signal.h>
#include <sys/types.h>
#include <sys/time.h>
#include <sys/wait.h>
#include <sys/ioctl.h>
#include <pwd.h>
#include <grp.h>
#include <unistd.h>
#include <stdarg.h>
#include <netdb.h>
#include <net/if.h>

#include "net.h"

#ifndef ANDROID
#include <execinfo.h>
#endif

enum {
	ST_IDLE = 0,
	ST_WAIT_CACHE_ADD,
	ST_QUERY,
	ST_WAIT_ANS,
};

#define BUF_SIZE 1024
typedef struct {
	int                 fd;
	union {
		struct sockaddr_in  from_addr;
		int reqfd;
	};
	unsigned char       status;
	unsigned char       istcp;
	char				tcpbuf[2];
	char                buf[BUF_SIZE];
	int                 bufLen;
	struct timeval      start;
} udp_sock_t;

void logs(const char *fmt, ...);

#endif /* _DNSPROXY_INCLUDE_H_ */
