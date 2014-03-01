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
#include <dirent.h>

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

// http://hi.baidu.com/honeyhacker/blog/item/42f9bfdd6ff351aacc1166a1.html
// http://baiseda.iteye.com/blog/1268061
//DNS header structure
struct DNS_HEADER
{
	unsigned	short id;		    // identification number

	unsigned	char rd     :1;		// recursion desired
	unsigned	char tc     :1;		// truncated message
	unsigned	char aa     :1;		// authoritive answer
	unsigned	char opcode :4;	    // purpose of message
	unsigned	char qr     :1;		// query/response flag

	unsigned	char rcode  :4;	    // response code
	unsigned	char cd     :1;	    // checking disabled
	unsigned	char ad     :1;	    // authenticated data
	unsigned	char z      :1;		// its z! reserved
	unsigned	char ra     :1;		// recursion available

	unsigned    char q_count_h;	    // number of question entries
	unsigned    char q_count_l;	    // number of question entries
	unsigned	char ans_count_h;	// number of answer entries
	unsigned	char ans_count_l;	// number of answer entries
	unsigned	char auth_count_h;	// number of authority entries
	unsigned	char auth_count_l;	// number of authority entries
	unsigned	char add_count_h;	// number of resource entries
	unsigned	char add_count_l;	// number of resource entries
};

#pragma pack(push, 1)
struct RES_RECORD
{
	char                name[2];
	unsigned char       type_h;
	unsigned char       type_l;
	unsigned char       _class_h;
	unsigned char       _class_l;
	unsigned char       ttl_hh;
	unsigned char       ttl_h;
	unsigned char       ttl_l;
	unsigned char       ttl_ll;
	unsigned char       data_len_h;
	unsigned char       data_len_l;
	struct in6_addr     i6addr;
};
#pragma pack(pop)

void logs(const char *fmt, ...);

#endif /* _DNSPROXY_INCLUDE_H_ */
