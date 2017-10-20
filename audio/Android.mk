#
# Copyright (C) 2014 Cirrus Logic, Inc.
# Copyright (C) 2012 Wolfson Microelectronics plc
# Copyright (C) 2011 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

ifeq ($(strip $(BOARD_USES_TINYHAL_AUDIO)),true)
include $(CLEAR_VARS)

ifeq ($(strip $(TINYHAL_AUDIO_MODULE_NAME)),)
LOCAL_MODULE := audio.primary.$(TARGET_DEVICE)
else
LOCAL_MODULE := audio.$(strip $(TINYHAL_AUDIO_MODULE_NAME)).$(TARGET_DEVICE)
endif

LOCAL_MODULE_RELATIVE_PATH := $(TARGET_OUT_SHARED_LIBRARIES)/hw
LOCAL_MODULE_TAGS := optional

LOCAL_CFLAGS += -Werror -Wno-error=unused-parameter -Wno-unused-parameter

LOCAL_C_INCLUDES += \
	external/tinycompress/include \
	external/tinyalsa/include \
	external/tinyhal/include \
	external/expat/lib \
	$(call include-path-for, audio-utils)

LOCAL_SRC_FILES := \
	audio_hw.c

LOCAL_STATIC_LIBRARIES := \
	libmedia_helper

LOCAL_SHARED_LIBRARIES := \
	libcutils \
	libutils \
	libdl	\
	liblog	\
	libhardware_legacy \
	libtinyalsa	\
	libtinycompress	\
	libaudiohalcm \
	libaudioutils \
	libsysutils

ifeq ($(strip $(VOICE_RECOGNITION_REQUIRES_UNSHORTEN)),true)
LOCAL_C_INCLUDES += vendor/wolfson/tools/libs/libunshorten/include
LOCAL_CFLAGS += -DCOMPRESS_PCM_USE_UNSHORTEN
LOCAL_SHARED_LIBRARIES += libunshorten
endif

include $(BUILD_SHARED_LIBRARY)

endif
