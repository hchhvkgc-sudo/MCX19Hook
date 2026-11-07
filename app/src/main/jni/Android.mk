LOCAL_PATH := $(call my-dir)

# Dobby 静态库
include $(CLEAR_VARS)
LOCAL_MODULE := dobby
LOCAL_SRC_FILES := dobby/arm64-v8a/libdobby.a
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/dobby/include
include $(PREBUILT_STATIC_LIBRARY)

# 主模块
include $(CLEAR_VARS)
LOCAL_MODULE := mcx19hook
LOCAL_SRC_FILES := native_hooks.cpp sigscan.cpp mem.cpp anti_detect.cpp dobby_wrapper.cpp
LOCAL_C_INCLUDES := $(LOCAL_PATH)/dobby/include
LOCAL_CPPFLAGS := -std=c++20 -O3 -fvisibility=hidden -DDOBBY_EXPORT -DDISABLE_LOGGING -fstack-protector-strong -D_FORTIFY_SOURCE=2 -Wno-unused-parameter -Wno-write-strings
LOCAL_LDLIBS := -llog -ldl -landroid
LOCAL_STATIC_LIBRARIES := dobby
include $(BUILD_SHARED_LIBRARY)
