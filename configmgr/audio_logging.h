/*
 * Copyright (C) 2019-2020 Cirrus Logic, Inc. and
 *                         Cirrus Logic International Semiconductor Ltd.
 *                         All rights reserved.
 *
 * Copied from parts of Android Nougat
 * Copyright (C) 2011 The Android Open Source Project
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
/*
 * Normally we strip ALOGV (VERBOSE messages) from release builds.
 * You can modify this (for example with "#define LOG_NDEBUG 0"
 * at the top of your source file) to change that behavior.
 */

#ifndef TINYHAL_AUDIO_LOGGING_H
#define TINYHAL_AUDIO_LOGGING_H

#ifndef LOG_NDEBUG
#define LOG_NDEBUG 1
#endif

/*
 * This is the local tag used for the following simplified
 * logging macros.  You can change this preprocessor definition
 * before using the other macros to change the tag.
 */
#ifndef LOG_TAG
#define LOG_TAG NULL
#endif

#ifndef __predict_false
#define __predict_false(exp) __builtin_expect((exp) != 0, 0)
#endif

#define LOG_VERBOSE stdout
#define LOG_ERROR   stderr
#define LOG_WARN    stderr
#define LOG_DEBUG   stdout
#define LOG_INFO    stdout

/*
 * Simplified macro to send a verbose log message using the current LOG_TAG.
 */
#ifndef ALOGV
#define __ALOGV(fmt, ...) ((void)ALOG(LOG_VERBOSE, LOG_TAG, fmt, ##__VA_ARGS__))
#if LOG_NDEBUG
#define ALOGV(fmt, ...) do { if (0) { __ALOGV(fmt, ##__VA_ARGS__); } } while (0)
#else
#define ALOGV(fmt, ...) __ALOGV(fmt, ##__VA_ARGS__)
#endif
#endif

#ifndef ALOGV_IF
#if LOG_NDEBUG
#define ALOGV_IF(cond, fmt, ...)   ((void)0)
#else
#define ALOGV_IF(cond, fmt, ...) \
    ( (__predict_false(cond)) \
    ? ((void)ALOG(LOG_VERBOSE, LOG_TAG, fmt, ##__VA_ARGS__)) \
    : (void)0 )
#endif
#endif

/*
 * Simplified macro to send a debug log message using the current LOG_TAG.
 */
#ifndef ALOGD
#define ALOGD(fmt, ...) ((void)ALOG(LOG_DEBUG, LOG_TAG, fmt, ##__VA_ARGS__))
#endif

#ifndef ALOGD_IF
#define ALOGD_IF(cond, fmt, ...) \
    ( (__predict_false(cond)) \
    ? ((void)ALOG(LOG_DEBUG, LOG_TAG, fmt, ##__VA_ARGS__)) \
    : (void)0 )
#endif

/*
 * Simplified macro to send an info log message using the current LOG_TAG.
 */
#ifndef ALOGI
#define ALOGI(fmt, ...) ((void)ALOG(LOG_INFO, LOG_TAG, fmt, ##__VA_ARGS__))
#endif

#ifndef ALOGI_IF
#define ALOGI_IF(cond, fmt, ...) \
    ( (__predict_false(cond)) \
    ? ((void)ALOG(LOG_INFO, LOG_TAG, fmt, ##__VA_ARGS__)) \
    : (void)0 )
#endif

/*
 * Simplified macro to send a warning log message using the current LOG_TAG.
 */
#ifndef ALOGW
#define ALOGW(fmt, ...) ((void)ALOG(LOG_WARN, LOG_TAG, fmt, ##__VA_ARGS__))
#endif

#ifndef ALOGW_IF
#define ALOGW_IF(cond, fmt, ...) \
    ( (__predict_false(cond)) \
    ? ((void)ALOG(LOG_WARN, LOG_TAG, fmt, ##__VA_ARGS__)) \
    : (void)0 )
#endif

/*
 * Simplified macro to send an error log message using the current LOG_TAG.
 */
#ifndef ALOGE
#define ALOGE(fmt, ...) ((void)ALOG(LOG_ERROR, LOG_TAG, fmt, ##__VA_ARGS__))
#endif

#ifndef ALOGE_IF
#define ALOGE_IF(cond, fmt, ...) \
    ( (__predict_false(cond)) \
    ? ((void)ALOG(LOG_ERROR, LOG_TAG, fmt, ##__VA_ARGS__)) \
    : (void)0 )
#endif

/*
 * Basic log message macro.
 *
 * Example:
 *  ALOG(IO, NULL, "Failed with error %d", errno);
 *
 * The second argument may be NULL or "" to indicate the "global" tag.
 */
#define TINYLOG(io, fmt, ...) \
    fprintf(io, fmt, ##__VA_ARGS__)

#ifndef ALOG
#define ALOG(io, tag, fmt, ...) \
    TINYLOG(io, "%s: " fmt "\n", tag, ##__VA_ARGS__)
#endif

#endif //TINYHAL_AUDIO_LOGGING_H
