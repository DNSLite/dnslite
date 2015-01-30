#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "android_sys_prop.h"

int getprop(const char *name, char *value)
{
	FILE *fp;
	char buffer[64];
	value[0] = '\0';

	snprintf(buffer, sizeof(buffer), "getprop %s", name);
	fp = popen(buffer, "r");
	if (NULL == fp) {
		return -1;
	}

	fgets(value, PROP_VALUE_MAX, fp);
	pclose(fp);

	char *p = strchr(value, '\n');
	if (p) {
		*p = '\0';
		if ('\r' == *(p-1)) {
			*--p = '\0';
		}
	}

	return strlen(value);
}

int getprop_int(const char *name)
{
	FILE *fp;
	char buffer[64];
	int value;

	snprintf(buffer, sizeof(buffer), "getprop %s", name);
	fp = popen(buffer, "r");
	if (NULL == fp) {
		return -1;
	}

	fscanf(fp, "%d", &value);
	pclose(fp);

	return value;
}

int setprop(const char *name, const char *value)
{
	char buffer[256];
	snprintf(buffer, sizeof(buffer), "setprop %s %s", name, value);
	return system(buffer);
}
