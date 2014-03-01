# for i in *;do cd $i && cp dnsproxy libdnsproxy.so && cd ..;done
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := dnsproxy
LOCAL_SRC_FILES := dnsproxy.cc net.c cache.cc dns.c nameserver.c
LOCAL_C_INCLUDES := .
#LOCAL_CFLAGS := -std=gnu++0x
LOCAL_CPP_EXTENSION := .cc
#LOCAL_LDLIBS := -L$(SYSROOT)/usr/lib -llog

include $(BUILD_EXECUTABLE)


include $(CLEAR_VARS)

LOCAL_MODULE    := util
LOCAL_SRC_FILES := util.c

#LOCAL_STATIC_LIBRARIES := libtwolib-first

include $(BUILD_SHARED_LIBRARY)

