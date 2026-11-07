LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE := onixretro
# RetroAchievements: a biblioteca rcheevos (rcheevos/src) e a ligação cheevos.c.
RC_SRC := $(patsubst $(LOCAL_PATH)/%,%,$(wildcard $(LOCAL_PATH)/rcheevos/src/*.c $(LOCAL_PATH)/rcheevos/src/*/*.c))
LOCAL_SRC_FILES := retro.c cheevos.c $(RC_SRC)
LOCAL_C_INCLUDES := $(LOCAL_PATH) $(LOCAL_PATH)/rcheevos/include $(LOCAL_PATH)/rcheevos/src
LOCAL_CFLAGS := -O2 -std=c11 -Wall -Wno-unused-variable -Wno-unused-function -DRC_CLIENT_SUPPORTS_HASH
LOCAL_LDLIBS := -lGLESv2 -lEGL -lOpenSLES -llog -ldl -lz -lm
include $(BUILD_SHARED_LIBRARY)
