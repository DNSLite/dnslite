#ifndef _L_DNS_INCLUDE_H_
#define _L_DNS_INCLUDE_H_

#ifdef __cplusplus
extern "C" {
#endif

// http://hi.baidu.com/honeyhacker/blog/item/42f9bfdd6ff351aacc1166a1.html
// http://baiseda.iteye.com/blog/1268061
//DNS header structure
struct DNS_HEADER
{
    unsigned short id; // identification number

    unsigned char rd     :1; // recursion desired
    unsigned char tc     :1; // truncated message
    unsigned char aa     :1; // authoritive answer
    unsigned char opcode :4; // purpose of message
    unsigned char qr     :1; // query/response flag

    unsigned char rcode  :4; // response code
    unsigned char cd     :1; // checking disabled
    unsigned char ad     :1; // authenticated data
    unsigned char z      :1; // its z! reserved
    unsigned char ra     :1; // recursion available

    unsigned char q_count_h; // number of question entries
    unsigned char q_count_l; // number of question entries
    unsigned char ans_count_h; // number of answer entries
    unsigned char ans_count_l; // number of answer entries
    unsigned char auth_count_h; // number of authority entries
    unsigned char auth_count_l; // number of authority entries
    unsigned char add_count_h; // number of resource entries
    unsigned char add_count_l; // number of resource entries
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
