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

#define LOG_TAG "tiny_hal_config"

/* For asprintf */
#define _GNU_SOURCE

#include <limits.h>
#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <stdbool.h>
#include <stddef.h>
#include <string.h>
#include <errno.h>
#include <unistd.h>
#include <pthread.h>
#include <assert.h>
#include <ctype.h>
#include <dirent.h>
#include <linux/limits.h>
#ifdef ANDROID
#include <cutils/log.h>
#include <cutils/compiler.h>
#include <system/audio.h>
#else
#include "audio_logging.h"
#endif

/* Workaround for linker error if audio_effect.h is included in multiple
 * source files. Prevent audio.h including it */
#define ANDROID_AUDIO_EFFECT_H
struct effect_interface_s;
typedef struct effect_interface_s **effect_handle_t;
#ifdef ANDROID
#include <hardware/audio.h>
#endif

#ifdef ENABLE_COVERAGE
#include <coverage.h>
#endif

#include <tinyalsa/asoundlib.h>
#include <expat.h>

#include "../include/tinyhal/audio_config.h"

#define MIXER_CARD_DEFAULT 0

/* The dynamic arrays are extended in multiples of this number of objects */
#define DYN_ARRAY_GRANULE 16

#define INVALID_CTL_INDEX 0xFFFFFFFFUL

#ifdef ANDROID
#ifndef ETC_PATH
#define ETC_PATH "/system/etc"
#endif
#endif

struct config_mgr;
struct stream;
struct path;
struct device;
struct usecase;
struct scase;
struct codec_probe;
struct codec_case;
struct constant;

/* Dynamically extended array of fixed-size objects */
struct dyn_array {
    uint                count;
    uint16_t            max_count; /* current maximum size of allocated array */
    uint16_t            elem_size;  /* size of array elements */
    union {
        void               *data;
        struct device      *devices;
        struct stream      *streams;
        struct path        *paths;
        struct usecase     *usecases;
        struct scase       *cases;
        struct ctl         *ctls;
        struct codec_case  *codec_cases;
        struct constant    *constants;
        const char         **path_names;
    };
};

/* Paths for "on" and "off" are a special case and have fixed ids */
enum {
    e_path_id_off = 0,
    e_path_id_on = 1,
    e_path_id_custom_base = 2
};

/* Old versions of tinalsa don't have mixer_ctl_get_id() */
struct ctl_ref {
#ifdef TINYALSA_NO_CTL_GET_ID
    struct mixer_ctl    *ctl;
#else
    uint                id;
#endif
};

struct ctl {
    struct ctl_ref      ref;
    const char          *name;
    uint32_t            index;
    uint32_t            array_count;
    enum mixer_ctl_type type;
    uint8_t             *buffer;
    const char          *data_file_name;

    /* If the control couldn't be opened during boot the value will hold
     * a pointer to the original value string from the config file and will
     * be converted later into the appropriate type
     */
    union {
        int             integer;
        const uint8_t   *data;
        const char      *string;
    } value;
};

struct constant {
    const char *name;
    const char *value;
};

struct path {
    int                 id;         /* Integer identifier of this path */
    struct dyn_array    ctl_array;
};

struct codec_case {
    const char *codec_name;
    const char *file;
};

struct codec_probe {
    const char *file;
    const char *new_xml_file;
    struct dyn_array codec_case_array;
};

struct device {
    uint32_t    type;               /* 0 is reserved for the global device */
    int         use_count;          /* counts total streams using this device */
    struct dyn_array path_array;
};

struct scase {
    const char          *name;
    struct dyn_array    ctl_array;
};

struct usecase {
    const char          *name;
    struct dyn_array    case_array;
};

struct stream_control {
    struct ctl_ref      ref;
    uint                index;
    int                 min;
    int                 max;
};

struct stream {
    struct hw_stream  info;   /* must be first member */

    struct config_mgr*  cm;
    const char* name;

    int     ref_count;
    int     max_ref_count;

    int     enable_path;    /* id of paths to invoke when enabled */
    int     disable_path;   /* id of paths to invoke when disabled */

    uint32_t current_devices;   /* devices currently active for this stream */

    struct {
        struct stream_control volume_left;
        struct stream_control volume_right;
    } controls;

    struct dyn_array    usecase_array;
    struct dyn_array    constants_array;
};

struct config_mgr {
    pthread_mutex_t lock;

    struct mixer    *mixer;

    uint32_t        supported_output_devices;
    uint32_t        supported_input_devices;

    struct dyn_array device_array;
    struct dyn_array anon_stream_array;
    struct dyn_array named_stream_array;
};

/*********************************************************************
 * Structures and enums for XML parser
 *********************************************************************/

#define MAX_PARSE_DEPTH     6

/* For faster parsing put more commonly-used elements first */
enum element_index {
    e_elem_ctl = 0,
    e_elem_path,
    e_elem_device,
    e_elem_stream,
    e_elem_enable,
    e_elem_disable,
    e_elem_case,
    e_elem_usecase,
    e_elem_set,
    e_elem_stream_ctl,
    e_elem_init,
    e_elem_pre_init,
    e_elem_mixer,
    e_elem_audiohal,
    e_elem_codec_probe,
    e_elem_codec_case,

    e_elem_count
};

/* For faster parsing put more commonly-used attribs first */
enum attrib_index {
    e_attrib_name = 0,
    e_attrib_val,
    e_attrib_path,
    e_attrib_function,
    e_attrib_type,
    e_attrib_index,
    e_attrib_dir,
    e_attrib_card,
    e_attrib_device,
    e_attrib_instances,
    e_attrib_rate,
    e_attrib_period_size,
    e_attrib_period_count,
    e_attrib_min,
    e_attrib_max,
    e_attrib_file,

    e_attrib_count
};

#define BIT(x)     (1<<(x))

struct parse_state;
typedef int(*elem_fn)(struct parse_state *state);

struct parse_element {
    const char      *name;
    uint16_t        valid_attribs;  /* bitflags of valid attribs for this element */
    uint16_t        required_attribs;   /* bitflags of attribs that must be present */
    uint16_t        valid_subelem;  /* bitflags of valid sub-elements */
    elem_fn         start_fn;
    elem_fn         end_fn;
};

struct parse_attrib {
    const char      *name;
};

struct parse_device {
    const char      *name;
    uint32_t        device;
};

struct parse_stack_entry {
    uint16_t            elem_index;
    uint16_t            valid_subelem;
};

/* Temporary state info for config file parser */
struct parse_state {
    struct config_mgr   *cm;
    FILE                *file;
    const char          *cur_xml_file;     /* To store the current xml file name*/
    XML_Parser          parser;
    char                read_buf[256];
    int                 parse_error; /* value >0 aborts without error */
    int                 error_line;
    unsigned int        mixer_card_number;

    struct {
        const char      *value[e_attrib_count];
        const XML_Char  **all;
    } attribs;

    /* Current parent object. We don't need a stack because
     * an object cannot be nested below an object of the same type
     * so there can only ever be zero or one object of a given type
     * active at any time
     */
    struct {
        struct device   *device;
        struct stream   *stream;
        struct path     *path;
        struct usecase  *usecase;
        struct scase    *scase;
        struct codec_probe *codec_probe;
    } current;

    /* This array hold a de-duplicated list of all path names encountered */
    struct dyn_array    path_name_array;

    /* These are temporary path objects used to collect the initial
     * mixer setup control settings under <pre_init> and <init>
     */
    struct path         preinit_path;
    struct path         init_path;

    struct codec_probe  init_probe;

    struct {
        int             index;
        struct parse_stack_entry entry[MAX_PARSE_DEPTH];
    } stack;
};


static int string_to_uint(uint32_t *result, const char *str);
static int string_to_int(int *result, const char *str);
static int get_value_from_file(struct ctl *c, uint32_t vnum);
static int make_byte_work_buffer(struct ctl *pctl, uint32_t buffer_size);
static int make_byte_array(struct ctl *c, uint32_t vnum);
static const char *debug_device_to_name(uint32_t device);
static void free_ctl_array(struct dyn_array *ctl_array);

/*
 * Utility function to join a filename to a base path. This doesn't bother to
 * strip . and .. components.
 */
static char *join_paths(const char *base, const char *file, int strip_leaf)
{
    size_t base_len;
    char *p;

    if (!base) {
        base_len = 0;
    } else if (strip_leaf) {
        /* Strip trailing filename part of base */
        p = strrchr(base, '/');
        if (!p) {
            /* No path component */
            base_len = 0;
        } else {
            /* Take the part up to but not including the / */
            base_len = p - base;
        }
    } else {
        /* Normalize to not include a trailing / */
        base_len = strlen(base);
        if (base[base_len - 1] == '/') {
            --base_len;
        }
    }

    if (asprintf(&p, "%.*s/%s", (int)base_len, base, file) < 0) {
        return NULL;
    }

    return p;
}

/*********************************************************************
 * Routing control
 *********************************************************************/

static inline void ctl_ref_init(struct ctl_ref *pctl_ref)
{
#ifdef TINYALSA_NO_CTL_GET_ID
    pctl_ref->ctl = NULL;
#else
    pctl_ref->id = UINT_MAX;
#endif
}

static inline bool ctl_ref_valid(struct ctl_ref *pctl_ref)
{
#ifdef TINYALSA_NO_CTL_GET_ID
    return pctl_ref->ctl != NULL;
#else
    return pctl_ref->id != UINT_MAX;
#endif
}

static inline void ctl_set_ref(struct ctl_ref *pctl_ref,
                               struct mixer_ctl *ctl)
{
#ifdef TINYALSA_NO_CTL_GET_ID
    pctl_ref->ctl = ctl;
#else
    pctl_ref->id = mixer_ctl_get_id(ctl);
#endif
}

static inline struct mixer_ctl *ctl_get_ptr(const struct config_mgr *cm,
                                            const struct ctl_ref *pctl_ref)
{
#ifdef TINYALSA_NO_CTL_GET_ID
    (void)cm;
    return pctl_ref->ctl;
#else
    return mixer_get_ctl(cm->mixer, pctl_ref->id);
#endif
}

static int ctl_open(struct config_mgr *cm, struct ctl *pctl)
{
    enum mixer_ctl_type ctl_type;
    const char *val_str = pctl->value.string;
    struct mixer_ctl *ctl;
    int ret;

    if (ctl_ref_valid(&pctl->ref)) {
        /* control already populated on boot */
        return 0;
    }

   /* Control wasn't found on boot, try to get it now */

    ctl = mixer_get_ctl_by_name(cm->mixer, pctl->name);
#if !defined(TINYALSA_NO_ADD_NEW_CTRLS) || !defined(TINYALSA_NO_CTL_GET_ID)
    if (!ctl) {
        /* Update tinyalsa with any new controls that have been added
         * and try again. NOTE: only safe if mixer_ctl_get_id() supported
         * because the pointers are likely to change as the list is updated.
         */
        mixer_add_new_ctls(cm->mixer);
        ctl = mixer_get_ctl_by_name(cm->mixer, pctl->name);
    }
#endif

    if (!ctl) {
        ALOGW("Control '%s' not found", pctl->name);
        return -ENOENT;
    }

    ctl_type = mixer_ctl_get_type(ctl);
    switch(ctl_type) {
        case MIXER_CTL_TYPE_BYTE:
            if (pctl->index == INVALID_CTL_INDEX) {
                pctl->index = 0;
            }
            const unsigned int vnum = mixer_ctl_get_num_values(ctl);
            ret = make_byte_work_buffer(pctl, vnum);
            if (ret != 0) {
                return ret;
            }
            break;

        case MIXER_CTL_TYPE_BOOL:
        case MIXER_CTL_TYPE_INT:
            if (string_to_int(&pctl->value.integer, val_str) == -EINVAL) {
                return -EINVAL;
            }

            free((void*)val_str);  /* no need to keep this string now */

            /* This log statement is just to aid to debugging */
            ALOGE_IF((ctl_type == MIXER_CTL_TYPE_BOOL)
                     && ((unsigned int)pctl->value.integer > 1),
                     "WARNING: Illegal value for bool control");
            ALOGV("Added ctl '%s' value 0x%x", pctl->name, pctl->value.integer);
            break;

        case MIXER_CTL_TYPE_ENUM:
            ALOGV("Added ctl '%s' value '%s'", pctl->name, pctl->value.string);
            break;

        case MIXER_CTL_TYPE_IEC958:
        case MIXER_CTL_TYPE_INT64:
        case MIXER_CTL_TYPE_UNKNOWN:
        default:
            ALOGE("Mixer control '%s' has unsupported type", pctl->name);
            return -EINVAL;
    }

    pctl->type = ctl_type;
    ctl_set_ref(&pctl->ref, ctl);

    return 0;
}

static void apply_ctls_l(struct config_mgr *cm, struct ctl *pctl, const int ctl_count)
{
    struct mixer_ctl *ctl;
    int i;
    unsigned int vnum;
    unsigned int value_count;
    int err = 0;

    ALOGV("+apply_ctls_l");

    for (i = 0; i < ctl_count; ++i, ++pctl) {
        if (ctl_open(cm, pctl) != 0) {
            break;
        }

        ctl = ctl_get_ptr(cm, &pctl->ref);

        switch (mixer_ctl_get_type(ctl)) {
            case MIXER_CTL_TYPE_BOOL:
            case MIXER_CTL_TYPE_INT:
                value_count = mixer_ctl_get_num_values(ctl);

                ALOGV("apply ctl '%s' = 0x%x (%d values)",
                                        mixer_ctl_get_name(ctl),
                                        pctl->value.integer,
                                        value_count);

                if (pctl->index == INVALID_CTL_INDEX) {
                    for (vnum = 0; vnum < value_count; ++vnum) {
                        err = mixer_ctl_set_value(ctl, vnum, pctl->value.integer);
                        if (err < 0) {
                            break;
                        }
                    }
                } else {
                    err = mixer_ctl_set_value(ctl, pctl->index, pctl->value.integer);
                }
                ALOGE_IF(err < 0, "Failed to set ctl '%s' to 0x%x",
                                        mixer_ctl_get_name(ctl),
                                        pctl->value.integer);
                break;

            case MIXER_CTL_TYPE_BYTE:
                /* byte array */
                vnum = mixer_ctl_get_num_values(ctl);

                ALOGV("apply ctl '%s' = byte data (%d bytes)",
                                        mixer_ctl_get_name(ctl),
                                        vnum);

                if ((pctl->index == 0) && (pctl->array_count == vnum)) {
                    err = mixer_ctl_set_array(ctl, pctl->value.data, pctl->array_count);
                } else {
                    /* read-modify-write */
                    err = mixer_ctl_get_array(ctl, pctl->buffer, vnum);
                    if (err >= 0) {
                        memcpy(&pctl->buffer[pctl->index], pctl->value.data, pctl->array_count);
                        err = mixer_ctl_set_array(ctl, pctl->buffer, vnum);
                    }
                }

                ALOGE_IF(err < 0, "Failed to set ctl '%s'",
                                            mixer_ctl_get_name(ctl));
                break;

            case MIXER_CTL_TYPE_ENUM:
                ALOGV("apply ctl '%s' to '%s'",
                                            mixer_ctl_get_name(ctl),
                                            pctl->value.string);

                err = mixer_ctl_set_enum_by_string(ctl, pctl->value.string);

                ALOGE_IF(err < 0, "Failed to set ctl '%s' to '%s'",
                                            mixer_ctl_get_name(ctl),
                                            pctl->value.string);
                break;

            default:
                break;
        }
    }

    ALOGV("-apply_ctls_l");
}

static void apply_path_l(struct config_mgr *cm, struct path *path)
{
    ALOGV("+apply_path_l(%p) id=%u", path, path->id);

    apply_ctls_l(cm, path->ctl_array.ctls, path->ctl_array.count);

    ALOGV("-apply_path_l(%p)", path);
}

static void apply_device_path_l(struct config_mgr *cm, struct device *pdev,
                                    struct path *path)
{
    ALOGV("+apply_device_path_l(%p) id=%u", path, path->id);

    /* The on and off paths for a device are reference-counted */
    switch (path->id) {
    case e_path_id_off:
        if (--pdev->use_count > 0) {
            ALOGV("Device still in use - not applying 'off' path");
            return;
        }
        break;

     case e_path_id_on:
        if (++pdev->use_count > 1) {
            ALOGV("Device already enabled - not applying 'on' path");
            return;
        }
        break;

    default:
        break;
    }

    apply_path_l(cm, path);

    ALOGV("-apply_device_path_l(%p)", path);
}

static void apply_paths_by_id_l(struct config_mgr *cm, struct device *pdev,
                                int first_id, int second_id)
{
    struct path *ppath = pdev->path_array.paths;
    struct path *found_paths[2] = {0};
    int path_count = pdev->path_array.count;

    ALOGV("Applying paths [first=%u second=%u] to device(@%p, mask=0x%x '%s')",
                first_id, second_id, ppath, pdev->type, debug_device_to_name(pdev->type));

    /* To save time we find both paths in a single walk of the list */
    for (; path_count > 0; --path_count, ++ppath) {
        if (ppath->id == first_id) {
            found_paths[0] = ppath;
            if ((found_paths[1] != NULL) || (first_id == second_id)) {
                /* We have both paths or there is only one path to find */
                break;
            }
        } else if (ppath->id == second_id) {
            found_paths[1] = ppath;
            if (found_paths[0] != NULL) {
                break;
            }
        }
    }

    if (found_paths[0] != NULL) {
        apply_device_path_l(cm, pdev, found_paths[0]);
    }

    if (found_paths[1] != NULL) {
        apply_device_path_l(cm, pdev, found_paths[1]);
    }
}

static void apply_paths_to_devices_l(struct config_mgr *cm, uint32_t devices,
                                    int first_id, int second_id)
{
    struct device *pdev = cm->device_array.devices;
    int dev_count = cm->device_array.count;
    const uint32_t input_flag = devices & AUDIO_DEVICE_BIT_IN;

    /* invoke path path_id on all struct device matching devices */
    ALOGV("Apply paths [first=%u second=%u] to devices in 0x%x",
            first_id, second_id, devices);

    devices &= ~AUDIO_DEVICE_BIT_IN;

    while ((dev_count > 0) && (devices != 0)) {
        if (((pdev->type & input_flag) == input_flag)
                    && ((pdev->type & devices) != 0)) {
            devices &= ~pdev->type;
            apply_paths_by_id_l(cm, pdev, first_id, second_id);
        }

        --dev_count;
        ++pdev;
    }
}

static void apply_paths_to_global_l(struct config_mgr *cm,
                                    int first_id, int second_id)
{
    struct device *pdev = cm->device_array.devices;
    struct device * const pend = pdev + cm->device_array.count;

    ALOGV("Apply global paths [first=%u second=%u]", first_id, second_id);

    while (pdev < pend) {
        if (pdev->type == 0) {
            apply_paths_by_id_l(cm, pdev, first_id, second_id);
            break;
        }
        ++pdev;
    }
}

uint32_t get_current_routes( const struct hw_stream *stream )
{
    struct stream *s = (struct stream *)stream;
    ALOGV("get_current_routes(%p) 0x%x", stream, s->current_devices);
    return s->current_devices;
}

void apply_route( const struct hw_stream *stream, uint32_t devices )
{
    struct stream *s = (struct stream *)stream;
    struct config_mgr *cm = s->cm;

    ALOGV("apply_route(%p) devices=0x%x", stream, devices);

    if (devices != 0) {
        if (devices & AUDIO_DEVICE_BIT_IN) {
            if (!stream_is_input(stream)) {
                ALOGE("Attempting to set input routing %x on output stream %p",
                        devices, stream);
                return;
            }
            devices &= AUDIO_DEVICE_IN_ALL;
            devices |= AUDIO_DEVICE_BIT_IN;
        } else {
            if (stream_is_input(stream)) {
                ALOGE("Attempting to set output routing %x on input stream %p",
                        devices, stream);
                return;
            }
            devices &= AUDIO_DEVICE_OUT_ALL;
        }
    }

    pthread_mutex_lock(&cm->lock);

    /*
     * Only apply routes to devices that have changed state on this stream.
     * The input bit will be stripped as unchanged so restore it after.
     */
    uint32_t enabling = devices & ~s->current_devices;
    uint32_t disabling = ~devices & s->current_devices;
    enabling |= devices & AUDIO_DEVICE_BIT_IN;
    disabling |= devices & AUDIO_DEVICE_BIT_IN;

    apply_paths_to_devices_l(cm, disabling, s->disable_path, e_path_id_off);
    apply_paths_to_devices_l(cm, enabling, e_path_id_on, s->enable_path);

    /* Save new set of devices for this stream */
    s->current_devices = devices;

    pthread_mutex_unlock(&cm->lock);
}

/*********************************************************************
 * Stream control
 *********************************************************************/

static int set_vol_ctl(struct stream *stream,
                       const struct stream_control *volctl,
                       int percent)
{
    struct mixer_ctl *ctl = ctl_get_ptr(stream->cm, &volctl->ref);
    int val;
    long long lmin;
    long long lmax;
    long long lval;

    switch (percent) {
    case 0:
        val = volctl->min;
        break;

    case 100:
        val = volctl->max;
        break;

    default:
        lmin = volctl->min;
        lmax = volctl->max;
        lval = lmin + (((lmax - lmin) * percent) / 100LL);
        val = (int)lval;
        break;
    }

    mixer_ctl_set_value(ctl, volctl->index, val);
    return 0;
}

int set_hw_volume( const struct hw_stream *stream, int left_pc, int right_pc)
{
    struct stream *s = (struct stream *)stream;
    int ret = -ENOSYS;

    if ((left_pc < 0) || (left_pc > 100)) {
        ALOGE("Volume percent %d is out of range 0..100", left_pc);
        return -EINVAL;
    }

    if ((right_pc < 0) || (right_pc > 100)) {
        ALOGE("Volume percent %d is out of range 0..100", right_pc);
        return -EINVAL;
    }

    if (ctl_ref_valid(&s->controls.volume_left.ref)) {
        if (!ctl_ref_valid(&s->controls.volume_right.ref)) {
            /* Control is mono so average left and right */
            left_pc = (left_pc + right_pc) / 2;
        }

        ret = set_vol_ctl(s, &s->controls.volume_left, left_pc);
    }

    if (ctl_ref_valid(&s->controls.volume_right.ref)) {
        ret = set_vol_ctl(s, &s->controls.volume_right, right_pc);
    }

    ALOGV_IF(ret == 0, "set_hw_volume: L=%d%% R=%d%%", left_pc, right_pc);

    return ret;
}

static struct stream *find_named_stream(struct config_mgr *cm,
                                   const char *name)
{
    struct stream *s = cm->named_stream_array.streams;
    int i;

    for (i = cm->named_stream_array.count - 1; i >= 0; --i) {
        if (s->name) {
            if (strcmp(s->name, name) == 0) {
                return s;
            }
        }
        s++;
    }
    return NULL;
}

static bool open_stream_l(struct config_mgr *cm, struct stream *s)
{
    if (s->ref_count < s->max_ref_count) {
        ++s->ref_count;
        if (s->ref_count == 1) {
            apply_paths_to_global_l(cm, e_path_id_on, s->enable_path);
        }
        return true;
    } else {
        ALOGV("stream at maximum refcount %d", s->ref_count);
        return false;
    }
}

const struct hw_stream *get_stream(struct config_mgr *cm,
                                   const audio_devices_t devices,
                                   const audio_output_flags_t flags,
                                   const struct audio_config *config )
{
    int i;
    struct stream *s = cm->anon_stream_array.streams;
    const bool pcm = audio_is_linear_pcm(config->format);
    enum stream_type type;

    ALOGV("+get_stream devices=0x%x flags=0x%x format=0x%x",
                            devices, flags, config->format );

    if (devices & AUDIO_DEVICE_BIT_IN) {
        type = pcm ? e_stream_in_pcm : e_stream_in_compress;
    } else {
        type = pcm ? e_stream_out_pcm : e_stream_out_compress;
    }

    pthread_mutex_lock(&cm->lock);
    for (i = cm->anon_stream_array.count - 1; i >= 0; --i) {
        ALOGV("get_stream: require type=%d; try type=%d refcount=%d refmax=%d",
                    type, s[i].info.type, s[i].ref_count, s[i].max_ref_count );
        if (s[i].info.type == type) {
            if (open_stream_l(cm, &s[i])) {
                break;
            }
        }
    }
    pthread_mutex_unlock(&cm->lock);

    if (i >= 0) {
        // apply initial routing
        apply_route(&s[i].info, devices);

        ALOGV("-get_stream =%p (refcount=%d)", &s[i].info,
                                                s[i].ref_count );
        return &s[i].info;
    } else {
        ALOGE("-get_stream no suitable stream" );
        return NULL;
    }
}

const struct hw_stream *get_named_stream(struct config_mgr *cm,
                                   const char *name)
{
    struct stream *s;

    ALOGV("+get_named_stream '%s'", name);

    /* Streams can't be deleted so don't need to hold the lock during search */
    s = find_named_stream(cm, name);

    pthread_mutex_lock(&cm->lock);
    if (s != NULL) {
        if (!open_stream_l(cm, s)) {
            s = NULL;
        }
    }
    pthread_mutex_unlock(&cm->lock);

    if (s != NULL) {
        ALOGV("-get_named_stream =%p (refcount=%d)", &s->info, s->ref_count );
        return &s->info;
    } else {
        ALOGE("-get_named_stream no suitable stream" );
        return NULL;
    }
}

bool is_named_stream_defined(struct config_mgr *cm, const char *name)
{
    struct stream *s;

    /* Streams can't be deleted so don't need to hold the lock during search */
    s = find_named_stream(cm, name);

    ALOGV("is_named_stream_defined '%s' = %d", name, (s != NULL));
    return (s != NULL);
}

void release_stream( const struct hw_stream* stream )
{
    struct stream *s = (struct stream *)stream;

    ALOGV("release_stream %p", stream );

    if (s) {
        pthread_mutex_lock(&s->cm->lock);
        if (--s->ref_count == 0) {
            /* Ensure all paths it was using are disabled */
            apply_paths_to_devices_l(s->cm, s->current_devices,
                                    e_path_id_off, s->disable_path);
            apply_paths_to_global_l(s->cm, s->disable_path, e_path_id_off);
            s->current_devices = 0;
        }
        pthread_mutex_unlock(&s->cm->lock);
    }
}

uint32_t get_supported_output_devices( struct config_mgr *cm )
{
    const uint32_t d = cm->supported_output_devices;

    ALOGV("get_supported_output_devices=0x%x", d);
    return d;
}

uint32_t get_supported_input_devices( struct config_mgr *cm )
{
    const uint32_t d = cm->supported_input_devices;

    ALOGV("get_supported_input_devices=0x%x", d);
    return d;
}

/*********************************************************************
 * Use-case control
 *********************************************************************/
int apply_use_case( const struct hw_stream* stream,
                    const char *setting,
                    const char *case_name)
{
    struct stream *s = (struct stream *)stream;
    struct usecase *puc = s->usecase_array.usecases;
    int usecase_count = s->usecase_array.count;
    struct scase *pcase;
    int case_count;
    int ret;

    ALOGV("apply_use_case(%p) %s=%s", stream, setting, case_name);

    for (; usecase_count > 0; usecase_count--, puc++) {
        if (0 == strcmp(puc->name, setting)) {
            pcase = puc->case_array.cases;
            case_count = puc->case_array.count;
            for(; case_count > 0; case_count--, pcase++) {
                if (0 == strcmp(pcase->name, case_name)) {
                    pthread_mutex_lock(&s->cm->lock);
                    apply_ctls_l(s->cm, pcase->ctl_array.ctls, pcase->ctl_array.count);
                    pthread_mutex_unlock(&s->cm->lock);
                    ret = 0;
                    goto exit;
                }
            }
        }
    }

    ret = -ENOSYS;      /* use-case not implemented */

exit:
    return ret;
}

/*********************************************************************
 * Constants
 *********************************************************************/
int get_stream_constant_string(const struct hw_stream *stream,
                                const char *name, char const **value)
{
    struct stream *s = (struct stream *)stream;
    struct constant *pc = s->constants_array.constants;
    const int count = s->constants_array.count;
    int i;

    for (i = 0; i < count; ++i) {
        if (0 == strcmp(pc[i].name, name)) {
            *value = pc[i].value;
            return 0;
        }
    }
    return -ENOSYS;
}

int get_stream_constant_uint32(const struct hw_stream *stream,
                                const char *name, uint32_t *value)
{
    const char *string = NULL;
    uint32_t val = 0;
    int ret = get_stream_constant_string(stream, name, &string);

    if (!ret) {
        ret = string_to_uint(&val, string);
        if (!ret) {
            *value = val;
        }
    }

    return ret;
}

int get_stream_constant_int32(const struct hw_stream *stream,
                              const char *name, int32_t *value)
{
    const char *string = NULL;
    int val = 0;
    int ret = get_stream_constant_string(stream, name, &string);

    if (!ret) {
        ret = string_to_int(&val, string);
        if (ret != 0) {
            return ret;
        }
        /* pick up out-of-range on 64-bit machines */
        if ((sizeof(int) > sizeof(int32_t)) &&
            ((val > 0x7FFFFFFF) || (-val > 0x7FFFFFFF))) {
            return -EINVAL;
        }
        *value = val;
    }

    return ret;
}

/*********************************************************************
 * Config file parsing
 *
 * To keep this simple we restrict the order that config file entries
 * may appear:
 * - The <mixer> section must always appear first
 * - Paths must be defined before they can be referred to
 *********************************************************************/
static int parse_mixer_start(struct parse_state *state);
static int parse_mixer_end(struct parse_state *state);
static int parse_device_start(struct parse_state *state);
static int parse_device_end(struct parse_state *state);
static int parse_stream_start(struct parse_state *state);
static int parse_stream_end(struct parse_state *state);
static int parse_stream_ctl_start(struct parse_state *state);
static int parse_path_start(struct parse_state *state);
static int parse_path_end(struct parse_state *state);
static int parse_case_start(struct parse_state *state);
static int parse_case_end(struct parse_state *state);
static int parse_usecase_start(struct parse_state *state);
static int parse_usecase_end(struct parse_state *state);
static int parse_enable_start(struct parse_state *state);
static int parse_disable_start(struct parse_state *state);
static int parse_ctl_start(struct parse_state *state);
static int parse_preinit_start(struct parse_state *state);
static int parse_preinit_end(struct parse_state *state);
static int parse_init_start(struct parse_state *state);
static int parse_init_end(struct parse_state *state);
static int parse_codec_probe_start(struct parse_state *state);
static int parse_codec_probe_end(struct parse_state *state);
static int parse_codec_case_start(struct parse_state *state);
static int parse_set_start(struct parse_state *state);

static const struct parse_element elem_table[e_elem_count] = {
    [e_elem_ctl] =    {
        .name = "ctl",
        .valid_attribs = BIT(e_attrib_name) | BIT(e_attrib_val)
                            | BIT(e_attrib_index) | BIT(e_attrib_file),
        .required_attribs = BIT(e_attrib_name),
        .valid_subelem = 0,
        .start_fn = parse_ctl_start,
        .end_fn = NULL
        },

    [e_elem_path] =    {
        .name = "path",
        .valid_attribs = BIT(e_attrib_name),
        .required_attribs = BIT(e_attrib_name),
        .valid_subelem = BIT(e_elem_ctl),
        .start_fn = parse_path_start,
        .end_fn = parse_path_end
        },

    [e_elem_device] =    {
        .name = "device",
        .valid_attribs = BIT(e_attrib_name),
        .required_attribs = BIT(e_attrib_name),
        .valid_subelem = BIT(e_elem_path),
        .start_fn = parse_device_start,
        .end_fn = parse_device_end
        },

    [e_elem_stream] =    {
        .name = "stream",
        .valid_attribs = BIT(e_attrib_name) | BIT(e_attrib_type)
                            | BIT(e_attrib_dir) | BIT(e_attrib_card)
                            | BIT(e_attrib_device) | BIT(e_attrib_instances)
                            | BIT(e_attrib_rate) | BIT(e_attrib_period_size)
                            | BIT(e_attrib_period_count),
        .required_attribs = BIT(e_attrib_type),
        .valid_subelem = BIT(e_elem_stream_ctl)
                            | BIT(e_elem_enable) | BIT(e_elem_disable)
                            | BIT(e_elem_usecase) | BIT(e_elem_set),
        .start_fn = parse_stream_start,
        .end_fn = parse_stream_end
        },

    [e_elem_enable] =    {
        .name = "enable",
        .valid_attribs = BIT(e_attrib_path),
        .required_attribs = BIT(e_attrib_path),
        .valid_subelem = 0,
        .start_fn = parse_enable_start,
        .end_fn = NULL
        },

    [e_elem_disable] =    {
        .name = "disable",
        .valid_attribs = BIT(e_attrib_path),
        .required_attribs = BIT(e_attrib_path),
        .valid_subelem = 0,
        .start_fn = parse_disable_start,
        .end_fn = NULL
        },

    [e_elem_case] =    {
        .name = "case",
        .valid_attribs = BIT(e_attrib_name),
        .required_attribs = BIT(e_attrib_name),
        .valid_subelem = BIT(e_elem_ctl),
        .start_fn = parse_case_start,
        .end_fn = parse_case_end
        },

    [e_elem_usecase] =    {
        .name = "usecase",
        .valid_attribs = BIT(e_attrib_name),
        .required_attribs = BIT(e_attrib_name),
        .valid_subelem = BIT(e_elem_case),
        .start_fn = parse_usecase_start,
        .end_fn = parse_usecase_end
        },

    [e_elem_set] =    {
        .name = "set",
        .valid_attribs = BIT(e_attrib_name) | BIT(e_attrib_val),
        .required_attribs = BIT(e_attrib_name) | BIT(e_attrib_val),
        .valid_subelem = 0,
        .start_fn = parse_set_start
        },

    [e_elem_stream_ctl] =    {
        .name = "ctl",
        .valid_attribs = BIT(e_attrib_name) | BIT(e_attrib_function)
                            | BIT(e_attrib_index)
                            | BIT(e_attrib_min) | BIT(e_attrib_max),
        .required_attribs = BIT(e_attrib_name) | BIT(e_attrib_function),
        .valid_subelem = 0,
        .start_fn = parse_stream_ctl_start,
        .end_fn = NULL
        },

    [e_elem_init] =     {
        .name = "init",
        .valid_attribs = 0,
        .required_attribs = 0,
        .valid_subelem = BIT(e_elem_ctl),
        .start_fn = parse_init_start,
        .end_fn = parse_init_end
        },

    [e_elem_pre_init] =     {
        .name = "pre_init",
        .valid_attribs = 0,
        .required_attribs = 0,
        .valid_subelem = BIT(e_elem_ctl),
        .start_fn = parse_preinit_start,
        .end_fn = parse_preinit_end
        },

    [e_elem_mixer] =    {
        .name = "mixer",
        .valid_attribs = BIT(e_attrib_name) | BIT(e_attrib_card),
        .required_attribs = 0,
        .valid_subelem = BIT(e_elem_pre_init) | BIT(e_elem_init),
        .start_fn = parse_mixer_start,
        .end_fn = parse_mixer_end
        },

    [e_elem_audiohal] =    {
        .name = "audiohal",
        .valid_attribs = 0,
        .required_attribs = 0,
        .valid_subelem = BIT(e_elem_mixer) | BIT(e_elem_codec_probe),
        .start_fn = NULL,
        .end_fn = NULL
        },

    [e_elem_codec_probe] =    {
        .name = "codec_probe",
        .valid_attribs = BIT(e_attrib_file),
        .required_attribs = BIT(e_attrib_file),
        .valid_subelem = BIT(e_elem_codec_case),
        .start_fn = parse_codec_probe_start,
        .end_fn = parse_codec_probe_end
        },

    [e_elem_codec_case] =    {
        .name = "case",
        .valid_attribs = BIT(e_attrib_name) | BIT(e_attrib_file),
        .required_attribs = BIT(e_attrib_name) | BIT(e_attrib_file),
        .valid_subelem = 0,
        .start_fn = parse_codec_case_start,
        .end_fn = NULL
        }
};

static const struct parse_attrib attrib_table[e_attrib_count] = {
    [e_attrib_name] =       {"name"},
    [e_attrib_val] =        {"val"},
    [e_attrib_path] =       {"path"},
    [e_attrib_function] =   {"function"},
    [e_attrib_type] =       {"type"},
    [e_attrib_index] =      {"index"},
    [e_attrib_dir] =        {"dir"},
    [e_attrib_card] =       {"card"},
    [e_attrib_device] =     {"device"},
    [e_attrib_instances] =  {"instances"},
    [e_attrib_rate] =       {"rate"},
    [e_attrib_period_size] = {"period_size"},
    [e_attrib_period_count] = {"period_count"},
    [e_attrib_min] = {"min"},
    [e_attrib_max] = {"max"},
    [e_attrib_file] = {"file"}
 };

static const struct parse_device device_table[] = {
    {"global",      0}, /* special dummy device for global settings */
    {"speaker",     AUDIO_DEVICE_OUT_SPEAKER},
    {"earpiece",    AUDIO_DEVICE_OUT_EARPIECE},
    {"headset",     AUDIO_DEVICE_OUT_WIRED_HEADSET},
    {"headset_in",  AUDIO_DEVICE_IN_WIRED_HEADSET},
    {"headphone",   AUDIO_DEVICE_OUT_WIRED_HEADPHONE},
    {"sco",         AUDIO_DEVICE_OUT_ALL_SCO},
    {"sco_in",      AUDIO_DEVICE_IN_ALL_SCO},
    {"a2dp",        AUDIO_DEVICE_OUT_ALL_A2DP},
    {"usb",         AUDIO_DEVICE_OUT_ALL_USB},
    {"mic",         AUDIO_DEVICE_IN_BUILTIN_MIC},
    {"back mic",    AUDIO_DEVICE_IN_BACK_MIC},
    {"voice",       AUDIO_DEVICE_IN_VOICE_CALL},
    {"aux",         AUDIO_DEVICE_IN_AUX_DIGITAL}
};

static const char *predefined_path_name_table[] = {
    [e_path_id_off] = "off",
    [e_path_id_on] = "on"
};

static int dyn_array_extend(struct dyn_array *array)
{
    const uint elem_size = array->elem_size;
    const uint new_count = array->count + 1;
    uint max_count = array->max_count;
    uint old_size, new_size;
    void *p;
    uint8_t *pbyte;

    if (new_count > max_count) {
        if (max_count > 0xFFFF - DYN_ARRAY_GRANULE) {
            return -ENOMEM;
        }

        old_size = max_count * elem_size;
        max_count += DYN_ARRAY_GRANULE;
        new_size = max_count * elem_size;

        p = realloc(array->data, new_size);
        if (!p) {
            return -ENOMEM;
        }

        pbyte = p;
        memset(pbyte + old_size, 0, new_size - old_size);

        array->data = p;
        array->max_count = max_count;
    }

    array->count = new_count;
    return 0;
}

static void dyn_array_fix(struct dyn_array *array)
{
    /* Fixes the allocated memory to exactly the required length
     * This will always be a shrink, discarding granular allocations
     * that we don't need */
    const uint size = array->count * array->elem_size;
    void *p = realloc(array->data, size);

    if (p) {
        array->data = p;
        array->max_count = array->count;
    }
}

static void dyn_array_free(struct dyn_array *array)
{
    free(array->data);
}

static struct ctl* new_ctl(struct dyn_array *array, const char *name)
{
    struct ctl *c;

    if (dyn_array_extend(array) < 0) {
        return NULL;
    }

    c = &array->ctls[array->count - 1];
    ctl_ref_init(&c->ref);
    c->index = INVALID_CTL_INDEX;
    c->name = name;
    c->type = MIXER_CTL_TYPE_UNKNOWN;
    return c;
}

static struct codec_case* new_codec_case(struct dyn_array *array, const char *codec, const char *file)
{
    struct codec_case *cc;

    if (dyn_array_extend(array) < 0) {
        return NULL;
    }

    cc = &array->codec_cases[array->count - 1];
    cc->codec_name = codec;
    cc->file = file;

    return cc;
}

static struct path* new_path(struct dyn_array *array, int id)
{
    struct path *path;

    if (dyn_array_extend(array) < 0) {
        return NULL;
    }

    path = &array->paths[array->count - 1];
    path->ctl_array.elem_size = sizeof(struct ctl);
    path->id = id;
    return path;
}

static void compress_path(struct path *path)
{
    dyn_array_fix(&path->ctl_array);
}

static struct scase* new_case(struct dyn_array *array, const char *name)
{
    struct scase *sc;

    if (dyn_array_extend(array) < 0) {
        return NULL;
    }

    sc = &array->cases[array->count - 1];
    sc->ctl_array.elem_size = sizeof(struct ctl);
    sc->name = strdup(name);
    if (!sc->name) {
        return NULL;
    }

    return sc;
}

static void compress_probe(struct codec_probe *cp)
{
    dyn_array_fix(&cp->codec_case_array);
}

static void compress_case(struct scase *sc)
{
    dyn_array_fix(&sc->ctl_array);
}

static struct usecase* new_usecase(struct dyn_array *array, const char *name)
{
    struct usecase *puc;

    if (dyn_array_extend(array) < 0) {
        return NULL;
    }

    puc = &array->usecases[array->count - 1];
    puc->case_array.elem_size = sizeof(struct scase);
    puc->name = strdup(name);
    if (!puc->name) {
        return NULL;
    }

    return puc;
}

static void compress_usecase(struct usecase *puc)
{
    dyn_array_fix(&puc->case_array);
}

static struct constant* new_constant(struct dyn_array *array,
                                     const char *name, const char *val)
{
    struct constant *pc;

    if (dyn_array_extend(array) < 0) {
        return NULL;
    }

    pc = &array->constants[array->count - 1];
    pc->name = name;
    pc->value = val;
    return pc;
}

static struct device* new_device(struct dyn_array *array, uint32_t type)
{
    struct device *d;

    if (dyn_array_extend(array) < 0) {
        return NULL;
    }

    d = &array->devices[array->count - 1];
    d->path_array.elem_size = sizeof(struct path);
    d->type = type;
    return d;
}

static void compress_device(struct device *d)
{
    dyn_array_fix(&d->path_array);
}

static struct stream* new_stream(struct dyn_array *array, struct config_mgr *cm)
{
    struct stream *s;

    if (dyn_array_extend(array) < 0) {
        return NULL;
    }

    s = &array->streams[array->count - 1];
    s->usecase_array.elem_size = sizeof(struct usecase);
    s->constants_array.elem_size = sizeof(struct constant);
    s->cm = cm;
    s->enable_path = -1;    /* by default no special path to invoke */
    s->disable_path = -1;
    ctl_ref_init(&s->controls.volume_left.ref);
    ctl_ref_init(&s->controls.volume_right.ref);
    return s;
}

static void compress_stream(struct stream *s)
{
    dyn_array_fix(&s->usecase_array);
}

static int new_name(struct dyn_array *array, const char* name)
{
    int i;

    if (dyn_array_extend(array) < 0) {
        return -ENOMEM;
    }

    i = array->count - 1;
    array->path_names[i] = strdup(name);
    if (!array->path_names[i]) {
        return -ENOMEM;
    }

    return i;
}

static struct config_mgr* new_config_mgr()
{
    struct config_mgr* mgr = calloc(1, sizeof(struct config_mgr));
    if (!mgr) {
        return NULL;
    }
    mgr->device_array.elem_size = sizeof(struct device);
    mgr->anon_stream_array.elem_size = sizeof(struct stream);
    mgr->named_stream_array.elem_size = sizeof(struct stream);
    pthread_mutex_init(&mgr->lock, NULL);
    return mgr;
}

static void compress_config_mgr(struct config_mgr *mgr)
{
    dyn_array_fix(&mgr->device_array);
    dyn_array_fix(&mgr->anon_stream_array);
    dyn_array_fix(&mgr->named_stream_array);
}

static int find_path_name(struct parse_state *state, const char *name)
{
    struct dyn_array *array = &state->path_name_array;
    int i;

    for (i = array->count - 1; i >= 0; --i) {
        if (0 == strcmp(array->path_names[i], name)) {
            ALOGV("Existing path '%s' id=%d", name, i);
            return i;   /* found - return existing index */
        }
    }
    return -EINVAL;
}

static int add_path_name(struct parse_state *state, const char *name)
{
    struct dyn_array *array = &state->path_name_array;
    int index;

    /* Check if already in array */
    index = find_path_name(state, name);
    if (index >= 0) {
        return index;   /* already exists */
    }

    index = new_name(array, name);
    if (index < 0) {
        return -ENOMEM;
    }

    ALOGV("New path '%s' id=%d", name, index);
    return index;
}

static void path_names_free(struct parse_state *state)
{
    struct dyn_array *array = &state->path_name_array;
    int i;

    for (i = array->count - 1; i >= 0; --i) {
        free((void*)array->path_names[i]);
    }
    dyn_array_free(array);
}

static void codec_probe_free(struct parse_state *state)
{
    struct dyn_array *array = &state->init_probe.codec_case_array;
    int i;

    for (i = array->count - 1; i >= 0; --i) {
        free((void*)array->codec_cases[i].codec_name);
        array->codec_cases[i].codec_name = NULL;
        free((void*)array->codec_cases[i].file);
        array->codec_cases[i].file = NULL;
    }

    free((void*)state->init_probe.file);
    state->init_probe.file = NULL;

    free((void*)state->cur_xml_file);
    state->cur_xml_file = NULL;

    state->init_probe.new_xml_file = NULL;

    dyn_array_free(array);
}

static int string_to_uint(uint32_t *result, const char *str)
{
    char *endptr;
    unsigned long int v;

    if (!str) {
        return -ENOENT;
    }

    /* return error if not a valid decimal or hex number */
    v = strtoul(str, &endptr, 0);
    if ((endptr[0] == '\0') && (endptr != str) && (v <= 0xFFFFFFFF)) {
        *result = (uint32_t)v;
        return 0;
    } else {
        ALOGE("'%s' not a valid number", str);
        return -EINVAL;
    }
}

static int string_to_int(int *result, const char *str)
{
    char *endptr;
    unsigned long int v;

    if (!str) {
        return -ENOENT;
    }

    /* return error if not a valid decimal or hex number */
    v = strtol(str, &endptr, 0);
    if ((endptr[0] == '\0') && (endptr != str)) {
        *result = v;
        return 0;
    } else {
        ALOGE("'%s' not a valid signed integer", str);
        return -EINVAL;
    }
}

static int attrib_to_uint(uint32_t *result, struct parse_state *state,
                                enum attrib_index index)
{
    const char *str = state->attribs.value[index];
    return string_to_uint(result, str);
}

static int attrib_to_int(int *result, struct parse_state *state,
                                enum attrib_index index)
{
    const char *str = state->attribs.value[index];
    return string_to_int(result, str);
}

static int make_byte_work_buffer(struct ctl *c,
                                 uint32_t buffer_size)
{
    int ret = 0;

    c->buffer = malloc(buffer_size);
    if (!c->buffer) {
        ALOGE("Failed to allocate work buffer");
        return -ENOMEM;
    }

    if (c->data_file_name) {
        ret = get_value_from_file(c, buffer_size);
    } else {
        ret = make_byte_array(c, buffer_size);
    }
    if (ret != 0) {
        return ret;
    }

    ALOGV("Added ctl '%s' byte array len %d", c->name, c->array_count);
    return 0;
}

static int get_value_from_file(struct ctl *c, uint32_t vnum)
{
    uint32_t data_size;
    FILE *fp;

    fp = fopen(c->data_file_name, "rb");
    if (fp == 0) {
        ALOGE("Failed to open %s", c->data_file_name);
        return -EIO;
    }
    fseek(fp, 0L, SEEK_END);
    data_size = (uint32_t) ftell(fp);
    rewind(fp);

    if (data_size > vnum) {
        ALOGE("Data size %d exceeded max control size, the first %d bytes are kept",
               data_size, vnum);
        c->array_count = vnum;
    } else {
        c->array_count = data_size;
    }

    uint8_t *buffer = malloc(c->array_count);
    if (!buffer) {
        ALOGE("Failed to allocate read buffer");
        fclose(fp);
        return -ENOMEM;
    }

    int read = fread((void *)buffer, 1, c->array_count, fp);
    fclose(fp);
    if (read < (int)c->array_count) {
        ALOGE("Failed to get control value: %d", -errno);
        free(buffer);
        return -EIO;
    }

    c->value.data = buffer;

    return 0;
}

static int make_byte_array(struct ctl *c, uint32_t vnum)
{
    const char *val_str = c->value.string;
    char *str;
    uint8_t *pdatablock = NULL;
    uint8_t *bytes;
    int count;
    char *p, *savep;
    uint32_t v;
    int ret;

    if (c->index >= vnum) {
        ALOGE("Control index out of range(%u>%u)", c->index, vnum);
        return -EINVAL;
    }

    str = strdup(val_str);
    if (!str) {
        return -ENOMEM;
    }

    /* get number of entries in value string by counting commas */
    p = strtok_r(str, ",", &savep);
    for (count = 0; p != NULL; count++) {
        p = strtok_r(NULL, ",", &savep);
    }

    if (count == 0) {
        ALOGE("No values for byte array");
        ret = -EINVAL;
        goto fail;
    }

    if ((c->index + count) > vnum) {
        ALOGE("Array overflows control (%u+%u > %u)",
                    c->index, count, vnum);
        ret = -EINVAL;
        goto fail;
    }
    c->array_count = count;

    pdatablock = malloc(count);
    if (!pdatablock) {
        ALOGE("Out of memory for control data");
        ret = -ENOMEM;
        goto fail;
    }

    strcpy(str,val_str);
    bytes = pdatablock;

    for (p = strtok_r(str, ",", &savep); p != NULL;) {
        ret = string_to_uint(&v, p);
        if (ret != 0) {
            goto fail;
        }
        ALOGE_IF(v > 0xFF, "Byte out of range");

        *bytes++ = (uint8_t)v;
        p = strtok_r(NULL, ",", &savep);
    }

    free(str);
    free((void *)val_str); /* no need to keep this string now */
    c->value.data = pdatablock;
    return 0;

fail:
    free(pdatablock);
    free(str);
    return ret;
}

static const struct parse_device *parse_match_device(const char *name)
{
    const struct parse_device *p = &device_table[0];
    const struct parse_device *p_end =
            &device_table[ sizeof(device_table) / sizeof(device_table[0]) ];

    for (; p < p_end; ++p) {
        if (0 == strcmp(name, p->name)) {
            return p;
        }
    }

    return NULL;
}

static const char *debug_device_to_name(uint32_t device)
{
    const struct parse_device *p = &device_table[0];
    const struct parse_device *p_end =
            &device_table[ sizeof(device_table) / sizeof(device_table[0]) ];

    /* Assumes device contains a single bitflag plus direction bit */

    for (; p < p_end; ++p) {
        if (device == p->device) {
            return p->name;
        }
    }

    return "unknown";
}


static int parse_ctl_start(struct parse_state *state)
{
    const char *name = strdup(state->attribs.value[e_attrib_name]);
    struct dyn_array *array;
    struct ctl *c = NULL;
    int ret;

    if (state->current.path) {
        ALOGV("parse_ctl_start:path ctl");
        array = &state->current.path->ctl_array;
    } else {
        ALOGV("parse_ctl_start:case ctl");
        array = &state->current.scase->ctl_array;
    }

    if (!name) {
        return -ENOMEM;
    }

    c = new_ctl(array, name);
    if (c == NULL) {
        ret = -ENOMEM;
        goto fail;
    }

    if (attrib_to_uint(&c->index, state, e_attrib_index) == -EINVAL) {
        ALOGE("Invalid ctl index");
        ret = -EINVAL;
        goto fail;
    }

    const char *filename = state->attribs.value[e_attrib_file];
    if (filename) {
        c->data_file_name = strdup(filename);
        if (!c->data_file_name) {
            ret = -ENOMEM;
            goto fail;
        }
    } else {
        c->value.string = strdup(state->attribs.value[e_attrib_val]);
        if(!c->value.string) {
            ret = -ENOMEM;
            goto fail;
        }
    }

    ret = ctl_open(state->cm, c);
    if (ret == -ENOENT) {
        /* control not found, just ignore and do lazy open when it's used */
        return 0;
    } else {
        return ret;
    }

fail:
    free(c);
    free((void *)name);
    return ret;
}

static int parse_codec_case_start(struct parse_state * state)
{
    const char *codec = strdup(state->attribs.value[e_attrib_name]);
    const char *file = state->attribs.value[e_attrib_file];
    struct dyn_array *array = &state->current.codec_probe->codec_case_array;
    struct codec_case  *cc = NULL;
    int ret = 0;

    while (isspace(*file)) {
        ++file;
    }

    if (file[0] == '/') {
        /* Absolute path: use as-is. */
        file = strdup(file);
    } else {
        /* Relative to location of current XML file: get full path. */
        file = join_paths(state->cur_xml_file, file, 1);
    }

    if (!file) {
        return -ENOMEM;
    }

    cc = new_codec_case(array, codec, file);
    if (cc == NULL) {
        ret = -ENOMEM;
        free((void*)codec);
        free((void*)file);
    }

    return ret;
}

static int parse_init_start(struct parse_state *state)
{
    /* The <init> section inside <mixer> is really just a
     * path that we only use once. We re-use the parsing of
     * <ctl> entries by creating a temporary path which we
     * apply at the end of parsing and then discard
     */
    state->current.path = &state->init_path;

    /* Don't allow <pre_init> or another <init> to follow this */
    state->stack.entry[state->stack.index - 1].valid_subelem &=
        ~(BIT(e_elem_pre_init) | BIT(e_elem_init));

    ALOGV("Added init path");
    return 0;
}

static int parse_init_end(struct parse_state *state)
{
    compress_path(state->current.path);
    state->current.path = NULL;
    return 0;
}

static int parse_preinit_start(struct parse_state *state)
{
    /* This is handled the same way as <init> section except that
     * when we get the end tag we immediately process the settings
     * before parsing the rest of the config.
     */
    state->current.path = &state->preinit_path;

    ALOGV("Started <pre_init>");
    return 0;
}

static int parse_preinit_end(struct parse_state *state)
{
    ALOGV("Applying <pre_init>");

    state->current.path = NULL;

    /* Execute the pre_init commands now */
    apply_path_l(state->cm, &state->preinit_path);

    /* Re-open tinyalsa to pick up any controls added by the pre_init */
    mixer_close(state->cm->mixer);
    state->cm->mixer = mixer_open(state->mixer_card_number);

    if (!state->cm->mixer) {
        ALOGE("Failed to re-open mixer card %u", state->mixer_card_number);
        return -EINVAL;
    }

    return 0;
}

static char *probe_trim_spaces(char *str)
{
    int len;
    char *end;

    while(isspace(*str))
        ++str;

    len = strlen(str);

    if(!len)
        return str;

    end = str + len -1;
    while ((end > str) && isspace(*end))
        end--;
    *(end + 1) = '\0';

    return str;
}


static int probe_config_file(struct parse_state *state)
{
    int i;
    char buf[40], *codec;
    FILE *fp;
    int ret;

    ALOGV("+probe_config_file");

    fp = fopen(state->init_probe.file, "r");
    while (fp == NULL) {
        usleep(50000);
        fp = fopen(state->init_probe.file, "r");
    }

    if (fgets(buf,sizeof(buf),fp) == NULL) {
        ALOGE("I/O error reading codec probe file");
        ret = -EIO;
        goto exit;
    }

    codec = probe_trim_spaces(buf);
    state->init_probe.new_xml_file = NULL;

    for (i = 0; i < (int)state->init_probe.codec_case_array.count; ++i)
    {
        if (strcmp(codec, state->init_probe.codec_case_array.codec_cases[i].codec_name) == 0)
        {
            break;
        }
    }

    if (i == (int)state->init_probe.codec_case_array.count) {
        ALOGE("Codec probe file not found");
        ret = 0;
        goto exit;
    }

    state->init_probe.new_xml_file = state->init_probe.codec_case_array.codec_cases[i].file;

    if (strcmp(state->init_probe.new_xml_file, state->cur_xml_file) == 0) {
        /* There is no new xml file to redirect */
        state->init_probe.new_xml_file = NULL;
        XML_StopParser(state->parser,false);
        ALOGE("A codec probe case can't redirect to its own config file");
        ret = -EINVAL;
    } else {
        /*
         * We are stopping the Parser as we got new codec xml file
         * and we will restart the parser with that new file
         */
        ALOGV("Got new config file %s", state->init_probe.new_xml_file);
        XML_StopParser(state->parser,XML_TRUE);
        ret = 0;
    }

exit:
    fclose(fp);

    return ret;
}

static int parse_codec_probe_start(struct parse_state *state)
{
    const char *file = state->attribs.value[e_attrib_file];
    int ret = 0;

    while (isspace(*file)) {
        ++file;
    }

    if (file[0] == '/') {
        /* Absolute path: use as-is. */
        file = strdup(file);
    } else {
        /* Relative to location of current XML file: get full path. */
        file = join_paths(state->cur_xml_file, file, 1);
    }

    if (!file) {
        return -ENOMEM;
    }

    if (state->init_probe.file == NULL) {
        state->init_probe.file = file;
        state->current.codec_probe = &state->init_probe;
        ret = 0;
    } else {
        ALOGE("The codec_probe block redefined");
        free((void*)file);
        ret = -EINVAL;
    }
    return ret;
}

static int parse_codec_probe_end(struct parse_state *state)
{

    compress_probe(state->current.codec_probe);
    state->current.codec_probe = NULL;
    probe_config_file(state);

    return 0;
}

static int parse_path_start(struct parse_state *state)
{
    const char *name = state->attribs.value[e_attrib_name];
    struct device *device = state->current.device;
    struct dyn_array *array = &device->path_array;
    struct path *path;
    int id;

    id = add_path_name(state, name);
    if (id < 0) {
        return id;
    }

    path = new_path(array, id);
    if (path == NULL) {
        return -ENOMEM;
    }

    state->current.path = path;

    ALOGV("Added path '%s' id=%d", name, id);
    return 0;
}

static int parse_path_end(struct parse_state *state)
{
    /* Free unused memory in the ctl array */
    compress_path(state->current.path);
    state->current.path = NULL;
    return 0;
}

static int parse_case_start(struct parse_state *state)
{
    const char *name = state->attribs.value[e_attrib_name];
    struct usecase *puc = state->current.usecase;
    struct dyn_array *array = &puc->case_array;
    struct scase *sc;

    sc = new_case(array, name);
    if (sc == NULL) {
        return -ENOMEM;
    }

    state->current.scase = sc;

    ALOGV("Added case '%s' to '%s'", name, puc->name);
    return 0;
}

static int parse_case_end(struct parse_state *state)
{
    /* Free unused memory in the ctl array */
    compress_case(state->current.scase);
    state->current.scase = NULL;
    return 0;
}

static int parse_usecase_start(struct parse_state *state)
{
    const char *name = state->attribs.value[e_attrib_name];
    struct dyn_array *array = &state->current.stream->usecase_array;
    struct usecase *puc;

    puc = new_usecase(array, name);
    if (puc == NULL) {
        return -ENOMEM;
    }

    state->current.usecase = puc;

    ALOGV("Added usecase '%s'", name);

    return 0;
}

static int parse_usecase_end(struct parse_state *state)
{
    /* Free unused memory in the case array */
    compress_usecase(state->current.usecase);
    return 0;
}

static int parse_set_start(struct parse_state *state)
{
    const char *name = strdup(state->attribs.value[e_attrib_name]);
    struct dyn_array *array = &state->current.stream->constants_array;
    struct constant *pc;
    const char *val = NULL;

    if (!name) {
        return -ENOMEM;
    }

    val = strdup(state->attribs.value[e_attrib_val]);
    if (!val) {
        free((void *)name);
        return -ENOMEM;
    }

    pc = new_constant(array, name, val);
    if (pc == NULL) {
        free((void *)name);
        free((void *)val);
        return -ENOMEM;
    }

    ALOGV("Added constant '%s'=%s", name, val);

    return 0;
}

static int parse_enable_disable_start(struct parse_state *state, bool is_enable)
{
    /* Handling of <enable> and <disable> is almost identical so
     * they are both handled in this function
     */

    const char *path_name = state->attribs.value[e_attrib_path];
    int i;

    i = find_path_name(state,path_name);
    if (i < 0) {
        ALOGE("Path '%s' not defined", path_name);
        return -EINVAL;
    }

    if (is_enable) {
        ALOGV("Add enable path '%s' (id=%d)",
                                state->path_name_array.path_names[i], i);
        state->current.stream->enable_path = i;
    } else {
        ALOGV("Add disable path '%s' (id=%d)",
                                state->path_name_array.path_names[i], i);
        state->current.stream->disable_path = i;
    }

    return 0;
}

static int parse_enable_start(struct parse_state *state)
{
    return parse_enable_disable_start(state, true);
}

static int parse_disable_start(struct parse_state *state)
{
    return parse_enable_disable_start(state, false);
}

static int parse_stream_ctl_start(struct parse_state *state)
{
    /* Parse a <ctl> element within a stream which defines
     * mixer controls - currently only supports volume controls
     */

    const char *name = state->attribs.value[e_attrib_name];
    const char *function = state->attribs.value[e_attrib_function];
    const char *index = state->attribs.value[e_attrib_index];
    struct mixer_ctl *ctl;
    struct stream_control *streamctl;
    uint idx_val = 0;
    int v;

    ctl = mixer_get_ctl_by_name(state->cm->mixer, name);
    if (!ctl) {
        ALOGE("Control '%s' not found", name);
        return -EINVAL;
    }

    /*
     * Tinyalsa mixer_ctl_get_range_min()/mixer_ctl_get_range_max()
     * return negative error if the control isn't valid. As the minimum
     * value could be negative we can't check for errors so check in advance
     * that the control will not cause an error from these functions.
     */
    if (mixer_ctl_get_type(ctl) != MIXER_CTL_TYPE_INT) {
        ALOGE("Control '%s' is not an integer", name);
        return -EINVAL;
    }

    if (index != NULL) {
        if (attrib_to_uint(&idx_val, state, e_attrib_index) == -EINVAL) {
            return -EINVAL;
        }
    }

    if (0 == strcmp(function, "leftvol")) {
        ALOGE_IF(ctl_ref_valid(&state->current.stream->controls.volume_left.ref),
                                "Left volume control specified again");
        streamctl = &(state->current.stream->controls.volume_left);
    } else if (0 == strcmp(function, "rightvol")) {
        ALOGE_IF(ctl_ref_valid(&state->current.stream->controls.volume_right.ref),
                                "Right volume control specified again");
        streamctl = &(state->current.stream->controls.volume_right);
    } else {
        ALOGE("'%s' is not a valid control function", function);
        return -EINVAL;
    }

    streamctl->index = idx_val;

    switch (attrib_to_int(&v, state, e_attrib_min)) {
    case -EINVAL:
        ALOGE("Invalid min for '%s'", name);
        return -EINVAL;

    case -ENOENT:
        /* Not specified, get control's min value */
        streamctl->min = mixer_ctl_get_range_min(ctl);
        break;

    default:
        streamctl->min = v;
        break;
    }

    switch (attrib_to_int(&v, state, e_attrib_max)) {
    case -EINVAL:
        ALOGE("Invalid max for '%s'", name);
        return -EINVAL;

    case -ENOENT:
        /* Not specified, get control's max value */
        streamctl->max = mixer_ctl_get_range_max(ctl);
        break;

    default:
        streamctl->max = v;
        break;
    }

    ctl_set_ref(&streamctl->ref, ctl);

    ALOGV("(%p) Added control '%s' function '%s' range %d-%d",
                state->current.stream,
                name, function, streamctl->min, streamctl->max);

    return 0;
}

static int parse_stream_start(struct parse_state *state)
{
    const char *type = state->attribs.value[e_attrib_type];
    const char *dir = state->attribs.value[e_attrib_dir];
    const char *name = state->attribs.value[e_attrib_name];
    bool out;
    bool global;
    uint32_t card = state->mixer_card_number;
    uint32_t device = UINT_MAX;
    uint32_t maxref = INT_MAX;
    struct stream *s;

    if (name != NULL) {
        if (find_named_stream(state->cm, name) != NULL) {
            ALOGE("Stream '%s' already declared", name);
            return -EINVAL;
        }
        s = new_stream(&state->cm->named_stream_array, state->cm);
    } else {
        s = new_stream(&state->cm->anon_stream_array, state->cm);
    }

    if (s == NULL) {
        return -ENOMEM;
    }

    if (!name) {
        global = false;
    } else {
        global = (strcmp(name, "global") == 0);
    }

    if (dir == NULL) {
        if (!global) {
            ALOGE("'dir' is required");
            return -EINVAL;
        }
    } else if (0 == strcmp(dir, "out")) {
        out = true;
    } else if (0 == strcmp(dir, "in")) {
        out = false;
    } else {
        ALOGE("'%s' is not a valid direction", dir);
        return -EINVAL;
    }

    if (global) {
        s->info.type = e_stream_global;
    } else if (0 == strcmp(type, "hw")) {
        if (name == NULL) {
            ALOGE("Anonymous stream cannot be type hw");
            return -EINVAL;
        }
        s->info.type = out ? e_stream_out_hw : e_stream_in_hw;
    } else if (0 == strcmp(type, "pcm")) {
        s->info.type = out ? e_stream_out_pcm : e_stream_in_pcm;
    } else if (0 == strcmp(type, "compress")) {
        s->info.type = out ? e_stream_out_compress : e_stream_in_compress;
    } else {
        ALOGE("'%s' not a valid stream type", type);
        return -EINVAL;
    }

    if (attrib_to_uint(&card, state, e_attrib_card) == -EINVAL) {
        return -EINVAL;
    }

    if (attrib_to_uint(&device, state, e_attrib_device) == -EINVAL) {
        return -EINVAL;
    }

    if (attrib_to_uint(&maxref, state, e_attrib_instances) == -EINVAL) {
        return -EINVAL;
    }

    if (attrib_to_uint(&s->info.rate, state, e_attrib_rate) == -EINVAL) {
        return -EINVAL;
    }

    if (attrib_to_uint(&s->info.period_count, state,
                        e_attrib_period_count) == -EINVAL) {
        return -EINVAL;
    }

    if (attrib_to_uint(&s->info.period_size, state,
                        e_attrib_period_size) == -EINVAL) {
        return -EINVAL;
    }

    if (name != NULL) {
        s->name = strdup(name);
        if (!s->name) {
            return -ENOMEM;
        }
    }
    s->info.card_number = card;
    s->info.device_number = device;
    s->max_ref_count = maxref;

    ALOGV("Added stream %s type=%u card=%u device=%u max_ref=%u",
                    s->name ? s->name : "",
                    s->info.type, s->info.card_number, s->info.device_number,
                    s->max_ref_count );

    state->current.stream = s;

    return 0;
}

static int parse_stream_end(struct parse_state *state)
{
    /* Free unused memory in the ctl array */
    compress_stream(state->current.stream);
    return 0;
}

static int parse_device_start(struct parse_state *state)
{
    const char *dev_name = state->attribs.value[e_attrib_name];
    struct dyn_array *array = &state->cm->device_array;
    uint32_t device_flag;
    uint32_t *existing_devices;
    const struct parse_device *p;
    struct device* d;

    p = parse_match_device(dev_name);

    if (p == NULL) {
        ALOGE("'%s' is not a valid device", dev_name);
        return -EINVAL;
    }

    device_flag = p->device;

    if (device_flag != 0) {
        /* not the global device - add it to list of available devices */
        if (device_flag & AUDIO_DEVICE_BIT_IN) {
                existing_devices = &state->cm->supported_input_devices;
        } else {
                existing_devices = &state->cm->supported_output_devices;
        }

        if ((device_flag & *existing_devices) == device_flag) {
            ALOGE("Device '%s' already defined", dev_name);
            ALOGE("Device = 0x%x extisting_devices = 0x%x", device_flag, *existing_devices);
            ALOGE("supported_output_devices=0x%x supported_input_devices=0x%x",
                            state->cm->supported_output_devices,
                            state->cm->supported_input_devices );
            return -EINVAL;
        }
        *existing_devices |= device_flag;
    }

    ALOGV("Add device '%s'", dev_name);

    d = new_device(array, device_flag);
    if (d == NULL) {
        return -ENOMEM;
    }
    state->current.device = d;

    return 0;
}

static int parse_device_end(struct parse_state *state)
{
    /* Free unused memory in the path array */
    compress_device(state->current.device);
    return 0;
}

static int get_card_name_for_id(unsigned int id, char* name, int len)
{
    char cardInfoFile[32];
    FILE *fp;
    int ret = 0;
    snprintf(cardInfoFile, sizeof(cardInfoFile), "/proc/asound/card%u/id", id);

    fp = fopen(cardInfoFile, "r");
    if (fp == NULL) {
        ALOGE("Failed to open file: %s", cardInfoFile);
        return -EINVAL;
    }

    if (fgets(name, len, fp) == NULL) {
        ALOGE("Failed to read name from file: %s", cardInfoFile);
        ret = -EINVAL;
        goto read_fail;
    }
    //Only return first line of file, without new lines.
    name[strcspn(name, "\n")] = 0;
read_fail:
    fclose(fp);
    return ret;
}

static int get_card_id_for_name(const char* name, uint32_t *id)
{
    if (name == NULL) {
        return -EINVAL;
    }

    DIR* dir;
    struct dirent* entry;
    int ret = -EINVAL;

    dir = opendir("/proc/asound");

    if (dir != NULL) {
        while ((entry = readdir(dir)) != NULL) {
            unsigned int t_id;
            if (sscanf(entry->d_name, "card%u" , &t_id)) {
                char t_name[128];
                if (get_card_name_for_id(t_id, t_name, sizeof(t_name)) == 0 &&
                        strcmp(t_name, name) == 0) {
                    ALOGV("Found card %u with name %s", t_id, name);
                    *id = t_id;
                    ret = 0;
                    break;
                }
            }
        }
        closedir(dir);
    }
    return ret;
}

static int parse_mixer_start(struct parse_state *state)
{
    uint32_t card = MIXER_CARD_DEFAULT;

    ALOGV("parse_mixer_start");
    if (attrib_to_uint(&card, state, e_attrib_card) == 0) {
        if (state->attribs.value[e_attrib_name] != NULL) {
            ALOGE("Mixer must be configured by only one of 'card' OR 'name'. Both provided.");
            return -EINVAL;
        }
    } else if (get_card_id_for_name(state->attribs.value[e_attrib_name],
                                    &card) != 0) {
        return -EINVAL;
    }

    ALOGV("Opening mixer card %u", card);

    state->cm->mixer = mixer_open(card);

    if (!state->cm->mixer) {
        ALOGE("Failed to open mixer card %u", card);
        return -EINVAL;
    }

    state->mixer_card_number = card;

    return 0;
}

static int parse_mixer_end(struct parse_state *state)
{
    ALOGV("parse_mixer_end");

    /* Now we can allow all other root elements but not another <mixer> */
    state->stack.entry[state->stack.index - 1].valid_subelem =
                                                  BIT(e_elem_device)
                                                | BIT(e_elem_stream);
    return 0;
}

static int parse_set_error(struct parse_state *state, int error)
{
    state->parse_error = error;
    state->error_line = XML_GetCurrentLineNumber(state->parser);
    return error;
}

static int parse_log_error(struct parse_state *state)
{
    int err = state->parse_error;
    int xml_err = XML_GetErrorCode(state->parser);

    if((err < 0) || (xml_err != XML_ERROR_NONE)) {
        ALOGE_IF(err < 0, "Error in config file at line %d", state->error_line);
        ALOGE_IF(xml_err != XML_ERROR_NONE,
                            "Parse error '%s' in config file at line %u",
                            XML_ErrorString(xml_err),
                            (uint)XML_GetCurrentLineNumber(state->parser));
        return -EINVAL;
    } else {
        return 0;
    }
}

static int extract_attribs(struct parse_state *state, int elem_index)
{
    const uint32_t valid_attribs = elem_table[elem_index].valid_attribs;
    uint32_t required_attribs = elem_table[elem_index].required_attribs;
    const XML_Char **attribs = state->attribs.all;
    int i;

    memset(&state->attribs.value, 0, sizeof(state->attribs.value));

    while (attribs[0] != NULL) {
        for (i = 0; i < e_attrib_count; ++i ) {
            if ((BIT(i) & valid_attribs) != 0) {
                if (0 == strcmp(attribs[0], attrib_table[i].name)) {
                    state->attribs.value[i] = attribs[1];
                    required_attribs &= ~BIT(i);
                    break;
                }
            }
        }
        if (i >= e_attrib_count) {
            ALOGE("Attribute '%s' not allowed here", attribs[0] );
            return -EINVAL;
        }

        attribs += 2;
    }

    if (required_attribs != 0) {
        for (i = 0; i < e_attrib_count; ++i ) {
            if ((required_attribs & BIT(i)) != 0) {
                ALOGE("Attribute '%s' required", attrib_table[i].name);
            }
        }
        return -EINVAL;
    }

    return 0;
}

static void parse_section_start(void *data, const XML_Char *name,
                                const XML_Char **attribs)
{
    struct parse_state *state = (struct parse_state *)data;
    int stack_index = state->stack.index;
    const uint32_t valid_elems =
                        state->stack.entry[stack_index].valid_subelem;
    int i;

    if (state->parse_error != 0) {
        return;
    }

    ALOGV("parse start <%s>", name );

    /* Find element in list of elements currently valid */
    for (i = 0; i < e_elem_count; ++i) {
        if ((BIT(i) & valid_elems) != 0) {
            if (0 == strcmp(name, elem_table[i].name)) {
                break;
            }
        }
    }

    if ((i >= e_elem_count) || (stack_index >= MAX_PARSE_DEPTH)) {
        ALOGE("Element '%s' not allowed here", name);
        parse_set_error(state, -EINVAL);
    } else {
        /* element ok - push onto stack */
        ++stack_index;
        state->stack.entry[stack_index].elem_index = i;
        state->stack.entry[stack_index].valid_subelem
                                                = elem_table[i].valid_subelem;
        state->stack.index = stack_index;

        /* Extract attributes and call handler */
        state->attribs.all = attribs;
        if (extract_attribs(state, i) != 0) {
            parse_set_error(state, -EINVAL);
        } else {
            if (elem_table[i].start_fn) {
                parse_set_error(state, (*elem_table[i].start_fn)(state));
            }
        }
    }
}

static void parse_section_end(void *data, const XML_Char *name)
{
    struct parse_state *state = (struct parse_state *)data;
    const int i = state->stack.entry[state->stack.index].elem_index;

    if (state->parse_error != 0) {
        return;
    }

    ALOGV("parse end <%s>", name );

    if (elem_table[i].end_fn) {
        state->parse_error = (*elem_table[i].end_fn)(state);
    }

    --state->stack.index;
}

static int do_parse(struct parse_state *state)
{
    bool eof = false;
    int len;
    int ret = 0;

    state->parse_error = 0;
    state->stack.index = 0;
    /* First element must be <audiohal> */
    state->stack.entry[0].valid_subelem = BIT(e_elem_audiohal);

    while (!eof && (state->parse_error == 0) ) {
        len = fread(state->read_buf, 1, sizeof(state->read_buf), state->file);
        if (ferror(state->file)) {
            ALOGE("I/O error reading config file");
            ret = -EIO;
            break;
        }

        eof = feof(state->file);

        if (XML_Parse(state->parser,
                      state->read_buf,
                      len,
                      eof) == XML_STATUS_SUSPENDED) {
            /* A codec_probe redirection suspends parsing of the current file */
            break;
        }
        if (parse_log_error(state) < 0) {
            ret = -EINVAL;
            break;
        }
    }

    return ret;
}

static int open_config_file(struct parse_state *state, const char *file)
{
    free((void *)state->cur_xml_file);

    if (file == NULL) {
        ALOGE("Invalid file name (NULL)\n");
        return -EINVAL;
    }
    state->cur_xml_file = strdup(file);

    ALOGV("Reading configuration from %s\n", file);
    state->file = fopen(file, "r");
    if (state->file) {
        return 0;
    } else {
        ALOGE_IF(!state->file, "Failed to open config file %s", file);
        return -ENOSYS;
    }
}


static void cleanup_parser(struct parse_state *state)
{
    if (state) {
        path_names_free(state);

        codec_probe_free(state);

        free_ctl_array(&state->init_path.ctl_array);
        free_ctl_array(&state->preinit_path.ctl_array);

        if (state->parser) {
            XML_ParserFree(state->parser);
        }

        if (state->file) {
            fclose(state->file);
        }

        free(state);
    }
}

static int init_state(struct parse_state *state)
{
    int ret;

    if (state == NULL) {
        ALOGE("Invalid argument\n");
        return -EINVAL;
    }

    state->path_name_array.elem_size = sizeof(const char *);
    state->preinit_path.ctl_array.elem_size = sizeof(struct ctl);
    state->init_path.ctl_array.elem_size = sizeof(struct ctl);
    state->init_probe.codec_case_array.elem_size = sizeof(struct codec_case);

    /* "off" and "on" are pre-defined path names */
    ret = add_path_name(state, predefined_path_name_table[0]);
    if (ret < 0) {
        return ret;
    }
    ret = add_path_name(state, predefined_path_name_table[1]);
    if (ret < 0) {
        return ret;
    }

    return 0;
}

static void print_ctls(const struct config_mgr *cm)
{
    const struct dyn_array *path_array, *ctl_array;
    const struct device *dev;
    const struct ctl *c;
    uint dev_idx, path_idx, ctl_idx;

    if (!cm)
        return;

    ALOGV("%d devices", cm->device_array.count);
    for (dev_idx = 0; dev_idx < cm->device_array.count; dev_idx++) {
        dev = &cm->device_array.devices[dev_idx];
        path_array = &dev->path_array;
        ALOGV("Device %d: type 0x%x, %d paths",
                dev_idx, dev->type, path_array->count);
        for (path_idx = 0; path_idx < path_array->count; path_idx++) {
            ctl_array = &path_array->paths[path_idx].ctl_array;
            ALOGV("Path %d: %d ctls", path_idx, ctl_array->count);
            for (ctl_idx = 0; ctl_idx < ctl_array->count; ctl_idx++) {
                c = &ctl_array->ctls[ctl_idx];
                ALOGV("Ctl %d: "
                        "name %s, "
                        "index %d, "
                        "array_count %d, "
                        "type %d ",
                        ctl_idx,
                        c->name,
                        c->index,
                        c->array_count,
                        c->type);

                switch (c->type) {
                case MIXER_CTL_TYPE_BOOL:
                case MIXER_CTL_TYPE_INT:
                    ALOGV("int: 0x%x", c->value.integer);
                    break;
                case MIXER_CTL_TYPE_BYTE:
                    if (c->data_file_name) {
                        ALOGV("file: %s", c->data_file_name);
                    } else {
                        ALOGV("byte[0]: %d", c->value.data[0]);
                    }
                    break;
                default:
                    ALOGV("string: \"%s\"", c->value.string);
                    break;
                }
            }
        }
    }
}

static int parse_config_file(struct config_mgr *cm, const char *file_name)
{
    struct parse_state *state;
    int ret = 0;

    state = calloc(1, sizeof(struct parse_state));
    if (!state) {
        return -ENOMEM;
    }

    state->cm = cm;
    state->init_probe.new_xml_file = NULL;

    ret = init_state(state);
    if (ret < 0) {
         goto fail;
    }

    state->parser = XML_ParserCreate(NULL);
    if ( !state->parser ) {
        goto fail;
    }
    ret = open_config_file(state,file_name);
    do {
        if (ret != 0) {
            ALOGE("Error while opening XML file\n");
            ret = -ENOMEM;
            break;
        }

        if (state->init_probe.new_xml_file != NULL) {
            free((void*)state->init_probe.file);
            state->init_probe.file = NULL;
            XML_ParserReset(state->parser, NULL);
            state->init_probe.new_xml_file = NULL;
        }

        XML_SetUserData(state->parser, state);
        XML_SetElementHandler(state->parser, parse_section_start, parse_section_end);
        ret = do_parse(state);

        if (ret != 0) {
            ALOGE("Error while parsing XML file\n");
            break;
        }

        if (state->init_probe.new_xml_file != NULL) {
            if (state->file) {
                fclose(state->file);
            }

            ALOGV("Opening new XML file");
            ret = open_config_file(state, state->init_probe.new_xml_file);
        }
    } while (state->init_probe.new_xml_file != NULL);

    if (ret >= 0) {
        print_ctls(cm);

        /* Initialize the mixer by applying the <init> path */
        /* No need to take mutex during initialization */
        apply_path_l(cm, &state->init_path);
    }

fail:
    cleanup_parser(state);
    return ret;
}

/*********************************************************************
 * Initialization
 *********************************************************************/

struct config_mgr *init_audio_config(const char *config_file_name)
{
    char *cwd_path;
    char *absolute_path = NULL;
    int ret;

    struct config_mgr* mgr = new_config_mgr();

#ifdef ENABLE_COVERAGE
    enableCoverageSignal();
#endif

    /*
     * If path is relative, make it absolute so it can be used to
     * create the base path for any codec_probe redirections that are
     * specified as relative.
     */
    while (isspace(*config_file_name)) {
        ++config_file_name;
    }

    if (config_file_name[0] != '/') {
#ifdef ANDROID
        absolute_path = join_paths(ETC_PATH, config_file_name, 0);
#else
        /*
         * realpath() will cause links to be pre-resolved now, prefer getcwd()
         * which leaves links to be resolved at the time the file is opened.
         */
        cwd_path = malloc(sizeof(char) * PATH_MAX);
        if (getcwd(cwd_path, PATH_MAX) == NULL) {
            ret = errno;
            free(cwd_path);
            errno = ret;
            return NULL;
        }

        absolute_path = join_paths(cwd_path, config_file_name, 0);
        free(cwd_path);
#endif

        config_file_name = absolute_path;
    }

    ret = parse_config_file(mgr, config_file_name);
    free(absolute_path);
    if (ret != 0) {
        free(mgr);
        errno = -ret;
        return NULL;
    }

    /* Free unused memory in the device and stream arrays */
    compress_config_mgr(mgr);

    return mgr;
}

struct mixer *get_mixer( const struct config_mgr *cm )
{
    return cm->mixer;
}

static void free_ctl_array(struct dyn_array *ctl_array)
{
    struct ctl *c;
    int ctl_idx;

    for (ctl_idx = ctl_array->count - 1; ctl_idx >= 0; --ctl_idx) {
        c = &ctl_array->ctls[ctl_idx];
        /* The name attribute is mandatory for controls */
        free((void *)c->name);

        switch (c->type) {
        /*
         * The val attribute has been freed for the BOOL/INT
         * types of controls
         */
        case MIXER_CTL_TYPE_BOOL:
        case MIXER_CTL_TYPE_INT:
            break;
        /*
         * The val attribute has been converted to byte array
         * for the BYTE type of controls
         */
        case MIXER_CTL_TYPE_BYTE:
            free((void *)c->value.data);
            free((void *)c->buffer);
            free((void *)c->data_file_name);
            break;
        default:
            free((void *)c->value.string);
            break;
        }
    }

    dyn_array_free(ctl_array);
}

static void free_usecases( struct stream *stream )
{
    struct usecase *puc = stream->usecase_array.usecases;
    int uc_count = stream->usecase_array.count;
    struct scase *pcase;
    int i;

    for (; uc_count > 0; uc_count--, puc++) {
        free((void *)puc->name);
        pcase = puc->case_array.cases;
        for (i = puc->case_array.count; i > 0; i--, pcase++) {
            free((void *)pcase->name);
            free_ctl_array(&pcase->ctl_array);
        }
        dyn_array_free(&puc->case_array);
    }

    dyn_array_free(&stream->usecase_array);
}

static void free_constants( struct stream *stream )
{
    struct constant *pc = stream->constants_array.constants;
    int count = stream->constants_array.count;

    for (; count > 0; count--, pc++) {
        free((void *)pc->name);
        free((void *)pc->value);
    }

    dyn_array_free(&stream->constants_array);
}

static void free_stream_array(struct dyn_array *stream_array)
{
    int stream_idx;
    struct stream *s;

    for(stream_idx = stream_array->count - 1; stream_idx >= 0; --stream_idx) {
        s = &stream_array->streams[stream_idx];
        free((void *)s->name);
        free_usecases(s);
        free_constants(s);
    }

    dyn_array_free(stream_array);
}

void free_audio_config( struct config_mgr *cm )
{
    struct dyn_array *path_array;
    int dev_idx, path_idx;

    if (cm) {
        /* Free all devices */
        for (dev_idx = cm->device_array.count - 1; dev_idx >= 0; --dev_idx) {
            /* Free all paths in device */
            path_array = &cm->device_array.devices[dev_idx].path_array;
            for (path_idx = path_array->count - 1; path_idx >= 0; --path_idx) {
                free_ctl_array(&path_array->paths[path_idx].ctl_array);
            }

            dyn_array_free(path_array);
        }

        dyn_array_free(&cm->device_array);

        free_stream_array(&cm->anon_stream_array);
        free_stream_array(&cm->named_stream_array);

        if (cm->mixer) {
            mixer_close(cm->mixer);
        }
        pthread_mutex_destroy(&cm->lock);
        free(cm);
    }
}

