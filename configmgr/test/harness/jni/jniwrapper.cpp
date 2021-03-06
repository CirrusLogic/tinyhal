/*
 * Copyright (C) 2020 Cirrus Logic, Inc. and
 *                    Cirrus Logic International Semiconductor Ltd.
 *                    All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "tinyhal_test_harness"

#include <cerrno>
#include <memory>
#include <string>
#include <vector>

#include "CAlsaMock.h"

#ifdef ANDROID
#include <android_runtime/AndroidRuntime.h>
#include <nativehelper/JNIHelp.h>
#include <system/audio.h>
#include <utils/Errors.h>
#include <utils/Log.h>

#undef JNIEXPORT
#define JNIEXPORT static
#endif // ANDROID

#include <tinyhal/audio_config.h>

#ifndef ANDROID
#include "../../../audio_logging.h"

#include <jni.h>
#include "com_cirrus_tinyhal_test_thcm_CAlsaMock.h"
#include "com_cirrus_tinyhal_test_thcm_CConfigMgr.h"
#endif

#include "alloc_hooks.h"

#ifndef __unused
#define __unused __attribute__((__unused__))
#endif

static void throwJavaException(JNIEnv* env, const char* className, const char* message)
{
    jclass exClass = env->FindClass(className);
    if (exClass == nullptr) {
        ALOGE("Unable to find exception class %s", className);
        return;
    }

    if (env->ThrowNew(exClass, message) != JNI_OK) {
        ALOGE("Failed throwing '%s' '%s'", className, message);
    }
}

static void throwRuntimeException(JNIEnv* env, const char* message)
{
    throwJavaException(env, "java/lang/RuntimeException", message);
}

static void throwOomException(JNIEnv* env, const char* message)
{
    throwJavaException(env, "java/lang/OutOfMemoryError", message);
}

// RAII class to manage lifetime of strings returned from GetStringUTFChars
class TStringUtfAutoReleased {
public:
    explicit TStringUtfAutoReleased(JNIEnv *env, jstring str);
    ~TStringUtfAutoReleased();

    bool isOk() const { return mStr != nullptr; }
    const char* c_str() const { return mStr; }

private:
    TStringUtfAutoReleased();
    JNIEnv *mEnv;
    jstring mOriginalString;
    const char *mStr;
};

TStringUtfAutoReleased::TStringUtfAutoReleased(JNIEnv *env, jstring str)
    : mEnv(env),
      mOriginalString(str),
      mStr(env->GetStringUTFChars(str, nullptr))
{
    if (mStr == nullptr) {
        throwJavaException(env, "java/lang/OutOfMemoryError", nullptr);
    }
}

TStringUtfAutoReleased::~TStringUtfAutoReleased()
{
    if (mStr) {
        mEnv->ReleaseStringUTFChars(mOriginalString, mStr);
    }
}

static void setMockPointer(JNIEnv *env, jobject thiz, cirrus::CAlsaMock* ptr)
{
    jclass clazz = env->GetObjectClass(thiz);
    jfieldID field = env->GetFieldID(clazz, "mNativeMixerPtr", "J");
    env->SetLongField(thiz, field, (jlong)ptr);
}

static cirrus::CAlsaMock* getMockPointer(JNIEnv *env, jobject thiz)
{
    jclass clazz = env->GetObjectClass(thiz);
    jfieldID field = env->GetFieldID(clazz, "mNativeMixerPtr", "J");
    jlong ptr = env->GetLongField(thiz, field);

    return (cirrus::CAlsaMock*)ptr;
}

static void setMgrPointer(JNIEnv *env, jobject thiz, struct config_mgr * ptr)
{
    jclass clazz = env->GetObjectClass(thiz);
    jfieldID field = env->GetFieldID(clazz, "mNativeMgrPtr", "J");
    env->SetLongField(thiz, field, (jlong)ptr);
}

static struct config_mgr * getMgrPointer(JNIEnv *env, jobject thiz)
{
    jclass clazz = env->GetObjectClass(thiz);
    jfieldID field = env->GetFieldID(clazz, "mNativeMgrPtr", "J");
    jlong ptr = env->GetLongField(thiz, field);

    return (struct config_mgr *)ptr;
}

static cirrus::CMockControl* findControl(JNIEnv *env, jobject thiz, jstring name)
{
    TStringUtfAutoReleased c_name(env, name);
    if (!c_name.isOk()) {
        throwRuntimeException(env, "bad name string");
        return nullptr;
    }

    cirrus::CAlsaMock* mocker = getMockPointer(env, thiz);
    if (mocker == nullptr) {
        throwRuntimeException(env, "null mock pointer");
        return nullptr;
    }

    auto* c = mocker->getControlByName(c_name.c_str());
    if (c == nullptr) {
        throwRuntimeException(env, "control not found");
        return nullptr;
    }

    ALOGV("%s: '%s' return %p", __func__, c_name.c_str(), c);
    return c;
}

JNIEXPORT void JNICALL
Java_com_cirrus_tinyhal_test_thcm_CAlsaMock_native_1setup(JNIEnv *env __unused,
                                                          jobject thiz __unused)
{
    ALOGV("%s complete", __func__);
}

JNIEXPORT void JNICALL
Java_com_cirrus_tinyhal_test_thcm_CAlsaMock_setRedirectedProcPath(JNIEnv *env,
                                                                  jclass clazz __unused,
                                                                  jstring path)
{
    TStringUtfAutoReleased c_path(env, path);
    if (!c_path.isOk()) {
        throwRuntimeException(env, "bad path string");
    }

    cirrus::harnessSetRedirectedProcPath(std::string(c_path.c_str()));
}

JNIEXPORT jint JNICALL
Java_com_cirrus_tinyhal_test_thcm_CAlsaMock_createMixer(JNIEnv *env,
                                                        jobject thiz,
                                                        jstring fileName,
                                                        jint cardNum)
{
    TStringUtfAutoReleased c_fileName(env, fileName);
    if (!c_fileName.isOk()) {
        return -EINVAL;
    }

    std::unique_ptr<cirrus::CAlsaMock> mocker =
        std::make_unique<cirrus::CAlsaMock>(static_cast<unsigned int>(cardNum));
    int ret = mocker->readFromFile(c_fileName.c_str());
    if (ret != 0) {
        return ret;
    }

    setMockPointer(env, thiz, mocker.get());
    mocker->dump();
    mocker.release();

    ALOGV("%s complete", __func__);

    return 0;
}

JNIEXPORT void JNICALL
Java_com_cirrus_tinyhal_test_thcm_CAlsaMock_closeMixer(JNIEnv *env, jobject thiz)
{
    // this will delete the pointer on destruction if it isn't nullptr
    std::unique_ptr<cirrus::CAlsaMock> mocker(getMockPointer(env, thiz));
    setMockPointer(env, thiz, nullptr);

    ALOGV("%s complete", __func__);
}

JNIEXPORT jlong JNICALL
Java_com_cirrus_tinyhal_test_thcm_CAlsaMock_getMixerPointer(JNIEnv *env,
                                                            jobject thiz)
{
    return reinterpret_cast<jlong>(getMockPointer(env, thiz));
}

JNIEXPORT jboolean JNICALL
Java_com_cirrus_tinyhal_test_thcm_CAlsaMock_isChanged(JNIEnv *env,
                                                      jobject thiz,
                                                      jstring name)
{
    const auto* c = findControl(env, thiz, name);
    if (c == nullptr) {
        return false;
    }

    jboolean result = c->isChanged();

    return result;
}

JNIEXPORT void JNICALL
Java_com_cirrus_tinyhal_test_thcm_CAlsaMock_clearChangedFlag(JNIEnv *env,
                                                             jobject thiz,
                                                             jstring name)
{
    auto* c = findControl(env, thiz, name);
    if (c == nullptr) {
        return;
    }

    c->clearChangedFlag();
}

JNIEXPORT jint JNICALL
Java_com_cirrus_tinyhal_test_thcm_CAlsaMock_getBool(JNIEnv *env,
                                                    jobject thiz,
                                                    jstring name, jint id)
{
    const auto* c = findControl(env, thiz, name);
    if (c == nullptr) {
        return -EINVAL;
    }

    if (!c->isBool()) {
        throwRuntimeException(env, "Not Bool control");
        return -EINVAL;
    }

    // Return the full int value so test can check that it was set to 1 or 0
    return c->getInt(id);
}

JNIEXPORT jint JNICALL
Java_com_cirrus_tinyhal_test_thcm_CAlsaMock_getInt(JNIEnv *env,
                                                   jobject thiz,
                                                   jstring name, jint id)
{
    const auto* c = findControl(env, thiz, name);
    if (c == nullptr) {
        return -EINVAL;
    }

    if (!c->isInt()) {
        throwRuntimeException(env, "Not an Int control");
        return -EINVAL;
    }

    return c->getInt(id);
}

JNIEXPORT jstring JNICALL
Java_com_cirrus_tinyhal_test_thcm_CAlsaMock_getEnum(JNIEnv *env,
                                                    jobject thiz,
                                                    jstring name)
{
    const auto* c = findControl(env, thiz, name);
    if (c == nullptr) {
        return nullptr;
    }

    if (!c->isEnum()) {
        throwRuntimeException(env, "Not an Enum control");
        return nullptr;
    }

    const auto& str = c->getEnum();

    return env->NewStringUTF(str.c_str());
}

JNIEXPORT jbyteArray JNICALL
Java_com_cirrus_tinyhal_test_thcm_CAlsaMock_getData(JNIEnv *env,
                                                    jobject thiz,
                                                    jstring name)
{
    const auto* c = findControl(env, thiz, name);
    if (c == nullptr) {
        return nullptr;
    }

    if (!c->isByte()) {
        throwRuntimeException(env, "Not Byte control");
        return nullptr;
    }

    const auto& data = c->getData();

    jbyteArray result = env->NewByteArray(data.size());
    if (result == nullptr) {
        throwOomException(env, "getData: failed to alloc byte array");
        return nullptr;
    }

    env->SetByteArrayRegion(result, 0, data.size(), (const jbyte*)data.data());

    return result;
}

JNIEXPORT void JNICALL
Java_com_cirrus_tinyhal_test_thcm_CConfigMgr_native_1setup(JNIEnv *env __unused,
                                                           jobject thiz __unused)
{
    ALOGV("%s complete", __func__);
}

JNIEXPORT jboolean JNICALL
Java_com_cirrus_tinyhal_test_thcm_CConfigMgr_stream_1is_1input(JNIEnv *env,
                                                               jobject thiz,
                                                               jlong strm)
{
    auto* ptr = getMgrPointer(env, thiz);
    if (!ptr) {
        return -EINVAL;
    }

    auto *s = reinterpret_cast<const struct hw_stream *>(strm);
    if (s == nullptr) {
        return -EINVAL;
    }

    return stream_is_input(s);
}

JNIEXPORT jboolean JNICALL
Java_com_cirrus_tinyhal_test_thcm_CConfigMgr_stream_1is_1pcm(JNIEnv *env,
                                                             jobject thiz,
                                                             jlong strm)
{
    auto* ptr = getMgrPointer(env, thiz);
    if (!ptr) {
        return -EINVAL;
    }

    auto *s = reinterpret_cast<const struct hw_stream *>(strm);
    if (s == nullptr) {
        return -EINVAL;
    }

    return stream_is_pcm(s);
}

JNIEXPORT jboolean JNICALL
Java_com_cirrus_tinyhal_test_thcm_CConfigMgr_stream_1is_1compressed(JNIEnv *env,
                                                                    jobject thiz,
                                                                    jlong strm)
{
    auto* ptr = getMgrPointer(env, thiz);
    if (!ptr) {
        return -EINVAL;
    }

    auto *s = reinterpret_cast<const struct hw_stream *>(strm);
    if (s == nullptr) {
        return -EINVAL;
    }

    return stream_is_compressed(s);
}

JNIEXPORT jboolean JNICALL
Java_com_cirrus_tinyhal_test_thcm_CConfigMgr_stream_1is_1pcm_1out(JNIEnv *env,
                                                                  jobject thiz,
                                                                  jlong strm)
{
    auto* ptr = getMgrPointer(env, thiz);
    if (!ptr) {
        return -EINVAL;
    }

    auto *s = reinterpret_cast<const struct hw_stream *>(strm);
    if (s == nullptr) {
        return -EINVAL;
    }

    return stream_is_pcm_out(s);
}

JNIEXPORT jboolean JNICALL
Java_com_cirrus_tinyhal_test_thcm_CConfigMgr_stream_1is_1pcm_1in(JNIEnv *env,
                                                                 jobject thiz,
                                                                 jlong strm)
{
    auto* ptr = getMgrPointer(env, thiz);
    if (!ptr) {
        return -EINVAL;
    }

    auto *s = reinterpret_cast<const struct hw_stream *>(strm);
    if (s == nullptr) {
        return -EINVAL;
    }

    return stream_is_pcm_in(s);
}

JNIEXPORT jboolean JNICALL
Java_com_cirrus_tinyhal_test_thcm_CConfigMgr_stream_1is_1compressed_1out(JNIEnv *env,
                                                                         jobject thiz,
                                                                         jlong strm)
{
    auto* ptr = getMgrPointer(env, thiz);
    if (!ptr) {
        return -EINVAL;
    }

    auto *s = reinterpret_cast<const struct hw_stream *>(strm);
    if (s == nullptr) {
        return -EINVAL;
    }

    return stream_is_compressed_out(s);
}

JNIEXPORT jboolean JNICALL
Java_com_cirrus_tinyhal_test_thcm_CConfigMgr_stream_1is_1compressed_1in(JNIEnv *env,
                                                                        jobject thiz,
                                                                        jlong strm)
{
    auto* ptr = getMgrPointer(env, thiz);
    if (!ptr) {
        return -EINVAL;
    }

    auto *s = reinterpret_cast<const struct hw_stream *>(strm);
    if (s == nullptr) {
        return -EINVAL;
    }

    return stream_is_compressed_in(s);
}

JNIEXPORT jboolean JNICALL
Java_com_cirrus_tinyhal_test_thcm_CConfigMgr_stream_1is_1hardware(JNIEnv *env,
                                                                  jobject thiz,
                                                                  jlong strm)
{
    auto* ptr = getMgrPointer(env, thiz);
    if (!ptr) {
        return -EINVAL;
    }

    auto *s = reinterpret_cast<const struct hw_stream *>(strm);
    if (s == nullptr) {
        return -EINVAL;
    }

    return stream_is_hardware(s);
}

JNIEXPORT jint JNICALL
Java_com_cirrus_tinyhal_test_thcm_CConfigMgr_getHwStreamStruct_1card_1number(JNIEnv *env,
                                                                             jobject thiz,
                                                                             jlong strm)
{
    auto* ptr = getMgrPointer(env, thiz);
    if (!ptr) {
        return -EINVAL;
    }

    auto *s = reinterpret_cast<const struct hw_stream *>(strm);
    if (s == nullptr) {
        return -EINVAL;
    }

    return s->card_number;
}

JNIEXPORT jint JNICALL
Java_com_cirrus_tinyhal_test_thcm_CConfigMgr_getHwStreamStruct_1device_1number(JNIEnv *env,
                                                                               jobject thiz,
                                                                               jlong strm)
{
    auto* ptr = getMgrPointer(env, thiz);
    if (!ptr) {
        return -EINVAL;
    }

    auto *s = reinterpret_cast<const struct hw_stream *>(strm);
    if (s == nullptr) {
        return -EINVAL;
    }

    return s->device_number;
}

JNIEXPORT jint JNICALL
Java_com_cirrus_tinyhal_test_thcm_CConfigMgr_getHwStreamStruct_1rate(JNIEnv *env,
                                                                     jobject thiz,
                                                                     jlong strm)
{
    auto* ptr = getMgrPointer(env, thiz);
    if (!ptr) {
        return -EINVAL;
    }

    auto *s = reinterpret_cast<const struct hw_stream *>(strm);
    if (s == nullptr) {
        return -EINVAL;
    }

    return s->rate;
}

JNIEXPORT jint JNICALL
Java_com_cirrus_tinyhal_test_thcm_CConfigMgr_getHwStreamStruct_1period_1size(JNIEnv *env,
                                                                             jobject thiz,
                                                                             jlong strm)
{
    auto* ptr = getMgrPointer(env, thiz);
    if (!ptr) {
        return -EINVAL;
    }

    auto *s = reinterpret_cast<const struct hw_stream *>(strm);
    if (s == nullptr) {
        return -EINVAL;
    }

    return s->period_size;
}

JNIEXPORT jint JNICALL
Java_com_cirrus_tinyhal_test_thcm_CConfigMgr_getHwStreamStruct_1period_1count(JNIEnv *env,
                                                                              jobject thiz,
                                                                              jlong strm)
{
    auto* ptr = getMgrPointer(env, thiz);
    if (!ptr) {
        return -EINVAL;
    }

    auto *s = reinterpret_cast<const struct hw_stream *>(strm);
    if (s == nullptr) {
        return -EINVAL;
    }

    return s->period_count;
}

JNIEXPORT jint JNICALL
Java_com_cirrus_tinyhal_test_thcm_CConfigMgr_init_1audio_1config(JNIEnv *env,
                                                                 jobject thiz,
                                                                 jstring fileName)
{
    TStringUtfAutoReleased c_fileName(env, fileName);
    if (!c_fileName.isOk()) {
        return -EINVAL;
    }

    auto* mgr = init_audio_config(c_fileName.c_str());
    if (mgr == nullptr) {
        return -EINVAL;
    }

    setMgrPointer(env, thiz, mgr);

    ALOGV("%s complete", __func__);

    return 0;
}

JNIEXPORT jint JNICALL
Java_com_cirrus_tinyhal_test_thcm_CConfigMgr_free_1audio_1config(JNIEnv *env,
                                                                 jobject thiz)
{
    auto* ptr = getMgrPointer(env, thiz);
    if (!ptr) {
        return -EINVAL;
    }

    free_audio_config(ptr);
    setMgrPointer(env, thiz, nullptr);

    ALOGV("%s complete", __func__);

    return 0;
}

JNIEXPORT jlong JNICALL
Java_com_cirrus_tinyhal_test_thcm_CConfigMgr_get_1mixer(JNIEnv *env,
                                                        jobject thiz)
{
    auto* ptr = getMgrPointer(env, thiz);
    if (!ptr) {
        return 0;
    }

    return reinterpret_cast<jlong>(get_mixer(ptr));
}

JNIEXPORT jlong JNICALL
Java_com_cirrus_tinyhal_test_thcm_CConfigMgr_get_1supported_1input_1devices(JNIEnv *env,
                                                                            jobject thiz)
{
    auto* ptr = getMgrPointer(env, thiz);
    if (!ptr) {
        return -EINVAL;
    }

    // logical-AND to prevent sign extension
    return ((jlong)get_supported_input_devices(ptr)) & 0xffffffffL;
}

JNIEXPORT jlong JNICALL
Java_com_cirrus_tinyhal_test_thcm_CConfigMgr_get_1supported_1output_1devices(JNIEnv *env,
                                                                             jobject thiz)
{
    auto* ptr = getMgrPointer(env, thiz);
    if (!ptr) {
        return -EINVAL;
    }

    // logical-AND to prevent sign extension
    return ((jlong)get_supported_output_devices(ptr)) & 0xffffffffL;
}

JNIEXPORT jlong JNICALL
Java_com_cirrus_tinyhal_test_thcm_CConfigMgr_get_1stream(JNIEnv *env,
                                                         jobject thiz,
                                                         jlong devices,
                                                         jlong flags,
                                                         jobject config)
{
    auto* ptr = getMgrPointer(env, thiz);
    if (!ptr) {
        return -EINVAL;
    }

    struct audio_config native_cfg;
    memset(&native_cfg, 0, sizeof(native_cfg));

    jclass cfgcls = env->GetObjectClass(config);

    jfieldID fid = env->GetFieldID(cfgcls, "sample_rate", "I");
    native_cfg.sample_rate = env->GetIntField(config, fid);

    fid = env->GetFieldID(cfgcls, "channel_mask", "J");
    native_cfg.channel_mask = env->GetLongField(config, fid);

    fid = env->GetFieldID(cfgcls, "format", "J");
    native_cfg.format = static_cast<audio_format_t>(env->GetLongField(config, fid));

    auto* s = get_stream(ptr,
                         static_cast<audio_devices_t>(devices),
                         static_cast<audio_output_flags_t>(flags),
                         &native_cfg);

    if (s == nullptr) {
        return -ENOENT;
    }

    return (jlong)s;
}

JNIEXPORT jlong JNICALL
Java_com_cirrus_tinyhal_test_thcm_CConfigMgr_get_1named_1stream(JNIEnv *env,
                                                                jobject thiz,
                                                                jstring name)
{
    TStringUtfAutoReleased c_name(env, name);
    if (!c_name.isOk()) {
        return -EINVAL;
    }

    auto* ptr = getMgrPointer(env, thiz);
    if (!ptr) {
        return -EINVAL;
    }

    auto* s = get_named_stream(ptr, c_name.c_str());
    if (s == nullptr) {
        return -ENOENT;
    }

    return (jlong)s;
}

JNIEXPORT jstring JNICALL
Java_com_cirrus_tinyhal_test_thcm_CConfigMgr_get_1stream_1constant_1string(JNIEnv *env,
                                                                           jobject thiz,
                                                                           jlong strm,
                                                                           jstring name)
{
    auto* ptr = getMgrPointer(env, thiz);
    if (!ptr) {
        throwRuntimeException(env, "No manager pointer");
    }

    auto *s = reinterpret_cast<const struct hw_stream *>(strm);
    if (s == nullptr) {
        throwRuntimeException(env, "Stream is null");
    }

    TStringUtfAutoReleased c_name(env, name);
    if (!c_name.isOk()) {
        throwRuntimeException(env, "Bad constant name");
    }

    const char *v = nullptr;
    if (get_stream_constant_string(s, c_name.c_str(), &v) < 0) {
        throwRuntimeException(env, "Stream constant not found");
    }

    return env->NewStringUTF(v);
}

JNIEXPORT jlong JNICALL
Java_com_cirrus_tinyhal_test_thcm_CConfigMgr_get_1stream_1constant_1uint32(JNIEnv *env,
                                                                           jobject thiz,
                                                                           jlong strm,
                                                                           jstring name)
{
    auto* ptr = getMgrPointer(env, thiz);
    if (!ptr) {
        throwRuntimeException(env, "No manager pointer");
    }

    auto *s = reinterpret_cast<const struct hw_stream *>(strm);
    if (s == nullptr) {
        throwRuntimeException(env, "Stream is null");
    }

    TStringUtfAutoReleased c_name(env, name);
    if (!c_name.isOk()) {
        throwRuntimeException(env, "Bad constant name");
    }

    uint32_t v;
    if (get_stream_constant_uint32(s, c_name.c_str(), &v) < 0) {
        throwRuntimeException(env, "Stream constant not found");
    }

    // logical-AND to prevent sign extension
    return ((jlong)v) & 0xffffffffL;
}

JNIEXPORT jlong JNICALL
Java_com_cirrus_tinyhal_test_thcm_CConfigMgr_get_1stream_1constant_1int32(JNIEnv *env,
                                                                          jobject thiz,
                                                                          jlong strm,
                                                                          jstring name)
{
    auto* ptr = getMgrPointer(env, thiz);
    if (!ptr) {
        throwRuntimeException(env, "No manager pointer");
    }

    auto *s = reinterpret_cast<const struct hw_stream *>(strm);
    if (s == nullptr) {
        throwRuntimeException(env, "Stream is null");
    }

    TStringUtfAutoReleased c_name(env, name);
    if (!c_name.isOk()) {
        throwRuntimeException(env, "Bad constant name");
    }

    int32_t v;
    if (get_stream_constant_int32(s, c_name.c_str(), &v) < 0) {
        throwRuntimeException(env, "Stream constant not found");
    }

    return v;
}

JNIEXPORT jboolean JNICALL
Java_com_cirrus_tinyhal_test_thcm_CConfigMgr_is_1named_1stream_1defined(JNIEnv *env,
                                                                        jobject thiz,
                                                                        jstring name)
{
    auto* ptr = getMgrPointer(env, thiz);
    if (!ptr) {
        throwRuntimeException(env, "No manager pointer");
    }

    TStringUtfAutoReleased c_name(env, name);
    if (!c_name.isOk()) {
        throwRuntimeException(env, "Bad constant name");
    }

    return is_named_stream_defined(ptr, c_name.c_str());
}

JNIEXPORT jint JNICALL
Java_com_cirrus_tinyhal_test_thcm_CConfigMgr_release_1stream(JNIEnv *env,
                                                             jobject thiz,
                                                             jlong strm)
{
    auto* ptr = getMgrPointer(env, thiz);
    if (!ptr) {
        return -EINVAL;
    }

    auto *s = reinterpret_cast<const struct hw_stream *>(strm);
    if (s == nullptr) {
        return -EINVAL;
    }

    release_stream(s);

    return 0;
}

JNIEXPORT jlong JNICALL
Java_com_cirrus_tinyhal_test_thcm_CConfigMgr_get_1current_1routes(JNIEnv *env,
                                                                  jobject thiz,
                                                                  jlong strm)
{
    auto* ptr = getMgrPointer(env, thiz);
    if (!ptr) {
        return -EINVAL;
    }

    auto *s = reinterpret_cast<const struct hw_stream *>(strm);
    if (s == nullptr) {
        return -EINVAL;
    }

    // logical-AND to prevent sign extension
    return ((jlong)get_current_routes(s)) & 0xffffffffL;
}

JNIEXPORT jint JNICALL
Java_com_cirrus_tinyhal_test_thcm_CConfigMgr_apply_1use_1case(JNIEnv *env,
                                                              jobject thiz,
                                                              jlong strm,
                                                              jstring setting,
                                                              jstring casename)
{
    TStringUtfAutoReleased c_setting(env, setting);
    if (!c_setting.isOk()) {
        return -EINVAL;
    }

    TStringUtfAutoReleased c_casename(env, casename);
    if (!c_casename.isOk()) {
        return -EINVAL;
    }

    auto* ptr = getMgrPointer(env, thiz);
    if (!ptr) {
        return -EINVAL;
    }

    auto *s = reinterpret_cast<const struct hw_stream *>(strm);
    if (s == nullptr) {
        return -EINVAL;
    }

    return apply_use_case(s, c_setting.c_str(), c_casename.c_str());
}

JNIEXPORT void JNICALL
Java_com_cirrus_tinyhal_test_thcm_CConfigMgr_apply_1route(JNIEnv *env,
                                                          jobject thiz,
                                                          jlong strm,
                                                          jlong devices)
{
    auto* ptr = getMgrPointer(env, thiz);
    if (!ptr) {
        throwRuntimeException(env, "No manager pointer");
        return;
    }

    auto *s = reinterpret_cast<const struct hw_stream *>(strm);
    if (s == nullptr) {
        throwRuntimeException(env, "Stream is null");
        return;
    }

    apply_route(s, devices);
}

JNIEXPORT jint JNICALL
Java_com_cirrus_tinyhal_test_thcm_CConfigMgr_set_1hw_1volume(JNIEnv *env,
                                                             jobject thiz,
                                                             jlong strm,
                                                             jint left_pc,
                                                             jint right_pc)
{
    auto* ptr = getMgrPointer(env, thiz);
    if (!ptr) {
        return -EINVAL;
    }

    auto *s = reinterpret_cast<const struct hw_stream *>(strm);
    if (s == nullptr) {
        return -EINVAL;
    }

    return set_hw_volume(s, left_pc, right_pc);
}

JNIEXPORT jboolean JNICALL
Java_com_cirrus_tinyhal_test_thcm_CConfigMgr_are_1allocs_1leaked(JNIEnv *env __unused,
                                                                 jclass clazz __unused)
{
    return cirrus::harness_are_allocs_leaked();
}

#ifdef ANDROID
static const char kAlsaMockClassPathName[] = "com.cirrus.tinyhal.test.thcm.CAlsaMock";

static const JNINativeMethod kAlsaMockMethods[] = {
    { "native_setup",
      "()V",
      (void *)Java_com_cirrus_tinyhal_test_thcm_CAlsaMock_native_1setup
    },
    { "setRedirectedProcPath",
      "(Ljava/lang/String;)V",
      (void *)Java_com_cirrus_tinyhal_test_thcm_CAlsaMock_setRedirectedProcPath
    },
    { "createMixer",
      "(Ljava/lang/String;I)I",
      (void *)Java_com_cirrus_tinyhal_test_thcm_CAlsaMock_createMixer
    },
    { "closeMixer",
      "()V",
      (void *)Java_com_cirrus_tinyhal_test_thcm_CAlsaMock_closeMixer
    },
    { "getMixerPointer",
      "()J",
      (void *)Java_com_cirrus_tinyhal_test_thcm_CAlsaMock_getMixerPointer
    },
    { "isChanged",
      "(Ljava/lang/String;)Z",
      (void *)Java_com_cirrus_tinyhal_test_thcm_CAlsaMock_isChanged
    },
    { "clearChangedFlag",
      "(Ljava/lang/String;)V",
      (void *)Java_com_cirrus_tinyhal_test_thcm_CAlsaMock_clearChangedFlag
    },
    { "getBool",
      "(Ljava/lang/String;I)I",
      (void *)Java_com_cirrus_tinyhal_test_thcm_CAlsaMock_getBool
    },
    { "getInt",
      "(Ljava/lang/String;I)I",
      (void *)Java_com_cirrus_tinyhal_test_thcm_CAlsaMock_getInt
    },
    { "getEnum",
      "(Ljava/lang/String;)Ljava/lang/String;",
      (void *)Java_com_cirrus_tinyhal_test_thcm_CAlsaMock_getEnum
    },
    { "getData",
      "(Ljava/lang/String;)[B",
      (void *)Java_com_cirrus_tinyhal_test_thcm_CAlsaMock_getData
    },
};

static const char kConfigMgrClassPathName[] = "com.cirrus.tinyhal.test.thcm.CConfigMgr";

static const JNINativeMethod kConfigMgrMethods[] = {
    { "native_setup",
      "()V",
      (void *)Java_com_cirrus_tinyhal_test_thcm_CConfigMgr_native_1setup
    },
    { "stream_is_input",
      "(J)Z",
      (void *)Java_com_cirrus_tinyhal_test_thcm_CConfigMgr_stream_1is_1input,
    },
    { "stream_is_pcm",
      "(J)Z",
      (void *)Java_com_cirrus_tinyhal_test_thcm_CConfigMgr_stream_1is_1pcm,
    },
    { "stream_is_compressed",
      "(J)Z",
      (void *)Java_com_cirrus_tinyhal_test_thcm_CConfigMgr_stream_1is_1compressed,
    },
    { "stream_is_pcm_out",
      "(J)Z",
      (void *)Java_com_cirrus_tinyhal_test_thcm_CConfigMgr_stream_1is_1pcm_1out,
    },
    { "stream_is_pcm_in",
      "(J)Z",
      (void *)Java_com_cirrus_tinyhal_test_thcm_CConfigMgr_stream_1is_1pcm_1in,
    },
    { "stream_is_compressed_out",
      "(J)Z",
      (void *)Java_com_cirrus_tinyhal_test_thcm_CConfigMgr_stream_1is_1compressed_1out,
    },
    { "stream_is_compressed_in",
      "(J)Z",
      (void *)Java_com_cirrus_tinyhal_test_thcm_CConfigMgr_stream_1is_1compressed_1in,
    },
    { "stream_is_hardware",
      "(J)Z",
      (void *)Java_com_cirrus_tinyhal_test_thcm_CConfigMgr_stream_1is_1hardware,
    },
    { "getHwStreamStruct_card_number",
      "(J)I",
      (void *)Java_com_cirrus_tinyhal_test_thcm_CConfigMgr_getHwStreamStruct_1card_1number,
    },
    { "getHwStreamStruct_device_number",
      "(J)I",
      (void *)Java_com_cirrus_tinyhal_test_thcm_CConfigMgr_getHwStreamStruct_1device_1number,
    },
    { "getHwStreamStruct_rate",
      "(J)I",
      (void *)Java_com_cirrus_tinyhal_test_thcm_CConfigMgr_getHwStreamStruct_1rate,
    },
    { "getHwStreamStruct_period_size",
      "(J)I",
      (void *)Java_com_cirrus_tinyhal_test_thcm_CConfigMgr_getHwStreamStruct_1period_1size,
    },
    { "getHwStreamStruct_period_count",
      "(J)I",
      (void *)Java_com_cirrus_tinyhal_test_thcm_CConfigMgr_getHwStreamStruct_1period_1count,
    },
    { "init_audio_config",
      "(Ljava/lang/String;)I",
      (void *)Java_com_cirrus_tinyhal_test_thcm_CConfigMgr_init_1audio_1config
    },
    { "free_audio_config",
      "()I",
      (void *)Java_com_cirrus_tinyhal_test_thcm_CConfigMgr_free_1audio_1config
    },
    { "get_mixer",
      "()J",
      (void *)Java_com_cirrus_tinyhal_test_thcm_CConfigMgr_get_1mixer
    },
    { "get_supported_input_devices",
      "()J",
      (void *)Java_com_cirrus_tinyhal_test_thcm_CConfigMgr_get_1supported_1input_1devices
    },
    { "get_supported_output_devices",
      "()J",
      (void *)Java_com_cirrus_tinyhal_test_thcm_CConfigMgr_get_1supported_1output_1devices
    },
    { "get_stream",
      "(JJLcom/cirrus/tinyhal/test/thcm/CConfigMgr$AudioConfig;)J",
      (void *)Java_com_cirrus_tinyhal_test_thcm_CConfigMgr_get_1stream
    },
    { "get_named_stream",
      "(Ljava/lang/String;)J",
      (void *)Java_com_cirrus_tinyhal_test_thcm_CConfigMgr_get_1named_1stream
    },
    { "get_stream_constant_string",
      "(JLjava/lang/String;)Ljava/lang/String;",
      (void *)Java_com_cirrus_tinyhal_test_thcm_CConfigMgr_get_1stream_1constant_1string,
    },
    { "get_stream_constant_uint32",
      "(JLjava/lang/String;)J",
      (void *)Java_com_cirrus_tinyhal_test_thcm_CConfigMgr_get_1stream_1constant_1uint32,
    },
    { "get_stream_constant_int32",
      "(JLjava/lang/String;)J",
      (void *)Java_com_cirrus_tinyhal_test_thcm_CConfigMgr_get_1stream_1constant_1int32,
    },
    { "is_named_stream_defined",
      "(Ljava/lang/String;)Z",
      (void *)Java_com_cirrus_tinyhal_test_thcm_CConfigMgr_is_1named_1stream_1defined,
    },
    { "release_stream",
      "(J)I",
      (void *)Java_com_cirrus_tinyhal_test_thcm_CConfigMgr_release_1stream
    },
    { "get_current_routes",
      "(J)J",
      (void *)Java_com_cirrus_tinyhal_test_thcm_CConfigMgr_get_1current_1routes
    },
    { "apply_use_case",
      "(JLjava/lang/String;Ljava/lang/String;)I",
      (void *)Java_com_cirrus_tinyhal_test_thcm_CConfigMgr_apply_1use_1case
    },
    { "apply_route",
      "(JJ)V",
      (void *)Java_com_cirrus_tinyhal_test_thcm_CConfigMgr_apply_1route
    },
    { "set_hw_volume",
      "(JII)I",
      (void *)Java_com_cirrus_tinyhal_test_thcm_CConfigMgr_set_1hw_1volume
    },
    { "are_allocs_leaked",
      "()Z",
      (void *)Java_com_cirrus_tinyhal_test_thcm_CConfigMgr_are_1allocs_1leaked
    },
};

jint JNI_OnLoad(JavaVM* vm, void* reserved __unused)
{
    JNIEnv* env = nullptr;

    if (vm->GetEnv((void**) &env, JNI_VERSION_1_4) != JNI_OK) {
        ALOGE("GetEnv failed\n");
        return -1;
    }
    assert(env != NULL);

    int ret = ::android::AndroidRuntime::registerNativeMethods(env,
                                                               kAlsaMockClassPathName,
                                                               kAlsaMockMethods,
                                                               NELEM(kAlsaMockMethods));
    if (ret < 0) {
        ALOGE("CAlsaMock native registration failed %d", ret);
        return -1;
    }

    ret = ::android::AndroidRuntime::registerNativeMethods(env,
                                                           kConfigMgrClassPathName,
                                                           kConfigMgrMethods,
                                                           NELEM(kConfigMgrMethods));
    if (ret < 0) {
        ALOGE("CConfigMgr native registration failed %d", ret);
        return -1;
    }

    return JNI_VERSION_1_4;
}
#endif // ANDROID

