#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <assert.h>
#include <string.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include "dns.h"

int cname2_info_set(char *ans_cache, int *size)
{
#define CNAME_RES_LEN 14
    unsigned char buf[CNAME_RES_LEN] = {0xc0, 0x0c, 0, 5, 0, 1, 0, 0, 0x1c, 0x20, 0, 2, 0xc0, 0x0c};
    if (CNAME_RES_LEN < *size) {
        memcpy(ans_cache, buf, CNAME_RES_LEN);
        *size = CNAME_RES_LEN;
        return CNAME_RES_LEN;
    }
    return 0;
}

int a_info_set(const char *addr4, char *ans_cache, int *size)
{
#define A_RES_LEN 16
    unsigned char buf[A_RES_LEN] = {0xC0, 0x0C, 0, 1, 0, 1, 0, 0, 0x1C, 0x20, 0, sizeof(struct in_addr)};
    if (A_RES_LEN < *size) {
        memcpy(buf+12, addr4, 4);
        memcpy(ans_cache, buf, A_RES_LEN);
        *size = A_RES_LEN;
        //dumpHex(UDPSock[sockNo].buf, bytecount);
        return 1;
    }
    return 0;
}

int aaaa_info_set(const char *addr6, char *ans_cache, int *size)
{
#define AAAA_RES_LEN 28
    unsigned char buf[AAAA_RES_LEN] = {0xC0, 0x0C, 0, 28, 0, 1, 0, 0, 0x1C, 0x20, 0, sizeof(struct in6_addr)};
    if (AAAA_RES_LEN < *size) {
        memcpy(buf+12, addr6, 16);
        memcpy(ans_cache, buf, AAAA_RES_LEN);
        *size = AAAA_RES_LEN;
        //dumpHex(UDPSock[sockNo].buf, bytecount);
        return 1;
    }
    return 0;
}

int ptr_info_set(const char *domain, char *ans_cache, int *size)
{
    const int len = strlen(domain);
#define PTR_RES_LEN 12
    unsigned char buf[PTR_RES_LEN] = {0xc0, 0x0c, 0, 12, 0, 1, 0, 0, 0x1c, 0x20, 0, (unsigned char)(len+2)};

    if (PTR_RES_LEN + len + 2 < *size) {
        memcpy(ans_cache, buf, PTR_RES_LEN);
        *size = PTR_RES_LEN + len + 2;
        ans_cache += PTR_RES_LEN;
        char *prev = ans_cache;
        ++ans_cache;
        int i = 0;
        for (i=0; i<len; ++i) {
            if (domain[i] == '.') {
                *prev = ans_cache + i - prev - 1;
                ans_cache[i] = 0;
                prev = ans_cache + i;
            } else {
                ans_cache[i] = domain[i];
            }
        }
        *prev = ans_cache + i - prev - 1;
        ans_cache[i] = 0;
        return i+1;
    }
    return 0;
}

#ifdef USE_SET_TRUE_CNAME
int cname_info_set(const char *cname, char *ans_cache, int *size)
{
    const int len = strlen(cname);
    unsigned char buf[CNAME_RES_LEN] = {0xc0, 0x0c, 0, 5, 0, 1, 0, 0, 0x1c, 0x20, 0, (unsigned char)(len+2), 0, 0};

    if (CNAME_RES_LEN + len < *size) {
        memcpy(ans_cache, buf, CNAME_RES_LEN - 2);
        ans_cache += CNAME_RES_LEN - 2;
        *size = CNAME_RES_LEN + len;
        char *prev = ans_cache;
        ++ans_cache;
        int i = 0;
        for (i=0; i<len; ++i) {
            if (cname[i] == '.') {
                *prev = ans_cache + i - prev - 1;
                ans_cache[i] = 0;
                prev = ans_cache + i;
            } else {
                ans_cache[i] = cname[i];
            }
        }
        *prev = ans_cache + i - prev - 1;
        ans_cache[i] = 0;
        return i+1;
    }
    return 0;
}
#endif

int getQDomain(const char *query, const int qlen, char *domain, int dsize)
{
    int i, j;

    if (qlen < 14) {
        return -1;
    }
    i=13;
    j=0;
    while (query[i] != 0 && j < dsize) {
        if (query[i] < 0x20) {
            domain[j] = '.';
        } else {
            domain[j] = query[i];
        }
        ++i;
        ++j;
    }
    if (j == dsize) {
        return -1;
    }
    domain[j]='\0';
    return j;
}

int getQueryDomain(const char *query, const int qlen, char *domain, int dsize, int *dot, int *type)
{
    int i, j;

    *dot = 0;
    *type = 0;
    if (qlen < 14) {
        return -1;
    }
    i=13;
    j=0;
    while (query[i] != 0 && j < dsize) {
        if (query[i] < 0x20) {
            domain[j] = '.';
            ++(*dot);
        } else {
            domain[j] = query[i];
        }
        ++i;
        ++j;
    }
    if (j == dsize) {
        return -1;
    }
    if (query[i] != 0) {
        return -1;
    }
    ++i;
    if (i+4 > qlen) {
        return -1;
    }
    *type = ((query[i] & 0xFF) << 8) | (query[i+1] & 0xFF);
    domain[j]='\0';
    return j;
}

int getResAAAAAAns(const char *ans, const int len, int *type, char *domain, int dmsize, char *dst, int dst_size)
{
    int i, j;

    if (len < 14) {
        return -1;
    }
    i=13;
    j=0;
    while (ans[i] != 0 && j < dmsize) {
        if (ans[i] < 0x20) {
            domain[j] = '.';
        } else {
            domain[j] = ans[i];
        }
        ++i;
        ++j;
    }
    if (j == dmsize) {
        return -1;
    }
    domain[j]='\0';
    ++i;
    if (i+1 < len) {
        *type = ((ans[i] & 0xFF) << 8)|(ans[i+1] & 0xFF);
    } else {
        return -1;
    }
    i+=4;
    dst[0] = ans[6];
    dst[1] = ans[7];
    if (dst_size > len - i) {
        memcpy(dst+2, ans+i, len - i);
        return len - i + 2;
    }
    return -1;
}

#ifdef ONLY_CACHA_IPRES
int getResAAAAAAns_ip(const char *ans, const int len, int *type, char *domain, int dmsize, char *dst, int dst_size)
{
    int i, j;

    if (len < 14) {
        return -1;
    }
    //int ans_count = ((ans[6] & 0xFF) << 8)|(ans[7] & 0xFF);
    i=13;
    j=0;
    while (ans[i] != 0 && j < dmsize) {
        if (ans[i] < 0x20) {
            domain[j] = '.';
        } else {
            domain[j] = ans[i];
        }
        ++i;
        ++j;
    }
    if (j == dmsize) {
        return -1;
    }
    domain[j]='\0';
    ++i;
    if (i+1 < len) {
        *type = ((ans[i] & 0xFF) << 8)|(ans[i+1] & 0xFF);
    } else {
        return -1;
    }
    i+=4;
    int find_count = 0;
    j = 2;
    dst[0] = 0;
    dst[1] = 0;
    while (i < len) {
        if ((ans[i]&0xFF) == 0xC0 && i+3<len) {
            if (ans[i+2] == 0 && (ans[i+3] == ns_t_a || ans[i+3] == ns_t_aaaa)) {
                if (i + 11 < len) {
                    int dlen = 12 + (((ans[i+10] & 0xFF) << 8)|(ans[i+11] & 0xFF));
                    if (i+dlen-1 < len) {
                        if (j + dlen < dst_size) {
                            memcpy(dst+j, ans + i, dlen);
                            dst[j+1] = 0x0C;
                            j+=dlen;
                            find_count++;
                        }
                        i+=dlen;
                    } else {
                        break;
                    }
                } else {
                    break;
                }
            } else {
                i+=10;
                if (i+1 < len) {
                    int skip = ((ans[i] & 0xFF) << 8)|(ans[i+1] & 0xFF);
                    i+=2;
                    while (i<len && skip > 0) {
                        if ((ans[i]&0xFF) == 0xC0) {
                            ++i;
                            --skip;
                        }
                        ++i;
                        --skip;
                    }
                }
            }
        } else {
            break;
        }
    }
    dst[0] = (find_count >> 8) & 0xFF;
    dst[1] = find_count & 0xFF;

    return j;
}
#endif
