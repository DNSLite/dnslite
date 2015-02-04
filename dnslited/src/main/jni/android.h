#ifndef _INCLUDE_ANDROID_H
#define _INCLUDE_ANDROID_H

#include <sys/cdefs.h>
#include "android_sys_prop.h"

__BEGIN_DECLS

#define NETID_ENV "DNSLite_NETID"

#define V_BASE 1
#define V_BASE_1_1 2
#define V_LOLLIPOP 21

int get_build_version_sdk_int();

void set_net_dns(const char *dns);
void set_net_dns_2(const char *dns1, const char *dns2);

__END_DECLS

#endif
