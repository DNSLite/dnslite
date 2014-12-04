#include "nameserver.h"
#include "conf.h"

extern conf_t *gconf;

int init_nameserver(const char *arg)
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

const char *get_rand_nameserver_ip()
{
    static int t = 0;
    assert (gconf->nameserver_num > 0);
    ++t;
    t %= gconf->nameserver_num;
    return gconf->nameservers[t];
}
