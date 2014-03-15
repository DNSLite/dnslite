#include <stdio.h>
#include <stdlib.h>
#include <assert.h>
#include <dirent.h>

#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>

#include "dnsproxy.h"
#include "define.h"
#include "cache.h"
#include "conf.h"
#include "dns.h"

extern conf_t *gconf;

#include <map>
#include <string>
using namespace std;
std::map <string, string> static_cache;
std::map <string, string> dnsa_cache;
std::map <string, string> dnsaaaa_cache;

static int is_ip(const char *ip);

void load_db_dnscache()
{
    load_db_file("/etc/hosts");
    load_db_file(gconf->db_filename);
    load_db_dir(gconf->confd);
}

void load_db_dir(const char *path)
{
    DIR *dirp = opendir(path);
    if (dirp == NULL) {
        return;
    }
    char filename[256];
    struct dirent *dp;
    while ((dp = readdir(dirp)) != NULL) {
        if (dp->d_type != DT_REG) {
            continue;
        }
        snprintf(filename, sizeof(filename), "%s/%s", path, dp->d_name);
        load_db_file(filename);
    }
    closedir(dirp);
}

void load_db_file(const char *filename)
{
    FILE *fp = fopen(filename, "r");
    if (fp == NULL) {
        return;
    }

    char line[256];
    while (fgets(line, sizeof(line), fp) != NULL) {
        char *p = line;
        LTRIM(p);
        if (p[0] == '#') {
            continue;
        }
        char *pend = p;
        GOTO_LINE_END(pend);
        *pend = 0;
#define START_WITH(str, with) \
        (!strncasecmp(str, with, sizeof(with) - 1))

        if (START_WITH(p, "address=/")) {
            static_cache_add_address(p);
        } else if (START_WITH(p, "server=/")) {
            static_cache_add_server(p);
        } else {
            static_cache_add_hosts_line(p);
        }
    }
    fclose(fp);
}

void static_cache_add_hosts_line(char *line)
{
    char delim[] = " \t";
    char *token = strsep(&line, delim);
    if (token == NULL) {
        return;
    }

    char *ip = token;
    if (!is_ip(ip)) {
        return;
    }

    char buf[256];
    for(token = strsep(&line, delim); token != NULL; token = strsep(&line, delim)) {
        if (!*token) {
            continue;
        }

        if (*token == '#') {
            return;
        }

        if (*token == '.') {
            static_cache_add(token+1, ip);
            snprintf(buf, sizeof(buf), "*%s", token);
            static_cache_add(buf, ip);
        } else {
            static_cache_add(token, ip);
        }
    }
}

void static_cache_add_address(char *line)
{
    /* address=/google.com/127.0.0.1 */
    char *ip = strrchr(line, '/');
    if (ip == NULL) {
        return;
    }
    *ip++ = 0;

    if (!is_ip(ip)) {
        return;
    }

    char delim[] = "/";
    char *token = strsep(&line, delim);
    if (token == NULL) {
        return;
    }
    char buf[256];
    for(token = strsep(&line, delim); token != NULL; token = strsep(&line, delim)) {
        if (!*token) {
            continue;
        }

        if (token == ip) {
            return;
        }
        if (*token == '.') {
            static_cache_add(token+1, ip);
            snprintf(buf, sizeof(buf), "*%s", token);
            static_cache_add(buf, ip);
        } else {
            static_cache_add(token, ip);
            snprintf(buf, sizeof(buf), "*.%s", token);
            static_cache_add(buf, ip);
        }
    }
}

void static_cache_add_server(char *line)
{
    /* server=/google.com/127.0.0.1 */
    char *ip = strrchr(line, '/');
    if (ip == NULL) {
        return;
    }
    *ip++ = 0;

    if (!is_ip(ip)) {
        return;
    }

    char delim[] = "/";
    char *token = strsep(&line, delim);
    if (token == NULL) {
        return;
    }
    char buf[256];
    for(token = strsep(&line, delim); token != NULL; token = strsep(&line, delim)) {
        if (!*token) {
            continue;
        }

        if (token == ip) {
            return;
        }

        if (*token == '.') {
            nameserver_cache_add(token+1, ip);
            snprintf(buf, sizeof(buf), "*%s", token);
            nameserver_cache_add(buf, ip);
        } else {
            nameserver_cache_add(token, ip);
            snprintf(buf, sizeof(buf), "*.%s", token);
            nameserver_cache_add(buf, ip);
        }
    }
}

void nameserver_cache_add(char *domain, char *ip)
{
    char addr[100];
    strncpy(addr+1, ip, 99);
    addr[99] = 0;
    addr[0] = '@';
    logs("server [%s] [%s]\n", ip, domain);
    static_cache_add(domain, addr);
}

void static_cache_add(char *domain, char *ip)
{
    logs("location [%s] [%s]\n", ip, domain);
    map<string, string>::iterator it;
    it = static_cache.find(domain);
    if (it != static_cache.end()) {
        const char *str = (*it).second.c_str();
        if (*str == '@') {
            string ip_str(ip);
            (*it).second = ip_str + "/" + (*it).second;
        } else {
            (*it).second.append("/");
            (*it).second.append(ip);
        }
        logs("  --> [%s] [%s]\n", (*it).second.c_str(), domain);
    } else {
        static_cache.insert(pair<string,string>(domain, ip));
    }
}

int is_ip(const char *ip)
{
    return 1;
}

void cache_clean()
{
    dnsa_cache.clear();
    dnsaaaa_cache.clear();
    gconf->last_clean_cache = gconf->tmnow.tv_sec;
    logs("cache clear\n");
}

int cache_static_ans(const char *res, char *ans_cache, int *size)
{
    int addr_type = -1;
    int wlen = 0;
    struct in6_addr addr;
    int num = -1;

    int ret = inet_pton(AF_INET, res, (struct in_addr*)&addr);
    if (ret > 0) {
        addr_type = AF_INET;
    } else {
        ret = inet_pton(AF_INET6, res, &addr);
        if (ret > 0) {
            addr_type = AF_INET6;
        } else {
            logs("E: inet_pton: %d\n", errno);
            return -1;
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
    return num;
}

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
            if (*((*it).second.c_str()) == '@') {
                strncpy(ans_cache, (*it).second.c_str(), *size);
                ans_cache[*size - 1] = 0;
                if (*size > (int)(*it).second.length()) {
                    *size = (*it).second.length();
                    ans_cache[*size] = 0;
                }
                num = -2;
                break;
            }

            num = cache_static_ans((*it).second.c_str(), ans_cache, size);
            break;
        }

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
    } while (dot > 1);

    return num;
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

long static_cache_count()
{
    return static_cache.size();
}

int cache_hit_ptr(const char *domain, char *ans_cache, int *size)
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
