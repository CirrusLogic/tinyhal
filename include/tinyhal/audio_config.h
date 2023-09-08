/*
 * Copyright (C) 2012-2013 Wolfson Microelectronics plc
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

#ifndef AUDIO_CONFIG_H
#define AUDIO_CONFIG_H

#include <stddef.h>
#ifdef ANDROID
#include <system/audio.h>
#else
#include <tinyhal/audio_defs.h>
#endif

#if defined(__cplusplus)
extern "C" {
#endif

struct mixer;
struct mixer_ctl;
struct config_mgr;
struct audio_config;
struct device;

/** Stream type */
enum stream_type {
    e_stream_out_pcm,
    e_stream_in_pcm,
    e_stream_out_compress,
    e_stream_in_compress,
    e_stream_out_hw,
    e_stream_in_hw,
    e_stream_global
};

/** Information about a stream */
struct hw_stream {
    enum stream_type    type : 8;
    unsigned int        card_number;
    unsigned int        device_number;
    unsigned int        rate;
    unsigned int        period_size;
    unsigned int        period_count;
};

/** Test whether a stream is an input */
static inline bool stream_is_input( const struct hw_stream *stream )
{
    return (stream->type == e_stream_in_pcm)
        || (stream->type == e_stream_in_compress)
        || (stream->type == e_stream_in_hw);
}

/** Test whether a stream is PCM */
static inline bool stream_is_pcm( const struct hw_stream *stream )
{
    return (stream->type == e_stream_out_pcm)
        || (stream->type == e_stream_in_pcm);
}

/** Test whether a stream is compressed */
static inline bool stream_is_compressed( const struct hw_stream *stream )
{
    return (stream->type == e_stream_out_compress)
        || (stream->type == e_stream_in_compress);
}

/** Test whether stream is PCM output */
static inline bool stream_is_pcm_out( const struct hw_stream *stream )
{
return (stream->type == e_stream_out_pcm);
}

/** Test whether stream is PCM input */
static inline bool stream_is_pcm_in( const struct hw_stream *stream )
{
return (stream->type == e_stream_in_pcm);
}

/** Test whether stream is compressed output */
static inline bool stream_is_compressed_out( const struct hw_stream *stream )
{
return (stream->type == e_stream_out_compress);
}

/** Test whether stream is compressed input */
static inline bool stream_is_compressed_in( const struct hw_stream *stream )
{
return (stream->type == e_stream_in_compress);
}

/** Test whether stream is a hardware link */
static inline bool stream_is_hardware( const struct hw_stream *stream )
{
return (stream->type == e_stream_out_hw)
    || (stream->type == e_stream_in_hw);
}

/** Initialize audio config layer
 * On error return value is NULL and errno is set
 */
struct config_mgr *init_audio_config(const char *config_file_name);

/** Delete audio config layer */
void free_audio_config( struct config_mgr *cm );

/** Get libtinyalsa mixer backing this config_mgr instance */
struct mixer *get_mixer( const struct config_mgr *cm );

/** Return list of all supported input devices */
uint32_t get_supported_input_devices( struct config_mgr *cm );

/** Return list of all supported output devices */
uint32_t get_supported_output_devices( struct config_mgr *cm );

/**
 * Find a suitable stream and return pointer to it.
 * Note that this only considers unnamed streams (those without a 'name'
 * attribute). For named streams use get_named_stream().
 */
struct hw_stream *get_stream(  struct config_mgr *cm,
                                        const audio_devices_t devices,
                                        const audio_output_flags_t flags,
                                        const struct audio_config *config );

/** Find a named custom stream and return a pointer to it */
struct hw_stream *get_named_stream(struct config_mgr *cm,
                                   const char *name);

/** Return the value of a constant defined by a <set> element as a string
 * @return      0 on success
 * @return      -ENOSYS if the constant does not exist
 */
int get_stream_constant_string(const struct hw_stream *stream,
                                const char *name, char const **value);

/** Return the value of a constant defined by a <set> element as an unsigned
 * 32-bit integer
 * @return      0 on success
 * @return      -ENOSYS if the constant does not exist
 * @return      -EINVAL if the constant cannot be interpreted as an integer
 */
int get_stream_constant_uint32(const struct hw_stream *stream,
                               const char *name, uint32_t *value);

/** Return the value of a constant defined by a <set> element as a signed
 * 32-bit integer
 * @return      0 on success
 * @return      -ENOSYS if the constant does not exist
 * @return      -EINVAL if the constant cannot be interpreted as an integer
 */
int get_stream_constant_int32(const struct hw_stream *stream,
                              const char *name, int32_t *value);

/** Test whether a named custom stream is defined */
bool is_named_stream_defined(struct config_mgr *cm, const char *name);

/** Release stream */
void release_stream( const struct hw_stream *stream );

/** Get bitmask of devices currently connected to this stream */
uint32_t get_current_routes( const struct hw_stream *stream );

/** Apply new device routing to a stream */
void apply_route( const struct hw_stream *stream, uint32_t devices );

/** Apply hardware volume */
int set_hw_volume( const struct hw_stream *stream, int left_pc, int right_pc);

/** Apply a custom use-case
 *
 * @return      0 on success
 * @return      -ENOSYS if the usecase not declared
 */
int apply_use_case( const struct hw_stream* stream,
                    const char *setting,
                    const char *case_name);

/** Get the ALSA card and device number associated to this device
 * @return      0 if the device type has card and device numbers defined
 * @return      -ENODEV if the device type has no card and device number defined
 * @return      -ENOENT if the device type was not found
 */
int get_device_alsadev(struct config_mgr *cm, uint32_t type, uint32_t *cardnum, uint32_t *devnum);

#if defined(__cplusplus)
}  /* extern "C" */
#endif

#endif  /* ifndef AUDIO_CONFIG_H */
