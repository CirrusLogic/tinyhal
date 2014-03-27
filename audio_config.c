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
/*#define LOG_NDEBUG 0*/
/*#undef NDEBUG*/

#include <stddef.h>
#include <errno.h>
#include <assert.h>
#include <cutils/log.h>
#include <cutils/properties.h>
#include <cutils/compiler.h>

#include <system/audio.h>

/* Workaround for linker error if audio_effect.h is included in multiple
 * source files. Prevent audio.h including it */
#define ANDROID_AUDIO_EFFECT_H
struct effect_interface_s;
typedef struct effect_interface_s **effect_handle_t;
#include <hardware/audio.h>

#include <tinyalsa/asoundlib.h>
#include <expat.h>

#include "audio_config.h"

#define MIXER_CARD_DEFAULT 0
#define PCM_CARD_DEFAULT 0
#define PCM_DEVICE_DEFAULT 0
#define COMPRESS_CARD_DEFAULT 0
#define COMPRESS_DEVICE_DEFAULT 0

/* The dynamic arrays are extended in multiples of this number of objects */
#define DYN_ARRAY_GRANULE 16

/* Largest byte array control we handle */
#define BYTE_ARRAY_MAX_SIZE 512

#define INVALID_CTL_INDEX 0xFFFFFFFFUL

struct config_mgr;
struct stream;
struct path;
struct device;
struct usecase;
struct scase;

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
        const char         **path_names;
    };
};

/* Paths for "on" and "off" are a special case and have fixed ids */
enum {
    e_path_id_off = 0,
    e_path_id_on = 1,
    e_path_id_custom_base = 2
};

struct ctl {
    struct mixer_ctl    *ctl;
    uint32_t            index;
    uint32_t            array_count;
    union {
        uint32_t        uinteger;
        const char      *name;
        const uint8_t   *data;
    } value;
};

struct path {
    int                 id;         /* Integer identifier of this path */
    struct dyn_array    ctl_array;
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
    struct mixer_ctl    *ctl;
    uint                id;
};

struct stream {
    struct hw_stream  info;   /* must be first member */

    struct config_mgr*  cm;
    const char* name;       /* used for named custom streams */

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
};

struct config_mgr {
    pthread_mutex_t lock;

    struct mixer    *mixer;

    uint32_t        supported_output_devices;
    uint32_t        supported_input_devices;

    struct dyn_array device_array;
    struct dyn_array stream_array;
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
    e_elem_stream_ctl,
    e_elem_init,
    e_elem_mixer,
    e_elem_audiohal,

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
    XML_Parser          parser;
    char                read_buf[256];
    int                 parse_error; /* value >0 aborts without error */
    int                 error_line;
    int                 mixer_card_number;

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
    } current;

    /* This array hold a de-duplicated list of all path names encountered */
    struct dyn_array    path_name_array;

    /* This is a temporary path object used to collect the initial
     * mixer setup control settings under <mixer><init>
     */
    struct path         init_path;

    struct {
        int             index;
        struct parse_stack_entry entry[MAX_PARSE_DEPTH];
    } stack;
};


static const char *debug_device_to_name(uint32_t device);

/*********************************************************************
 * Routing control
 *********************************************************************/

static void apply_ctls_l( struct ctl *pctl, const int ctl_count )
{
    int i;
    unsigned int vnum;
    unsigned int value_count;
    int err = 0;
    uint8_t ctl_data[BYTE_ARRAY_MAX_SIZE];

    ALOGV("+apply_ctls_l");

    for (i = 0; i < ctl_count; ++i, ++pctl) {
        switch (mixer_ctl_get_type(pctl->ctl)) {
            case MIXER_CTL_TYPE_BOOL:
            case MIXER_CTL_TYPE_INT:
                value_count = mixer_ctl_get_num_values(pctl->ctl);

                ALOGV("apply ctl '%s' = 0x%x (%d values)",
                                        mixer_ctl_get_name(pctl->ctl),
                                        pctl->value.uinteger,
                                        value_count);

                if (pctl->index == INVALID_CTL_INDEX) {
                    for (vnum = 0; vnum < value_count; ++vnum) {
                        err = mixer_ctl_set_value(pctl->ctl, vnum, pctl->value.uinteger);
                        if (err < 0) {
                            break;
                        }
                    }
                } else {
                    err = mixer_ctl_set_value(pctl->ctl, pctl->index, pctl->value.uinteger);
                }
                ALOGE_IF(err < 0, "Failed to set ctl '%s' to %u",
                                        mixer_ctl_get_name(pctl->ctl),
                                        pctl->value.uinteger);
                break;

            case MIXER_CTL_TYPE_BYTE:
                /* byte array */
                vnum = mixer_ctl_get_num_values(pctl->ctl);

                ALOGV("apply ctl '%s' = byte data (%d bytes)",
                                        mixer_ctl_get_name(pctl->ctl),
                                        vnum);

                if ((pctl->index == 0) && (pctl->array_count == vnum)) {
                    err = mixer_ctl_set_array(pctl->ctl, pctl->value.data, pctl->array_count);
                } else {
                    /* read-modify-write */
                    err = mixer_ctl_get_array(pctl->ctl, ctl_data, vnum);
                    if (err >= 0) {
                        memcpy(&ctl_data[pctl->index], pctl->value.data, pctl->array_count);
                        err = mixer_ctl_set_array(pctl->ctl, ctl_data, vnum);
                    }
                }

                ALOGE_IF(err < 0, "Failed to set ctl '%s'",
                                            mixer_ctl_get_name(pctl->ctl));
                break;

            case MIXER_CTL_TYPE_ENUM:
                ALOGV("apply ctl '%s' to '%s'",
                                            mixer_ctl_get_name(pctl->ctl),
                                            pctl->value.name);

                err = mixer_ctl_set_enum_by_string(pctl->ctl, pctl->value.name);

                ALOGE_IF(err < 0, "Failed to set ctl '%s' to '%s'",
                                            mixer_ctl_get_name(pctl->ctl),
                                            pctl->value.name);
                break;

            default:
                break;
        }
    }

    ALOGV("-apply_ctls_l");
}

static void apply_path_l(struct path *path)
{
    ALOGV("+apply_path_l(%p) id=%u", path, path->id);

    apply_ctls_l(path->ctl_array.ctls, path->ctl_array.count);

    ALOGV("-apply_path_l(%p)", path);
}

static void apply_device_path_l(struct device *pdev, struct path *path)
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

    apply_path_l(path);

    ALOGV("-apply_device_path_l(%p)", path);
}

static void apply_paths_by_id_l(struct device *pdev, int first_id,
                                int second_id)
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
        apply_device_path_l(pdev, found_paths[0]);
    }

    if (found_paths[1] != NULL) {
        apply_device_path_l(pdev, found_paths[1]);
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
            apply_paths_by_id_l(pdev, first_id, second_id);
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
            apply_paths_by_id_l(pdev, first_id, second_id);
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

    /* Only apply routes to devices that have changed state on this stream */
    uint32_t enabling = devices & ~s->current_devices;
    uint32_t disabling = ~devices & s->current_devices;

    ALOGV("apply_route(%p) devices=0x%x", stream, devices);

    if (stream_is_input(stream)) {
        devices &= AUDIO_DEVICE_IN_ALL;
        devices |= AUDIO_DEVICE_BIT_IN;
    } else {
        devices &= AUDIO_DEVICE_OUT_ALL;
    }

    pthread_mutex_lock(&cm->lock);

    apply_paths_to_devices_l(cm, disabling, s->disable_path, e_path_id_off);
    apply_paths_to_devices_l(cm, enabling, e_path_id_on, s->enable_path);

    /* Save new set of devices for this stream */
    s->current_devices = devices;

    pthread_mutex_unlock(&cm->lock);
}

uint32_t get_routed_devices( const struct hw_stream *stream )
{
    struct stream *s = (struct stream *)stream;
    return s->current_devices;
}

void rotate_routes( struct config_mgr *cm, int orientation )
{
    /* Route rotation not currently supported */
}

/*********************************************************************
 * Stream control
 *********************************************************************/

int set_hw_volume( const struct hw_stream *stream, float left, float right)
{
    struct stream *s = (struct stream *)stream;
    int pc;
    int ret = -ENOSYS;

    if (s->controls.volume_left.ctl) {
        pc = (int)(left * 100);
        mixer_ctl_set_percent(s->controls.volume_left.ctl,
                                s->controls.volume_left.id,
                                pc);
        ret = 0;
    }
    if (s->controls.volume_right.ctl) {
        pc = (int)(right * 100);
        mixer_ctl_set_percent(s->controls.volume_right.ctl,
                                s->controls.volume_right.id,
                                pc);
        ret = 0;
    }

    ALOGV_IF(ret == 0, "set_hw_volume: L=%f R=%f", left, right);

    return ret;
}

static struct stream *find_named_stream(struct config_mgr *cm,
                                   const char *name)
{
    struct stream *s = cm->named_stream_array.streams;
    int i;

    for (i = cm->named_stream_array.count - 1; i >= 0; --i) {
        if (strcmp(s->name, name) == 0) {
            return s;
        }
        s++;
    }
    return NULL;
}

const struct hw_stream *get_stream(struct config_mgr *cm,
                                   const audio_devices_t devices,
                                   const audio_output_flags_t flags,
                                   const struct audio_config *config )
{
    int i;
    struct stream *s = cm->stream_array.streams;
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
    for (i = cm->stream_array.count - 1; i >= 0; --i) {
        ALOGV("get_stream: require type=%d; try type=%d refcount=%d refmax=%d",
                    type, s[i].info.type, s[i].ref_count, s[i].max_ref_count );
        if (s[i].info.type == type) {
            if (s[i].ref_count < s[i].max_ref_count) {
                ++s[i].ref_count;
                if (s[i].ref_count == 1) {
                    apply_paths_to_global_l(cm, e_path_id_on, s[i].enable_path);
                }
                break;
            }
        }
    }

    pthread_mutex_unlock(&cm->lock);

    if (i >= 0) {
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
    int i;
    struct stream *s;

    ALOGV("+get_named_stream '%s'", name);

    /* Streams can't be deleted so don't need to hold the lock during search */
    s = find_named_stream(cm, name);

    pthread_mutex_lock(&cm->lock);
    if (s != NULL) {
        if (s->ref_count < s->max_ref_count) {
            ++s->ref_count;
            if (s->ref_count == 1) {
                apply_paths_to_global_l(cm, e_path_id_on, s->enable_path);
            }
        } else {
            ALOGV("stream '%s' at maximum refcount %d", name, s->ref_count);
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
                    apply_ctls_l(pcase->ctl_array.ctls, pcase->ctl_array.count);
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
 * Config file parsing
 *
 * To keep this simple we restrict the order that config file entries
 * may appear:
 * - The <mixer> section must always appear first
 * - Paths must be defined before they can be referred to
 *********************************************************************/
static int parse_mixer_start(struct parse_state *state);
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
static int parse_init_start(struct parse_state *state);

static const struct parse_element elem_table[e_elem_count] = {
    [e_elem_ctl] =    {
        .name = "ctl",
        .valid_attribs = BIT(e_attrib_name) | BIT(e_attrib_val) | BIT(e_attrib_index),
        .required_attribs = BIT(e_attrib_name) | BIT(e_attrib_val),
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
                            | BIT(e_elem_usecase),
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

    [e_elem_stream_ctl] =    {
        .name = "ctl",
        .valid_attribs = BIT(e_attrib_name) | BIT(e_attrib_function)
                            | BIT(e_attrib_index),
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
        .end_fn = NULL
        },

    [e_elem_mixer] =    {
        .name = "mixer",
        .valid_attribs = BIT(e_attrib_card),
        .required_attribs = 0,
        .valid_subelem = BIT(e_elem_init),
        .start_fn = parse_mixer_start,
        .end_fn = NULL
        },

    [e_elem_audiohal] =    {
        .name = "audiohal",
        .valid_attribs = 0,
        .required_attribs = 0,
        .valid_subelem = BIT(e_elem_mixer),
        .start_fn = NULL,
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
    [e_attrib_period_count] = {"period_count"}
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

    if (p)
        {
        array->data = p;
        array->max_count = array->count;
        }
}

static void dyn_array_free(struct dyn_array *array)
{
    free(array->data);
}

static struct ctl* new_ctl(struct dyn_array *array, struct mixer_ctl *ctl)
{
    struct ctl *c;

    if (dyn_array_extend(array) < 0) {
        return NULL;
    }

    c = &array->ctls[array->count - 1];
    c->index = INVALID_CTL_INDEX;
    c->ctl = ctl;
    return c;
}

static void compress_ctl(struct ctl *ctl)
{
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
    sc->name = name;
    return sc;
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
    puc->name = name;
    return puc;
}

static void compress_usecase(struct usecase *puc)
{
    dyn_array_fix(&puc->case_array);
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
    s->cm = cm;
    s->enable_path = -1;    /* by default no special path to invoke */
    s->disable_path = -1;
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
    array->path_names[i] = name;
    return i;
}

static struct config_mgr* new_config_mgr()
{
    struct config_mgr* mgr = calloc(1, sizeof(struct config_mgr));
    if (!mgr) {
        return NULL;
    }
    mgr->device_array.elem_size = sizeof(struct device);
    mgr->stream_array.elem_size = sizeof(struct stream);
    mgr->named_stream_array.elem_size = sizeof(struct stream);
    pthread_mutex_init(&mgr->lock, NULL);
    return mgr;
}

static void compress_config_mgr(struct config_mgr *mgr)
{
    dyn_array_fix(&mgr->device_array);
    dyn_array_fix(&mgr->stream_array);
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
    const char *s;

    /* Check if already in array */
    index = find_path_name(state, name);
    if (index >= 0) {
        return index;   /* already exists */
    }

    s = strdup(name);
    if (s == NULL) {
        return -ENOMEM;
    }

    index = new_name(array, s);
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

static int string_to_uint(uint32_t *result, const char *str)
{
    char *endptr;
    unsigned long int v;

    if (!str) {
        return -ENOENT;
    }

    /* return error if not a valid decimal or hex number */
    v = strtoul(str, &endptr, 0);
    if ((endptr[0] == '\0') && (endptr != str) && (v <= INT_MAX)) {
        *result = (uint32_t)v;
        return 0;
    } else {
        ALOGE("'%s' not a valid number", str);
        return -EINVAL;
    }
}

static int attrib_to_uint(uint32_t *result, struct parse_state *state,
                                enum attrib_index index)
{
    const char *str = state->attribs.value[index];
    return string_to_uint(result, str);
}

static int make_byte_array(struct parse_state *state, struct ctl *c)
{
    char *str = strdup(state->attribs.value[e_attrib_val]);
    const unsigned int vnum = mixer_ctl_get_num_values(c->ctl);
    uint8_t *bytes;
    int count;
    char *p;
    uint32_t v;
    int ret;

    if (!str) {
        ret = -ENOMEM;
        goto fail;
    }

    if (vnum > BYTE_ARRAY_MAX_SIZE) {
        ALOGE("Byte array control too big(%u)", vnum);
        return -EINVAL;
    }

    if (c->index >= vnum) {
        ALOGE("Control index out of range(%u>%u)", c->index, vnum);
        ret = -EINVAL;
        goto fail;
    }

    /* get number of entries in value string by counting commas */
    p = strtok(str, ",");
    for (count = 0; p != NULL; count++) {
        p = strtok(NULL, ",");
    }

    if ((c->index + count) > vnum) {
        ALOGE("Array overflows control (%u+%u > %u)",
                    c->index, count, vnum);
        ret = -EINVAL;
        goto fail;
    }
    c->array_count = count;

    bytes = malloc(count);
    if (!bytes) {
        ALOGE("Out of memory for control data");
        ret = -ENOMEM;
        goto fail;
    }
    c->value.data = bytes;

    strcpy(str,state->attribs.value[e_attrib_val]);

    for (p = strtok(str, ","); p != NULL;) {
        ret = string_to_uint(&v, p);
        if (ret != 0) {
            goto fail;
        }
        ALOGE_IF(v > 0xFF, "Byte out of range");

        *bytes++ = (uint8_t)v;
        p = strtok(NULL, ",");
    }

    free(str);
    return 0;

fail:
    free((void *)c->value.data);
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
    const char *name = state->attribs.value[e_attrib_name];
    const char *index = state->attribs.value[e_attrib_index];
    struct dyn_array *array;
    struct ctl *c;
    struct mixer_ctl *ctl;
    enum mixer_ctl_type ctl_type;
    int ret;

    if (state->current.path) {
        ALOGV("parse_ctl_start:path ctl");
        array = &state->current.path->ctl_array;
    } else {
        ALOGV("parse_ctl_start:case ctl");
        array = &state->current.scase->ctl_array;
    }

    ctl = mixer_get_ctl_by_name(state->cm->mixer, name);
    if (!ctl) {
        ALOGE("Control '%s' not found", name);
        return -EINVAL;
    }

    c = new_ctl(array, ctl);
    if (c == NULL) {
        return -ENOMEM;
    }

    if (attrib_to_uint(&c->index, state, e_attrib_index) == -EINVAL) {
        ALOGE("Invalid ctl index");
        return -EINVAL;
    }

    ctl_type = mixer_ctl_get_type(ctl);
    switch(ctl_type)
        {
        case MIXER_CTL_TYPE_BYTE:
            if (c->index == INVALID_CTL_INDEX) {
                c->index = 0;
            }
            ret = make_byte_array(state, c);
            if (ret != 0) {
                return ret;
            }
            ALOGV("Added ctl '%s' byte array", name);
            break;

        case MIXER_CTL_TYPE_BOOL:
        case MIXER_CTL_TYPE_INT:
            if (attrib_to_uint(&c->value.uinteger, state, e_attrib_val)
                                                            == -EINVAL) {
                return -EINVAL;
            }
            /* This log statement is just to aid to debugging */
            ALOGE_IF((ctl_type == MIXER_CTL_TYPE_BOOL)
                        && (c->value.uinteger > 1),
                        "WARNING: Illegal value for bool control");
            ALOGV("Added ctl '%s' value %u", name, c->value.uinteger);
            break;

        case MIXER_CTL_TYPE_ENUM:
            c->value.name = strdup(state->attribs.value[e_attrib_val]);
            if(!c->value.name) {
                return -ENOMEM;
            }
            ALOGV("Added ctl '%s' value '%s'", name, c->value.name);
            break;

        case MIXER_CTL_TYPE_IEC958:
        case MIXER_CTL_TYPE_INT64:
        case MIXER_CTL_TYPE_UNKNOWN:
        default:
            ALOGE("Mixer control '%s' has unsupported type", name);
            return -EINVAL;
        };

    return 0;
}

static int parse_init_start(struct parse_state *state)
{
    /* The <init> section inside <mixer> is really just a
     * path that we only use once. We re-use the parsing of
     * <ctl> entries by creating a temporary path which we
     * apply at the end of parsing and then discard
     */
    state->current.path = &state->init_path;

    ALOGV("Added init path");
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
    const char *name = strdup(state->attribs.value[e_attrib_name]);
    struct usecase *puc = state->current.usecase;
    struct dyn_array *array = &puc->case_array;
    struct scase *sc;

    if (!name) {
        return -ENOMEM;
    }

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
    const char *name = strdup(state->attribs.value[e_attrib_name]);
    struct dyn_array *array = &state->current.stream->usecase_array;
    struct usecase *puc;

    if (!name) {
        return -ENOMEM;
    }

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
    uint idx_val = 0;

    ctl = mixer_get_ctl_by_name(state->cm->mixer, name);
    if (!ctl) {
        ALOGE("Control '%s' not found", name);
        return -EINVAL;
    }

    if (index != NULL) {
        if (attrib_to_uint(&idx_val, state, e_attrib_index) == -EINVAL) {
            return -EINVAL;
        }
    }

    if (0 == strcmp(function, "leftvol")) {
        ALOGE_IF(state->current.stream->controls.volume_left.ctl,
                                "Left volume control specified again");
        state->current.stream->controls.volume_left.ctl = ctl;
        state->current.stream->controls.volume_left.id = idx_val;

    } else if (0 == strcmp(function, "rightvol")) {
        ALOGE_IF(state->current.stream->controls.volume_right.ctl,
                                "Right volume control specified again");
        state->current.stream->controls.volume_right.ctl = ctl;
        state->current.stream->controls.volume_right.id = idx_val;

    } else {
        ALOGE("'%s' is not a valid control function", function);
        return -EINVAL;
    }

    ALOGV("Added control '%s' function '%s'", name, function);

    return 0;
}

static int parse_stream_start(struct parse_state *state)
{
    const char *type = state->attribs.value[e_attrib_type];
    const char *dir = state->attribs.value[e_attrib_dir];
    const char *name = state->attribs.value[e_attrib_name];
    struct dyn_array *array;
    bool out;
    uint32_t card;
    uint32_t device;
    uint32_t maxref = INT_MAX;
    struct stream *s;

    if (name != NULL) {
        name = strdup(name);
        if (name == NULL) {
            return -ENOMEM;
        }
    }

    if (name != NULL) {
        if (find_named_stream(state->cm, name) != NULL) {
            ALOGE("Stream '%s' already declared", name);
            return -EINVAL;
        }
        array = &state->cm->named_stream_array;
    } else {
        array = &state->cm->stream_array;
    }

    s = new_stream(array, state->cm);
    if (s == NULL) {
        return -ENOMEM;
    }

    if (0 == strcmp(type, "hw")) {
        if (name == NULL) {
            ALOGE("Anonymous stream cannot be type hw");
            return -EINVAL;
        }
        s->info.type = e_stream_hardware;
    } else {
        if (dir == NULL) {
            ALOGE("dir tag missing");
            return -EINVAL;
        }

        if (0 == strcmp(dir, "out")) {
            out = true;
        } else if (0 == strcmp(dir, "in")) {
            out = false;
        } else {
            ALOGE("'%s' is not a valid direction", dir);
            return -EINVAL;
        }

        if (0 == strcmp(type, "pcm")) {
            s->info.type = out ? e_stream_out_pcm : e_stream_in_pcm;
            card = PCM_CARD_DEFAULT;
            device = PCM_DEVICE_DEFAULT;
        } else if (0 == strcmp(type, "compress")) {
            s->info.type = out ? e_stream_out_compress : e_stream_in_compress;
            card = COMPRESS_CARD_DEFAULT;
            device = COMPRESS_DEVICE_DEFAULT;
        } else {
            ALOGE("'%s' not a valid stream type", type);
            return -EINVAL;
        }
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

    if (attrib_to_uint(&s->info.period_count, state,
                        e_attrib_period_size) == -EINVAL) {
        return -EINVAL;
    }

    s->name = name;
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

static int parse_mixer_start(struct parse_state *state)
{
    uint32_t card = MIXER_CARD_DEFAULT;

    ALOGV("parse_mixer_start");

    if (attrib_to_uint(&card, state, e_attrib_card) == -EINVAL) {
        return -EINVAL;
    }

    ALOGV("Opening mixer card %u", card);

    state->cm->mixer = mixer_open(card);

    if (!state->cm->mixer) {
        ALOGE("Failed to open mixer card %u", card);
        return -EINVAL;
    }

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

    while (!eof && (state->parse_error == 0)) {
        len = fread(state->read_buf, 1, sizeof(state->read_buf), state->file);
        if (ferror(state->file)) {
            ALOGE("I/O error reading config file");
            ret = -EIO;
            break;
        }

        eof = feof(state->file);

        XML_Parse(state->parser, state->read_buf, len, eof);
        if (parse_log_error(state) < 0) {
            ret = -EINVAL;
            break;
        }
    }

    return ret;
}

static int open_config_file(struct parse_state *state)
{
    char name[80];
    char property[PROPERTY_VALUE_MAX];

    property_get("ro.product.device", property, "generic");
    snprintf(name, sizeof(name), "/system/etc/audio.%s.xml", property);

    ALOGV("Reading configuration from %s\n", name);
    state->file = fopen(name, "r");
    if (state->file) {
        return 0;
    } else {
        ALOGE_IF(!state->file, "Failed to open config file %s", name);
        return -ENOSYS;
    }
}

static void cleanup_parser(struct parse_state *state)
{
    if (state) {
        path_names_free(state);

        dyn_array_free(&state->init_path.ctl_array);

        if (state->parser) {
            XML_ParserFree(state->parser);
        }

        if (state->file) {
            fclose(state->file);
        }

        free(state);
    }
}

static int parse_config_file(struct config_mgr *cm)
{
    struct parse_state *state;
    int ret = 0;

    state = calloc(1, sizeof(struct parse_state));
    if (!state) {
        return -ENOMEM;
    }
    state->cm = cm;
    state->path_name_array.elem_size = sizeof(const char *);
    state->init_path.ctl_array.elem_size = sizeof(struct ctl);

    /* "off" and "on" are pre-defined path names */
    ret = add_path_name(state, predefined_path_name_table[0]);
    if (ret < 0) {
        goto fail;
    }
    ret = add_path_name(state, predefined_path_name_table[1]);
    if (ret < 0) {
        goto fail;
    }

    ret = open_config_file(state);
    if (ret == 0) {
        ret = -ENOMEM;
        state->parser = XML_ParserCreate(NULL);
        if (state->parser) {
            XML_SetUserData(state->parser, state);
            XML_SetElementHandler(state->parser, parse_section_start, parse_section_end);
            ret = do_parse(state);
        }
    }

    if (ret >= 0) {
        /* Initialize the mixer by applying the <init> path */
        /* No need to take mutex during initialization */
        apply_path_l(&state->init_path);
    }

fail:
    cleanup_parser(state);
    return ret;
}

/*********************************************************************
 * Initialization
 *********************************************************************/

struct config_mgr *init_audio_config()
{
    struct stream *streams;
    int ret;

    struct config_mgr* mgr = new_config_mgr();

    if (0 != parse_config_file(mgr)) {
        free(mgr);
        return NULL;
    }

    /* Free unused memory in the device and stream arrays */
    compress_config_mgr(mgr);

    return mgr;
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
        }
    }
}

void free_audio_config( struct config_mgr *cm )
{
    struct dyn_array *path_array, *ctl_array, *stream_array;
    int dev_idx, path_idx, ctl_idx, stream_idx;

    if (cm) {
        /* Free all devices */
        for (dev_idx = cm->device_array.count - 1; dev_idx >= 0; --dev_idx) {
            /* Free all paths in device */
            path_array = &cm->device_array.devices[dev_idx].path_array;
            for (path_idx = path_array->count - 1; path_idx >= 0; --path_idx) {
                /* Free all ctls in path */
                ctl_array = &path_array->paths[path_idx].ctl_array;
                for (ctl_idx = ctl_array->count - 1; ctl_idx >= 0; --ctl_idx) {
                    if (ctl_array->ctls[ctl_idx].value.name) {
                        free((void*)ctl_array->ctls[ctl_idx].value.name);
                        ctl_array->ctls[ctl_idx].value.name = NULL;
                    }
                    if (ctl_array->ctls[ctl_idx].value.data) {
                        free((void*)ctl_array->ctls[ctl_idx].value.data);
                        ctl_array->ctls[ctl_idx].value.data = NULL;
                    }
                }
                dyn_array_free(ctl_array);
            }

            dyn_array_free(path_array);
        }
        dyn_array_free(&cm->device_array);

        stream_array = &cm->stream_array;
        for(stream_idx = stream_array->count - 1; stream_idx >= 0; --stream_idx) {
            free_usecases(&stream_array->streams[stream_idx]);
        }
        dyn_array_free(&cm->stream_array);

        stream_array = &cm->named_stream_array;
        for(stream_idx = stream_array->count - 1; stream_idx >= 0; --stream_idx) {
            free_usecases(&stream_array->streams[stream_idx]);
        }
        dyn_array_free(&cm->stream_array);

        if (cm->mixer) {
            mixer_close(cm->mixer);
        }
        pthread_mutex_destroy(&cm->lock);
        free(cm);
    }
}

