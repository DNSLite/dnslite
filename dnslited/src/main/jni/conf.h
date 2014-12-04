#ifndef _CONF_INCLUDE_H_
#define _CONF_INCLUDE_H_

#include "dnsproxy.h"
#include "define.h"

#ifndef MAXHOSTNAMELEN
#define MAXHOSTNAMELEN 256
#endif

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
    event_util_t *eu;
    int set_system_dns;
#ifdef __ANDROID__
    char net_dns[2][PROP_VALUE_MAX];
#endif
    const char *db_filename;
    const char *confd;
    struct in_addr eth0;
    char host_name[MAXHOSTNAMELEN];
    const char *fix_system_dns;
} conf_t;

#endif /* _CONF_INCLUDE_H_ */
