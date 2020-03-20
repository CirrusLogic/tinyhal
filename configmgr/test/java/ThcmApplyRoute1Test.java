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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.Integer;
import java.lang.String;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.junit.runner.RunWith;

import com.cirrus.tinyhal.test.thcm.CAlsaMock;
import com.cirrus.tinyhal.test.thcm.CConfigMgr;
import com.cirrus.tinyhal.test.thcm.ThcmPlatform;

/**
 * Test apply_route() invoking the &lt;path&gt; elements for a single
 * &lt;stream&gt;.
 * Tests that &lt;enable&gt; and &lt;disable&gt; in &lt;stream&gt; elements
 * invoke the correct &lt;path&gt; elements for apply_route() calls. This test
 * is parameterized to run various combinations of stream type and device type.
 * The testing assumes that &lt;ctl&gt; elements execute correctly.
 */
@RunWith(Parameterized.class)
public class ThcmApplyRoute1Test
{
    private static final String[] TEST_CONTROLS = {
        // PCM in stream
        "SwitchP1", "SwitchP2", "SwitchP3", "SwitchP4",
        // PCM out stream
        "SwitchP5", "SwitchP6", "SwitchP7", "SwitchP8",
        // Compressed in stream
        "SwitchC1", "SwitchC2", "SwitchC3", "SwitchC4",
        // Compressed out stream
        "SwitchC5", "SwitchC6", "SwitchC7", "SwitchC8",
        // Named out stream
        "SwitchN1", "SwitchN2", "SwitchN3", "SwitchN4",
        // Named in stream
        "SwitchN5", "SwitchN6", "SwitchN7", "SwitchN8",
        // Controls for paths that should never be invoked
        "SwitchX1", "SwitchX2", "SwitchX3", "SwitchX4",
        "SwitchX5", "SwitchX6", "SwitchX7", "SwitchX8"
    };

    private static final String PCM_STREAM_TYPE = "pcm";
    private static final String COMPRESSED_STREAM_TYPE = "compressed";
    private static final String HW_STREAM_TYPE = "hw";

    private static final File sWorkFilesPath = ThcmPlatform.workFilesPath();
    private static final File sControlsFile = new File(sWorkFilesPath, "thcm_apply_route1_controls.csv");
    private static final File sXmlFile = new File(sWorkFilesPath, "thcm_apply_route1_config.xml");

    private CAlsaMock mAlsaMock = new CAlsaMock();
    private CConfigMgr mConfigMgr = new CConfigMgr();

    private String mTestStreamType;
    private long[] mTestDevices;
    private long mEnableOnlyDevice;
    private long mDisableOnlyDevice;
    private long mNoPathsDevice;
    private long mNoneDevice;
    private String[] mTestControls;
    private String mEnableOnlyControl;
    private String mDisableOnlyControl;

    @Parameterized.Parameters
    public static Collection parameters() {
        return Arrays.asList(new String[][] {
            // stream type, device 1, device2, enable-only device, disable-only device,
            //              device1 control, device2 control, enable-only control, disable-only control

            // outputs
            { PCM_STREAM_TYPE, "speaker", "earpiece", "headset", "headphone", "sco",
                               "SwitchP1", "SwitchP2", "SwitchP3", "SwitchP4"
            },
            { COMPRESSED_STREAM_TYPE, "speaker", "earpiece", "headset", "headphone", "sco",
                                      "SwitchC1", "SwitchC2", "SwitchC3", "SwitchC4"
            },
            { "nout", "speaker", "earpiece", "headset", "headphone", "sco",
                      "SwitchN1", "SwitchN2", "SwitchN3", "SwitchN4"
            },

            // inputs
            { PCM_STREAM_TYPE, "mic", "back mic", "voice", "aux", "sco_in",
                               "SwitchP5", "SwitchP6", "SwitchP7", "SwitchP8"
            },
            { COMPRESSED_STREAM_TYPE, "mic", "back mic", "voice", "aux", "sco_in",
                                      "SwitchC5", "SwitchC6", "SwitchC7", "SwitchC8"
            },
            { "nin", "mic", "back mic", "voice", "aux", "sco_in",
                     "SwitchN5", "SwitchN6", "SwitchN7", "SwitchN8"
            }
        });
    }

    public ThcmApplyRoute1Test(String streamType,
                               String device1, String device2,
                               String enableOnlyDevice,
                               String disableOnlyDevice,
                               String noPathsDevice,
                               String control1, String control2,
                               String enableOnlyControl,
                               String disableOnlyControl)
    {
        mTestStreamType = streamType;
        mTestDevices = new long[2];
        mTestDevices[0] = CConfigMgr.deviceFromName(device1);
        mTestDevices[1] = CConfigMgr.deviceFromName(device2);
        mEnableOnlyDevice = CConfigMgr.deviceFromName(enableOnlyDevice);
        mDisableOnlyDevice = CConfigMgr.deviceFromName(disableOnlyDevice);
        mNoPathsDevice = CConfigMgr.deviceFromName(noPathsDevice);
        mTestControls = new String[] {control1, control2};
        mEnableOnlyControl = enableOnlyControl;
        mDisableOnlyControl = disableOnlyControl;

        // AUDIO_DEVICE_NONE reports as an output, so add the input bit
        // if this test pass is for input devices
        mNoneDevice = CConfigMgr.AUDIO_DEVICE_NONE |
                      (mTestDevices[0] & (CConfigMgr.AUDIO_DEVICE_BIT_IN));
    }

    @BeforeClass
    public static void setUpClass() throws IOException
    {
        createAlsaControlsFile();
        createXmlFile();
    }

    @AfterClass
    public static void tearDownClass()
    {
        if (sXmlFile.exists()) {
            sXmlFile.delete();
        }

        if (sControlsFile.exists()) {
            sControlsFile.delete();
        }
    }

    @Before
    public void setUp()
    {
        assertTrue("Illegal mTestDevices[0]", mTestDevices[0] > 0);
        assertTrue("Illegal mTestDevices[1]", mTestDevices[1] > 0);
        assertTrue("Illegal mEnableOnlyDevice", mEnableOnlyDevice > 0);
        assertTrue("Illegal mDisableOnlyDevice", mDisableOnlyDevice > 0);
        assertTrue("Illegal mNoPathsDevice", mNoPathsDevice > 0);

        assertEquals("Failed to create CAlsaMock",
                     0,
                     mAlsaMock.createMixer(sControlsFile.toPath().toString()));

        assertEquals("Failed to open CConfigMgr",
                     0,
                     mConfigMgr.init_audio_config(sXmlFile.toPath().toString()));

        for (String name : TEST_CONTROLS) {
            assertEquals(name + " not initially 0", 0, mAlsaMock.getBool(name, 0));
            assertFalse(name + " marked changed", mAlsaMock.isChanged(name));
        }
    }

    @After
    public void tearDown()
    {
        if (mConfigMgr != null) {
            mConfigMgr.free_audio_config();
            mConfigMgr = null;
        }

        if (mAlsaMock != null) {
            mAlsaMock.closeMixer();
            mAlsaMock = null;
        }
    }

    private static void createAlsaControlsFile() throws IOException
    {
        FileWriter writer = new FileWriter(sControlsFile);

        // Some simple controls to prove that paths were invoked
        for (String name : TEST_CONTROLS) {
            writer.write(name + ",bool,1,0,0:1\n");
        }

        writer.close();
    }

    private static void createXmlFile() throws IOException
    {
        FileWriter writer = new FileWriter(sXmlFile);

        // Header elements
        writer.write("<audiohal>\n<mixer card=\"0\" />\n");

        // All streams of the same type (pcm, compressed, named) use the same
        // path names to maximise the chances of picking up bugs where the
        // wrong device is matched.

        // Speaker device that has paths for all streams
        writer.write("<device name=\"speaker\">\n");
        writePathEntry(writer, "pcm_on", "SwitchP1", 1);
        writePathEntry(writer, "pcm_off", "SwitchP1", 0);
        writePathEntry(writer, "comp_on", "SwitchC1", 1);
        writePathEntry(writer, "comp_off", "SwitchC1", 0);
        writePathEntry(writer, "h_on", "SwitchN1", 1);
        writePathEntry(writer, "h_off", "SwitchN1", 0);
        writer.write("</device>\n");

        // Earpiece device that has paths for all streams
        writer.write("<device name=\"earpiece\">\n");
        writePathEntry(writer, "pcm_on", "SwitchP2", 1);
        writePathEntry(writer, "pcm_off", "SwitchP2", 0);
        writePathEntry(writer, "comp_on", "SwitchC2", 1);
        writePathEntry(writer, "comp_off", "SwitchC2", 0);
        writePathEntry(writer, "h_on", "SwitchN2", 1);
        writePathEntry(writer, "h_off", "SwitchN2", 0);
        writer.write("</device>\n");

        // Headset device that only has enable path
        writer.write("<device name=\"headset\">\n");
        writePathEntry(writer, "pcm_on", "SwitchP3", 1);
        writePathEntry(writer, "comp_on", "SwitchC3", 1);
        writePathEntry(writer, "h_on", "SwitchN3", 1);
        writer.write("</device>\n");

        // Headphone device that only has disable path
        // This writes the control to '1' so we can check the value was written
        writer.write("<device name=\"headphone\">\n");
        writePathEntry(writer, "pcm_off", "SwitchP4", 1);
        writePathEntry(writer, "comp_off", "SwitchC4", 1);
        writePathEntry(writer, "h_off", "SwitchN4", 1);
        writer.write("</device>\n");

        // SCO device doesn't have any matching paths
        writer.write("<device name=\"sco\">\n");
        writePathEntry(writer, "dummy_on", "SwitchX1", 1);
        writePathEntry(writer, "dummy_off", "SwitchX2", 1);
        writePathEntry(writer, "dummy_left", "SwitchX3", 1);
        writePathEntry(writer, "dummy_right", "SwitchX4", 1);
        writer.write("</device>\n");

        // Mic device that has paths for all streams
        writer.write("<device name=\"mic\">\n");
        writePathEntry(writer, "pcm_on", "SwitchP5", 1);
        writePathEntry(writer, "pcm_off", "SwitchP5", 0);
        writePathEntry(writer, "comp_on", "SwitchC5", 1);
        writePathEntry(writer, "comp_off", "SwitchC5", 0);
        writePathEntry(writer, "h_on", "SwitchN5", 1);
        writePathEntry(writer, "h_off", "SwitchN5", 0);
        writer.write("</device>\n");

        // Back mic device that has paths for all streams
        writer.write("<device name=\"back mic\">\n");
        writePathEntry(writer, "pcm_on", "SwitchP6", 1);
        writePathEntry(writer, "pcm_off", "SwitchP6", 0);
        writePathEntry(writer, "comp_on", "SwitchC6", 1);
        writePathEntry(writer, "comp_off", "SwitchC6", 0);
        writePathEntry(writer, "h_on", "SwitchN6", 1);
        writePathEntry(writer, "h_off", "SwitchN6", 0);
        writer.write("</device>\n");

        // Voice device that only has enable path
        writer.write("<device name=\"voice\">\n");
        writePathEntry(writer, "pcm_on", "SwitchP7", 1);
        writePathEntry(writer, "comp_on", "SwitchC7", 1);
        writePathEntry(writer, "h_on", "SwitchN7", 1);
        writer.write("</device>\n");

        // Aux device that only has disable path
        // This writes the control to '1' so we can check that it was written
        writer.write("<device name=\"aux\">\n");
        writePathEntry(writer, "pcm_off", "SwitchP8", 1);
        writePathEntry(writer, "comp_off", "SwitchC8", 1);
        writePathEntry(writer, "h_off", "SwitchN8", 1);
        writer.write("</device>\n");

        // SCO-in device doesn't have any matching paths
        writer.write("<device name=\"sco_in\">\n");
        writePathEntry(writer, "dummy_on", "SwitchX5", 1);
        writePathEntry(writer, "dummy_off", "SwitchX6", 1);
        writePathEntry(writer, "dummy_left", "SwitchX7", 1);
        writePathEntry(writer, "dummy_right", "SwitchX8", 1);
        writer.write("</device>\n");

        // Test streams
        writeStreamEntry(writer, null, "pcm", "out", "pcm_on", "pcm_off");
        writeStreamEntry(writer, null, "compress", "out", "comp_on", "comp_off");
        writeStreamEntry(writer, "nout", "hw", "out", "h_on", "h_off");
        writeStreamEntry(writer, null, "pcm", "in", "pcm_on", "pcm_off");
        writeStreamEntry(writer, null, "compress", "in", "comp_on", "comp_off");
        writeStreamEntry(writer, "nin", "hw", "in", "h_on", "h_off");

        // Footer elements
        writer.write("\n</audiohal>\n");

        writer.close();
    }

    private static void writeCtl(FileWriter writer,
                                 String controlName,
                                 String value) throws IOException
    {
            writer.write("<ctl name=\"" + controlName + "\" val=\"" + value + "\"/>\n");
    }

    private static void writePathEntry(FileWriter writer,
                                         String name,
                                         String control,
                                         int value) throws IOException
    {
        writer.write("<path name=\"" + name + "\">\n");
        writeCtl(writer, control, Integer.toString(value));
        writer.write("</path>\n");
    }

    private static void writeStreamEntry(FileWriter writer,
                                         String name,
                                         String type,
                                         String dir,
                                         String enable,
                                         String disable) throws IOException
    {
        String nameAttr = "";
        if (name != null) {
            nameAttr = "name=\"" + name + "\" ";
        }

        writer.write("<stream " + nameAttr + "type=\"" + type + "\" dir=\"" +
                     dir + "\" >\n");
        writer.write("<enable path=\"" + enable + "\" />\n");
        writer.write("<disable path=\"" + disable + "\" />\n");
        writer.write("</stream>\n");
    }

    private void checkExpectedControlChange(String expectedChanged) {
        for (String name : TEST_CONTROLS) {
            if (expectedChanged.equals(name)) {
                assertTrue(name + " was not changed", mAlsaMock.isChanged(name));
            } else {
                assertFalse(name + " should not have changed",
                            mAlsaMock.isChanged(name));
            }
        }
    }

    private void checkExpectedControlChange(String expectedChanged1,
                                            String expectedChanged2) {
        for (String name : TEST_CONTROLS) {
            if (expectedChanged1.equals(name) || expectedChanged2.equals(name)) {
                assertTrue(name + " was not changed", mAlsaMock.isChanged(name));
            } else {
                assertFalse(name + " should not have changed",
                            mAlsaMock.isChanged(name));
            }
        }
    }

    private void checkNoControlsChanged() {
        for (String name : TEST_CONTROLS) {
            assertFalse(name + " should not have changed",
                        mAlsaMock.isChanged(name));
        }
    }

    private long openPcmStream(long device)
    {
        CConfigMgr.AudioConfig config = new CConfigMgr.AudioConfig();
        config.sample_rate = 48000;
        config.channel_mask = CConfigMgr.AUDIO_CHANNEL_OUT_FRONT_LEFT;
        config.format = CConfigMgr.AUDIO_FORMAT_PCM;

        return mConfigMgr.get_stream(device, 0, config);
    }

    private long openCompressedStream(long device)
    {
        CConfigMgr.AudioConfig config = new CConfigMgr.AudioConfig();
        config.sample_rate = 48000;
        config.channel_mask = CConfigMgr.AUDIO_CHANNEL_OUT_FRONT_LEFT;
        config.format = CConfigMgr.AUDIO_FORMAT_MP3;

        return mConfigMgr.get_stream(device, 0, config);
    }

    private long openTestStream(long device)
    {
        if (mTestStreamType.equals(PCM_STREAM_TYPE)) {
            return openPcmStream(device);
        } else if (mTestStreamType.equals(COMPRESSED_STREAM_TYPE)) {
            return openCompressedStream(device);
        } else {
            // mTestStreamType is name of hardware stream
            long stream = mConfigMgr.get_named_stream(mTestStreamType);
            if ((stream >= 0) && (device != mNoneDevice)) {
                mConfigMgr.apply_route(stream, device);
            }
            return stream;
        }
    }

    private void closeStream(long stream)
    {
        if (stream >= 0) {
            mConfigMgr.release_stream(stream);
        }
    }

    /**
     * Open a stream not connected to any device then apply a single device and
     * close the stream.
     */
    @Test
    public void testConnectFirstDevice()
    {
        // open without initial device
        long stream = openTestStream(mNoneDevice);
        assertTrue("Failed to get stream", stream >= 0);
        checkNoControlsChanged();

        // connect device
        mConfigMgr.apply_route(stream, mTestDevices[0]);
        checkExpectedControlChange(mTestControls[0]);
        assertEquals(mTestControls[0] + " not written correctly",
                     1,
                     mAlsaMock.getBool(mTestControls[0], 0));
        mAlsaMock.clearChangedFlag(mTestControls[0]);

        // close
        closeStream(stream);
        checkExpectedControlChange(mTestControls[0]);
        assertEquals(mTestControls[0] + " not written correctly",
                     0,
                     mAlsaMock.getBool(mTestControls[0], 0));
    }

    /**
     * Open a stream not connected to any device then add two devices
     * one at a time and close the stream.
     */
    @Test
    public void testConnectFirstTwoDevicesSequential()
    {
        // open not connected to any device
        long stream = openTestStream(mNoneDevice);
        assertTrue("Failed to get stream", stream >= 0);
        checkNoControlsChanged();

        // Add one device
        mConfigMgr.apply_route(stream, mTestDevices[0]);
        checkExpectedControlChange(mTestControls[0]);
        assertEquals(mTestControls[0] + " not written correctly",
                     1,
                     mAlsaMock.getBool(mTestControls[0], 0));
        mAlsaMock.clearChangedFlag(mTestControls[0]);

        // Add another device
        mConfigMgr.apply_route(stream, mTestDevices[0] | mTestDevices[1]);
        checkExpectedControlChange(mTestControls[1]);
        assertEquals(mTestControls[1] + " not written correctly",
                     1,
                     mAlsaMock.getBool(mTestControls[1], 0));
        mAlsaMock.clearChangedFlag(mTestControls[1]);

        closeStream(stream);
        checkExpectedControlChange(mTestControls[0], mTestControls[1]);
        assertEquals(mTestControls[0] + " not written correctly",
                     0,
                     mAlsaMock.getBool(mTestControls[0], 0));
        assertEquals(mTestControls[1] + " not written correctly",
                     0,
                     mAlsaMock.getBool(mTestControls[0], 0));
    }

    /**
     * Open a stream not connected to any device then add two devices
     * in one apply_route() and close the stream.
     */
    @Test
    public void testConnectFirstTwoDevicesTogether()
    {
        // open not connected to any device
        long stream = openTestStream(mNoneDevice);
        assertTrue("Failed to get stream", stream >= 0);
        checkNoControlsChanged();

        // Add two devices
        mConfigMgr.apply_route(stream, mTestDevices[0] | mTestDevices[1]);
        checkExpectedControlChange(mTestControls[0], mTestControls[1]);
        assertEquals(mTestControls[0] + " not written correctly",
                     1,
                     mAlsaMock.getBool(mTestControls[0], 0));
        assertEquals(mTestControls[1] + " not written correctly",
                     1,
                     mAlsaMock.getBool(mTestControls[0], 0));
        mAlsaMock.clearChangedFlag(mTestControls[0]);
        mAlsaMock.clearChangedFlag(mTestControls[1]);

        closeStream(stream);
        checkExpectedControlChange(mTestControls[0], mTestControls[1]);
        assertEquals(mTestControls[0] + " not written correctly",
                     0,
                     mAlsaMock.getBool(mTestControls[0], 0));
        assertEquals(mTestControls[1] + " not written correctly",
                     0,
                     mAlsaMock.getBool(mTestControls[0], 0));
    }

    /**
     * Open a stream connected to a single device then apply the same device
     * again.
     */
    @Test
    public void testConnectInitialDeviceAgain()
    {
        // open connected to initial device
        long stream = openTestStream(mTestDevices[0]);
        assertTrue("Failed to get stream", stream >= 0);
        checkExpectedControlChange(mTestControls[0]);
        assertEquals(mTestControls[0] + " not written correctly",
                     1,
                     mAlsaMock.getBool(mTestControls[0], 0));
        mAlsaMock.clearChangedFlag(mTestControls[0]);

        // apply the same device
        mConfigMgr.apply_route(stream, mTestDevices[0]);
        checkNoControlsChanged();

        // close
        closeStream(stream);
        checkExpectedControlChange(mTestControls[0]);
        assertEquals(mTestControls[0] + " not written correctly",
                     0,
                     mAlsaMock.getBool(mTestControls[0], 0));
    }

    /**
     * Open a stream connected to two devices then apply the same devices
     * again.
     */
    @Test
    public void testConnectTwoInitialDevicesAgain()
    {
        // open connected to initial devices
        long stream = openTestStream(mTestDevices[0] | mTestDevices[1]);
        assertTrue("Failed to get stream", stream >= 0);
        checkExpectedControlChange(mTestControls[0], mTestControls[1]);
        assertEquals(mTestControls[0] + " not written correctly",
                     1,
                     mAlsaMock.getBool(mTestControls[0], 0));
        assertEquals(mTestControls[1] + " not written correctly",
                     1,
                     mAlsaMock.getBool(mTestControls[1], 0));
        mAlsaMock.clearChangedFlag(mTestControls[0]);
        mAlsaMock.clearChangedFlag(mTestControls[1]);

        // apply the same devices
        mConfigMgr.apply_route(stream, mTestDevices[0] | mTestDevices[1]);
        checkNoControlsChanged();

        // close
        closeStream(stream);
        checkExpectedControlChange(mTestControls[0], mTestControls[1]);
        assertEquals(mTestControls[0] + " not written correctly",
                     0,
                     mAlsaMock.getBool(mTestControls[0], 0));
        assertEquals(mTestControls[1] + " not written correctly",
                     0,
                     mAlsaMock.getBool(mTestControls[1], 0));
    }

    /**
     * Open a stream not connected to any device then apply a single device and
     * apply it again.
     */
    @Test
    public void testConnectFirstDeviceAgain()
    {
        // open connected to initial device
        long stream = openTestStream(mNoneDevice);
        assertTrue("Failed to get stream", stream >= 0);
        checkNoControlsChanged();

        // connect to device
        mConfigMgr.apply_route(stream, mTestDevices[0]);
        checkExpectedControlChange(mTestControls[0]);
        assertEquals(mTestControls[0] + " not written correctly",
                     1,
                     mAlsaMock.getBool(mTestControls[0], 0));
        mAlsaMock.clearChangedFlag(mTestControls[0]);

        // apply the same device
        mConfigMgr.apply_route(stream, mTestDevices[0]);
        checkNoControlsChanged();

        // close
        closeStream(stream);
        checkExpectedControlChange(mTestControls[0]);
        assertEquals(mTestControls[0] + " not written correctly",
                     0,
                     mAlsaMock.getBool(mTestControls[0], 0));
    }

    /**
     * Open a stream not connected to any device then apply two devices and
     * apply them again.
     */
    @Test
    public void testConnectFirstTwoDevicesAgain()
    {
        // open connected to initial device
        long stream = openTestStream(mNoneDevice);
        assertTrue("Failed to get stream", stream >= 0);
        checkNoControlsChanged();

        // connect to device
        mConfigMgr.apply_route(stream, mTestDevices[0] | mTestDevices[1]);
        checkExpectedControlChange(mTestControls[0], mTestControls[1]);
        assertEquals(mTestControls[0] + " not written correctly",
                     1,
                     mAlsaMock.getBool(mTestControls[1], 0));
        assertEquals(mTestControls[1] + " not written correctly",
                     1,
                     mAlsaMock.getBool(mTestControls[1], 0));
        mAlsaMock.clearChangedFlag(mTestControls[0]);
        mAlsaMock.clearChangedFlag(mTestControls[1]);

        // apply the same devices
        mConfigMgr.apply_route(stream, mTestDevices[0] | mTestDevices[1]);
        checkNoControlsChanged();

        // close
        closeStream(stream);
        checkExpectedControlChange(mTestControls[0], mTestControls[1]);
        assertEquals(mTestControls[0] + " not written correctly",
                     0,
                     mAlsaMock.getBool(mTestControls[1], 0));
        assertEquals(mTestControls[1] + " not written correctly",
                     0,
                     mAlsaMock.getBool(mTestControls[1], 0));
    }

    /**
     * Open a stream connected to a single device then remove that device and
     * close the stream.
     */
    @Test
    public void testDisconnectInitialDevice()
    {
        // open connected to initial device
        long stream = openTestStream(mTestDevices[0]);
        assertTrue("Failed to get stream", stream >= 0);
        checkExpectedControlChange(mTestControls[0]);
        assertEquals(mTestControls[0] + " not written correctly",
                     1,
                     mAlsaMock.getBool(mTestControls[0], 0));
        mAlsaMock.clearChangedFlag(mTestControls[0]);

        // disconnect the device
        mConfigMgr.apply_route(stream, 0);
        checkExpectedControlChange(mTestControls[0]);
        assertEquals(mTestControls[0] + " not written correctly",
                     0,
                     mAlsaMock.getBool(mTestControls[0], 0));
        mAlsaMock.clearChangedFlag(mTestControls[0]);

        // close
        closeStream(stream);
        checkNoControlsChanged();
    }

    /**
     * Open a stream connected to two devices then remove the devices one at a
     * time and close the stream.
     */
    @Test
    public void testDisconnectTwoInitialDevicesSequential()
    {
        // open connected to initial devices
        long stream = openTestStream(mTestDevices[0] | mTestDevices[1]);
        assertTrue("Failed to get stream", stream >= 0);
        checkExpectedControlChange(mTestControls[0], mTestControls[1]);
        assertEquals(mTestControls[0] + " not written correctly",
                     1,
                     mAlsaMock.getBool(mTestControls[0], 0));
        assertEquals(mTestControls[1] + " not written correctly",
                     1,
                     mAlsaMock.getBool(mTestControls[1], 0));
        mAlsaMock.clearChangedFlag(mTestControls[0]);
        mAlsaMock.clearChangedFlag(mTestControls[1]);

        // disconnect one device
        mConfigMgr.apply_route(stream, mTestDevices[1]);
        checkExpectedControlChange(mTestControls[0]);
        assertEquals(mTestControls[0] + " not written correctly",
                     0,
                     mAlsaMock.getBool(mTestControls[0], 0));
        mAlsaMock.clearChangedFlag(mTestControls[0]);

        // disconnect other device
        mConfigMgr.apply_route(stream, 0);
        assertEquals(mTestControls[1] + " not written correctly",
                     0,
                     mAlsaMock.getBool(mTestControls[1], 0));
        mAlsaMock.clearChangedFlag(mTestControls[1]);

        // close
        closeStream(stream);
        checkNoControlsChanged();
    }

    /**
     * Open a stream connected to two device then remove both devices in one
     * apply_route() and close the stream.
     */
    @Test
    public void testDisconnectTwoInitialDevicesTogether()
    {
        // open connected to initial devices
        long stream = openTestStream(mTestDevices[0] | mTestDevices[1]);
        assertTrue("Failed to get stream", stream >= 0);
        checkExpectedControlChange(mTestControls[0], mTestControls[1]);
        assertEquals(mTestControls[0] + " not written correctly",
                     1,
                     mAlsaMock.getBool(mTestControls[0], 0));
        assertEquals(mTestControls[1] + " not written correctly",
                     1,
                     mAlsaMock.getBool(mTestControls[1], 0));
        mAlsaMock.clearChangedFlag(mTestControls[0]);
        mAlsaMock.clearChangedFlag(mTestControls[1]);

        // disconnect both devices
        mConfigMgr.apply_route(stream, 0);
        checkExpectedControlChange(mTestControls[0], mTestControls[1]);
        assertEquals(mTestControls[0] + " not written correctly",
                     0,
                     mAlsaMock.getBool(mTestControls[0], 0));
        assertEquals(mTestControls[1] + " not written correctly",
                     0,
                     mAlsaMock.getBool(mTestControls[1], 0));
        mAlsaMock.clearChangedFlag(mTestControls[0]);
        mAlsaMock.clearChangedFlag(mTestControls[1]);

        // close
        closeStream(stream);
        checkNoControlsChanged();
    }

     /**
      * Open a stream not connected to any device then apply and disconnect a
      * single device and close the stream.
      */
    @Test
    public void testDisconnectFirstDevice()
    {
        // open without initial device
        long stream = openTestStream(mNoneDevice);
        assertTrue("Failed to get stream", stream >= 0);
        checkNoControlsChanged();

        // connect device
        mConfigMgr.apply_route(stream, mTestDevices[0]);
        checkExpectedControlChange(mTestControls[0]);
        assertEquals(mTestControls[0] + " not written correctly",
                     1,
                     mAlsaMock.getBool(mTestControls[0], 0));
        mAlsaMock.clearChangedFlag(mTestControls[0]);

        // disconnect device
        mConfigMgr.apply_route(stream, 0);
        checkExpectedControlChange(mTestControls[0]);
        assertEquals(mTestControls[0] + " not written correctly",
                     0,
                     mAlsaMock.getBool(mTestControls[0], 0));
        mAlsaMock.clearChangedFlag(mTestControls[0]);

        // close
        closeStream(stream);
        checkNoControlsChanged();
    }

     /**
      * Open a stream not connected to any device then apply two devices and
      * disconnect them one at a time and close the stream.
      */
    @Test
    public void testDisconnectFirstTwoDevicesSequential()
    {
        // open without initial device
        long stream = openTestStream(mNoneDevice);
        assertTrue("Failed to get stream", stream >= 0);
        checkNoControlsChanged();

        // connect devices
        mConfigMgr.apply_route(stream, mTestDevices[0] | mTestDevices[1]);
        checkExpectedControlChange(mTestControls[0], mTestControls[1]);
        assertEquals(mTestControls[0] + " not written correctly",
                     1,
                     mAlsaMock.getBool(mTestControls[0], 0));
        assertEquals(mTestControls[1] + " not written correctly",
                     1,
                     mAlsaMock.getBool(mTestControls[1], 0));
        mAlsaMock.clearChangedFlag(mTestControls[0]);
        mAlsaMock.clearChangedFlag(mTestControls[1]);

        // disconnect one device
        mConfigMgr.apply_route(stream, mTestDevices[1]);
        checkExpectedControlChange(mTestControls[0]);
        assertEquals(mTestControls[0] + " not written correctly",
                     0,
                     mAlsaMock.getBool(mTestControls[0], 0));
        mAlsaMock.clearChangedFlag(mTestControls[0]);

        // disconnect other device
        mConfigMgr.apply_route(stream, 0);
        checkExpectedControlChange(mTestControls[1]);
        assertEquals(mTestControls[0] + " not written correctly",
                     0,
                     mAlsaMock.getBool(mTestControls[1], 0));
        mAlsaMock.clearChangedFlag(mTestControls[1]);

        // close
        closeStream(stream);
        checkNoControlsChanged();
    }

     /**
      * Open a stream not connected to any device then apply two devices and
      * disconnect them in one apply_route() and close the stream.
      */
    @Test
    public void testDisconnectFirstTwoDevicesTogether()
    {
        // open without initial device
        long stream = openTestStream(mNoneDevice);
        assertTrue("Failed to get stream", stream >= 0);
        checkNoControlsChanged();

        // connect devices
        mConfigMgr.apply_route(stream, mTestDevices[0] | mTestDevices[1]);
        checkExpectedControlChange(mTestControls[0], mTestControls[1]);
        assertEquals(mTestControls[0] + " not written correctly",
                     1,
                     mAlsaMock.getBool(mTestControls[0], 0));
        assertEquals(mTestControls[1] + " not written correctly",
                     1,
                     mAlsaMock.getBool(mTestControls[1], 0));
        mAlsaMock.clearChangedFlag(mTestControls[0]);
        mAlsaMock.clearChangedFlag(mTestControls[1]);

        // disconnect both devices
        mConfigMgr.apply_route(stream, 0);
        checkExpectedControlChange(mTestControls[0], mTestControls[1]);
        assertEquals(mTestControls[0] + " not written correctly",
                     0,
                     mAlsaMock.getBool(mTestControls[0], 0));
        assertEquals(mTestControls[0] + " not written correctly",
                     0,
                     mAlsaMock.getBool(mTestControls[1], 0));
        mAlsaMock.clearChangedFlag(mTestControls[0]);
        mAlsaMock.clearChangedFlag(mTestControls[1]);

        // close
        closeStream(stream);
        checkNoControlsChanged();
    }

   /**
     * Open a stream connected to a single device and remove that device twice,
     * then close the stream.
     */
    @Test
    public void testDisconnectInitialDeviceTwice()
    {
        // open connected to initial device
        long stream = openTestStream(mTestDevices[0]);
        assertTrue("Failed to get stream", stream >= 0);
        checkExpectedControlChange(mTestControls[0]);
        assertEquals(mTestControls[0] + " not written correctly",
                     1,
                     mAlsaMock.getBool(mTestControls[0], 0));
        mAlsaMock.clearChangedFlag(mTestControls[0]);

        // disconnect the device
        mConfigMgr.apply_route(stream, 0);
        checkExpectedControlChange(mTestControls[0]);
        assertEquals(mTestControls[0] + " not written correctly",
                     0,
                     mAlsaMock.getBool(mTestControls[0], 0));
        mAlsaMock.clearChangedFlag(mTestControls[0]);

        // disconnect the device again
        mConfigMgr.apply_route(stream, 0);
        checkNoControlsChanged();

        // close
        closeStream(stream);
        checkNoControlsChanged();
    }

     /**
     * Open a stream not connected to any device, apply a single device and
     * disconnect it twice then close the stream.
     */
    @Test
    public void testDisconnectFirstDeviceTwice()
    {
        // open without initial device
        long stream = openTestStream(mNoneDevice);
        assertTrue("Failed to get stream", stream >= 0);
        checkNoControlsChanged();

        // connect device
        mConfigMgr.apply_route(stream, mTestDevices[0]);
        checkExpectedControlChange(mTestControls[0]);
        assertEquals(mTestControls[0] + " not written correctly",
                     1,
                     mAlsaMock.getBool(mTestControls[0], 0));
        mAlsaMock.clearChangedFlag(mTestControls[0]);

        // disconnect device
        mConfigMgr.apply_route(stream, 0);
        checkExpectedControlChange(mTestControls[0]);
        assertEquals(mTestControls[0] + " not written correctly",
                     0,
                     mAlsaMock.getBool(mTestControls[0], 0));
        mAlsaMock.clearChangedFlag(mTestControls[0]);

        // disconnect device again
        mConfigMgr.apply_route(stream, 0);
        checkNoControlsChanged();

        // close
        closeStream(stream);
        checkNoControlsChanged();
    }

    /**
     * Apply disconnection to a stream isn't connected to any device.
     */
    @Test
    public void testDisconnectNothing()
    {
        long stream = openTestStream(mNoneDevice);
        assertTrue("Failed to get stream", stream >= 0);
        checkNoControlsChanged();

        mConfigMgr.apply_route(stream, 0);
        checkNoControlsChanged();

        mConfigMgr.apply_route(stream, 0);
        checkNoControlsChanged();

        closeStream(stream);
        checkNoControlsChanged();
    }

    /**
     * Open a stream connected to a single device then add another
     * device and remove it.
     */
    @Test
    public void testConnectDisconnectOneDevice()
    {
        // open with initial device
        long stream = openTestStream(mTestDevices[0]);
        assertTrue("Failed to get stream", stream >= 0);
        checkExpectedControlChange(mTestControls[0]);
        assertEquals(mTestControls[0] + " not written correctly",
                     1,
                     mAlsaMock.getBool(mTestControls[0], 0));
        mAlsaMock.clearChangedFlag(mTestControls[0]);

        // connect second device
        mConfigMgr.apply_route(stream, mTestDevices[0] | mTestDevices[1]);
        checkExpectedControlChange(mTestControls[1]);
        assertEquals(mTestControls[1] + " not written correctly",
                     1,
                     mAlsaMock.getBool(mTestControls[1], 0));
        mAlsaMock.clearChangedFlag(mTestControls[1]);

        // disconnect second device
        mConfigMgr.apply_route(stream, mTestDevices[0]);
        checkExpectedControlChange(mTestControls[1]);
        assertEquals(mTestControls[1] + " not written correctly",
                     0,
                     mAlsaMock.getBool(mTestControls[1], 0));
        mAlsaMock.clearChangedFlag(mTestControls[1]);

        // close
        closeStream(stream);
        checkExpectedControlChange(mTestControls[0]);
        assertEquals(mTestControls[0] + " not written correctly",
                     0,
                     mAlsaMock.getBool(mTestControls[0], 0));
    }

    /**
     * Connect a stream to a device that does not have any matching paths.
     */
    @Test
    public void testConnectFirstDeviceNoPaths()
    {
        long stream = openTestStream(mNoneDevice);
        assertTrue("Failed to get stream", stream >= 0);
        checkNoControlsChanged();

        mConfigMgr.apply_route(stream, mNoPathsDevice);
        checkNoControlsChanged();

        closeStream(stream);
        checkNoControlsChanged();
    }


    /**
     * Open a stream connected to a device then add another device that does
     * not have any matching paths.
     */
    @Test
    public void testAddOneDeviceNoPaths()
    {
        // open with initial device
        long stream = openTestStream(mTestDevices[0]);
        assertTrue("Failed to get stream", stream >= 0);
        checkExpectedControlChange(mTestControls[0]);
        assertEquals(mTestControls[0] + " not written correctly",
                     1,
                     mAlsaMock.getBool(mTestControls[0], 0));
        mAlsaMock.clearChangedFlag(mTestControls[0]);

        mConfigMgr.apply_route(stream, mTestDevices[0] | mNoPathsDevice);
        checkNoControlsChanged();

        closeStream(stream);
        checkExpectedControlChange(mTestControls[0]);
        assertEquals(mTestControls[0] + " not written correctly",
                     0,
                     mAlsaMock.getBool(mTestControls[0], 0));
    }

    /**
     * Open a stream connected to two devices with matching paths in only one
     * device, then remove the device without paths.
     */
    @Test
    public void testRemoveOneDeviceNoPaths()
    {
        // open with initial devices
        long stream = openTestStream(mTestDevices[0] | mNoPathsDevice);
        assertTrue("Failed to get stream", stream >= 0);
        checkExpectedControlChange(mTestControls[0]);
        assertEquals(mTestControls[0] + " not written correctly",
                     1,
                     mAlsaMock.getBool(mTestControls[0], 0));
        mAlsaMock.clearChangedFlag(mTestControls[0]);

        mConfigMgr.apply_route(stream, mTestDevices[0]);
        checkNoControlsChanged();

        closeStream(stream);
        checkExpectedControlChange(mTestControls[0]);
        assertEquals(mTestControls[0] + " not written correctly",
                     0,
                     mAlsaMock.getBool(mTestControls[0], 0));
    }

    /**
     * Open a stream not connected to any device then add two devices together
     * with a matching path in only one device.
     */
    @Test
    public void testAddTwoDevicesOnePath()
    {
        // open not connected to any device
        long stream = openTestStream(mNoneDevice);
        assertTrue("Failed to get stream", stream >= 0);
        checkNoControlsChanged();

        // Add two devices where one doesn't have any matching paths
        mConfigMgr.apply_route(stream, mTestDevices[0] | mNoPathsDevice);
        checkExpectedControlChange(mTestControls[0]);
        assertEquals(mTestControls[0] + " not written correctly",
                     1,
                     mAlsaMock.getBool(mTestControls[0], 0));
        mAlsaMock.clearChangedFlag(mTestControls[0]);

        closeStream(stream);
        checkExpectedControlChange(mTestControls[0]);
        assertEquals(mTestControls[0] + " not written correctly",
                     0,
                     mAlsaMock.getBool(mTestControls[0], 0));
    }

    /**
     * Open a stream connected to two devices with matching paths in only one
     * device, then remove both devices.
     */
    @Test
    public void testRemoveTwoDevicesOnePath()
    {
        // open with initial devices
        long stream = openTestStream(mTestDevices[0] | mNoneDevice);
        assertTrue("Failed to get stream", stream >= 0);
        checkExpectedControlChange(mTestControls[0]);
        assertEquals(mTestControls[0] + " not written correctly",
                     1,
                     mAlsaMock.getBool(mTestControls[0], 0));
        mAlsaMock.clearChangedFlag(mTestControls[0]);

        // Remove both devices
        mConfigMgr.apply_route(stream, 0);
        checkExpectedControlChange(mTestControls[0]);
        assertEquals(mTestControls[0] + " not written correctly",
                     0,
                     mAlsaMock.getBool(mTestControls[0], 0));
        mAlsaMock.clearChangedFlag(mTestControls[0]);

        closeStream(stream);
        checkNoControlsChanged();
    }

    /**
     * Open a stream connected to a single device and then change to
     * another device with make-before-break.
     */
    @Test
    public void testMakeBeforeBreakOneDevice()
    {
        // open with initial device
        long stream = openTestStream(mTestDevices[0]);
        assertTrue("Failed to get stream", stream >= 0);
        checkExpectedControlChange(mTestControls[0]);
        assertEquals(mTestControls[0] + " not written correctly",
                     1,
                     mAlsaMock.getBool(mTestControls[0], 0));
        mAlsaMock.clearChangedFlag(mTestControls[0]);

        // add second device
        mConfigMgr.apply_route(stream, mTestDevices[0] | mTestDevices[1]);
        checkExpectedControlChange(mTestControls[1]);
        assertEquals(mTestControls[1] + " not written correctly",
                     1,
                     mAlsaMock.getBool(mTestControls[1], 0));
        mAlsaMock.clearChangedFlag(mTestControls[1]);

        // remove original device
        mConfigMgr.apply_route(stream, mTestDevices[1]);
        checkExpectedControlChange(mTestControls[0]);
        assertEquals(mTestControls[0] + " not written correctly",
                     0,
                     mAlsaMock.getBool(mTestControls[0], 0));
        mAlsaMock.clearChangedFlag(mTestControls[0]);

        // close
        closeStream(stream);
        checkExpectedControlChange(mTestControls[1]);
        assertEquals(mTestControls[1] + " not written correctly",
                     0,
                     mAlsaMock.getBool(mTestControls[1], 0));
    }

    /**
     * Open a stream connected to a single device and then change to
     * another device with break-before-make.
     */
    @Test
    public void testBreakBeforeMakeOneDevice()
    {
        // open with initial device
        long stream = openTestStream(mTestDevices[0]);
        assertTrue("Failed to get stream", stream >= 0);
        checkExpectedControlChange(mTestControls[0]);
        assertEquals(mTestControls[0] + " not written correctly",
                     1,
                     mAlsaMock.getBool(mTestControls[0], 0));
        mAlsaMock.clearChangedFlag(mTestControls[0]);

        // disconnect initial device
        mConfigMgr.apply_route(stream, 0);
        checkExpectedControlChange(mTestControls[0]);
        assertEquals(mTestControls[0] + " not written correctly",
                     0,
                     mAlsaMock.getBool(mTestControls[0], 0));
        mAlsaMock.clearChangedFlag(mTestControls[0]);

        // connect second device
        mConfigMgr.apply_route(stream, mTestDevices[1]);
        checkExpectedControlChange(mTestControls[1]);
        assertEquals(mTestControls[1] + " not written correctly",
                     1,
                     mAlsaMock.getBool(mTestControls[1], 0));
        mAlsaMock.clearChangedFlag(mTestControls[1]);

        // close
        closeStream(stream);
        checkExpectedControlChange(mTestControls[1]);
        assertEquals(mTestControls[1] + " not written correctly",
                     0,
                     mAlsaMock.getBool(mTestControls[1], 0));
    }

    /**
     * Connect and disconnect a device that only has an enable path for the
     * stream.
     */
    @Test
    public void testConnectOneDeviceEnableOnly()
    {
        // open not connected to any device
        long stream = openTestStream(mNoneDevice);
        assertTrue("Failed to get stream", stream >= 0);
        checkNoControlsChanged();

        // add device that will enable but not disable
        mConfigMgr.apply_route(stream, mEnableOnlyDevice);
        checkExpectedControlChange(mEnableOnlyControl);
        assertEquals(mEnableOnlyControl + " not written correctly",
                     1,
                     mAlsaMock.getBool(mEnableOnlyControl, 0));
        mAlsaMock.clearChangedFlag(mEnableOnlyControl);

        // remove the device
        mConfigMgr.apply_route(stream, 0);
        checkNoControlsChanged();

        closeStream(stream);
        checkNoControlsChanged();
    }

    /**
     * Connect and disconnect a device that only has an disable path for the
     * stream.
     */
    @Test
    public void testConnectOneDeviceDisableOnly()
    {
        // open not connected to any device
        long stream = openTestStream(mNoneDevice);
        assertTrue("Failed to get stream", stream >= 0);
        checkNoControlsChanged();

        // add device that will only disable
        mConfigMgr.apply_route(stream, mDisableOnlyDevice);
        checkNoControlsChanged();

        // disconnect device
        // The disable writes the control to '1'
        mConfigMgr.apply_route(stream, 0);
        checkExpectedControlChange(mDisableOnlyControl);
        assertEquals(mDisableOnlyControl + " not written correctly",
                     1,
                     mAlsaMock.getBool(mDisableOnlyControl, 0));
        mAlsaMock.clearChangedFlag(mDisableOnlyControl);

        closeStream(stream);
        checkNoControlsChanged();
    }

    /**
     * Connect and disconnect two devices where both have enable paths but only
     * one has a disable path.
     */
    @Test
    public void testConnectTwoDevicesOneEnableOnly()
    {
        // open not connected to any device
        long stream = openTestStream(mNoneDevice);
        assertTrue("Failed to get stream", stream >= 0);
        checkNoControlsChanged();

        // add devices that will both enable
        mConfigMgr.apply_route(stream, mTestDevices[0] | mEnableOnlyDevice);
        checkExpectedControlChange(mTestControls[0], mEnableOnlyControl);
        assertEquals(mTestControls[0] + " not written correctly",
                     1,
                     mAlsaMock.getBool(mTestControls[0], 0));
        assertEquals(mEnableOnlyControl + " not written correctly",
                     1,
                     mAlsaMock.getBool(mEnableOnlyControl, 0));
        mAlsaMock.clearChangedFlag(mTestControls[0]);
        mAlsaMock.clearChangedFlag(mEnableOnlyControl);

        // disconnect devices
        mConfigMgr.apply_route(stream, 0);
        checkExpectedControlChange(mTestControls[0]);
        assertEquals(mTestControls[0] + " not written correctly",
                     0,
                     mAlsaMock.getBool(mTestControls[0], 0));
        mAlsaMock.clearChangedFlag(mTestControls[0]);

        closeStream(stream);
        checkNoControlsChanged();
    }

    /**
     * Connect and disconnect two devices where only one has an enable path and
     * both have disable paths.
     */
    @Test
    public void testDisconnectTwoDevicesOneDisableOnly()
    {
        // open not connected to any device
        long stream = openTestStream(mNoneDevice);
        assertTrue("Failed to get stream", stream >= 0);
        checkNoControlsChanged();

        // add devices where only one will enable
        mConfigMgr.apply_route(stream, mTestDevices[0] | mDisableOnlyDevice);
        checkExpectedControlChange(mTestControls[0]);
        assertEquals(mTestControls[0] + " not written correctly",
                     1,
                     mAlsaMock.getBool(mTestControls[0], 0));
        mAlsaMock.clearChangedFlag(mTestControls[0]);

        // disconnect devices, both will disable
        mConfigMgr.apply_route(stream, 0);
        checkExpectedControlChange(mTestControls[0], mDisableOnlyControl);
        assertEquals(mTestControls[0] + " not written correctly",
                     0,
                     mAlsaMock.getBool(mTestControls[0], 0));
        // The disable-only writes the control to '1'
        assertEquals(mDisableOnlyControl + " not written correctly",
                     1,
                     mAlsaMock.getBool(mDisableOnlyControl, 0));
        mAlsaMock.clearChangedFlag(mTestControls[0]);
        mAlsaMock.clearChangedFlag(mDisableOnlyControl);

        closeStream(stream);
        checkNoControlsChanged();
    }

};
