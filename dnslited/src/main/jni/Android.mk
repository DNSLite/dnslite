LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := dnsproxy
LOCAL_SRC_FILES := dnsproxy.cc net.c cache.cc dns.c nameserver.c \
	android_sys_prop.c android.c
LOCAL_C_INCLUDES := .
LOCAL_CFLAGS += -pie -fPIE
LOCAL_LDFLAGS += -pie -fPIE
LOCAL_CPP_EXTENSION := .cc
LOCAL_LDLIBS := -llog

include $(BUILD_EXECUTABLE)
