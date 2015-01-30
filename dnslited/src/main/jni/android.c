#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdarg.h>
#include "android.h"

int get_build_version_sdk_int()
{
	static int sdk_int = -1;
	if (sdk_int < 0) {
		sdk_int = getprop_int("ro.build.version.sdk");
	}
	return sdk_int;
}

int run_command(const char *cmd)
{
	return system(cmd);
}

void set_net_dns1(const char *dns)
{
	setprop("net.dns1", dns);
	setprop("net.change", "net.dns1");
}

void ndc_set_net_dns(int nid, const char *dns)
{
	char buf[1024];
	snprintf(buf, sizeof(buf), "ndc resolver setnetdns %d \"\" %s", nid, dns);
	run_command(buf);
	flush_dns();
}

void set_net_dns2(const char *dns)
{
	setprop("net.dns2", dns);
	setprop("net.change", "net.dns2");
}

void flush_dns()
{
	if (get_build_version_sdk_int() < V_LOLLIPOP) {
		run_command("ndc resolver flushdefaultif;ndc resolver flushif wlan0");
	} else {
		run_command("ndc resolver flushnet 0");
		run_command("ndc resolver flushnet 1");
	}
}
