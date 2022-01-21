/*
 * Copyright (C) 2018-2019 Cirrus Logic
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

#ifndef TINYHAL_AUDIO_DEFS_H
#define TINYHAL_AUDIO_DEFS_H

#include <limits.h>
#include <string.h>

/* Taken from Android N */

enum {
    AUDIO_DEVICE_NONE                          = 0x0,
    /* reserved bits */
    AUDIO_DEVICE__AAABIT_IN                        = 0x80000000,
    AUDIO_DEVICE_BIT_DEFAULT                   = 0x40000000,
    /* output devices */
    AUDIO_DEVICE_OUT_EARPIECE                  = 0x1,
    AUDIO_DEVICE_OUT_SPEAKER                   = 0x2,
    AUDIO_DEVICE_OUT_WIRED_HEADSET             = 0x4,
    AUDIO_DEVICE_OUT_WIRED_HEADPHONE           = 0x8,
    AUDIO_DEVICE_OUT_BLUETOOTH_SCO             = 0x10,
    AUDIO_DEVICE_OUT_BLUETOOTH_SCO_HEADSET     = 0x20,
    AUDIO_DEVICE_OUT_BLUETOOTH_SCO_CARKIT      = 0x40,
    AUDIO_DEVICE_OUT_BLUETOOTH_A2DP            = 0x80,
    AUDIO_DEVICE_OUT_BLUETOOTH_A2DP_HEADPHONES = 0x100,
    AUDIO_DEVICE_OUT_BLUETOOTH_A2DP_SPEAKER    = 0x200,
    AUDIO_DEVICE_OUT_AUX_DIGITAL               = 0x400,
    AUDIO_DEVICE_OUT_HDMI                      = AUDIO_DEVICE_OUT_AUX_DIGITAL,
    /* uses an analog connection (multiplexed over the USB connector pins for instance) */
    AUDIO_DEVICE_OUT_ANLG_DOCK_HEADSET         = 0x800,
    AUDIO_DEVICE_OUT_DGTL_DOCK_HEADSET         = 0x1000,
    /* USB accessory mode: your Android device is a USB device and the dock is a USB host */
    AUDIO_DEVICE_OUT_USB_ACCESSORY             = 0x2000,
    /* USB host mode: your Android device is a USB host and the dock is a USB device */
    AUDIO_DEVICE_OUT_USB_DEVICE                = 0x4000,
    AUDIO_DEVICE_OUT_REMOTE_SUBMIX             = 0x8000,
    /* Telephony voice TX path */
    AUDIO_DEVICE_OUT_TELEPHONY_TX              = 0x10000,
    /* Analog jack with line impedance detected */
    AUDIO_DEVICE_OUT_LINE                      = 0x20000,
    /* HDMI Audio Return Channel */
    AUDIO_DEVICE_OUT_HDMI_ARC                  = 0x40000,
    /* S/PDIF out */
    AUDIO_DEVICE_OUT_SPDIF                     = 0x80000,
    /* FM transmitter out */
    AUDIO_DEVICE_OUT_FM                        = 0x100000,
    /* Line out for av devices */
    AUDIO_DEVICE_OUT_AUX_LINE                  = 0x200000,
    /* limited-output speaker device for acoustic safety */
    AUDIO_DEVICE_OUT_SPEAKER_SAFE              = 0x400000,
    AUDIO_DEVICE_OUT_IP                        = 0x800000,
    /* audio bus implemented by the audio system (e.g an MOST stereo channel) */
    AUDIO_DEVICE_OUT_BUS                       = 0x1000000,
    AUDIO_DEVICE_OUT_DEFAULT                   = AUDIO_DEVICE_BIT_DEFAULT,
    AUDIO_DEVICE_OUT_ALL      = (AUDIO_DEVICE_OUT_EARPIECE |
                                 AUDIO_DEVICE_OUT_SPEAKER |
                                 AUDIO_DEVICE_OUT_WIRED_HEADSET |
                                 AUDIO_DEVICE_OUT_WIRED_HEADPHONE |
                                 AUDIO_DEVICE_OUT_BLUETOOTH_SCO |
                                 AUDIO_DEVICE_OUT_BLUETOOTH_SCO_HEADSET |
                                 AUDIO_DEVICE_OUT_BLUETOOTH_SCO_CARKIT |
                                 AUDIO_DEVICE_OUT_BLUETOOTH_A2DP |
                                 AUDIO_DEVICE_OUT_BLUETOOTH_A2DP_HEADPHONES |
                                 AUDIO_DEVICE_OUT_BLUETOOTH_A2DP_SPEAKER |
                                 AUDIO_DEVICE_OUT_HDMI |
                                 AUDIO_DEVICE_OUT_ANLG_DOCK_HEADSET |
                                 AUDIO_DEVICE_OUT_DGTL_DOCK_HEADSET |
                                 AUDIO_DEVICE_OUT_USB_ACCESSORY |
                                 AUDIO_DEVICE_OUT_USB_DEVICE |
                                 AUDIO_DEVICE_OUT_REMOTE_SUBMIX |
                                 AUDIO_DEVICE_OUT_TELEPHONY_TX |
                                 AUDIO_DEVICE_OUT_LINE |
                                 AUDIO_DEVICE_OUT_HDMI_ARC |
                                 AUDIO_DEVICE_OUT_SPDIF |
                                 AUDIO_DEVICE_OUT_FM |
                                 AUDIO_DEVICE_OUT_AUX_LINE |
                                 AUDIO_DEVICE_OUT_SPEAKER_SAFE |
                                 AUDIO_DEVICE_OUT_IP |
                                 AUDIO_DEVICE_OUT_BUS |
                                 AUDIO_DEVICE_OUT_DEFAULT),
    AUDIO_DEVICE_OUT_ALL_A2DP = (AUDIO_DEVICE_OUT_BLUETOOTH_A2DP |
                                 AUDIO_DEVICE_OUT_BLUETOOTH_A2DP_HEADPHONES |
                                 AUDIO_DEVICE_OUT_BLUETOOTH_A2DP_SPEAKER),
    AUDIO_DEVICE_OUT_ALL_SCO  = (AUDIO_DEVICE_OUT_BLUETOOTH_SCO |
                                 AUDIO_DEVICE_OUT_BLUETOOTH_SCO_HEADSET |
                                 AUDIO_DEVICE_OUT_BLUETOOTH_SCO_CARKIT),
    AUDIO_DEVICE_OUT_ALL_USB  = (AUDIO_DEVICE_OUT_USB_ACCESSORY |
                                 AUDIO_DEVICE_OUT_USB_DEVICE),
    /* input devices */
    AUDIO_DEVICE_IN_COMMUNICATION         = AUDIO_DEVICE_BIT_IN | 0x1,
    AUDIO_DEVICE_IN_AMBIENT               = AUDIO_DEVICE_BIT_IN | 0x2,
    AUDIO_DEVICE_IN_BUILTIN_MIC           = AUDIO_DEVICE_BIT_IN | 0x4,
    AUDIO_DEVICE_IN_BLUETOOTH_SCO_HEADSET = AUDIO_DEVICE_BIT_IN | 0x8,
    AUDIO_DEVICE_IN_WIRED_HEADSET         = AUDIO_DEVICE_BIT_IN | 0x10,
    AUDIO_DEVICE_IN_AUX_DIGITAL           = AUDIO_DEVICE_BIT_IN | 0x20,
    AUDIO_DEVICE_IN_HDMI                  = AUDIO_DEVICE_IN_AUX_DIGITAL,
    /* Telephony voice RX path */
    AUDIO_DEVICE_IN_VOICE_CALL            = AUDIO_DEVICE_BIT_IN | 0x40,
    AUDIO_DEVICE_IN_TELEPHONY_RX          = AUDIO_DEVICE_IN_VOICE_CALL,
    AUDIO_DEVICE_IN_BACK_MIC              = AUDIO_DEVICE_BIT_IN | 0x80,
    AUDIO_DEVICE_IN_REMOTE_SUBMIX         = AUDIO_DEVICE_BIT_IN | 0x100,
    AUDIO_DEVICE_IN_ANLG_DOCK_HEADSET     = AUDIO_DEVICE_BIT_IN | 0x200,
    AUDIO_DEVICE_IN_DGTL_DOCK_HEADSET     = AUDIO_DEVICE_BIT_IN | 0x400,
    AUDIO_DEVICE_IN_USB_ACCESSORY         = AUDIO_DEVICE_BIT_IN | 0x800,
    AUDIO_DEVICE_IN_USB_DEVICE            = AUDIO_DEVICE_BIT_IN | 0x1000,
    /* FM tuner input */
    AUDIO_DEVICE_IN_FM_TUNER              = AUDIO_DEVICE_BIT_IN | 0x2000,
    /* TV tuner input */
    AUDIO_DEVICE_IN_TV_TUNER              = AUDIO_DEVICE_BIT_IN | 0x4000,
    /* Analog jack with line impedance detected */
    AUDIO_DEVICE_IN_LINE                  = AUDIO_DEVICE_BIT_IN | 0x8000,
    /* S/PDIF in */
    AUDIO_DEVICE_IN_SPDIF                 = AUDIO_DEVICE_BIT_IN | 0x10000,
    AUDIO_DEVICE_IN_BLUETOOTH_A2DP        = AUDIO_DEVICE_BIT_IN | 0x20000,
    AUDIO_DEVICE_IN_LOOPBACK              = AUDIO_DEVICE_BIT_IN | 0x40000,
    AUDIO_DEVICE_IN_IP                    = AUDIO_DEVICE_BIT_IN | 0x80000,
    /* audio bus implemented by the audio system (e.g an MOST stereo channel) */
    AUDIO_DEVICE_IN_BUS                   = AUDIO_DEVICE_BIT_IN | 0x100000,
    AUDIO_DEVICE_IN_DEFAULT               = AUDIO_DEVICE_BIT_IN | AUDIO_DEVICE_BIT_DEFAULT,

    AUDIO_DEVICE_IN_ALL     = (AUDIO_DEVICE_IN_COMMUNICATION |
                               AUDIO_DEVICE_IN_AMBIENT |
                               AUDIO_DEVICE_IN_BUILTIN_MIC |
                               AUDIO_DEVICE_IN_BLUETOOTH_SCO_HEADSET |
                               AUDIO_DEVICE_IN_WIRED_HEADSET |
                               AUDIO_DEVICE_IN_HDMI |
                               AUDIO_DEVICE_IN_TELEPHONY_RX |
                               AUDIO_DEVICE_IN_BACK_MIC |
                               AUDIO_DEVICE_IN_REMOTE_SUBMIX |
                               AUDIO_DEVICE_IN_ANLG_DOCK_HEADSET |
                               AUDIO_DEVICE_IN_DGTL_DOCK_HEADSET |
                               AUDIO_DEVICE_IN_USB_ACCESSORY |
                               AUDIO_DEVICE_IN_USB_DEVICE |
                               AUDIO_DEVICE_IN_FM_TUNER |
                               AUDIO_DEVICE_IN_TV_TUNER |
                               AUDIO_DEVICE_IN_LINE |
                               AUDIO_DEVICE_IN_SPDIF |
                               AUDIO_DEVICE_IN_BLUETOOTH_A2DP |
                               AUDIO_DEVICE_IN_LOOPBACK |
                               AUDIO_DEVICE_IN_IP |
                               AUDIO_DEVICE_IN_BUS |
                               AUDIO_DEVICE_IN_DEFAULT),
    AUDIO_DEVICE_IN_ALL_SCO = AUDIO_DEVICE_IN_BLUETOOTH_SCO_HEADSET,
    AUDIO_DEVICE_IN_ALL_USB  = (AUDIO_DEVICE_IN_USB_ACCESSORY |
                                AUDIO_DEVICE_IN_USB_DEVICE),
};

typedef uint32_t audio_devices_t;

/* the audio output flags serve two purposes:
 * - when an AudioTrack is created they indicate a "wish" to be connected to an
 * output stream with attributes corresponding to the specified flags
 * - when present in an output profile descriptor listed for a particular audio
 * hardware module, they indicate that an output stream can be opened that
 * supports the attributes indicated by the flags.
 * the audio policy manager will try to match the flags in the request
 * (when getOuput() is called) to an available output stream.
 */
typedef enum {
    AUDIO_OUTPUT_FLAG_NONE = 0x0,       // no attributes
    AUDIO_OUTPUT_FLAG_DIRECT = 0x1,     // this output directly connects a track
                                        // to one output stream: no software mixer
    AUDIO_OUTPUT_FLAG_PRIMARY = 0x2,    // this output is the primary output of
                                        // the device. It is unique and must be
                                        // present. It is opened by default and
                                        // receives routing, audio mode and volume
                                        // controls related to voice calls.
    AUDIO_OUTPUT_FLAG_FAST = 0x4,       // output supports "fast tracks",
                                        // defined elsewhere
    AUDIO_OUTPUT_FLAG_DEEP_BUFFER = 0x8, // use deep audio buffers
    AUDIO_OUTPUT_FLAG_COMPRESS_OFFLOAD = 0x10,  // offload playback of compressed
                                                // streams to hardware codec
    AUDIO_OUTPUT_FLAG_NON_BLOCKING = 0x20, // use non-blocking write
    AUDIO_OUTPUT_FLAG_HW_AV_SYNC = 0x40,   // output uses a hardware A/V synchronization source
    AUDIO_OUTPUT_FLAG_TTS = 0x80,          // output for streams transmitted through speaker
                                           // at a sample rate high enough to accommodate
                                           // lower-range ultrasonic playback
    AUDIO_OUTPUT_FLAG_RAW = 0x100,         // minimize signal processing
    AUDIO_OUTPUT_FLAG_SYNC = 0x200,        // synchronize I/O streams

    AUDIO_OUTPUT_FLAG_IEC958_NONAUDIO = 0x400, // Audio stream contains compressed audio in
                                               // SPDIF data bursts, not PCM.
} audio_output_flags_t;


/* Audio format  is a 32-bit word that consists of:
 *   main format field (upper 8 bits)
 *   sub format field (lower 24 bits).
 *
 * The main format indicates the main codec type. The sub format field
 * indicates options and parameters for each format. The sub format is mainly
 * used for record to indicate for instance the requested bitrate or profile.
 * It can also be used for certain formats to give informations not present in
 * the encoded audio stream (e.g. octet alignement for AMR).
 */
typedef enum {
    AUDIO_FORMAT_INVALID             = 0xFFFFFFFFUL,
    AUDIO_FORMAT_DEFAULT             = 0,
    AUDIO_FORMAT_PCM                 = 0x00000000UL, /* DO NOT CHANGE */
    AUDIO_FORMAT_MP3                 = 0x01000000UL,
    AUDIO_FORMAT_AMR_NB              = 0x02000000UL,
    AUDIO_FORMAT_AMR_WB              = 0x03000000UL,
    AUDIO_FORMAT_AAC                 = 0x04000000UL,
    AUDIO_FORMAT_HE_AAC_V1           = 0x05000000UL, /* Deprecated, Use AUDIO_FORMAT_AAC_HE_V1*/
    AUDIO_FORMAT_HE_AAC_V2           = 0x06000000UL, /* Deprecated, Use AUDIO_FORMAT_AAC_HE_V2*/
    AUDIO_FORMAT_VORBIS              = 0x07000000UL,
    AUDIO_FORMAT_OPUS                = 0x08000000UL,
    AUDIO_FORMAT_AC3                 = 0x09000000UL,
    AUDIO_FORMAT_E_AC3               = 0x0A000000UL,
    AUDIO_FORMAT_DTS                 = 0x0B000000UL,
    AUDIO_FORMAT_DTS_HD              = 0x0C000000UL,
    // IEC61937 is encoded audio wrapped in 16-bit PCM.
    AUDIO_FORMAT_IEC61937            = 0x0D000000UL,
    AUDIO_FORMAT_DOLBY_TRUEHD        = 0x0E000000UL,
    AUDIO_FORMAT_MAIN_MASK           = 0xFF000000UL, /* Deprecated. Use audio_get_main_format() */
    AUDIO_FORMAT_SUB_MASK            = 0x00FFFFFFUL,

    /* Aliases */
    /* note != AudioFormat.ENCODING_PCM_16BIT */
    AUDIO_FORMAT_PCM_16_BIT          = (AUDIO_FORMAT_PCM),
    /* note != AudioFormat.ENCODING_PCM_8BIT */
    AUDIO_FORMAT_PCM_8_BIT           = (AUDIO_FORMAT_PCM),
    AUDIO_FORMAT_PCM_32_BIT          = (AUDIO_FORMAT_PCM),
    AUDIO_FORMAT_PCM_8_24_BIT        = (AUDIO_FORMAT_PCM),
    AUDIO_FORMAT_PCM_FLOAT           = (AUDIO_FORMAT_PCM),
    AUDIO_FORMAT_PCM_24_BIT_PACKED   = (AUDIO_FORMAT_PCM ),
    AUDIO_FORMAT_AAC_MAIN            = (AUDIO_FORMAT_AAC),
    AUDIO_FORMAT_AAC_LC              = (AUDIO_FORMAT_AAC),
    AUDIO_FORMAT_AAC_SSR             = (AUDIO_FORMAT_AAC),
    AUDIO_FORMAT_AAC_LTP             = (AUDIO_FORMAT_AAC),
    AUDIO_FORMAT_AAC_HE_V1           = (AUDIO_FORMAT_AAC),
    AUDIO_FORMAT_AAC_SCALABLE        = (AUDIO_FORMAT_AAC),
    AUDIO_FORMAT_AAC_ERLC            = (AUDIO_FORMAT_AAC),
    AUDIO_FORMAT_AAC_LD              = (AUDIO_FORMAT_AAC),
    AUDIO_FORMAT_AAC_HE_V2           = (AUDIO_FORMAT_AAC),
    AUDIO_FORMAT_AAC_ELD             = (AUDIO_FORMAT_AAC),
} audio_format_t;

/* A channel mask per se only defines the presence or absence of a channel, not the order.
 * But see AUDIO_INTERLEAVE_* below for the platform convention of order.
 *
 * audio_channel_mask_t is an opaque type and its internal layout should not
 * be assumed as it may change in the future.
 * Instead, always use the functions declared in this header to examine.
 *
 * These are the current representations:
 *
 *   AUDIO_CHANNEL_REPRESENTATION_POSITION
 *     is a channel mask representation for position assignment.
 *     Each low-order bit corresponds to the spatial position of a transducer (output),
 *     or interpretation of channel (input).
 *     The user of a channel mask needs to know the context of whether it is for output or input.
 *     The constants AUDIO_CHANNEL_OUT_* or AUDIO_CHANNEL_IN_* apply to the bits portion.
 *     It is not permitted for no bits to be set.
 *
 *   AUDIO_CHANNEL_REPRESENTATION_INDEX
 *     is a channel mask representation for index assignment.
 *     Each low-order bit corresponds to a selected channel.
 *     There is no platform interpretation of the various bits.
 *     There is no concept of output or input.
 *     It is not permitted for no bits to be set.
 *
 * All other representations are reserved for future use.
 *
 * Warning: current representation distinguishes between input and output, but this will not the be
 * case in future revisions of the platform. Wherever there is an ambiguity between input and output
 * that is currently resolved by checking the channel mask, the implementer should look for ways to
 * fix it with additional information outside of the mask.
 */
typedef uint32_t audio_channel_mask_t;


/* Additional information about compressed streams offloaded to
 * hardware playback
 * The version and size fields must be initialized by the caller by using
 * one of the constants defined here.
 */
typedef struct {
    uint16_t version;                   // version of the info structure
    uint16_t size;                      // total size of the structure including version and size
    uint32_t sample_rate;               // sample rate in Hz
    audio_channel_mask_t channel_mask;  // channel mask
    uint32_t bit_rate;                  // bit rate in bits per second
    int64_t duration_us;                // duration in microseconds, -1 if unknown
    bool has_video;                     // true if stream is tied to a video stream
    bool is_streaming;                  // true if streaming, false if local playback
} audio_offload_info_t;


/* common audio stream configuration parameters
 * You should memset() the entire structure to zero before use to
 * ensure forward compatibility
 */
struct audio_config {
    uint32_t sample_rate;
    audio_channel_mask_t channel_mask;
    audio_format_t  format;
    audio_offload_info_t offload_info;
    size_t frame_count;
};

/**
 * Extract the primary format, eg. PCM, AC3, etc.
 */
static inline audio_format_t audio_get_main_format(audio_format_t format)
{
    return (audio_format_t)(format & AUDIO_FORMAT_MAIN_MASK);
}

/**
 * Is the data plain PCM samples that can be scaled and mixed?
 */
static inline bool audio_is_linear_pcm(audio_format_t format)
{
    return (audio_get_main_format(format) == AUDIO_FORMAT_PCM);
}

#endif //TINYHAL_AUDIO_DEFS_H
