======================================================================
TINYHAL README
Copyright (C) 2015, 2019 Cirrus Logic, Inc. and
                         Cirrus Logic International Semiconductor Ltd.
                         All rights reserved.
======================================================================

TinyHAL is an audio HAL for Android systems aimed at providing an improved
basis for doing audio system integration in the Android application layer.

Traditionally the use-case logic has been hardcoded into HALs, which means that
to edit use-cases or add new ones the code has to be changed and rebuilt.
Typically any configuration files have been restricted to listing the ALSA
control settings to be applied for a use-case but have no control over the
actual use-case decisions.

A major aim of TinyHAL is for the configuration file to also control the logic
of decision making about which use-cases to apply.

Key features include:

- Based on top of tinyalsa and tinycompress

- Configuration via an XML file

- As much configuration as possible done via a text configuration file,
    minimizing the need for source code modifications

- Custom streams can be defined to represent extra hardware audio routes
    outside of the standard Android playback and record - for example specific
    route handling for baseband, FM radio, HDMI, etc.

- One-off boot-time configuration of mixer controls

- Per-device mixer control settings for enabling and disabling that audio device

- Connecting or disconnecting an audio device (speaker, headset, mic) on a
    stream can execute a different set of mixer settings for every connected
    {stream, device} combination.

- Custom use-cases allow creation of mixer control setups that will be executed
    on events unique to the product, without having to hardcode knowledge of
    that use-case into TinyHAL.

- Support for various types of ALSA control

- Support for writing values into binary ALSA controls

- Configuration file description is abstracted from Android’s audio definitions
    to avoid being constrained by what Android knows about. This allows greater
    flexibility in handling custom cases than would be possible if limited to
    Android’s built-in view of the audio.

- Support for compressed offloaded streams (introduced in KitKat)

- Use-case manager (configuration file and ALSA control handling) is a separate
    library so can be re-used with different HAL implementations

- Support for auto-selecting which configuration file to use, based on reading
    an ID file or pseudo-file

==================
BUILD DEPENDENCIES
==================

Requires:
 - tinyalsa
 - tinycompress
 - expat

TinyHAL can build against the upstream (mainline) versions of tinyalsa and
tinycompress, or the versions shipped with Android. When building against
the Android versions, see section below "BUILDING AGAINST OLD LIBRARY VERSIONS".

The upstream versions are available from:
tinyalsa: https://github.com/tinyalsa
tinycompress: http://git.alsa-project.org/?p=tinycompress.git;a=summary

=========================================================
BUILDING AGAINST OLD LIBRARY VERSIONS (including Android)
=========================================================
The version of tinyalsa and tinycompress shipped with Android source is not
usually the latest mainline so some functionality might be missing or different.
By default TinyHAL builds against the upstream versions.

There are build flags that you can define to disable or modify features if
you are building against an older version of a library:

TINYALSA_NO_ADD_NEW_CTRLS
    define if your version of tinyalsa does not have mixer_add_new_ctls()

TINYALSA_NO_CTL_GET_ID
    define if your version of tinyalsa does not have mixer_ctl_get_id()

TINYCOMPRESS_TSTAMP_IS_LONG
    define if your version of tinycompress takes an unsigned long* argument to
    compress_get_tstamp()

=================
HOW TINYHAL WORKS
=================

TinyHAL is based on the idea of audio streams connected to one or more "devices"
(in Android terminology, that is speakers, microphones, or other transducers)

- A "stream" is a logical representation of one audio path.

- A device is a logical representation of some form of audio source or sink

- streams correspond closely to Android's Audio HAL audio_stream_out and
    audio_stream in, but can also be used more generically for hardware audio
    paths that are not seen by AudioFlinger, for example baseband modem-to-codec

- Groups of ALSA settings will be invoked when:
    a stream is opened
    a stream is closed
    a device is enabled by connecting it to a stream
    a device is disabled by disconnecting it from all streams
    a device is connected to a stream
    a device is disconnected from a stream
    a custom use-case is executed on a stream

    Each device can have multiple sets of ALSA control settings, each set
    identified by a user-defined label. Every stream defines which label to
    invoke on connected devices when they are connected to that stream.

    For example if the PCM playback stream defines that the label for enable
    is "pcm_out_enable", when any device is connected to this stream if that
    device has a "pcm_out_enable" ALSA control list those settings will be
    invoked.

    Each device also has two (optional) pre-defined labels "on" and "off" for
    a list of ALSA settings to be invoked when the device is activated or
    deactivated. The "on" case will be invoked when a device is first connected
    to a stream (that is, its state changed from unused to in-use). The "off"
    is invoked when the device is disconnected from the last stream (its state
    changes from in-use to unused.)

    This is explained in more detail, with example configuration lines, in the
    file audio.example.xml.

-----------------
Pseudo-logic view
-----------------
In general the control logic that TinyHAL uses to invoke ALSA settings works
like this:

Stream opened=>
    if (stream defines "enable path" label)
        if ("global" device defines this label)
            invoke ALSA settings from "global" stream label

Stream connected to device=>
    if (device currently not in use)
        if (device defines "on" label)
            invoke ALSA settings from device "on" label

    if (stream defines "enable path" label)
        if (device defines this label)
            invoke ALSA settings from device label

Stream custom use-case issued=>
    if (stream defines this use-case)
        if (use-case list includes the given value)
            invoke ALSA controls listed for this use-case value

Stream disconnected from device=>
    if (stream defines "disable path" label)
        if (device defines this label)
            invoke ALSA settings from device label

    if (device not connected to any other streams)
        if (device defines "off" label)
            invoke ALSA settings from device "off" label

Stream closed=>
    if (deviced currently connected)
        disconnect each device

    if (disable path label defined)
        if ("global" stream defines this label)
            invoke ALSA settings from "global" stream label

===========================
Auto-detecting the hardware
===========================
The simplest mode of auto-detection is to specify the ALSA card by name instead
of by number. Tinyhal will search all /proc/asound/card*/id files for one that
matches the given name. This abstracts from the actual card number, which isn't
guaranteed to be the same between boots or across different devices using the
same ALSA sound card.

A more sophisticated mechanism is to auto-select which configuration file to
load. This is based on having a file or pseudo-file that contains as its first
line a string identifying the hardware. TinyHAL doesn't care how this file is
generated. A typical use is to point it at the pseudo-file
/sys/class/sound/card0/id, which contains a single line giving the name of
the audio card (as defined by the kernel audio card driver or codec driver.)
But it could just as well be any readable pseudo-file, or a real file created
on startup by the init script.

The first line from the given file is stripped of leading and trailing
whitespace and then compared against a list of strings provided in the
initial XML file that TinyHAL loads on startup. For each string an alternate
XML filename is given.

If one of these strings matches, TinyHAL will abandon processing of the current
XML file and instead start processing the alternate XML file. If none of the
strings match, TinyHAL will continue to process the current XML file.

The syntax for this is shown in audio.example.xml (see the codec_probe block)
