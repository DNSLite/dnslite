#include <jni.h>
#include "android_sys_prop.h"

jstring
Java_me_xu_tools_Sudo_getprop( JNIEnv* env,
		jobject this,
		jstring name_str)
{
	const char *name = (*env)->GetStringUTFChars(env, name_str, 0);

	char val[PROP_VALUE_MAX] = "";
	getprop(name, val);

	(*env)->ReleaseStringUTFChars(env, name_str, name);
	return (*env)->NewStringUTF(env, val);
}
