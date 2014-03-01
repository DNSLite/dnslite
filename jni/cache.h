#ifndef _CACHE_INCLUDE_H_
#define _CACHE_INCLUDE_H_

#ifdef __cplusplus
extern "C" {
#endif

void load_db_dir(const char *path);
void load_db_file(const char *filename);
void load_db_dnscache();

void static_cache_add_hosts_line(char *line);
void static_cache_add_address(char *line);
void static_cache_add_server(char *line);
void static_cache_add(char *domain, char *ip);

long static_cache_count();

int cache_add(int is_dnsa, const char *domain, const char *ans, int len);
int cache_hit(int is_dnsa, const char *domain, char *ans_cache, int *size);
int cache_hit_ptr(const char *domain, char *ans_cache, int *size);
int cache_static_hit(const char *domain, int dot, char *ans_cache, int *size, int query_type);
void cache_clean();

#ifdef __cplusplus
}
#endif

#endif /* _CACHE_INCLUDE_H_ */
