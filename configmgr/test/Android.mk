# Copyright (C) 2020 Cirrus Logic, Inc. and
#                    Cirrus Logic International Semiconductor Ltd.
#                    All rights reserved.
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

ifeq ($(strip $(BUILD_TINYHAL_THCM_TESTS)),true)

LOCAL_PATH := $(call my-dir)

######################################################################
# Build JNI

include $(CLEAR_VARS)

LOCAL_MODULE := libcom.cirrus.tinyhal.test.thcm_jni
LOCAL_MODULE_TAGS := optional
LOCAL_PROPRIETARY_MODULE := true

LOCAL_CFLAGS += \
	-Werror -Wno-error=unused-parameter -Wno-unused-parameter \
	-DTHCM_TEST_HARNESS_BUILD

# tinyalsa include path won't be picked up magically because we aren't
# linking to it, so we must point at the (expected) header location
LOCAL_C_INCLUDES += \
	$(LOCAL_PATH)/../../../../../external/tinyalsa/include \
	$(LOCAL_PATH)/../../include \
	$(LOCAL_PATH)/.. \
	$(call include-path-for, audio-utils)


LOCAL_SRC_FILES := \
	harness/jni/CAlsaMock.cpp \
	harness/jni/jniwrapper.cpp \
	harness/jni/alloc_hooks.cpp \
	../audio_config.c

LOCAL_SHARED_LIBRARIES := \
	libcutils \
	libutils \
	liblog \
	libandroid_runtime \
	libnativehelper \
	libexpat

ifeq ($(strip $(TINYALSA_NO_ADD_NEW_CTRLS)),true)
LOCAL_CFLAGS += -DTINYALSA_NO_ADD_NEW_CTRLS
endif

ifeq ($(strip $(TINYALSA_NO_CTL_GET_ID)),true)
LOCAL_CFLAGS += -DTINYALSA_NO_CTL_GET_ID
endif

ifeq ($(strip $(BOARD_USES_VENDORIMAGE)),true)
LOCAL_CFLAGS += -DETC_PATH=\"/vendor/etc/\"
endif

include $(BUILD_SHARED_LIBRARY)

######################################################################
# Build static JAR for JNI class wrapper

include $(CLEAR_VARS)
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE := com.cirrus.tinyhal.test.thcm_jniwrap

LOCAL_SRC_FILES := $(call all-java-files-under, harness/java)

#LOCAL_PROGUARD_ENABLED := disabled
include $(BUILD_STATIC_JAVA_LIBRARY)

######################################################################
# Build tests

include $(CLEAR_VARS)
LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES := $(call all-java-files-under, java)
LOCAL_SRC_FILES += $(call all-java-files-under, platform/android)
LOCAL_PACKAGE_NAME := com.cirrus.tinyhal.test.thcm
LOCAL_PRIVATE_PLATFORM_APIS := true
LOCAL_PROPRIETARY_MODULE := true

LOCAL_STATIC_JAVA_LIBRARIES := \
    com.cirrus.tinyhal.test.thcm_jniwrap\
    android-support-test

LOCAL_JAVA_LIBRARIES := \
    android.test.runner

LOCAL_REQUIRED_MODULES := \
    libcom.cirrus.tinyhal.test.thcm_jni \
    thcm_root_xml_config.xml

# Proguard strips modules used indirectly by modules we use
# and then complains that they are missing
LOCAL_PROGUARD_ENABLED := disabled

include $(BUILD_PACKAGE)

######################################################################
# Install test config files to etc

include $(CLEAR_VARS)
LOCAL_MODULE := thcm_root_xml_config.xml
LOCAL_PROPRIETARY_MODULE := true
LOCAL_SRC_FILES := data/android/thcm_root_xml_config.xml
LOCAL_MODULE_CLASS := ETC
LOCAL_MODULE_TAGS := optional
#LOCAL_MODULE_PATH := $(TARGET_OUT_VENDOR)/etc
include $(BUILD_PREBUILT)

endif
