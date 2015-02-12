#ifndef _INCLUDE_ANDROID_SYSTEM_PROPERTIES_H
#define _INCLUDE_ANDROID_SYSTEM_PROPERTIES_H

#include <sys/cdefs.h>
#include <sys/system_properties.h>

__BEGIN_DECLS

int getprop(const char *name, char *value);
int setprop(const char *name, const char *value);

int getprop_int(const char *name);

__END_DECLS

#endif
