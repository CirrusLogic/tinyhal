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

LOCAL_MODULE := libaudiohalcm
LOCAL_MODULE_TAGS := optional
LOCAL_EXPORT_C_INCLUDE_DIRS := $(LOCAL_PATH)/../include

LOCAL_CFLAGS += -Werror -Wno-error=unused-parameter -Wno-unused-parameter

LOCAL_C_INCLUDES += \
	external/tinycompress/include \
	external/tinyalsa/include \
	external/expat/lib \
	system/media/audio_effects/include \
	system/media/audio_route/include \
	$(call include-path-for, audio-utils)


LOCAL_SRC_FILES := \
	audio_config.c

LOCAL_SHARED_LIBRARIES := \
	libcutils \
	libutils \
	libdl	\
	liblog	\
	libhardware \
	libexpat	\
	libtinyalsa	\
	libaudioroute \

ifeq ($(strip $(TINYALSA_NO_ADD_NEW_CTRLS)),true)
LOCAL_CFLAGS += -DTINYALSA_NO_ADD_NEW_CTRLS
endif

ifeq ($(strip $(TINYALSA_NO_CTL_GET_ID)),true)
LOCAL_CFLAGS += -DTINYALSA_NO_CTL_GET_ID
endif

ifeq ($(strip $(BOARD_USES_VENDORIMAGE)),true)
LOCAL_PROPRIETARY_MODULE := true
LOCAL_CFLAGS += -DETC_PATH=\"/vendor/etc/\"
endif


include $(BUILD_SHARED_LIBRARY)

