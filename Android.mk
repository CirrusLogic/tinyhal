ifeq  ($(strip $(BOARD_USES_TINY_AUDIO_HW)),true)

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

# Should change this so the enable variable gets used as the name?
LOCAL_MODULE := audio.primary.herring
LOCAL_MODULE_PATH := $(TARGET_OUT_SHARED_LIBRARIES)/hw
LOCAL_SRC_FILES := audio_hw.c
LOCAL_C_INCLUDES += \
	external/tinyalsa/include \
	external/expat/lib \
	system/media/audio_utils/include \
	system/media/audio_effects/include
LOCAL_SHARED_LIBRARIES := liblog libcutils libtinyalsa libaudioutils \
	libdl libexpat
LOCAL_MODULE_TAGS := optional

include $(BUILD_SHARED_LIBRARY)

endif
