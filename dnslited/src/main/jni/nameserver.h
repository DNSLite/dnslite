#ifndef _L_NAMESERVER_INCLUDE_H_
#define _L_NAMESERVER_INCLUDE_H_

#ifdef __cplusplus
extern "C" {
#endif

int init_nameserver(const char *arg);
const char *get_rand_nameserver_ip();

#ifdef __cplusplus
}
#endif

#endif /* _L_NAMESERVER_INCLUDE_H_ */
