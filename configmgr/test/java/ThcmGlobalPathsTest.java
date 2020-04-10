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
 * Test that &lt;path&gt; elements in the global device are invoked correctly
 * for multiple combinations of &lt;stream&gt; elements.
 * The testing assumes that &lt;ctl&gt; elements execute correctly.
 */
@RunWith(Parameterized.class)
public class ThcmGlobalPathsTest
{
    private static final String[] TEST_CONTROLS = {
        // PCM streams
        "SwitchP1", "SwitchP2", "SwitchP3", "SwitchP4",
        // Compressed streams
        "SwitchC1", "SwitchC2", "SwitchC3", "SwitchC4",
        // Named streams
        "SwitchN1", "SwitchN2", "SwitchN3", "SwitchN4",
        // For enable-only and disable-only testing
        "SwitchEO1", "SwitchEO2", "SwitchDO1", "SwitchDO2",
        // Global device on/off control
        "SwitchG1"
    };

    private static final File sWorkFilesPath = ThcmPlatform.workFilesPath();
    private static final File sControlsFile = new File(sWorkFilesPath, "thcm_global_route_controls.csv");
    private static final File sXmlFile = new File(sWorkFilesPath, "thcm_global_route_config.xml");

    private static final String PCM_STREAM_TYPE = "pcm";
    private static final String COMPRESSED_STREAM_TYPE = "compressed";
    private static final String HW_STREAM_TYPE = "hw";

    private CAlsaMock mAlsaMock = new CAlsaMock();
    private CConfigMgr mConfigMgr = new CConfigMgr();

    private String[] mTestStreams;
    private String[][] mTestControls;

    @Parameterized.Parameters
    public static Collection parameters() {
        return Arrays.asList(new String[][] {
            { PCM_STREAM_TYPE, COMPRESSED_STREAM_TYPE,
              "SwitchP1", "SwitchP2", "SwitchC1", "SwitchC2"
            },
            { COMPRESSED_STREAM_TYPE, PCM_STREAM_TYPE,
              "SwitchC1", "SwitchC2", "SwitchP1", "SwitchP2"
            },
            { PCM_STREAM_TYPE, HW_STREAM_TYPE,
              "SwitchP1", "SwitchP2", "SwitchN1", "SwitchN2"
            },
            { HW_STREAM_TYPE, PCM_STREAM_TYPE,
              "SwitchN1", "SwitchN2", "SwitchP1", "SwitchP2"
            },
            { COMPRESSED_STREAM_TYPE, HW_STREAM_TYPE,
              "SwitchC1", "SwitchC2", "SwitchN1", "SwitchN2"
            },
            { HW_STREAM_TYPE, COMPRESSED_STREAM_TYPE,
              "SwitchN1", "SwitchN2", "SwitchC1", "SwitchC2"
            }
        });
    }

    public ThcmGlobalPathsTest(String stream1, String stream2,
                               String stream1Control1, String stream1Control2,
                               String stream2Control1, String stream2Control2)
    {
        mTestStreams = new String[] { stream1, stream2 };
        mTestControls = new String[][] {
            { stream1Control1, stream1Control2 },
            { stream2Control1, stream2Control2 }
        };
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
        assertEquals("Failed to create CAlsaMock",
                     0,
                     mAlsaMock.createMixer(sControlsFile.toPath().toString()));

        assertEquals("Failed to open CConfigMgr",
                     0,
                     mConfigMgr.init_audio_config(sXmlFile.toPath().toString()));
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

        writer.write("<device name=\"global\">\n");
        writePathEntry(writer, "on", "SwitchG1", 1);
        writePathEntry(writer, "off", "SwitchG1", 0);
        writePathEntry(writer, "pcm_out_on", "SwitchP1", 1);
        writePathEntry(writer, "pcm_out_off", "SwitchP1", 0);
        writePathEntry(writer, "comp_out_on", "SwitchC1", 1);
        writePathEntry(writer, "comp_out_off", "SwitchC1", 0);
        writePathEntry(writer, "h_out_on", "SwitchN1", 1);
        writePathEntry(writer, "h_out_off", "SwitchN1", 0);
        writePathEntry(writer, "ho_out_on", "SwitchEO1", 1);
        // Disable-only so written as 1 to prove it was written
        writePathEntry(writer, "ho_out_off", "SwitchDO1", 1);
        writePathEntry(writer, "pcm_in_on", "SwitchP2", 1);
        writePathEntry(writer, "pcm_in_off", "SwitchP2", 0);
        writePathEntry(writer, "comp_in_on", "SwitchC2", 1);
        writePathEntry(writer, "comp_in_off", "SwitchC2", 0);
        writePathEntry(writer, "h_in_on", "SwitchN2", 1);
        writePathEntry(writer, "h_in_off", "SwitchN2", 0);
        writePathEntry(writer, "ho_in_on", "SwitchEO2", 1);
        // Disable-only - written to '1' so the value can be tested
        writePathEntry(writer, "ho_in_off", "SwitchDO2", 1);
        writer.write("</device>\n");

        // Test streams
        writeStreamEntry(writer, null, "pcm", "out", "pcm_out_on", "pcm_out_off");
        writeStreamEntry(writer, null, "compress", "out", "comp_out_on", "comp_out_off");
        writeStreamEntry(writer, "nout", "hw", "out", "h_out_on", "h_out_off");
        writeStreamEntry(writer, "nout_eno", "hw", "out", "ho_out_on", null);
        writeStreamEntry(writer, "nout_diso", "hw", "out", null, "ho_out_off");
        writeStreamEntry(writer, null, "pcm", "in", "pcm_in_on", "pcm_in_off");
        writeStreamEntry(writer, null, "compress", "in", "comp_in_on", "comp_in_off");
        writeStreamEntry(writer, "nin", "hw", "in", "h_in_on", "h_in_off");
        writeStreamEntry(writer, "nin_eno", "hw", "out", "ho_in_on", null);
        writeStreamEntry(writer, "nin_diso", "hw", "out", null, "ho_in_off");

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
        if (enable != null) {
            writer.write("<enable path=\"" + enable + "\" />\n");
        }
        if (disable != null) {
            writer.write("<disable path=\"" + disable + "\" />\n");
        }
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

    private long openTestStream(String type, long device)
    {
        if (type.equals(PCM_STREAM_TYPE)) {
            return openPcmStream(device);
        } else if (type.equals(COMPRESSED_STREAM_TYPE)) {
            return openCompressedStream(device);
        } else {
            String name;
            if ((device & CConfigMgr.AUDIO_DEVICE_BIT_IN) != 0) {
                name = "nin";
            } else {
                name = "nout";
            }
            long stream = mConfigMgr.get_named_stream(name);
            if (stream < 0) {
                return stream;
            }

            if (device != 0) {
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
     * Open and close a single output stream.
     */
    @Test
    public void testOpenCloseOneOutputStream()
    {
        // open without initial device
        long stream = openTestStream(mTestStreams[0], CConfigMgr.AUDIO_DEVICE_NONE);
        assertTrue("Failed to get stream", stream >= 0);
        checkExpectedControlChange("SwitchG1", mTestControls[0][0]);
        assertEquals(mTestControls[0][0] + " not written correctly",
                     1,
                     mAlsaMock.getBool(mTestControls[0][0], 0));
        assertEquals("SwitchG1 not written correctly",
                     1,
                     mAlsaMock.getBool("SwitchG1", 0));
        mAlsaMock.clearChangedFlag(mTestControls[0][0]);
        mAlsaMock.clearChangedFlag("SwitchG1");

        closeStream(stream);
        checkExpectedControlChange("SwitchG1", mTestControls[0][0]);
        assertEquals(mTestControls[0][0] + " not written correctly",
                     0,
                     mAlsaMock.getBool(mTestControls[0][0], 0));
        assertEquals("SwitchG1 not written correctly",
                     0,
                     mAlsaMock.getBool("SwitchG1", 0));
    }

    /**
     * Open and close a single input stream.
     */
    @Test
    public void testOpenCloseOneInputStream()
    {
        // open without initial device
        long stream = openTestStream(mTestStreams[0], CConfigMgr.AUDIO_DEVICE_BIT_IN);
        assertTrue("Failed to get stream", stream >= 0);
        checkExpectedControlChange("SwitchG1", mTestControls[0][1]);
        assertEquals(mTestControls[0][1] + " not written correctly",
                     1,
                     mAlsaMock.getBool(mTestControls[0][1], 0));
        assertEquals("SwitchG1 not written correctly",
                     1,
                     mAlsaMock.getBool("SwitchG1", 0));
        mAlsaMock.clearChangedFlag(mTestControls[0][1]);
        mAlsaMock.clearChangedFlag("SwitchG1");

        closeStream(stream);
        checkExpectedControlChange("SwitchG1", mTestControls[0][1]);
        assertEquals(mTestControls[0][1] + " not written correctly",
                     0,
                     mAlsaMock.getBool(mTestControls[0][1], 0));
        assertEquals("SwitchG1 not written correctly",
                     0,
                     mAlsaMock.getBool("SwitchG1", 0));
    }

    /**
     * Open and close two output streams.
     */
    @Test
    public void testOpenCloseTwoOutputStreams()
    {
        // open without initial device
        long stream1 = openTestStream(mTestStreams[0], CConfigMgr.AUDIO_DEVICE_NONE);
        assertTrue("Failed to get stream1", stream1 >= 0);
        checkExpectedControlChange("SwitchG1", mTestControls[0][0]);
        assertEquals(mTestControls[0][0] + " not written correctly",
                     1,
                     mAlsaMock.getBool(mTestControls[0][0], 0));
        assertEquals("SwitchG1 not written correctly",
                     1,
                     mAlsaMock.getBool("SwitchG1", 0));
        mAlsaMock.clearChangedFlag(mTestControls[0][0]);
        mAlsaMock.clearChangedFlag("SwitchG1");

        long stream2 = openTestStream(mTestStreams[1], CConfigMgr.AUDIO_DEVICE_NONE);
        assertTrue("Failed to get stream2", stream2 >= 0);
        checkExpectedControlChange(mTestControls[1][0]);
        assertEquals(mTestControls[1][0] + " not written correctly",
                     1,
                     mAlsaMock.getBool(mTestControls[1][0], 0));
        mAlsaMock.clearChangedFlag(mTestControls[1][0]);

        closeStream(stream1);
        checkExpectedControlChange(mTestControls[0][0]);
        assertEquals(mTestControls[0][0] + " not written correctly",
                     0,
                     mAlsaMock.getBool(mTestControls[0][0], 0));
        mAlsaMock.clearChangedFlag(mTestControls[0][0]);

        closeStream(stream2);
        checkExpectedControlChange("SwitchG1", mTestControls[1][0]);
        assertEquals(mTestControls[1][0] + " not written correctly",
                     0,
                     mAlsaMock.getBool(mTestControls[1][0], 0));
        assertEquals("SwitchG1 not written correctly",
                     0,
                     mAlsaMock.getBool("SwitchG1", 0));
    }

    /**
     * Open and close two input streams.
     */
    @Test
    public void testOpenCloseTwoInputStreams()
    {
        // open without initial device
        long stream1 = openTestStream(mTestStreams[0], CConfigMgr.AUDIO_DEVICE_BIT_IN);
        assertTrue("Failed to get stream1", stream1 >= 0);
        checkExpectedControlChange("SwitchG1", mTestControls[0][1]);
        assertEquals(mTestControls[0][1] + " not written correctly",
                     1,
                     mAlsaMock.getBool(mTestControls[0][1], 0));
        assertEquals("SwitchG1 not written correctly",
                     1,
                     mAlsaMock.getBool("SwitchG1", 0));
        mAlsaMock.clearChangedFlag(mTestControls[0][1]);
        mAlsaMock.clearChangedFlag("SwitchG1");

        long stream2 = openTestStream(mTestStreams[1], CConfigMgr.AUDIO_DEVICE_BIT_IN);
        assertTrue("Failed to get stream2", stream2 >= 0);
        checkExpectedControlChange(mTestControls[1][1]);
        assertEquals(mTestControls[1][1] + " not written correctly",
                     1,
                     mAlsaMock.getBool(mTestControls[1][1], 0));
        mAlsaMock.clearChangedFlag(mTestControls[1][1]);

        closeStream(stream1);
        checkExpectedControlChange(mTestControls[0][1]);
        assertEquals(mTestControls[0][1] + " not written correctly",
                     0,
                     mAlsaMock.getBool(mTestControls[0][1], 0));
        mAlsaMock.clearChangedFlag(mTestControls[0][1]);

        closeStream(stream2);
        checkExpectedControlChange("SwitchG1", mTestControls[1][1]);
        assertEquals(mTestControls[1][1] + " not written correctly",
                     0,
                     mAlsaMock.getBool(mTestControls[1][1], 0));
        assertEquals("SwitchG1 not written correctly",
                     0,
                     mAlsaMock.getBool("SwitchG1", 0));
    }

    /**
     * Open and close one input stream and one output stream.
     */
    @Test
    public void testOpenCloseOneInputOneOutputStream()
    {
        // open without initial device
        long in = openTestStream(mTestStreams[0], CConfigMgr.AUDIO_DEVICE_BIT_IN);
        assertTrue("Failed to get input stream", in >= 0);
        checkExpectedControlChange("SwitchG1", mTestControls[0][1]);
        assertEquals(mTestControls[0][1] + " not written correctly",
                     1,
                     mAlsaMock.getBool(mTestControls[0][1], 0));
        assertEquals("SwitchG1 not written correctly",
                     1,
                     mAlsaMock.getBool("SwitchG1", 0));
        mAlsaMock.clearChangedFlag(mTestControls[0][1]);
        mAlsaMock.clearChangedFlag("SwitchG1");

        long out = openTestStream(mTestStreams[1], CConfigMgr.AUDIO_DEVICE_NONE);
        assertTrue("Failed to get output stream", out >= 0);
        checkExpectedControlChange(mTestControls[1][0]);
        assertEquals(mTestControls[1][1] + " not written correctly",
                     1,
                     mAlsaMock.getBool(mTestControls[1][0], 0));
        mAlsaMock.clearChangedFlag(mTestControls[1][0]);

        closeStream(in);
        checkExpectedControlChange(mTestControls[0][1]);
        assertEquals(mTestControls[0][1] + " not written correctly",
                     0,
                     mAlsaMock.getBool(mTestControls[0][1], 0));
        mAlsaMock.clearChangedFlag(mTestControls[0][1]);

        closeStream(out);
        checkExpectedControlChange("SwitchG1", mTestControls[1][0]);
        assertEquals(mTestControls[1][0] + " not written correctly",
                     0,
                     mAlsaMock.getBool(mTestControls[1][0], 0));
        assertEquals("SwitchG1 not written correctly",
                     0,
                     mAlsaMock.getBool("SwitchG1", 0));
    }

    /**
     * Open and close one normal output stream with another that only has an
     * enable path.
     */
    @Test
    public void testOpenCloseTwoOutputStreamsOneEnableOnly()
    {
        // open without initial device
        long stream1 = openTestStream(mTestStreams[0], CConfigMgr.AUDIO_DEVICE_NONE);
        assertTrue("Failed to get stream1", stream1 >= 0);
        checkExpectedControlChange("SwitchG1", mTestControls[0][0]);
        assertEquals(mTestControls[0][0] + " not written correctly",
                     1,
                     mAlsaMock.getBool(mTestControls[0][0], 0));
        assertEquals("SwitchG1 not written correctly",
                     1,
                     mAlsaMock.getBool("SwitchG1", 0));
        mAlsaMock.clearChangedFlag(mTestControls[0][0]);
        mAlsaMock.clearChangedFlag("SwitchG1");

        long stream2 = mConfigMgr.get_named_stream("nout_eno");
        assertTrue("Failed to get stream2", stream2 >= 0);
        checkExpectedControlChange("SwitchEO1");
        assertEquals("SwitchEO1 not written correctly",
                     1,
                     mAlsaMock.getBool("SwitchEO1", 0));
        mAlsaMock.clearChangedFlag("SwitchEO1");

        closeStream(stream1);
        checkExpectedControlChange(mTestControls[0][0]);
        assertEquals(mTestControls[0][0] + " not written correctly",
                     0,
                     mAlsaMock.getBool(mTestControls[0][0], 0));
        mAlsaMock.clearChangedFlag(mTestControls[0][0]);

        closeStream(stream2);
        checkExpectedControlChange("SwitchG1");
        assertEquals("SwitchG1 not written correctly",
                     0,
                     mAlsaMock.getBool("SwitchG1", 0));
    }

    /**
     * Open and close one normal output stream with another that only has an
     * disable path.
     */
    @Test
    public void testOpenCloseTwoOutputStreamsOneDisableOnly()
    {
        // open without initial device
        long stream1 = openTestStream(mTestStreams[0], CConfigMgr.AUDIO_DEVICE_NONE);
        assertTrue("Failed to get stream1", stream1 >= 0);
        checkExpectedControlChange("SwitchG1", mTestControls[0][0]);
        assertEquals(mTestControls[0][0] + " not written correctly",
                     1,
                     mAlsaMock.getBool(mTestControls[0][0], 0));
        assertEquals("SwitchG1 not written correctly",
                     1,
                     mAlsaMock.getBool("SwitchG1", 0));
        mAlsaMock.clearChangedFlag(mTestControls[0][0]);
        mAlsaMock.clearChangedFlag("SwitchG1");

        long stream2 = mConfigMgr.get_named_stream("nout_diso");
        assertTrue("Failed to get stream2", stream2 >= 0);
        checkNoControlsChanged();

        closeStream(stream1);
        checkExpectedControlChange(mTestControls[0][0]);
        assertEquals(mTestControls[0][0] + " not written correctly",
                     0,
                     mAlsaMock.getBool(mTestControls[0][0], 0));
        mAlsaMock.clearChangedFlag(mTestControls[0][0]);

        closeStream(stream2);
        checkExpectedControlChange("SwitchDO1", "SwitchG1");
        // SwitchDO1 writes a 1 on disable so we can prove it was written
        assertEquals("SwitchDO1 not written correctly",
                     1,
                     mAlsaMock.getBool("SwitchDO1", 0));
        assertEquals("SwitchG1 not written correctly",
                     0,
                     mAlsaMock.getBool("SwitchG1", 0));
    }

    /**
     * Open and close one normal input stream with another that only has an
     * enable path.
     */
    @Test
    public void testOpenCloseTwoInputStreamsOneEnableOnly()
    {
        // open without initial device
        long stream1 = openTestStream(mTestStreams[0], CConfigMgr.AUDIO_DEVICE_BIT_IN);
        assertTrue("Failed to get stream1", stream1 >= 0);
        checkExpectedControlChange("SwitchG1", mTestControls[0][1]);
        assertEquals(mTestControls[0][1] + " not written correctly",
                     1,
                     mAlsaMock.getBool(mTestControls[0][1], 0));
        assertEquals("SwitchG1 not written correctly",
                     1,
                     mAlsaMock.getBool("SwitchG1", 0));
        mAlsaMock.clearChangedFlag(mTestControls[0][1]);
        mAlsaMock.clearChangedFlag("SwitchG1");

        long stream2 = mConfigMgr.get_named_stream("nin_eno");
        assertTrue("Failed to get stream2", stream2 >= 0);
        checkExpectedControlChange("SwitchEO2");
        assertEquals("SwitchEO2 not written correctly",
                     1,
                     mAlsaMock.getBool("SwitchEO2", 0));
        mAlsaMock.clearChangedFlag("SwitchEO2");

        closeStream(stream1);
        checkExpectedControlChange(mTestControls[0][1]);
        assertEquals(mTestControls[0][1] + " not written correctly",
                     0,
                     mAlsaMock.getBool(mTestControls[0][1], 0));
        mAlsaMock.clearChangedFlag(mTestControls[0][1]);

        closeStream(stream2);
        checkExpectedControlChange("SwitchG1");
        assertEquals("SwitchG1 not written correctly",
                     0,
                     mAlsaMock.getBool("SwitchG1", 0));
    }

    /**
     * Open and close one normal input stream with another that only has an
     * disable path.
     */
    @Test
    public void testOpenCloseTwoInputStreamsOneDisableOnly()
    {
        // open without initial device
        long stream1 = openTestStream(mTestStreams[0], CConfigMgr.AUDIO_DEVICE_BIT_IN);
        assertTrue("Failed to get stream1", stream1 >= 0);
        checkExpectedControlChange("SwitchG1", mTestControls[0][1]);
        assertEquals(mTestControls[0][1] + " not written correctly",
                     1,
                     mAlsaMock.getBool(mTestControls[0][1], 0));
        assertEquals("SwitchG1 not written correctly",
                     1,
                     mAlsaMock.getBool("SwitchG1", 0));
        mAlsaMock.clearChangedFlag(mTestControls[0][1]);
        mAlsaMock.clearChangedFlag("SwitchG1");

        long stream2 = mConfigMgr.get_named_stream("nin_diso");
        assertTrue("Failed to get stream2", stream2 >= 0);
        checkNoControlsChanged();

        closeStream(stream1);
        checkExpectedControlChange(mTestControls[0][1]);
        assertEquals(mTestControls[0][1] + " not written correctly",
                     0,
                     mAlsaMock.getBool(mTestControls[0][1], 0));
        mAlsaMock.clearChangedFlag(mTestControls[0][1]);

        closeStream(stream2);
        checkExpectedControlChange("SwitchDO2", "SwitchG1");
        // SwitchDO2 writes a 1 on disable so we can check the value was written
        assertEquals("SwitchDO2 not written correctly",
                     1,
                     mAlsaMock.getBool("SwitchDO2", 0));
        assertEquals("SwitchG1 not written correctly",
                     0,
                     mAlsaMock.getBool("SwitchG1", 0));
    }
};
