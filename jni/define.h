#ifndef _DEFINE_INCLUDE_H_
#define _DEFINE_INCLUDE_H_

#ifdef ANDROID
#define APP_NAME "me.xu.DNSLite"
#include <sys/system_properties.h>
//#include <android/log.h>
#endif

#ifdef _ANDROID_LOG_H
# define  LOG_TAG    "libdnslite.so"
# define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
# define  LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG,LOG_TAG,__VA_ARGS__)
# define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)
#else
# define  LOGI(...) do{ printf(__VA_ARGS__);printf("\n"); } while(0)
# define  LOGD(...) do{ printf(__VA_ARGS__);printf("\n"); } while(0)
# define  LOGE(...) do{ \
    fprintf(stderr, format, ## __VA_ARGS__); \
    fprintf(stderr, "\n"); \
} while(0)

#endif

#define ns_t_a    1
#define ns_t_ptr  12
#define ns_t_aaaa 28

#define MAXSOCKET    512
#define MAXDELAY     2000000

#define GetTimeCurrent(tv) gettimeofday(&tv, NULL)
#define SetTimeUsed(tused, tv1, tv2) { \
    tused  = (tv2.tv_sec-tv1.tv_sec) * 1000000; \
    tused += (tv2.tv_usec-tv1.tv_usec); \
    if (tused == 0){ tused+=1; } \
}

#define MIN_CLEAN_CACHE_GAP 86400

#define MAX_NAMESERVER_NUM 2
#define MAX_IP_LEN 16
#define MAX_DOMAIN_LEN 65

#define ENABLE_CACHE (gconf->clean_cache_gap)
#define NEED_CLEAN_CACHE (ENABLE_CACHE && (gconf->tmnow.tv_sec - gconf->last_clean_cache > (int)gconf->clean_cache_gap))
#define ENABLE_AUTO_EXIT (gconf->max_idle_time)
#define IDLE_TOO_LONG (ENABLE_AUTO_EXIT && gconf->tmnow.tv_sec - gconf->last_serv > (int)gconf->max_idle_time)

#define LTRIM(str) \
    while (*str && (*str == ' ' || *str == '\t')) { \
        ++str; \
    }

#define GOTO_LINE_END(str) \
    while (*str && *str != '\r' && *str != '\n') { \
        ++str; \
    }

#define GOTO_SPACE(str) \
    while (*str && *str != ' ' && *str != '\t') { \
        ++str; \
    }

#endif /* _DEFINE_INCLUDE_H_ */
