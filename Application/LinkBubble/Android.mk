LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_CFLAGS := -std=c++11
APP_STL:=c++_static
LOCAL_MODULE := LinkBubble
LOCAL_LDLIBS := \
	-llog \

LOCAL_SRC_FILES := \
	/Users/ivan/projects/link-bubble/Application/LinkBubble/src/main/jni/hashFn.cpp \
	/Users/ivan/projects/link-bubble/Application/LinkBubble/src/main/jni/TPParser.cpp \
	/Users/ivan/projects/link-bubble/Application/LinkBubble/src/main/jni/BloomFilter.cpp \
	/Users/ivan/projects/link-bubble/Application/LinkBubble/src/main/jni/cosmeticFilter.cpp \
	/Users/ivan/projects/link-bubble/Application/LinkBubble/src/main/jni/filter.cpp \
	/Users/ivan/projects/link-bubble/Application/LinkBubble/src/main/jni/ABPFilterParser.cpp \
	/Users/ivan/projects/link-bubble/Application/LinkBubble/src/main/jni/HashSet.cpp \
	/Users/ivan/projects/link-bubble/Application/LinkBubble/src/main/jni/main.cpp \

LOCAL_C_INCLUDES += /Users/ivan/projects/link-bubble/Application/LinkBubble/src/main/jni
LOCAL_C_INCLUDES += /Users/ivan/projects/link-bubble/Application/LinkBubble/src/playstore/jni
LOCAL_C_INCLUDES += /Users/ivan/projects/link-bubble/Application/LinkBubble/src/debug/jni
LOCAL_C_INCLUDES += /Users/ivan/projects/link-bubble/Application/LinkBubble/src/playstoreDebug/jni

include $(BUILD_SHARED_LIBRARY)
