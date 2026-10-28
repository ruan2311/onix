LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE := onixretro
LOCAL_SRC_FILES := retro.c
LOCAL_CFLAGS := -O2 -std=c11 -Wall -Wno-unused-variable -Wno-unused-function
LOCAL_LDLIBS := -lGLESv2 -lOpenSLES -llog -ldl -lz -lm
include $(BUILD_SHARED_LIBRARY)
