#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdarg.h>
#include "android.h"

#undef LOG_TAG
#undef LOGI
#undef LOGD
#undef LOGE

#ifdef _ANDROID_LOG_H
# define  LOG_TAG    "libdnslite.so"
# define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
# define  LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG,LOG_TAG,__VA_ARGS__)
# define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)
#else
# define  LOGI(...)
# define  LOGD(...)
# define  LOGE(...)
#endif

static void ndc_set_net_dns(const char *dns);

int get_build_version_sdk_int()
{
	static int sdk_int = -1;
	if (sdk_int < 0) {
		sdk_int = getprop_int("ro.build.version.sdk");
	}
	return sdk_int;
}

int run_command(const char *format,...)
{
	char cmd[1024];
	va_list ap;
	va_start(ap, format);
	vsnprintf(cmd, sizeof(cmd), format, ap);
	va_end(ap);
	return system(cmd);
}

void set_net_dns_single(int id, const char *dns)
{
	if (!dns || !*dns) {
		return;
	}
	char buf[64];
	snprintf(buf, sizeof(buf), "net.dns%d", id);
	setprop(buf, dns);
	setprop("net.change", buf);
}

void set_net_dns_2(const char *dns1, const char *dns2)
{
	if (dns1 && dns2 && *dns1 && *dns2) {

		char buf[512];
		snprintf(buf, sizeof(buf), "%s %s", dns1, dns2);
		set_net_dns(buf);

	} else if (dns1 && *dns1) {
		set_net_dns(dns1);
	} else if (dns2 && *dns2) {
		set_net_dns(dns2);
	}
}

void set_net_dns(const char *dns)
{
	if (!dns || !*dns) {
		return;
	}
	char buf[512];
	strncpy(buf, dns, sizeof(buf));
	buf[sizeof(buf)-1] = '\0';

	char *p;
	for (p = buf; *p; p++) {
		switch(*p) {
			case ';':
			case ',':
				*p = ' ';
				break;
			default:
				break;
		}
	}
	ndc_set_net_dns(buf);

	char *sep = " ";
	char *phrase, *brk;
	int n = 1;
	for (phrase = strtok_r(buf, sep, &brk);
			phrase;
			phrase = strtok_r(NULL, sep, &brk)) {

		set_net_dns_single(n, phrase);
		if (++n > 2) {
			break;
		}
	}
}

void flush_dns_below_lollipop(const char *ifname)
{
	run_command("ndc resolver flushif %s", ifname);
	run_command("ndc resolver flushdefaultif");
}

void flush_dns_above_lollipop(int nid)
{
	run_command("ndc resolver flushnet %d", nid);
}

void ndc_set_net_dns_above_lollipop(int nid, const char *dns)
{
	run_command("ndc resolver setnetdns %d localhost %s", nid, dns);
	flush_dns_above_lollipop(nid);
}

void ndc_set_net_dns_below_lollipop(const char *ifname, const char *dns)
{
	run_command("ndc resolver setifdns %s localhost %s", ifname, dns);
	flush_dns_below_lollipop(ifname);
}

void ndc_set_net_dns(const char *dns)
{
	if (get_build_version_sdk_int() < V_LOLLIPOP) {

		char ifname[PROP_VALUE_MAX];
		int ret = getprop("wifi.interface", ifname);
		if (ret < 1) {
			strcpy(ifname, "eth0");
		}
		ndc_set_net_dns_below_lollipop(ifname, dns);

	} else {

		char *ids = getenv(NETID_ENV);
		if (!ids || !*ids) {
			return;
		}
		char buf[PROP_VALUE_MAX];
		strncpy(buf, ids, sizeof(buf));
		buf[sizeof(buf)-1] = '\0';

		char *sep = ",";
		char *phrase, *brk;
		for (phrase = strtok_r(buf, sep, &brk);
				phrase;
				phrase = strtok_r(NULL, sep, &brk)) {

			int id = atoi(phrase);
			ndc_set_net_dns_above_lollipop(id, dns);
		}

	}
}
