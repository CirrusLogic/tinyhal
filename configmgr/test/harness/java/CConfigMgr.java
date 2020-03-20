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

package com.cirrus.tinyhal.test.thcm;

import java.lang.String;
import java.lang.Throwable;
import java.util.Map;

public class CConfigMgr
{
    private final long mNativeMgrPtr = 0;

    static {
        System.loadLibrary("com.cirrus.tinyhal.test.thcm_jni");
    }

    public CConfigMgr()
    {
        native_setup();
    }

    public static final long AUDIO_DEVICE_NONE                          = 0x0L;
    public static final long AUDIO_DEVICE_BIT_IN                        = 0x80000000L;
    public static final long AUDIO_DEVICE_BIT_DEFAULT                   = 0x40000000L;
    /* output devices */
    public static final long AUDIO_DEVICE_OUT_EARPIECE                  = 0x1L;
    public static final long AUDIO_DEVICE_OUT_SPEAKER                   = 0x2L;
    public static final long AUDIO_DEVICE_OUT_WIRED_HEADSET             = 0x4L;
    public static final long AUDIO_DEVICE_OUT_WIRED_HEADPHONE           = 0x8L;
    public static final long AUDIO_DEVICE_OUT_BLUETOOTH_SCO             = 0x10L;
    public static final long AUDIO_DEVICE_OUT_BLUETOOTH_SCO_HEADSET     = 0x20L;
    public static final long AUDIO_DEVICE_OUT_BLUETOOTH_SCO_CARKIT      = 0x40L;
    public static final long AUDIO_DEVICE_OUT_BLUETOOTH_A2DP            = 0x80L;
    public static final long AUDIO_DEVICE_OUT_BLUETOOTH_A2DP_HEADPHONES = 0x100L;
    public static final long AUDIO_DEVICE_OUT_BLUETOOTH_A2DP_SPEAKER    = 0x200L;
    public static final long AUDIO_DEVICE_OUT_AUX_DIGITAL               = 0x400L;
    public static final long AUDIO_DEVICE_OUT_HDMI                      = AUDIO_DEVICE_OUT_AUX_DIGITAL;
    public static final long AUDIO_DEVICE_OUT_ANLG_DOCK_HEADSET         = 0x800L;
    public static final long AUDIO_DEVICE_OUT_DGTL_DOCK_HEADSET         = 0x1000L;
    public static final long AUDIO_DEVICE_OUT_USB_ACCESSORY             = 0x2000L;
    public static final long AUDIO_DEVICE_OUT_USB_DEVICE                = 0x4000L;
    public static final long AUDIO_DEVICE_OUT_REMOTE_SUBMIX             = 0x8000L;
    public static final long AUDIO_DEVICE_OUT_TELEPHONY_TX              = 0x10000L;
    public static final long AUDIO_DEVICE_OUT_LINE                      = 0x20000L;
    public static final long AUDIO_DEVICE_OUT_HDMI_ARC                  = 0x40000L;
    public static final long AUDIO_DEVICE_OUT_SPDIF                     = 0x80000L;
    public static final long AUDIO_DEVICE_OUT_FM                        = 0x100000L;
    public static final long AUDIO_DEVICE_OUT_AUX_LINE                  = 0x200000L;
    public static final long AUDIO_DEVICE_OUT_SPEAKER_SAFE              = 0x400000L;
    public static final long AUDIO_DEVICE_OUT_IP                        = 0x800000L;
    public static final long AUDIO_DEVICE_OUT_BUS                       = 0x1000000L;
    public static final long AUDIO_DEVICE_OUT_DEFAULT                   = AUDIO_DEVICE_BIT_DEFAULT;
    public static final long AUDIO_DEVICE_OUT_ALL = (AUDIO_DEVICE_OUT_EARPIECE |
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
                                 AUDIO_DEVICE_OUT_DEFAULT);
    public static final long AUDIO_DEVICE_OUT_ALL_A2DP = (
                                 AUDIO_DEVICE_OUT_BLUETOOTH_A2DP |
                                 AUDIO_DEVICE_OUT_BLUETOOTH_A2DP_HEADPHONES |
                                 AUDIO_DEVICE_OUT_BLUETOOTH_A2DP_SPEAKER);
    public static final long AUDIO_DEVICE_OUT_ALL_SCO  = (
                                 AUDIO_DEVICE_OUT_BLUETOOTH_SCO |
                                 AUDIO_DEVICE_OUT_BLUETOOTH_SCO_HEADSET |
                                 AUDIO_DEVICE_OUT_BLUETOOTH_SCO_CARKIT);
    public static final long AUDIO_DEVICE_OUT_ALL_USB  = (
                                 AUDIO_DEVICE_OUT_USB_ACCESSORY |
                                 AUDIO_DEVICE_OUT_USB_DEVICE);

    public static final long AUDIO_DEVICE_IN_COMMUNICATION         = AUDIO_DEVICE_BIT_IN | 0x1L;
    public static final long AUDIO_DEVICE_IN_AMBIENT               = AUDIO_DEVICE_BIT_IN | 0x2L;
    public static final long AUDIO_DEVICE_IN_BUILTIN_MIC           = AUDIO_DEVICE_BIT_IN | 0x4L;
    public static final long AUDIO_DEVICE_IN_BLUETOOTH_SCO_HEADSET = AUDIO_DEVICE_BIT_IN | 0x8L;
    public static final long AUDIO_DEVICE_IN_WIRED_HEADSET         = AUDIO_DEVICE_BIT_IN | 0x10L;
    public static final long AUDIO_DEVICE_IN_AUX_DIGITAL           = AUDIO_DEVICE_BIT_IN | 0x20L;
    public static final long AUDIO_DEVICE_IN_HDMI                  = AUDIO_DEVICE_IN_AUX_DIGITAL;
    public static final long AUDIO_DEVICE_IN_VOICE_CALL            = AUDIO_DEVICE_BIT_IN | 0x40L;
    public static final long AUDIO_DEVICE_IN_TELEPHONY_RX          = AUDIO_DEVICE_IN_VOICE_CALL;
    public static final long AUDIO_DEVICE_IN_BACK_MIC              = AUDIO_DEVICE_BIT_IN | 0x80L;
    public static final long AUDIO_DEVICE_IN_REMOTE_SUBMIX         = AUDIO_DEVICE_BIT_IN | 0x100L;
    public static final long AUDIO_DEVICE_IN_ANLG_DOCK_HEADSET     = AUDIO_DEVICE_BIT_IN | 0x200L;
    public static final long AUDIO_DEVICE_IN_DGTL_DOCK_HEADSET     = AUDIO_DEVICE_BIT_IN | 0x400L;
    public static final long AUDIO_DEVICE_IN_USB_ACCESSORY         = AUDIO_DEVICE_BIT_IN | 0x800L;
    public static final long AUDIO_DEVICE_IN_USB_DEVICE            = AUDIO_DEVICE_BIT_IN | 0x1000L;
    public static final long AUDIO_DEVICE_IN_FM_TUNER              = AUDIO_DEVICE_BIT_IN | 0x2000L;
    public static final long AUDIO_DEVICE_IN_TV_TUNER              = AUDIO_DEVICE_BIT_IN | 0x4000L;
    public static final long AUDIO_DEVICE_IN_LINE                  = AUDIO_DEVICE_BIT_IN | 0x8000L;
    public static final long AUDIO_DEVICE_IN_SPDIF                 = AUDIO_DEVICE_BIT_IN | 0x10000L;
    public static final long AUDIO_DEVICE_IN_BLUETOOTH_A2DP        = AUDIO_DEVICE_BIT_IN | 0x20000L;
    public static final long AUDIO_DEVICE_IN_LOOPBACK              = AUDIO_DEVICE_BIT_IN | 0x40000L;
    public static final long AUDIO_DEVICE_IN_IP                    = AUDIO_DEVICE_BIT_IN | 0x80000L;
    public static final long AUDIO_DEVICE_IN_BUS                   = AUDIO_DEVICE_BIT_IN | 0x100000L;
    public static final long AUDIO_DEVICE_IN_DEFAULT               = AUDIO_DEVICE_BIT_IN | AUDIO_DEVICE_BIT_DEFAULT;

    public static final long AUDIO_DEVICE_IN_ALL = (
                               AUDIO_DEVICE_IN_COMMUNICATION |
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
                               AUDIO_DEVICE_IN_DEFAULT);
    public static final long AUDIO_DEVICE_IN_ALL_SCO = AUDIO_DEVICE_IN_BLUETOOTH_SCO_HEADSET;
    public static final long AUDIO_DEVICE_IN_ALL_USB  = (
                                AUDIO_DEVICE_IN_USB_ACCESSORY |
                                AUDIO_DEVICE_IN_USB_DEVICE);

    public static final long AUDIO_CHANNEL_NONE             = 0x0;
    public static final long AUDIO_CHANNEL_INVALID          = 0xC0000000;

    public static final long AUDIO_CHANNEL_OUT_FRONT_LEFT   = 0x1;
    public static final long AUDIO_CHANNEL_OUT_FRONT_RIGHT  = 0x2;
    public static final long AUDIO_CHANNEL_OUT_FRONT_CENTER = 0x4;
    public static final long AUDIO_CHANNEL_IN_LEFT          = 0x4;
    public static final long AUDIO_CHANNEL_IN_RIGHT         = 0x8;
    public static final long AUDIO_CHANNEL_IN_FRONT         = 0x10;
    public static final long AUDIO_CHANNEL_IN_BACK          = 0x20;

    public static final long AUDIO_FORMAT_INVALID           = 0xFFFFFFFF;
    public static final long AUDIO_FORMAT_DEFAULT           = 0;
    public static final long AUDIO_FORMAT_PCM               = 0x00000000;
    public static final long AUDIO_FORMAT_MP3               = 0x01000000;

    public static final String[] OUTPUT_DEVICES = {
        "speaker",
        "earpiece",
        "headset",
        "headphone",
        "sco",
        "a2dp",
        "usb"
    };

    public static final String[] INPUT_DEVICES = {
        "headset_in",
        "sco_in",
        "mic",
        "back mic",
        "voice",
        "aux"
    };

    public static long deviceFromName(String name)
    {
        if (name.equals("speaker"))    return AUDIO_DEVICE_OUT_SPEAKER;
        if (name.equals("earpiece"))   return AUDIO_DEVICE_OUT_EARPIECE;
        if (name.equals("headset"))    return AUDIO_DEVICE_OUT_WIRED_HEADSET;
        if (name.equals("headset_in")) return AUDIO_DEVICE_IN_WIRED_HEADSET;
        if (name.equals("headphone"))  return AUDIO_DEVICE_OUT_WIRED_HEADPHONE;
        if (name.equals("sco"))        return AUDIO_DEVICE_OUT_ALL_SCO;
        if (name.equals("sco_in"))     return AUDIO_DEVICE_IN_ALL_SCO;
        if (name.equals("a2dp"))       return AUDIO_DEVICE_OUT_ALL_A2DP;
        if (name.equals("usb"))        return AUDIO_DEVICE_OUT_ALL_USB;
        if (name.equals("mic"))        return AUDIO_DEVICE_IN_BUILTIN_MIC;
        if (name.equals("back mic"))   return AUDIO_DEVICE_IN_BACK_MIC;
        if (name.equals("voice"))      return AUDIO_DEVICE_IN_VOICE_CALL;
        if (name.equals("aux"))        return AUDIO_DEVICE_IN_AUX_DIGITAL;

        return 0;
    }

    public static class AudioConfig {
        public int sample_rate;
        public long channel_mask;
        public long format;
    };

    private native final void native_setup();

    public native final int init_audio_config(String config_file_name);
    public native final int free_audio_config();
    public native final long get_supported_input_devices();
    public native final long get_supported_output_devices();

    public native final long get_stream(long devices,
                                        long flags,
                                        AudioConfig config);
    public native final long get_named_stream(String name);
    public native final int release_stream(long stream);

    public native final int apply_use_case(long stream,
                                           String setting,
                                           String casename);

    public native final void apply_route(long stream, long devices);

    public native final int set_hw_volume(long stream, int left_pc, int right_pc);
};
