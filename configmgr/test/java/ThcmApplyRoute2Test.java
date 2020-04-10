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
 * Test apply_route() invoking the &lt;path&gt; elements for a multiple
 * &lt;stream&gt;.
 * Tests that &lt;enable&gt; and &lt;disable&gt; in &lt;stream&gt; elements
 * invoke the correct &lt;path&gt; elements for apply_route() calls. This test
 * is parameterized to run various combinations of streams.
 * The testing assumes that &lt;ctl&gt; elements execute correctly.
 */
@RunWith(Parameterized.class)
public class ThcmApplyRoute2Test
{
    private static final String[] TEST_CONTROLS = {
        // PCM in stream
        "SwitchP1", "SwitchP2",
        // PCM out stream
        "SwitchP3", "SwitchP4",
        // Compressed in stream
        "SwitchC1", "SwitchC2",
        // Compressed out stream
        "SwitchC3", "SwitchC4",
        // Named out stream
        "SwitchN1", "SwitchN2",
        // Named in stream
        "SwitchN3", "SwitchN4",
    };

    private static final String PCM_STREAM_TYPE = "pcm";
    private static final String COMPRESSED_STREAM_TYPE = "compressed";
    private static final String HW_STREAM_TYPE = "hw";

    private static final File sWorkFilesPath = ThcmPlatform.workFilesPath();
    private static final File sControlsFile = new File(sWorkFilesPath, "thcm_apply_route2_controls.csv");
    private static final File sXmlFile = new File(sWorkFilesPath, "thcm_apply_route2_config.xml");

    private CAlsaMock mAlsaMock = new CAlsaMock();
    private CConfigMgr mConfigMgr = new CConfigMgr();

    private String[] mTestStreams;
    private long[] mTestDevices;
    private String[][] mTestControls;
    private long mNoneDevice;

    @Parameterized.Parameters
    public static Collection parameters() {
        return Arrays.asList(new String[][] {
            // stream1, stream2, device 1, device2,
            // stream1 control 1, stream1 control 2, stream2 control 1, stream2 control 2,

            // outputs
            { PCM_STREAM_TYPE, COMPRESSED_STREAM_TYPE, "speaker", "earpiece",
              "SwitchP1", "SwitchP2", "SwitchC1", "SwitchC2"
            },
            { COMPRESSED_STREAM_TYPE, PCM_STREAM_TYPE, "speaker", "earpiece",
              "SwitchC1", "SwitchC2", "SwitchP1", "SwitchP2"
            },
            { PCM_STREAM_TYPE, "nout", "speaker", "earpiece",
              "SwitchP1", "SwitchP2", "SwitchN1", "SwitchN2"
            },
            { "nout", PCM_STREAM_TYPE, "speaker", "earpiece",
              "SwitchN1", "SwitchN2", "SwitchP1", "SwitchP2"
            },

            // inputs
            { PCM_STREAM_TYPE, COMPRESSED_STREAM_TYPE, "mic", "back mic",
              "SwitchP3", "SwitchP4", "SwitchC3", "SwitchC4"
            },
            { COMPRESSED_STREAM_TYPE, PCM_STREAM_TYPE, "mic", "back mic",
              "SwitchC3", "SwitchC4", "SwitchP3", "SwitchP4"
            },
            { PCM_STREAM_TYPE, "nin", "mic", "back mic",
              "SwitchP3", "SwitchP4", "SwitchN3", "SwitchN4"
            },
            { "nin", PCM_STREAM_TYPE, "mic", "back mic",
              "SwitchN3", "SwitchN4", "SwitchP3", "SwitchP4"
            },
        });
    }

    public ThcmApplyRoute2Test(String stream1,
                               String stream2,
                               String device1, String device2,
                               String s1control1, String s1control2,
                               String s2control1, String s2control2)
        {
        mTestStreams = new String[] {stream1, stream2};
        mTestDevices = new long[2];
        mTestDevices[0] = CConfigMgr.deviceFromName(device1);
        mTestDevices[1] = CConfigMgr.deviceFromName(device2);
        mTestControls = new String[][] {
            {s1control1, s1control2}, {s2control1, s2control2}
        };

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
            assertFalse("Configmgr leaked memory", CConfigMgr.are_allocs_leaked());
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

        // Mic device that has paths for all streams
        writer.write("<device name=\"mic\">\n");
        writePathEntry(writer, "pcm_on", "SwitchP3", 1);
        writePathEntry(writer, "pcm_off", "SwitchP3", 0);
        writePathEntry(writer, "comp_on", "SwitchC3", 1);
        writePathEntry(writer, "comp_off", "SwitchC3", 0);
        writePathEntry(writer, "h_on", "SwitchN3", 1);
        writePathEntry(writer, "h_off", "SwitchN3", 0);
        writer.write("</device>\n");

        // Back mic device that has paths for all streams
        writer.write("<device name=\"back mic\">\n");
        writePathEntry(writer, "pcm_on", "SwitchP4", 1);
        writePathEntry(writer, "pcm_off", "SwitchP4", 0);
        writePathEntry(writer, "comp_on", "SwitchC4", 1);
        writePathEntry(writer, "comp_off", "SwitchC4", 0);
        writePathEntry(writer, "h_on", "SwitchN4", 1);
        writePathEntry(writer, "h_off", "SwitchN4", 0);
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

    private long openTestStream(String name, long device)
    {
        if (name.equals(PCM_STREAM_TYPE)) {
            return openPcmStream(device);
        } else if (name.equals(COMPRESSED_STREAM_TYPE)) {
            return openCompressedStream(device);
        } else {
            long stream = mConfigMgr.get_named_stream(name);
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
     * Connect and disconnect a stream to a device that is already connected to
     * another stream.
     */
    @Test
    public void testAddRemoveOneEnabledDevice()
    {
        // stream1 opened to device 1
        long stream1 = openTestStream(mTestStreams[0], mTestDevices[0]);
        assertTrue("Failed to get stream1", stream1 >= 0);
        checkExpectedControlChange(mTestControls[0][0]);
        assertEquals(mTestControls[0][0] + " not written correctly",
                     1,
                     mAlsaMock.getBool(mTestControls[0][0], 0));
        mAlsaMock.clearChangedFlag(mTestControls[0][0]);

        // stream2 opened to device 2
        long stream2 = openTestStream(mTestStreams[1], mTestDevices[1]);
        assertTrue("Failed to get stream2", stream2 >= 0);
        checkExpectedControlChange(mTestControls[1][1]);
        assertEquals(mTestControls[1][1] + " not written correctly",
                     1,
                     mAlsaMock.getBool(mTestControls[1][1], 0));
        mAlsaMock.clearChangedFlag(mTestControls[1][1]);

        // Add device 2 to stream1
        mConfigMgr.apply_route(stream1, mTestDevices[0] | mTestDevices[1]);
        checkExpectedControlChange(mTestControls[0][1]);
        assertEquals(mTestControls[0][1] + " not written correctly",
                     1,
                     mAlsaMock.getBool(mTestControls[0][1], 0));
        mAlsaMock.clearChangedFlag(mTestControls[0][1]);

        // Disconnect stream1 from device 2
        mConfigMgr.apply_route(stream1, mTestDevices[0]);
        checkExpectedControlChange(mTestControls[0][1]);
        assertEquals(mTestControls[0][1] + " not written correctly",
                     0,
                     mAlsaMock.getBool(mTestControls[0][1], 0));
        mAlsaMock.clearChangedFlag(mTestControls[0][1]);

        closeStream(stream1);
        checkExpectedControlChange(mTestControls[0][0]);
        assertEquals(mTestControls[0][0] + " not written correctly",
                     0,
                     mAlsaMock.getBool(mTestControls[0][0], 0));
        mAlsaMock.clearChangedFlag(mTestControls[0][0]);

        closeStream(stream2);
        checkExpectedControlChange(mTestControls[1][1]);
        assertEquals(mTestControls[1][1] + " not written correctly",
                     0,
                     mAlsaMock.getBool(mTestControls[1][1], 0));
        mAlsaMock.clearChangedFlag(mTestControls[1][1]);
    }

    /**
     * Move a device from one stream to another with make-before-break
     */
    @Test
    public void testSwapStreamOneDeviceMakeBeforeBreak()
    {
        long stream1 = openTestStream(mTestStreams[0], mTestDevices[0]);
        assertTrue("Failed to get stream1", stream1 >= 0);
        checkExpectedControlChange(mTestControls[0][0]);
        assertEquals(mTestControls[0][0] + " not written correctly",
                     1,
                     mAlsaMock.getBool(mTestControls[0][0], 0));
        mAlsaMock.clearChangedFlag(mTestControls[0][0]);

        long stream2 = openTestStream(mTestStreams[1], mNoneDevice);
        assertTrue("Failed to get stream2", stream2 >= 0);
        checkNoControlsChanged();

        mConfigMgr.apply_route(stream2, mTestDevices[0]);
        checkExpectedControlChange(mTestControls[1][0]);
        assertEquals(mTestControls[1][0] + " not written correctly",
                     1,
                     mAlsaMock.getBool(mTestControls[1][0], 0));
        mAlsaMock.clearChangedFlag(mTestControls[1][0]);

        mConfigMgr.apply_route(stream1, 0);
        checkExpectedControlChange(mTestControls[0][0]);
        assertEquals(mTestControls[0][0] + " not written correctly",
                     0,
                     mAlsaMock.getBool(mTestControls[0][0], 0));
        mAlsaMock.clearChangedFlag(mTestControls[0][0]);

        closeStream(stream2);
        checkExpectedControlChange(mTestControls[1][0]);
        assertEquals(mTestControls[1][0] + " not written correctly",
                     0,
                     mAlsaMock.getBool(mTestControls[1][0], 0));
        mAlsaMock.clearChangedFlag(mTestControls[1][0]);

        closeStream(stream1);
        checkNoControlsChanged();
    }

    /**
     * Move a device from one stream to another with break-before-make
     */
    @Test
    public void testSwapStreamOneDeviceBreakBeforeMake()
    {
        long stream1 = openTestStream(mTestStreams[0], mTestDevices[0]);
        assertTrue("Failed to get stream1", stream1 >= 0);
        checkExpectedControlChange(mTestControls[0][0]);
        assertEquals(mTestControls[0][0] + " not written correctly",
                     1,
                     mAlsaMock.getBool(mTestControls[0][0], 0));
        mAlsaMock.clearChangedFlag(mTestControls[0][0]);

        long stream2 = openTestStream(mTestStreams[1], mNoneDevice);
        assertTrue("Failed to get stream2", stream2 >= 0);
        checkNoControlsChanged();

        mConfigMgr.apply_route(stream1, 0);
        checkExpectedControlChange(mTestControls[0][0]);
        assertEquals(mTestControls[0][0] + " not written correctly",
                     0,
                     mAlsaMock.getBool(mTestControls[0][0], 0));
        mAlsaMock.clearChangedFlag(mTestControls[0][0]);

        mConfigMgr.apply_route(stream2, mTestDevices[0]);
        checkExpectedControlChange(mTestControls[1][0]);
        assertEquals(mTestControls[1][0] + " not written correctly",
                     1,
                     mAlsaMock.getBool(mTestControls[1][0], 0));
        mAlsaMock.clearChangedFlag(mTestControls[1][0]);

        closeStream(stream2);
        checkExpectedControlChange(mTestControls[1][0]);
        assertEquals(mTestControls[1][0] + " not written correctly",
                     0,
                     mAlsaMock.getBool(mTestControls[1][0], 0));
        mAlsaMock.clearChangedFlag(mTestControls[1][0]);

        closeStream(stream1);
        checkNoControlsChanged();
    }
};
