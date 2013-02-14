#include <jni.h>
#include <sys/system_properties.h>

jstring
Java_me_xu_tools_Sudo_getprop( JNIEnv* env,
		jobject this,
		jstring name_str)
{
	const char *name = (*env)->GetStringUTFChars(env, name_str, 0);

	char val[PROP_VALUE_MAX] = "";
	__system_property_get(name, val);

	(*env)->ReleaseStringUTFChars(env, name_str, name);
	return (*env)->NewStringUTF(env, val);
}
