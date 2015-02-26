LOCAL_PATH := $(call my-dir)

TARGET_PIE := false
NDK_APP_PIE := false

include $(CLEAR_VARS)

LOCAL_MODULE    := run_pie
LOCAL_SRC_FILES := run_pie.c

include $(BUILD_EXECUTABLE)
