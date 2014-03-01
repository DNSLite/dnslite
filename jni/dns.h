#ifndef _L_DNS_INCLUDE_H_
#define _L_DNS_INCLUDE_H_

#ifdef __cplusplus
extern "C" {
#endif

int cname2_info_set(char *ans_cache, int *size);
int a_info_set(const char *addr4, char *ans_cache, int *size);
int aaaa_info_set(const char *addr6, char *ans_cache, int *size);
int ptr_info_set(const char *domain, char *ans_cache, int *size);

#ifdef USE_SET_TRUE_CNAME
int cname_info_set(const char *cname, char *ans_cache, int *size);
#endif

int getQDomain(const char *query, const int qlen, char *domain, int dsize);
int getQueryDomain(const char *query, const int qlen, char *domain, int dsize, int *dot, int *type);
int getResAAAAAAns(const char *ans, const int len, int *type, char *domain, int dmsize, char *dst, int dst_size);

#ifdef ONLY_CACHA_IPRES
int getResAAAAAAns_ip(const char *ans, const int len, int *type, char *domain, int dmsize, char *dst, int dst_size);
#endif

#ifdef __cplusplus
}
#endif

#endif /* _L_DNS_INCLUDE_H_ */
