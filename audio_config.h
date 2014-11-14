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
#include <system/audio.h>

struct mixer_ctl;
struct config_mgr;
struct audio_config;

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
    uint8_t             card_number;
    uint8_t             device_number;
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

/** Initialize audio config layer */
struct config_mgr *init_audio_config();

/** Delete audio config layer */
void free_audio_config( struct config_mgr *cm );

/** Get list of all supported devices */
uint32_t get_supported_devices( struct config_mgr *cm );

/** Find a suitable stream and return pointer to it */
const struct hw_stream *get_stream(  struct config_mgr *cm,
                                        const audio_devices_t devices,
                                        const audio_output_flags_t flags,
                                        const struct audio_config *config );

/** Find a named custom stream and return a pointer to it */
const struct hw_stream *get_named_stream(struct config_mgr *cm,
                                   const char *name);

/** Test whether a named custom stream is defined */
bool is_named_stream_defined(struct config_mgr *cm, const char *name);

/** Release stream */
void release_stream( const struct hw_stream *stream );

/** Get currently connected routes */
uint32_t get_current_routes( const struct hw_stream *stream );

/** Apply new device routing to a stream */
void apply_route( const struct hw_stream *stream, uint32_t devices );

/** Get bitmask of devices currently connected to this stream */
uint32_t get_routed_devices( const struct hw_stream *stream );

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
#endif  /* ifndef AUDIO_CONFIG_H */
