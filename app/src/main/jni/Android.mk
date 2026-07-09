LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := oboe_static

MY_OBOE_SRCS := $(wildcard $(LOCAL_PATH)/oboe/src/common/*.cpp)
MY_OBOE_SRCS += $(wildcard $(LOCAL_PATH)/oboe/src/aaudio/*.cpp)
MY_OBOE_SRCS += $(wildcard $(LOCAL_PATH)/oboe/src/fifo/*.cpp)
MY_OBOE_SRCS += $(wildcard $(LOCAL_PATH)/oboe/src/flowgraph/*.cpp)
# 🔥 THE MISSING LINK: Resampler folder added below!
MY_OBOE_SRCS += $(wildcard $(LOCAL_PATH)/oboe/src/flowgraph/resampler/*.cpp)
MY_OBOE_SRCS += $(wildcard $(LOCAL_PATH)/oboe/src/opensles/*.cpp)

LOCAL_SRC_FILES := $(MY_OBOE_SRCS:$(LOCAL_PATH)/%=%)

LOCAL_C_INCLUDES := $(LOCAL_PATH)/oboe $(LOCAL_PATH)/oboe/src

LOCAL_CFLAGS := -O3 -std=c++17 -march=armv8-a -mfpu=neon -mfloat-abi=softfp -fexceptions -frtti

include $(BUILD_STATIC_LIBRARY)


include $(CLEAR_VARS)

LOCAL_MODULE    := elite_audio_engine
LOCAL_SRC_FILES := EliteAudioEngine.cpp

LOCAL_C_INCLUDES := $(LOCAL_PATH)/oboe $(LOCAL_PATH)/oboe/src

LOCAL_LDLIBS := -lOpenSLES -llog -landroid

LOCAL_STATIC_LIBRARIES := oboe_static

LOCAL_CFLAGS := -O3 -std=c++17 -march=armv8-a -mfpu=neon -mfloat-abi=softfp

include $(BUILD_SHARED_LIBRARY)
