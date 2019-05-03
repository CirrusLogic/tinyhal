/*
 * Copyright (C) 2014 Cirrus Logic, Inc.
 * Copyright (C) 2012-14 Wolfson Microelectronics plc
 *
 * This code is heavily based on AOSP HAL for the asus/grouper
 *
 * Copyright (C) 2012 The Android Open Source Project
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

#define LOG_TAG "tinyhal"
/*#define LOG_NDEBUG 0*/

#include <errno.h>
#include <pthread.h>
#include <stdint.h>
#include <stdlib.h>
#include <sys/time.h>

#include <cutils/log.h>
#include <cutils/properties.h>
#include <cutils/str_parms.h>

#include <utils/Timers.h>

#include <hardware/audio.h>
#include <hardware/hardware.h>

#include <system/audio.h>

#include <tinyalsa/asoundlib.h>
#include <sound/compress_params.h>
#include <sound/compress_offload.h>
#include <tinycompress/tinycompress.h>

#include <audio_utils/resampler.h>

#include <tinyhal/audio_config.h>

#include <math.h>

#ifdef ENABLE_STHAL_STREAMS
#include <scchal/scc_audio.h>
#endif

/* Kit Kit doesn't change the HAL API version despite the API changing to
 * add compress support. Use an alternative way of ensuring we can still
 * build against Jellybean without the compress playback support
 */
#ifdef AUDIO_OFFLOAD_CODEC_PARAMS
#define TINYHAL_COMPRESS_PLAYBACK
#endif

/* These values are defined in _frames_ (not bytes) to match the ALSA API */
#define OUT_PERIOD_SIZE_DEFAULT 256
#define OUT_PERIOD_COUNT_DEFAULT 4
#define OUT_CHANNEL_MASK_DEFAULT AUDIO_CHANNEL_OUT_STEREO
#define OUT_CHANNEL_COUNT_DEFAULT 2
#define OUT_RATE_DEFAULT 44100

#define IN_PERIOD_SIZE_DEFAULT 256
#define IN_PERIOD_COUNT_DEFAULT 4
#define IN_CHANNEL_MASK_DEFAULT AUDIO_CHANNEL_IN_MONO
#define IN_CHANNEL_COUNT_DEFAULT 1
#define IN_RATE_DEFAULT 44100

#define IN_PCM_BUFFER_SIZE_DEFAULT \
        (IN_PERIOD_SIZE_DEFAULT * IN_CHANNEL_COUNT_DEFAULT * sizeof(uint16_t))

/* How long compress_write() will wait for driver to signal a poll()
 * before giving up. Set to -1 to make it wait indefinitely
 */
#define MAX_COMPRESS_POLL_WAIT_MS   -1

#ifndef ETC_PATH
#define ETC_PATH "/system/etc"
#endif

#ifdef TINYHAL_COMPRESS_PLAYBACK
enum async_mode {
    ASYNC_NONE,
    ASYNC_POLL,
    ASYNC_EARLY_DRAIN,
    ASYNC_FULL_DRAIN
};

typedef void* (*async_common_fn_t)( void* arg );

typedef struct {
    bool                    exit;
    pthread_cond_t          cv;
    const struct audio_stream_out* stream;
    stream_callback_t       callback;
    void*                   callback_param;
    pthread_mutex_t         mutex;
    pthread_t               thread;
    enum async_mode         mode;
} async_common_t;
#endif /* TINYHAL_COMPRESS_PLAYBACK */

struct audio_device {
    struct audio_hw_device hw_device;

    pthread_mutex_t lock;
    bool mic_mute;
    struct config_mgr *cm;

    const struct hw_stream* global_stream;
};


typedef void(*out_close_fn)(struct audio_stream_out *);

/* Fields common to all types of output stream */
struct stream_out_common {
    struct audio_stream_out stream;

    out_close_fn    close;
    struct audio_device *dev;
    const struct hw_stream* hw;

    pthread_mutex_t lock;

    bool standby;

    /* Stream parameters as seen by AudioFlinger
     * If stream is resampling AudioFlinger buffers before
     * passing them to hardware, these members refer to the
     * _input_ data from AudioFlinger
     */
    audio_format_t format;
    uint32_t channel_mask;
    int channel_count;
    uint32_t sample_rate;
    size_t frame_size;
    uint32_t buffer_size;

    uint32_t latency;

#ifdef TINYHAL_COMPRESS_PLAYBACK
    bool use_async;
    async_common_t async_common;
#endif
};

struct stream_out_pcm {
    struct stream_out_common common;

    struct pcm *pcm;

    uint32_t hw_sample_rate;    /* actual sample rate of hardware */
    int hw_channel_count;  /* actual number of output channels */
};

#ifdef TINYHAL_COMPRESS_PLAYBACK
struct stream_out_compress {
    struct stream_out_common common;

    struct compress *compress;

    struct snd_codec codec;

    struct {
        const uint8_t *data;
        int len;
    } write;

    bool started;
    volatile bool paused; /* prevents standby while in pause */

    struct compr_gapless_mdata g_data;
    bool refresh_gapless_meta;
};
#endif /* TINYHAL_COMPRESS_PLAYBACK */

struct in_resampler {
    struct resampler_itfe *resampler;
    struct resampler_buffer_provider buf_provider;
    int16_t *buffer;
    size_t in_buffer_size;
    int in_buffer_frames;
    size_t frames_in;
    int read_status;
};

typedef void(*in_close_fn)(struct audio_stream *);

/* Fields common to all types of input stream */
struct stream_in_common {
    struct audio_stream_in stream;

    in_close_fn    close;
    struct audio_device *dev;
    const struct hw_stream* hw;

    pthread_mutex_t lock;

    bool standby;

    /* Stream parameters as seen by AudioFlinger
     * If stream is resampling AudioFlinger buffers before
     * passing them to hardware, these members refer to the
     * _input_ data from AudioFlinger
     */
    audio_devices_t devices;
    audio_format_t format;
    uint32_t channel_mask;
    int channel_count;
    uint32_t sample_rate;
    size_t frame_size;
    size_t buffer_size;

    int input_source;

    nsecs_t last_read_ns;
};

struct stream_in_pcm {
    struct stream_in_common common;

    struct pcm *pcm;

    uint32_t hw_sample_rate;    /* actual sample rate of hardware */
    int hw_channel_count;  /* actual number of input channels */
    uint32_t period_size;       /* ... of PCM input */

    struct in_resampler resampler;
};

static uint32_t out_get_sample_rate(const struct audio_stream *stream);
static uint32_t in_get_sample_rate(const struct audio_stream *stream);

/*********************************************************************
 * Stream common functions
 *********************************************************************/

static int stream_invoke_usecases(const struct hw_stream *stream, const char *kvpairs)
{
    char *parms = strdup(kvpairs);
    char *p, *temp;
    char *pval;
    char value[32];
    int ret;

    ALOGV("+stream_invoke_usecases(%p) '%s'", stream, kvpairs);

    if (!parms) {
        return -ENOMEM;
    }

    /* It's not obvious what we should do if multiple parameters
     * are given and we only understand some. The action taken
     * here is to process all that we understand and only return
     * and error if we don't understand any
     */
    ret = -ENOTSUP;

    if (stream != NULL) {
        p = strtok_r(parms, ";", &temp);
        while(p) {
            pval = strchr(p, '=');
            if (pval && (pval[1] != '\0')) {
                *pval = '\0';
                if (apply_use_case(stream, p, pval+1) >= 0) {
                    ret = 0;
                }
                *pval = '=';
            }
            p = strtok_r(NULL, ";", &temp);
        }
    }

    return ret;
}

static int common_get_routing_param(uint32_t *vout, const char *kvpairs)
{
    struct str_parms *parms;
    char value[32];
    int ret;

    parms = str_parms_create_str(kvpairs);

    ret = str_parms_get_str(parms, AUDIO_PARAMETER_STREAM_ROUTING,
                            value, sizeof(value));
    if (ret >= 0) {
        *vout = atoi(value);
    }
    str_parms_destroy(parms);
    return ret;
}

/*********************************************************************
 * Output stream common functions
 *********************************************************************/

static uint32_t out_get_sample_rate(const struct audio_stream *stream)
{
    struct stream_out_common *out = (struct stream_out_common *)stream;
    uint32_t rate;

    if (out->sample_rate != 0) {
        rate = out->sample_rate;
    } else {
        rate = out->hw->rate;
    }

    ALOGV("out_get_sample_rate=%u", rate);
    return rate;
}

static int out_set_sample_rate(struct audio_stream *stream, uint32_t rate)
{
    return -ENOSYS;
}

static size_t out_get_buffer_size(const struct audio_stream *stream)
{
    struct stream_out_common *out = (struct stream_out_common *)stream;
    ALOGV("out_get_buffer_size(%p): %u", stream, out->buffer_size );
    return out->buffer_size;
}

static audio_channel_mask_t out_get_channels(const struct audio_stream *stream)
{
    struct stream_out_common *out = (struct stream_out_common *)stream;
    audio_channel_mask_t mask;

    if (out->channel_mask != 0) {
        mask = out->channel_mask;
    } else {
        mask = OUT_CHANNEL_MASK_DEFAULT;
    }

    ALOGV("out_get_channels=%x", mask);
    return mask;
}

static audio_format_t out_get_format(const struct audio_stream *stream)
{
    struct stream_out_common *out = (struct stream_out_common *)stream;
    /*ALOGV("out_get_format(%p): 0x%x", stream, out->format );*/
    return out->format;
}

static int out_set_format(struct audio_stream *stream, audio_format_t format)
{
    return -ENOSYS;
}

static int out_dump(const struct audio_stream *stream, int fd)
{
    return 0;
}

static int out_set_parameters(struct audio_stream *stream, const char *kvpairs)
{
    ALOGV("+out_set_parameters(%p) '%s'", stream, kvpairs);

    struct stream_out_common *out = (struct stream_out_common *)stream;
    struct audio_device *adev = out->dev;
    uint32_t v;
    int ret;

    ret = common_get_routing_param(&v, kvpairs);

    pthread_mutex_lock(&adev->lock);

    if (ret >= 0) {
        apply_route(out->hw, v);
    }

    stream_invoke_usecases(out->hw, kvpairs);

    pthread_mutex_unlock(&adev->lock);

    ALOGV("-out_set_parameters(%p)", out);

    /* Its meaningless to return an error here - it's not an error if
     * we were sent a parameter we aren't interested in
     */
    return 0;
}

static char * out_get_parameters(const struct audio_stream *stream, const char *keys)
{
    return strdup("");
}

static uint32_t out_get_latency(const struct audio_stream_out *stream)
{
    struct stream_out_common *out = (struct stream_out_common *)stream;

    return out->latency;
}

static int volume_to_percent(float volume)
{
    float decibels;
    float percent;

    /* Converting back to a decibel scale */
    if(volume > 0) {
        decibels = log(volume) / 0.115129f;
    } else {
        /* Use the maximum attenuation value 58 */
        decibels = -58;
    }

    /* decibels range is -58..0, rescale to range 0..100 */
    percent = ((decibels + 58.0) * (100.0/58.0));
    return (int)percent;
}

static int out_set_volume(struct audio_stream_out *stream, float left, float right)
{
    struct stream_out_common *out = (struct stream_out_common *)stream;
    int l_pc = volume_to_percent(left);
    int r_pc = volume_to_percent(right);

    ALOGV("out_set_volume (%f,%f) -> (%d%%,%d%%)", left, right, l_pc, r_pc);

    return set_hw_volume(out->hw, l_pc, r_pc);
}

static int out_add_audio_effect(const struct audio_stream *stream, effect_handle_t effect)
{
    return 0;
}

static int out_remove_audio_effect(const struct audio_stream *stream, effect_handle_t effect)
{
    return 0;
}

static int out_get_next_write_timestamp(const struct audio_stream_out *stream,
                                        int64_t *timestamp)
{
    return -EINVAL;
}

#ifdef TINYHAL_COMPRESS_PLAYBACK
static int out_set_callback(struct audio_stream_out *stream,
                                    stream_callback_t callback, void *cookie,
                                    async_common_fn_t fn)
{
    struct stream_out_common *out = (struct stream_out_common *)stream;
    async_common_t *async = &out->async_common;

    async->exit = false;
    async->callback = NULL;

    int rv = pthread_cond_init(&(async->cv), NULL );
    if (rv != 0) {
        ALOGE("failed to create async condvar");
        return rv;
    }

    rv = pthread_mutex_init(&(async->mutex), NULL);
    if(rv != 0) {
        ALOGE("failed to create async mutex");
        pthread_cond_destroy(&(async->cv));
        return rv;
    }

    rv = pthread_create(&(async->thread), NULL, fn, async);
    if (rv != 0) {
        ALOGE("failed to create async thread");
        pthread_mutex_destroy(&(async->mutex));
        pthread_cond_destroy(&(async->cv));
        return rv;
    }

    async->stream = stream;
    async->callback = callback;
    async->callback_param = cookie;
    out->use_async = true;
    return 0;
}

static int signal_async_thread(async_common_t *async, enum async_mode mode)
{
    int ret = 0;

    pthread_mutex_lock(&(async->mutex));
    if (async->mode != ASYNC_NONE) {
        ret = -EBUSY;
    } else {
        async->mode = mode;
        pthread_cond_signal(&(async->cv));
    }
    pthread_mutex_unlock(&(async->mutex));
    return ret;
}
#endif /* TINYHAL_COMPRESS_PLAYBACK */

static void do_close_out_common(struct audio_stream_out *stream)
{
    struct stream_out_common *out = (struct stream_out_common *)stream;

#ifdef TINYHAL_COMPRESS_PLAYBACK
    /* Signal the async thread to stop and exit */
    if (out->use_async) {
        pthread_mutex_lock(&(out->async_common.mutex));
        out->async_common.exit = true;
        pthread_cond_signal(&(out->async_common.cv));
        pthread_mutex_unlock(&(out->async_common.mutex));
        // Wait for thread to exit
        pthread_join(out->async_common.thread, NULL);
    }
#endif /* TINYHAL_COMPRESS_PLAYBACK */

    release_stream(out->hw);
    free(stream);
}

static int do_init_out_common( struct stream_out_common *out,
                                    const struct audio_config *config,
                                    audio_devices_t devices )
{
    int ret;

    ALOGV("do_init_out_common rate=%u channels=%x",
                config->sample_rate,
                config->channel_mask);

    out->standby = true;

    out->stream.common.get_sample_rate = out_get_sample_rate;
    out->stream.common.set_sample_rate = out_set_sample_rate;
    out->stream.common.get_buffer_size = out_get_buffer_size;
    out->stream.common.get_channels = out_get_channels;
    out->stream.common.get_format = out_get_format;
    out->stream.common.set_format = out_set_format;
    out->stream.common.dump = out_dump;
    out->stream.common.set_parameters = out_set_parameters;
    out->stream.common.get_parameters = out_get_parameters;
    out->stream.common.add_audio_effect = out_add_audio_effect;
    out->stream.common.remove_audio_effect = out_remove_audio_effect;
    out->stream.get_latency = out_get_latency;
    out->stream.set_volume = out_set_volume;
    out->stream.get_next_write_timestamp = out_get_next_write_timestamp;

    /* init requested stream config */
    out->format = config->format;
    out->sample_rate = config->sample_rate;
    out->channel_mask = config->channel_mask;
    out->channel_count = audio_channel_count_from_out_mask(out->channel_mask);

    /* Default settings */
#ifdef AUDIO_DEVICE_API_VERSION_3_0
    out->frame_size = audio_stream_out_frame_size(&out->stream);
#else
    out->frame_size = audio_stream_frame_size(&out->stream.common);
#endif
    /* Apply initial route */
    apply_route(out->hw, devices);

    return 0;
}

/*********************************************************************
 * PCM output stream
 *********************************************************************/

static unsigned int out_pcm_cfg_period_count(struct stream_out_pcm *out)
{
    if (out->common.hw->period_count != 0) {
        return out->common.hw->period_count;
    } else {
        return OUT_PERIOD_COUNT_DEFAULT;
    }
}

static unsigned int out_pcm_cfg_period_size(struct stream_out_pcm *out)
{
    if (out->common.hw->period_size != 0) {
        return out->common.hw->period_size;
    } else {
        return OUT_PERIOD_SIZE_DEFAULT;
    }
}

static unsigned int out_pcm_cfg_rate(struct stream_out_pcm *out)
{
    if (out->common.hw->rate != 0) {
        return out->common.hw->rate;
    } else {
        return OUT_RATE_DEFAULT;
    }
}

static unsigned int out_pcm_cfg_channel_count(struct stream_out_pcm *out)
{
    if (out->common.channel_count != 0) {
        return out->common.channel_count;
    } else {
        return OUT_CHANNEL_COUNT_DEFAULT;
    }
}

/* must be called with hw device and output stream mutexes locked */
static void do_out_pcm_standby(struct stream_out_pcm *out)
{
    ALOGV("+do_out_standby(%p)", out);

    if (!out->common.standby) {
        pcm_close(out->pcm);
        out->pcm = NULL;
        out->common.standby = true;
    }

    ALOGV("-do_out_standby(%p)", out);
}

static void out_pcm_fill_params(struct stream_out_pcm *out,
                                const struct pcm_config *config )
{
    out->hw_sample_rate = config->rate;
    out->hw_channel_count = config->channels;
    out->common.buffer_size = pcm_frames_to_bytes(out->pcm, config->period_size);

    out->common.latency = (config->period_size *
                           config->period_count * 1000) / config->rate;
}

/* must be called with hw device and output stream mutexes locked */
static int start_output_pcm(struct stream_out_pcm *out)
{
    int ret;

    struct pcm_config config = {
        .channels = out_pcm_cfg_channel_count(out),
        .rate = out_pcm_cfg_rate(out),
        .period_size = out_pcm_cfg_period_size(out),
        .period_count = out_pcm_cfg_period_count(out),
        .format = PCM_FORMAT_S16_LE,
        .start_threshold = 0,
        .stop_threshold = 0,
        .silence_threshold = 0
    };

    ALOGV("+start_output_stream(%p)", out);

    out->pcm = pcm_open(out->common.hw->card_number,
                        out->common.hw->device_number,
                        PCM_OUT,
                        &config);

    if (out->pcm && !pcm_is_ready(out->pcm)) {
        ALOGE("pcm_open(out) failed: %s", pcm_get_error(out->pcm));
        pcm_close(out->pcm);
        return -ENOMEM;
    }

    out_pcm_fill_params( out, &config );

    ALOGV("-start_output_stream(%p)", out);
    return 0;
}

static int out_pcm_standby(struct audio_stream *stream)
{
    struct stream_out_pcm *out = (struct stream_out_pcm *)stream;

    pthread_mutex_lock(&out->common.lock);
    do_out_pcm_standby(out);
    pthread_mutex_unlock(&out->common.lock);

    return 0;
}

static ssize_t out_pcm_write(struct audio_stream_out *stream, const void* buffer,
                         size_t bytes)
{
    ALOGV("+out_pcm_write(%p) l=%zu", stream, bytes);

    int ret = 0;
    struct stream_out_pcm *out = (struct stream_out_pcm *)stream;

    /* Check that we are routed to something. Android can send routing
     * commands that tell us to disconnect from everything and in that
     * state we shouldn't issue any write commands because we can't be
     * sure that the driver will accept a write to nowhere
     */
    if (get_current_routes(out->common.hw) == 0) {
        ALOGV("-out_pcm_write(%p) 0 (no routes)", stream);
        return 0;
    }

    pthread_mutex_lock(&out->common.lock);
    if (out->common.standby) {
        ret = start_output_pcm(out);
        if (ret != 0) {
            goto exit;
        }
        out->common.standby = false;
    }

    ret = pcm_write(out->pcm, buffer, bytes);
    if (ret >= 0) {
        ret = bytes;
    }

exit:
    pthread_mutex_unlock(&out->common.lock);

    ALOGV("-out_pcm_write(%p) r=%u", stream, ret);

    return ret;
}

static int out_pcm_get_render_position(const struct audio_stream_out *stream,
                                   uint32_t *dsp_frames)
{
    return -EINVAL;
}

static void do_close_out_pcm(struct audio_stream_out *stream)
{
    out_pcm_standby(&stream->common);
    do_close_out_common(stream);
}

static int do_init_out_pcm( struct stream_out_pcm *out,
                                    const struct audio_config *config )
{
    out->common.close = do_close_out_pcm;
    out->common.stream.common.standby = out_pcm_standby;
    out->common.stream.write = out_pcm_write;
    out->common.stream.get_render_position = out_pcm_get_render_position;

    out->common.buffer_size = out_pcm_cfg_period_size(out) * out->common.frame_size;

    out->common.latency = (out_pcm_cfg_period_size(out) *
                out_pcm_cfg_period_count(out) * 1000) / out->common.sample_rate;

    return 0;
}

/********************************************************************
 * Compressed output stream
 ********************************************************************/

#ifdef TINYHAL_COMPRESS_PLAYBACK
static int do_standby_compress_l(struct stream_out_compress *out)
{
    int ret;

    if (out->compress && !out->paused) {
        ALOGV("out_compress_standby(%p) not paused -closing compress\n", out);
        if (out->started) {
            compress_stop(out->compress);
            out->started = false;
        }
        compress_close(out->compress);
        out->compress = NULL;
    }

    return 0;
}

static int out_compress_standby(struct audio_stream *stream)
{
    struct stream_out_compress *out = (struct stream_out_compress *)stream;
    int ret;

    pthread_mutex_lock(&out->common.lock);
    ret = do_standby_compress_l(out);
    pthread_mutex_unlock(&out->common.lock);
    return ret;
}

static int open_output_compress(struct stream_out_compress *out)
{
    struct compr_config config;
    struct compress *cmpr;
    int ret = 0;

    pthread_mutex_lock(&out->common.lock);

    if (!out->compress) {
        config.fragment_size = 0;   /* don't care */
        config.fragments = 0;
        config.codec = &out->codec;

        /* tinycompress in & out defines are the reverse of tinyalsa
           For tinycompress COMPRESS_IN=output, COMPRESS_OUT=input */
        cmpr = compress_open(out->common.hw->card_number,
                                      out->common.hw->device_number,
                                      COMPRESS_IN,
                                      &config);
        if (!is_compress_ready(cmpr)) {
            ALOGE("Failed to open output compress: %s",
                                        compress_get_error(cmpr));
            compress_close(cmpr);
            ret = -EBUSY;
            goto exit;
        }
        compress_set_max_poll_wait(cmpr, MAX_COMPRESS_POLL_WAIT_MS);
        compress_nonblock(cmpr, out->common.use_async);
        out->common.buffer_size = config.fragment_size * config.fragments;
        ALOGV("compressed buffer size=%u", out->common.buffer_size);
        out->compress = cmpr;
    }

exit:
    pthread_mutex_unlock(&out->common.lock);

    return ret;
}

static int start_output_compress(struct stream_out_compress *out)
{
    int ret;

    pthread_mutex_lock(&out->common.lock);

    ret = compress_start(out->compress);

    if (ret < 0) {
        do_standby_compress_l(out);
    } else {
        out->started = true;
        if(out->refresh_gapless_meta){
            compress_set_gapless_metadata(out->compress,&out->g_data);
            out->refresh_gapless_meta = false;
            out->g_data.encoder_delay = 0;
            out->g_data.encoder_padding = 0;
        }
    }

    pthread_mutex_unlock(&out->common.lock);
    return ret;
}

static ssize_t out_compress_write(struct audio_stream_out *stream,
                            const void* buffer, size_t bytes)
{
    struct stream_out_compress *out = (struct stream_out_compress *)stream;
    int ret = 0;

    ALOGV("out_compress_write(%p) %zu", stream, bytes);

    ret = open_output_compress(out);

    if (ret < 0) {
        ALOGE("out_compress_write(%p): failed to open: %d", stream, ret );
        return ret;
    }

    ret = compress_write(out->compress, buffer, bytes);

    if (ret >= 0) {
        if (!out->started) {
            if (start_output_compress(out) < 0) {
                ret = -1;
                goto start_failed;
            }
        }

        if (out->common.use_async) {
            if ((unsigned)ret < bytes) {
                /* not all bytes written */
                signal_async_thread(&out->common.async_common, ASYNC_POLL);
            }
        }
    }
start_failed:
    ALOGE_IF(ret < 0,"out_compress_write(%p) failed: %d\n", stream, ret);
    return ret;
}

static int out_compress_pause(struct audio_stream_out *stream)
{
    struct stream_out_compress *out = (struct stream_out_compress *)stream;
    int ret = -EBADFD;

    ALOGV("out_compress_pause(%p)", stream);

    /* Avoid race condition with standby */
    pthread_mutex_lock(&out->common.lock);

    if (!out->paused && out->compress) {
        out->paused = true;
        ret = compress_pause(out->compress);
    }
    pthread_mutex_unlock(&out->common.lock);
    return ret;
}

static int out_compress_resume(struct audio_stream_out *stream)
{
    struct stream_out_compress *out = (struct stream_out_compress *)stream;
    int ret = -EBADFD;

    ALOGV("out_compress_resume(%p)", stream);

    /* Avoid race condition with standby */
    pthread_mutex_lock(&out->common.lock);
    if (out->paused && out->compress) {
        out->paused = false;
        ret = compress_resume(out->compress);
    }
    pthread_mutex_unlock(&out->common.lock);
    return ret;
}

static int out_compress_drain(struct audio_stream_out *stream,
                                    audio_drain_type_t type)
{
    struct stream_out_compress *out = (struct stream_out_compress *)stream;
    int ret = 0;

    ALOGV("out_compress_drain(%p)", stream);

    if (out->common.use_async) {
        ret = signal_async_thread(&out->common.async_common,
                (type == AUDIO_DRAIN_EARLY_NOTIFY)
                    ? ASYNC_EARLY_DRAIN
                    : ASYNC_FULL_DRAIN);
    } else {
        if(type == AUDIO_DRAIN_EARLY_NOTIFY){
            ret = compress_next_track(out->compress);
            if(ret != 0)
                return ret;
            ret = compress_partial_drain(out->compress);
        }
        else
            ret = compress_drain(out->compress);

        out->started = false;
    }

    return ret;
}

static int out_compress_flush(struct audio_stream_out *stream)
{
    struct stream_out_compress *out = (struct stream_out_compress *)stream;

    ALOGV("out_compress_flush(%p)", stream);

    pthread_mutex_lock(&out->common.lock);
    if (out->compress && out->started) {
        compress_stop(out->compress);
        out->paused = false;
        out->started = false;
    }
    pthread_mutex_unlock(&out->common.lock);
    return 0;
}

static int out_compress_get_render_position(const struct audio_stream_out *stream,
                                   uint32_t *dsp_frames)
{
    struct stream_out_compress *out = (struct stream_out_compress *)stream;
#ifdef TINYALSA_TSTAMP_IS_LONG
    unsigned long samples;
#else
    unsigned int samples;
#endif
    unsigned int sampling_rate;

    if (dsp_frames) {
        *dsp_frames = 0;

        if (!out->started) {
            ALOGV("out_compress_get_render_position(%p) not started", stream);
            return 0;
        }

        pthread_mutex_lock(&out->common.lock);

        if (out->started) {
            if (compress_get_tstamp(out->compress, &samples, &sampling_rate) == 0) {
                *dsp_frames = samples;
                ALOGV("compress(%p) render position=%u", stream, *dsp_frames);
            }
        }

        pthread_mutex_unlock(&out->common.lock);
    }

    return 0;
}

static void* out_compress_async_fn(void *arg)
{
    async_common_t* const pW = (async_common_t*)arg;
    struct stream_out_compress *out = (struct stream_out_compress *)pW->stream;
    enum async_mode mode;

    while(!pW->exit) {
        pthread_mutex_lock(&(pW->mutex));
        ALOGV( "async fn wait for work");
        pthread_cond_wait(&(pW->cv), &(pW->mutex));

        if(pW->exit) {
            break;
        }

        mode = pW->mode;
        pW->mode = ASYNC_NONE;
        pthread_mutex_unlock(&(pW->mutex));

        switch(mode){
            case ASYNC_POLL:
                ALOGV("ASYNC_POLL");

                compress_wait(out->compress, MAX_COMPRESS_POLL_WAIT_MS);

                pW->callback( STREAM_CBK_EVENT_WRITE_READY,
                                NULL,
                                pW->callback_param );
                break;

            case ASYNC_EARLY_DRAIN:
            case ASYNC_FULL_DRAIN:
                ALOGV("ASYNC_%s_DRAIN",
                            (mode == ASYNC_EARLY_DRAIN) ? "EARLY" : "FULL");

                if(mode == ASYNC_EARLY_DRAIN){
                    compress_next_track(out->compress);
                    compress_partial_drain(out->compress);
                }
                else
                    compress_drain(out->compress);

                out->started = false;

                pW->callback( STREAM_CBK_EVENT_DRAIN_READY,
                                NULL,
                                pW->callback_param );
                break;

            default:
                break;
        }

    }

    return NULL;
}

static int out_compress_set_callback(struct audio_stream_out *stream,
                                    stream_callback_t callback, void *cookie)
{
    struct stream_out_compress *out = (struct stream_out_compress *)stream;
    int ret = out_set_callback(stream, callback, cookie, out_compress_async_fn);
    return ret;
}

static void out_compress_close(struct audio_stream_out *stream)
{
    struct stream_out_compress *out = (struct stream_out_compress *)stream;

    ALOGV("out_compress_close(%p)", stream);

    out->paused = false;
    out_compress_standby(&stream->common);

    do_close_out_common(stream);
}

static int out_compress_set_parameters(struct audio_stream *stream, const char *kv_pairs)
{
    struct stream_out_compress *out = (struct stream_out_compress *)stream;
    struct str_parms *parms;
    char value[32];
    int ret;
    bool need_refresh_gapless = false;

    ALOGV("+out_compress_set_parameters(%p) '%s' ", stream, kv_pairs);
    parms = str_parms_create_str(kv_pairs);
    ret = str_parms_get_str(parms, AUDIO_OFFLOAD_CODEC_DELAY_SAMPLES,
                            value, sizeof(value));
    if(ret >= 0){
        out->g_data.encoder_delay= atoi(value);
        need_refresh_gapless = true;
    }

    ret = str_parms_get_str(parms, AUDIO_OFFLOAD_CODEC_PADDING_SAMPLES,
                            value, sizeof(value));
    if(ret >= 0){
        out->g_data.encoder_padding= atoi(value);
        need_refresh_gapless = true;
    }

    if(need_refresh_gapless)
        out->refresh_gapless_meta = true;

    str_parms_destroy(parms);

    out_set_parameters(&out->common.stream.common, kv_pairs);

    ALOGV("-out_compress_set_parameters(%p)", out);

    /* Its meaningless to return an error here - it's not an error if
     * we were sent a parameter we aren't interested in
     */
    return 0;
}

static int do_init_out_compress(struct stream_out_compress *out,
                                    const struct audio_config *config)
{
    int ret;

    out->common.close = out_compress_close;
    out->common.stream.common.standby = out_compress_standby;
    out->common.stream.write = out_compress_write;
    out->common.stream.pause = out_compress_pause;
    out->common.stream.resume = out_compress_resume;
    out->common.stream.drain = out_compress_drain;
    out->common.stream.flush = out_compress_flush;
    out->common.stream.get_render_position = out_compress_get_render_position;
    out->common.stream.set_callback = out_compress_set_callback;
    out->common.stream.common.set_parameters = out_compress_set_parameters;

    /* struct is pre-initialized to 0x00 */
    /*out->common.latency.screen_off = 0;
    out->common.latency.screen_on = 0;

    out->codec.ch_in = 0;
    out->codec.bit_rate = 0;
    out->codec.profile = 0;
    out->codec.level = 0;
    out->codec.ch_mode = 0;
    out->codec.format = 0;*/
    out->codec.align = 1;
    out->codec.rate_control = SND_RATECONTROLMODE_CONSTANTBITRATE
                                | SND_RATECONTROLMODE_VARIABLEBITRATE;

    out->codec.sample_rate = config->sample_rate;

    switch (config->format & AUDIO_FORMAT_MAIN_MASK) {
        case AUDIO_FORMAT_MP3:
            out->codec.id = SND_AUDIOCODEC_MP3;
            break;
        case AUDIO_FORMAT_AAC:
            out->codec.id = SND_AUDIOCODEC_AAC;
            break;
        case AUDIO_FORMAT_HE_AAC_V1:
            out->codec.id = SND_AUDIOCODEC_AAC;
            out->codec.level = SND_AUDIOMODE_AAC_HE;
            break;
        case AUDIO_FORMAT_HE_AAC_V2:
            out->codec.id = SND_AUDIOCODEC_AAC;
            out->codec.level = SND_AUDIOMODE_AAC_HE;
            break;
        case AUDIO_FORMAT_VORBIS:
            out->codec.id = SND_AUDIOCODEC_VORBIS;
            break;
        default:
            return -EINVAL;
    }

    out->codec.ch_out = audio_channel_count_from_out_mask(config->channel_mask);

    /* Open compress dev to check that it exists and
     * get the buffer size. If it isn't required soon
     * AudioFlinger will call standby
     */
    ret = open_output_compress(out);
    return ret;
}
#endif /* TINYHAL_COMPRESS_PLAYBACK */

/*********************************************************************
 * Input stream common functions
 *********************************************************************/
static uint32_t in_get_sample_rate(const struct audio_stream *stream)
{
    const struct stream_in_common *in = (struct stream_in_common *)stream;
    uint32_t rate;

    if (in->sample_rate != 0) {
        rate = in->sample_rate;
    } else {
        rate = in->hw->rate;
    }

    ALOGV("in_get_sample_rate=%u", rate);
    return rate;
}

static int in_set_sample_rate(struct audio_stream *stream, uint32_t rate)
{
    const struct stream_in_common *in = (struct stream_in_common *)stream;

    if (rate == in->sample_rate) {
        return 0;
    } else {
        return -ENOTSUP;
    }
}

static audio_channel_mask_t in_get_channels(const struct audio_stream *stream)
{
    const struct stream_in_common *in = (struct stream_in_common *)stream;
    audio_channel_mask_t mask;

    if (in->channel_mask != 0) {
        mask = in->channel_mask;
    } else {
        mask = IN_CHANNEL_MASK_DEFAULT;
    }

    ALOGV("in_get_channels=0x%x", mask);
    return mask;
}

static audio_format_t in_get_format(const struct audio_stream *stream)
{
    const struct stream_in_common *in = (struct stream_in_common *)stream;

    return in->format;
}

static int in_set_format(struct audio_stream *stream, audio_format_t format)
{
    return -ENOSYS;
}

static size_t in_get_buffer_size(const struct audio_stream *stream)
{
    const struct stream_in_common *in = (struct stream_in_common *)stream;
    ALOGV("in_get_buffer_size(%p): %zu", stream, in->buffer_size );
    return in->buffer_size;
}

static int in_dump(const struct audio_stream *stream, int fd)
{
    return 0;
}

static int in_set_parameters(struct audio_stream *stream, const char *kvpairs)
{
    return 0;
}

static char * in_get_parameters(const struct audio_stream *stream,
                                const char *keys)
{
    return strdup("");
}

static int in_set_gain(struct audio_stream_in *stream, float gain)
{
    return 0;
}

static uint32_t in_get_input_frames_lost(struct audio_stream_in *stream)
{
    return 0;
}

static int in_add_audio_effect(const struct audio_stream *stream,
                               effect_handle_t effect)
{
    return 0;
}

static int in_remove_audio_effect(const struct audio_stream *stream,
                                  effect_handle_t effect)
{
    return 0;
}

static void do_in_set_read_timestamp(struct stream_in_common *in)
{
    nsecs_t ns = systemTime(SYSTEM_TIME_MONOTONIC);

    /* 0 is used to mean we don't have a timestamp, so if */
    /* time count wraps to zero change it to 1 */
    if (ns == 0) {
        ns = 1;
    }

    in->last_read_ns = ns;
}

/*
 * Delay for the time it would have taken to read <bytes> since the last
 * read at the stream sample rate
 */
static void do_in_realtime_delay(struct stream_in_common *in, size_t bytes)
{
    nsecs_t required_interval;
    nsecs_t required_ns;
    nsecs_t elapsed_ns;
    struct timespec ts;

    if (in->last_read_ns != 0) {
        /* required interval is calculated so that a left shift 19 places
         * converts approximately to nanoseconds. This avoids the overhead
         * of having to do a 64-bit division if we worked entirely in
         * nanoseconds, and of a large multiply by 1000000 to convert
         * milliseconds to 64-bit nanoseconds.
         * (1907 << 19) = 999817216
         */
        required_interval = (1907 * bytes) / (in->frame_size * in->sample_rate);
        required_ns = (nsecs_t)required_interval << 19;
        elapsed_ns = systemTime(SYSTEM_TIME_MONOTONIC) - in->last_read_ns;

        /* use ~millisecond accurace to ignore trivial nanosecond differences */
        if (required_interval > (elapsed_ns >> 19)) {
            ts.tv_sec = 0;
            ts.tv_nsec = required_ns - elapsed_ns;
            nanosleep(&ts, NULL);
        }
    }
}

static void do_close_in_common(struct audio_stream *stream)
{
    struct stream_in_common *in = (struct stream_in_common *)stream;

    in->stream.common.standby(stream);

    if (in->hw != NULL) {
        release_stream(in->hw);
    }

    free(stream);
}

static int do_init_in_common( struct stream_in_common *in,
                                const struct audio_config *config,
                                audio_devices_t devices )
{
    in->standby = true;

    in->close = do_close_in_common;
    in->stream.common.get_sample_rate = in_get_sample_rate;
    in->stream.common.set_sample_rate = in_set_sample_rate;
    in->stream.common.get_buffer_size = in_get_buffer_size;
    in->stream.common.get_channels = in_get_channels;
    in->stream.common.get_format = in_get_format;
    in->stream.common.set_format = in_set_format;
    in->stream.common.dump = in_dump;
    in->stream.common.set_parameters = in_set_parameters;
    in->stream.common.get_parameters = in_get_parameters;
    in->stream.common.add_audio_effect = in_add_audio_effect;
    in->stream.common.remove_audio_effect = in_remove_audio_effect;
    in->stream.set_gain = in_set_gain;
    in->stream.get_input_frames_lost = in_get_input_frames_lost;

    /* init requested stream config */
    in->format = config->format;
    in->sample_rate = config->sample_rate;
    in->channel_mask = config->channel_mask;
    in->channel_count = audio_channel_count_from_in_mask(in->channel_mask);

#ifdef AUDIO_DEVICE_API_VERSION_3_0
    in->frame_size = audio_stream_in_frame_size(&in->stream);
#else
    in->frame_size = audio_stream_frame_size(&in->stream.common);
#endif
    /* Save devices so we can apply initial routing after we've
     * been told the input_source and opened the stream
     */
    in->devices = devices;

    return 0;
}

/*********************************************************************
 * PCM input resampler handling
 *********************************************************************/
static int get_next_buffer(struct resampler_buffer_provider *buffer_provider,
                                   struct resampler_buffer* buffer)
{
    struct in_resampler *rsp;
    struct stream_in_pcm *in;

    if (buffer_provider == NULL || buffer == NULL) {
        return -EINVAL;
    }

    rsp = (struct in_resampler *)((char *)buffer_provider -
                                   offsetof(struct in_resampler, buf_provider));
    in = (struct stream_in_pcm *)((char *)rsp -
                                   offsetof(struct stream_in_pcm, resampler));

    if (in->pcm == NULL) {
        buffer->raw = NULL;
        buffer->frame_count = 0;
        rsp->read_status = -ENODEV;
        return -ENODEV;
    }

    if (rsp->frames_in == 0) {
        rsp->read_status = pcm_read(in->pcm,
                                   (void*)rsp->buffer,
                                   rsp->in_buffer_size);
        if (rsp->read_status != 0) {
            ALOGE("get_next_buffer() pcm_read error %d", errno);
            buffer->raw = NULL;
            buffer->frame_count = 0;
            return rsp->read_status;
        }
        rsp->frames_in = rsp->in_buffer_frames;
        if ((in->common.channel_count == 1) && (in->hw_channel_count == 2)) {
            unsigned int i;

            /* Discard right channel */
            for (i = 1; i < rsp->frames_in; i++) {
                rsp->buffer[i] = rsp->buffer[i * 2];
            }
        }
    }

    buffer->frame_count = (buffer->frame_count > rsp->frames_in) ?
                                rsp->frames_in : buffer->frame_count;
    buffer->i16 = (int16_t*)rsp->buffer + ((rsp->in_buffer_frames - rsp->frames_in));

    return rsp->read_status;
}

static void release_buffer(struct resampler_buffer_provider *buffer_provider,
                                  struct resampler_buffer* buffer)
{
    struct in_resampler *rsp;

    if (buffer_provider == NULL || buffer == NULL)
        return;

    rsp = (struct in_resampler *)((char *)buffer_provider -
                                   offsetof(struct in_resampler, buf_provider));

    rsp->frames_in -= buffer->frame_count;
}

static ssize_t read_resampled_frames(struct stream_in_pcm *in,
                                      void *buffer, ssize_t frames)
{
    struct in_resampler *rsp = &in->resampler;
    ssize_t frames_wr = 0;

    while (frames_wr < frames) {
        size_t frames_rd = frames - frames_wr;
        rsp->resampler->resample_from_provider(rsp->resampler,
                                        (int16_t *)((char *)buffer +
                                                (frames_wr * in->common.frame_size)),
                                        &frames_rd);
        if (rsp->read_status != 0) {
            return rsp->read_status;
        }

        frames_wr += frames_rd;
    }
    return frames_wr;
}

static int in_resampler_init(struct stream_in_pcm *in, int hw_rate,
                             int channels, size_t hw_fragment)
{
    struct in_resampler *rsp = &in->resampler;
    int ret = 0;

    rsp->in_buffer_size = hw_fragment * channels * in->common.frame_size;
    rsp->in_buffer_frames = rsp->in_buffer_size /
                                        (channels * in->common.frame_size);
    rsp->buffer = malloc(rsp->in_buffer_size);

    if (!rsp->buffer) {
        ret = -ENOMEM;
    } else {
        rsp->buf_provider.get_next_buffer = get_next_buffer;
        rsp->buf_provider.release_buffer = release_buffer;

        ret = create_resampler(hw_rate,
                               in->common.sample_rate,
                               in->common.channel_count,
                               RESAMPLER_QUALITY_DEFAULT,
                               &rsp->buf_provider,
                               &rsp->resampler);
    }

    if (ret < 0) {
        free(rsp->buffer);
        rsp->buffer = NULL;
    }

    return ret;
}

static void in_resampler_free(struct stream_in_pcm *in)
{
    if (in->resampler.resampler) {
        release_resampler(in->resampler.resampler);
        in->resampler.resampler = NULL;
    }

    free(in->resampler.buffer);
    in->resampler.buffer = NULL;
}

/*********************************************************************
 * PCM input stream
 *********************************************************************/

static unsigned int in_pcm_cfg_period_count(struct stream_in_pcm *in)
{
    if (in->common.hw->period_count != 0) {
        return in->common.hw->period_count;
    } else {
        return IN_PERIOD_COUNT_DEFAULT;
    }
}

static unsigned int in_pcm_cfg_period_size(struct stream_in_pcm *in)
{
    if (in->common.hw->period_size != 0) {
        return in->common.hw->period_size;
    } else {
        return IN_PERIOD_SIZE_DEFAULT;
    }
}

static unsigned int in_pcm_cfg_rate(struct stream_in_pcm *in)
{
    if (in->common.hw->rate != 0) {
        return in->common.hw->rate;
    } else {
        return IN_RATE_DEFAULT;
    }
}

static unsigned int in_pcm_cfg_channel_count(struct stream_in_pcm *in)
{
    if (in->common.channel_count != 0) {
        return in->common.channel_count;
    } else {
        return IN_CHANNEL_COUNT_DEFAULT;
    }
}

/* must be called with hw device and input stream mutexes locked */
static void do_in_pcm_standby(struct stream_in_pcm *in)
{
    ALOGV("+do_in_pcm_standby");

    if (!in->common.standby) {
        pcm_close(in->pcm);
        in->pcm = NULL;
    }

    in_resampler_free(in);
    in->common.standby = true;

    ALOGV("-do_in_pcm_standby");
}

static void in_pcm_fill_params(struct stream_in_pcm *in,
                                const struct pcm_config *config )
{
    size_t size;

    in->hw_sample_rate = config->rate;
    in->hw_channel_count = config->channels;
    in->period_size = config->period_size;

    /*
     * take resampling into account and return the closest majoring
     * multiple of 16 frames, as audioflinger expects audio buffers to
     * be a multiple of 16 frames
     */
    size = (config->period_size * in->common.sample_rate) / config->rate;
    size = ((size + 15) / 16) * 16;
    in->common.buffer_size = size * in->common.frame_size;

}

/* must be called with hw device and input stream mutexes locked */
static int do_open_pcm_input(struct stream_in_pcm *in)
{
    struct pcm_config config;
    int ret;

    ALOGV("+do_open_pcm_input");

    if (in->common.hw == NULL) {
        ALOGW("input_source not set");
        ret = -EINVAL;
        goto exit;
    }

    memset(&config, 0, sizeof(config));
    config.channels = in_pcm_cfg_channel_count(in);
    config.rate = in_pcm_cfg_rate(in),
    config.period_size = in_pcm_cfg_period_size(in),
    config.period_count = in_pcm_cfg_period_count(in),
    config.format = PCM_FORMAT_S16_LE,
    config.start_threshold = 0;

    in->pcm = pcm_open(in->common.hw->card_number,
                       in->common.hw->device_number,
                       PCM_IN,
                       &config);

    if (!in->pcm || !pcm_is_ready(in->pcm)) {
        ALOGE_IF(in->pcm,"pcm_open(in) failed: %s", pcm_get_error(in->pcm));
        ALOGE_IF(!in->pcm,"pcm_open(in) failed");
        ret = -ENOMEM;
        goto fail;
    }

    in_pcm_fill_params( in, &config );

    ALOGV("input buffer size=0x%zx", in->common.buffer_size);

    /*
     * If the stream rate differs from the PCM rate, we need to
     * create a resampler.
     */
    if (in_get_sample_rate(&in->common.stream.common) != config.rate) {
        ret = in_resampler_init(in, config.rate, config.channels,
                                pcm_frames_to_bytes(in->pcm, config.period_size));
        if (ret < 0) {
            goto fail;
        }
    }
    ALOGV("-do_open_pcm_input");
    return 0;

fail:
    pcm_close(in->pcm);
    in->pcm = NULL;
exit:
    ALOGV("-do_open_pcm_input error:%d", ret);
    return ret;
}

/* must be called with hw device and input stream mutexes locked */
static int start_pcm_input_stream(struct stream_in_pcm *in)
{
    int ret = 0;

    if (in->common.standby) {
        ret = do_open_pcm_input(in);
        if (ret == 0) {
            in->common.standby = 0;
        }
    }

    return ret;
}

static int change_input_source_locked(struct stream_in_pcm *in, const char *value,
                                uint32_t devices, bool *was_changed)
{
    struct audio_config config;
    const char *stream_name;
    const struct hw_stream *hw = NULL;
    const int new_source = atoi(value);

    *was_changed = false;

    if (!in->common.standby) {
        ALOGE("attempt to change input source while active");
        return -EINVAL;
    }

    if (in->common.input_source == new_source) {
        ALOGV("input source not changed");
        return 0;
    }

    /* Special input sources are obtained from the configuration
     * by opening a named stream
     */
    switch (new_source) {
    case AUDIO_SOURCE_VOICE_RECOGNITION:
        /* We should verify here that current frame size, sample rate and
         * channels are compatible
         */

        /* depends on voice recognition type and state whether we open
         * the voice recognition stream or generic PCM stream
         */
        stream_name = "voice recognition";
        break;

    default:
        stream_name = NULL;
        break;
    }

    if (stream_name) {
        /* try to open a stream specific to the chosen input source */
        hw = get_named_stream(in->common.dev->cm, stream_name);
        ALOGV_IF(hw != NULL, "Changing input source to %s", stream_name);
    }

    if (!hw) {
        /* open generic PCM input stream */
        memset(&config, 0, sizeof(config));
        config.sample_rate = in->common.sample_rate;
        config.channel_mask = in->common.channel_mask;
        config.format = in->common.format;
        hw = get_stream(in->common.dev->cm, devices, 0, &config);
        ALOGV_IF(hw != NULL, "Changing to default input source for devices 0x%x",
                        devices);
    }

    if (hw != NULL) {
        /* A normal stream will be in standby and therefore device node */
        /* is closed when we get here. */

        if (in->common.hw != NULL) {
            release_stream(in->common.hw);
        }

        in->common.hw = hw;
        in->common.input_source = new_source;
        *was_changed = true;
        return 0;
    } else {
        ALOGV("Could not open new input stream");
        return -EINVAL;
    }
}

static ssize_t do_in_pcm_read(struct audio_stream_in *stream, void* buffer,
                       size_t bytes)
{
    int ret = 0;
    struct stream_in_pcm *in = (struct stream_in_pcm *)stream;
    size_t frames_rq = bytes / in->common.frame_size;

    ALOGV("+do_in_pcm_read %zu", bytes);

    pthread_mutex_lock(&in->common.lock);
    ret = start_pcm_input_stream(in);

    if (ret < 0) {
        goto exit;
    }

    if (in->resampler.resampler != NULL) {
        ret = read_resampled_frames(in, buffer, frames_rq);
    } else {
        ret = pcm_read(in->pcm, buffer, bytes);
    }

    /* Assume any non-negative return is a successful read */
    if (ret >= 0) {
        ret = bytes;
    }

exit:
    pthread_mutex_unlock(&in->common.lock);

    ALOGV("-do_in_pcm_read (%d)", ret);
    return ret;
}

static int in_pcm_standby(struct audio_stream *stream)
{
    struct stream_in_pcm *in = (struct stream_in_pcm *)stream;

    pthread_mutex_lock(&in->common.lock);

    if (in->common.hw != NULL) {
            do_in_pcm_standby(in);
    }

    pthread_mutex_unlock(&in->common.lock);

    return 0;
}

static ssize_t in_pcm_read(struct audio_stream_in *stream, void* buffer,
                       size_t bytes)
{
    struct stream_in_pcm *in = (struct stream_in_pcm *)stream;
    struct audio_device *adev = in->common.dev;
    int ret;

    if (in->common.hw == NULL) {
        ALOGW("in_pcm_read(%p): no input source for stream", stream);
        ret = -EINVAL;
    } else if (get_current_routes(in->common.hw) == 0) {
        ALOGV("in_pcm_read(%p) (no routes)", stream);
        ret = -EINVAL;
    } else {
        ret = do_in_pcm_read(stream, buffer, bytes);
    }

    /* If error, no data or muted, return a buffer of zeros and delay
     * for the time it would take to capture that much audio at the
     * current sample rate. AudioFlinger can't do anything useful with
     * read errors so convert errors into a read of silence
     */
    if ((ret <= 0) || adev->mic_mute) {
        memset(buffer, 0, bytes);

        /* Only delay if we failed to capture any audio */
        if (ret <= 0) {
            do_in_realtime_delay(&in->common, bytes);
        }

        ret = bytes;
    }

    do_in_set_read_timestamp(&in->common);

    return ret;
}

static int in_pcm_set_parameters(struct audio_stream *stream, const char *kvpairs)
{
    struct stream_in_pcm *in = (struct stream_in_pcm *)stream;
    struct str_parms *parms;
    char value[32];
    uint32_t new_routing = 0;
    bool routing_changed;
    uint32_t devices;
    bool input_was_changed;
    int ret;

    ALOGV("+in_pcm_set_parameters(%p) '%s'", stream, kvpairs);

    ret = common_get_routing_param(&new_routing, kvpairs);
    routing_changed = (ret >= 0);
    parms = str_parms_create_str(kvpairs);

    pthread_mutex_lock(&in->common.lock);

    if(str_parms_get_str(parms, AUDIO_PARAMETER_STREAM_INPUT_SOURCE,
                            value, sizeof(value)) >= 0) {

        if (routing_changed) {
            devices = new_routing;
        } else if (in->common.hw != NULL) {
            /* Route new stream to same devices as current stream */
            devices = get_routed_devices(in->common.hw);
        } else {
            devices = 0;
        }

        ret = change_input_source_locked(in, value, devices, &input_was_changed);
        if (ret < 0) {
            goto out;
        }

        /* We must apply any existing routing to the new stream */
        new_routing = devices;
        routing_changed = true;
    }

    if (routing_changed) {
        in->common.devices = new_routing;

        if (in->common.hw) {
            ALOGV("Apply routing=0x%x to input stream", new_routing);
            apply_route(in->common.hw, new_routing);
        }
    }

    stream_invoke_usecases(in->common.hw, kvpairs);

out:
    pthread_mutex_unlock(&in->common.lock);
    str_parms_destroy(parms);

    ALOGV("-in_pcm_set_parameters(%p)", stream);

    /* Its meaningless to return an error here - it's not an error if
     * we were sent a parameter we aren't interested in
     */
    return 0;
}

static void do_close_in_pcm(struct audio_stream *stream)
{
    struct stream_in_pcm *in = (struct stream_in_pcm *)stream;

    do_close_in_common(stream);
}

static int do_init_in_pcm( struct stream_in_pcm *in,
                                    struct audio_config *config )
{
    in->common.close = do_close_in_pcm;
    in->common.stream.common.standby = in_pcm_standby;
    in->common.stream.common.set_parameters = in_pcm_set_parameters;
    in->common.stream.read = in_pcm_read;

    /* Although AudioFlinger has not yet told us the input_source for
     * this stream, it expects us to already know the buffer size.
     * We just have to hardcode something that might work
     */
    in->common.buffer_size = IN_PCM_BUFFER_SIZE_DEFAULT;

    return 0;
}

/*********************************************************************
 * Stream open and close
 *********************************************************************/
static int adev_open_output_stream(struct audio_hw_device *dev,
                                   audio_io_handle_t handle,
                                   audio_devices_t devices,
                                   audio_output_flags_t flags,
                                   struct audio_config *config,
                                   struct audio_stream_out **stream_out
#ifdef AUDIO_DEVICE_API_VERSION_3_0
                                   , const char *address
#endif
                                   )
{
    struct audio_device *adev = (struct audio_device *)dev;
    union {
        struct stream_out_common *common;
        struct stream_out_pcm *pcm;
#ifdef TINYHAL_COMPRESS_PLAYBACK
        struct stream_out_compress *compress;
#endif
    } out;
    int ret;

    ALOGV("+adev_open_output_stream");

    devices &= AUDIO_DEVICE_OUT_ALL;
    const struct hw_stream *hw = get_stream(adev->cm, devices, flags, config);
    if (!hw) {
        ALOGE("No suitable output stream for devices=0x%x flags=0x%x format=0x%x",
                    devices, flags, config->format );
        ret = -EINVAL;
        goto err_fail;
    }

#ifdef TINYHAL_COMPRESS_PLAYBACK
    out.common = calloc(1, hw->type == e_stream_out_pcm
                                ? sizeof(struct stream_out_pcm)
                                : sizeof(struct stream_out_compress));
#else
    out.common = calloc(1, sizeof(struct stream_out_pcm));
#endif

    if (!out.common) {
        ret = -ENOMEM;
        goto err_fail;
    }

    out.common->dev = adev;
    out.common->hw = hw;
    ret = do_init_out_common( out.common, config, devices );
    if (ret < 0) {
        goto err_open;
    }

#ifdef TINYHAL_COMPRESS_PLAYBACK
    if (hw->type == e_stream_out_pcm) {
        ret = do_init_out_pcm( out.pcm, config );
    } else {
        ret = do_init_out_compress( out.compress, config );
    }
#else
    ret = do_init_out_pcm( out.pcm, config );
#endif

    if (ret < 0) {
        goto err_open;
    }

    /* Update config with initial stream settings */
    config->format = out.common->format;
    config->channel_mask = out.common->channel_mask;
    config->sample_rate = out.common->sample_rate;

    *stream_out = &out.common->stream;
    ALOGV("-adev_open_output_stream=%p", *stream_out);
    return 0;

err_open:
    free(out.common);
    *stream_out = NULL;
err_fail:
    ALOGV("-adev_open_output_stream (%d)", ret);
    return ret;
}

static void adev_close_output_stream(struct audio_hw_device *dev,
                                     struct audio_stream_out *stream)
{
    struct stream_out_common *out = (struct stream_out_common *)stream;
    ALOGV("adev_close_output_stream(%p)", stream);
    (out->close)(stream);
}

static int adev_open_input_stream(struct audio_hw_device *dev,
                                  audio_io_handle_t handle,
                                  audio_devices_t devices,
                                  struct audio_config *config,
                                  struct audio_stream_in **stream_in
#ifdef AUDIO_DEVICE_API_VERSION_3_0
                                  , audio_input_flags_t flags,
                                  const char *address,
                                  audio_source_t source
#endif
                                  )
{
    struct audio_device *adev = (struct audio_device *)dev;
    struct stream_in_pcm *in = NULL;
    int ret;

    ALOGV("+adev_open_input_stream");

    ALOGV("Tinyhal opening input stream format %d, channel_mask=%04x, sample_rate %u"
          " flags 0x%x source 0x%x\n",
                config->format, config->channel_mask, config->sample_rate,
                flags, source);

#ifdef ENABLE_STHAL_STREAMS
    if (source == AUDIO_SOURCE_HOTWORD ||
        source == AUDIO_SOURCE_VOICE_RECOGNITION)
            return cirrus_scc_open_stream(dev, handle, devices, config, stream_in,
                                         flags, address, source);
#endif

    *stream_in = NULL;

    /* We don't open a config manager stream here because we don't yet
     * know what input_source to use. Defer until Android sends us an
     * input_source set_parameter()
     */

    in = (struct stream_in_pcm *)calloc(1, sizeof(struct stream_in_pcm));
    if (!in) {
        ret = -ENOMEM;
        goto fail;
    }

    in->common.dev = adev;

    devices &= AUDIO_DEVICE_IN_ALL;
    ret = do_init_in_common(&in->common, config, devices);
    if (ret < 0) {
        goto fail;
    }

    ret = do_init_in_pcm(in, config);
    if (ret < 0) {
        goto fail;
    }

    *stream_in = &in->common.stream;
    return 0;

fail:
    free(in);
    ALOGV("-adev_open_input_stream (%d)", ret);
    return ret;
}

static void adev_close_input_stream(struct audio_hw_device *dev,
                                   struct audio_stream_in *stream)
{
    struct stream_in_common *in = (struct stream_in_common *)stream;
    ALOGV("adev_close_input_stream(%p)", stream);

#ifdef ENABLE_STHAL_STREAMS
    if (cirrus_is_scc_stream(stream)) {
        ALOGV("adev_close_input_stream: closing scc stream\n");
        cirrus_scc_close_stream(dev, stream);
        return;
    }
#endif

    (in->close)(&stream->common);
}

/*********************************************************************
 * Global API functions
 *********************************************************************/
static int adev_set_parameters(struct audio_hw_device *dev, const char *kvpairs)
{
    struct audio_device *adev = (struct audio_device *)dev;
    struct str_parms *parms;
    char *str;
    char value[32];

    ALOGW("adev_set_parameters '%s'", kvpairs);

    if (adev->global_stream != NULL)
        stream_invoke_usecases(adev->global_stream, kvpairs);

    return 0;
}

static char * adev_get_parameters(const struct audio_hw_device *dev,
                                  const char *keys)
{
    return strdup("");
}

static int adev_init_check(const struct audio_hw_device *dev)
{
    return 0;
}

static int adev_set_voice_volume(struct audio_hw_device *dev, float volume)
{
    return -ENOSYS;
}

static int adev_set_master_volume(struct audio_hw_device *dev, float volume)
{
    return -ENOSYS;
}

static int adev_set_mode(struct audio_hw_device *dev, audio_mode_t mode)
{
    return 0;
}

static int adev_set_mic_mute(struct audio_hw_device *dev, bool state)
{
    struct audio_device *adev = (struct audio_device *)dev;

    adev->mic_mute = state;

    return 0;
}

static int adev_get_mic_mute(const struct audio_hw_device *dev, bool *state)
{
    struct audio_device *adev = (struct audio_device *)dev;

    *state = adev->mic_mute;

    return 0;
}

static size_t adev_get_input_buffer_size(const struct audio_hw_device *dev,
                                         const struct audio_config *config)
{
    size_t s = IN_PERIOD_SIZE_DEFAULT *
                    audio_bytes_per_sample(config->format) *
                    audio_channel_count_from_in_mask(config->channel_mask);

    if (s > IN_PCM_BUFFER_SIZE_DEFAULT) {
        s = IN_PCM_BUFFER_SIZE_DEFAULT;
    }

    return s;
}

static int adev_dump(const audio_hw_device_t *device, int fd)
{
    return 0;
}

static int adev_close(hw_device_t *device)
{
    struct audio_device *adev = (struct audio_device *)device;

    free_audio_config(adev->cm);

    free(device);
    return 0;
}

static int adev_open(const hw_module_t* module, const char* name,
                     hw_device_t** device)
{
    struct audio_device *adev;
    char file_name[80];
    char property[PROPERTY_VALUE_MAX];
    int ret;

    if (strcmp(name, AUDIO_HARDWARE_INTERFACE) != 0)
        return -EINVAL;

    adev = calloc(1, sizeof(struct audio_device));
    if (!adev)
        return -ENOMEM;

    adev->hw_device.common.tag = HARDWARE_DEVICE_TAG;
    adev->hw_device.common.version = AUDIO_DEVICE_API_VERSION_2_0;
    adev->hw_device.common.module = (struct hw_module_t *) module;
    adev->hw_device.common.close = adev_close;

    adev->hw_device.init_check = adev_init_check;
    adev->hw_device.set_voice_volume = adev_set_voice_volume;
    adev->hw_device.set_master_volume = adev_set_master_volume;
    adev->hw_device.set_mode = adev_set_mode;
    adev->hw_device.set_mic_mute = adev_set_mic_mute;
    adev->hw_device.get_mic_mute = adev_get_mic_mute;
    adev->hw_device.set_parameters = adev_set_parameters;
    adev->hw_device.get_parameters = adev_get_parameters;
    adev->hw_device.get_input_buffer_size = adev_get_input_buffer_size;
    adev->hw_device.open_output_stream = adev_open_output_stream;
    adev->hw_device.close_output_stream = adev_close_output_stream;
    adev->hw_device.open_input_stream = adev_open_input_stream;
    adev->hw_device.close_input_stream = adev_close_input_stream;
    adev->hw_device.dump = adev_dump;

    property_get("ro.product.device", property, "generic");
    snprintf(file_name, sizeof(file_name), "%s/audio.%s.xml", ETC_PATH, property);

    ALOGV("Reading configuration from %s\n", file_name);
    adev->cm = init_audio_config(file_name);
    if (!adev->cm) {
        ret = -errno;
        ALOGE("Failed to open config file %s (%d)", file_name, ret);
        goto fail;
    }

    adev->global_stream = get_named_stream(adev->cm, "global");

    *device = &adev->hw_device.common;

#ifdef ENABLE_STHAL_STREAMS
    ret = cirrus_scc_init();
    if (ret !=0)
        return ret;
#endif

    return 0;

fail:
    if (adev->cm) {
        free_audio_config(adev->cm);
    }

    free(adev);
    return ret;
}

static struct hw_module_methods_t hal_module_methods = {
    .open = adev_open,
};

struct audio_module HAL_MODULE_INFO_SYM = {
    .common = {
        .tag = HARDWARE_MODULE_TAG,
        .module_api_version = AUDIO_MODULE_API_VERSION_0_1,
        .hal_api_version = HARDWARE_HAL_API_VERSION,
        .id = AUDIO_HARDWARE_MODULE_ID,
        .name = "TinyHAL",
        .author = "Richard Fitzgerald <rf@opensource.wolfsonmicro.com>",
        .methods = &hal_module_methods,
    },
};
