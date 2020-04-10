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
import java.util.ArrayList;
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
 * Test that "on" and "off" &lt;path&gt; elements in &lt;device&gt; elements
 * are invoked correctly for multiple combinations of &lt;stream&gt; elements.
 * The testing assumes that &lt;ctl&gt; elements execute correctly.
 */
@RunWith(Parameterized.class)
public class ThcmDeviceOnOffTest
{
    private static final File sWorkFilesPath = ThcmPlatform.workFilesPath();
    private static final File sControlsFile = new File(sWorkFilesPath, "thcm_device_onoff_controls.csv");
    private static final File sXmlFile = new File(sWorkFilesPath, "thcm_device_onoff_config.xml");

    private static final String PCM_STREAM_TYPE = "pcm";
    private static final String COMPRESSED_STREAM_TYPE = "compressed";
    private static final String HW_STREAM_TYPE = "hw";

    private static List<String> sAllControls;

    private CAlsaMock mAlsaMock = new CAlsaMock();
    private CConfigMgr mConfigMgr = new CConfigMgr();

    private String[] mTestStreams;
    private String[][] mTestControls;

    @Parameterized.Parameters
    public static Collection parameters() {
        return Arrays.asList(new String[][] {
            { PCM_STREAM_TYPE, COMPRESSED_STREAM_TYPE },
            { COMPRESSED_STREAM_TYPE, PCM_STREAM_TYPE },
            { PCM_STREAM_TYPE, HW_STREAM_TYPE },
            { HW_STREAM_TYPE, PCM_STREAM_TYPE },
            { COMPRESSED_STREAM_TYPE, HW_STREAM_TYPE },
            { HW_STREAM_TYPE, COMPRESSED_STREAM_TYPE }
        });
    }

    public ThcmDeviceOnOffTest(String stream1, String stream2)
    {
        mTestStreams = new String[] { stream1, stream2 };
    }

    @BeforeClass
    public static void setUpClass() throws IOException
    {
        // Create a switch control name for every available device.
        // Don't make control same as stream name because that might
        // hide string matching bugs.
        sAllControls = new ArrayList<String>();
        for (String d : CConfigMgr.OUTPUT_DEVICES) {
            sAllControls.add(d + "Switch");
        }
        for (String d : CConfigMgr.INPUT_DEVICES) {
            sAllControls.add(d + "Switch");
        }

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
        for (String name : sAllControls) {
            writer.write(name + ",bool,1,0,0:1\n");
        }

        writer.close();
    }

    private static void createXmlFile() throws IOException
    {
        FileWriter writer = new FileWriter(sXmlFile);

        // Header elements
        writer.write("<audiohal>\n<mixer card=\"0\" />\n");

        for (String d : CConfigMgr.OUTPUT_DEVICES) {
            writer.write("<device name=\"" + d + "\">\n");
            writePathEntry(writer, "on", d + "Switch", 1);
            writePathEntry(writer, "off", d + "Switch", 0);
            writer.write("</device>\n");
        }

        for (String d : CConfigMgr.INPUT_DEVICES) {
            writer.write("<device name=\"" + d + "\">\n");
            writePathEntry(writer, "on", d + "Switch", 1);
            writePathEntry(writer, "off", d + "Switch", 0);
            writer.write("</device>\n");
        }

        // Test streams
        writeStreamEntry(writer, null, "pcm", "out");
        writeStreamEntry(writer, null, "compress", "out");
        writeStreamEntry(writer, "nout", "hw", "out");
        writeStreamEntry(writer, null, "pcm", "in");
        writeStreamEntry(writer, null, "compress", "in");
        writeStreamEntry(writer, "nin", "hw", "in");

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
                                         String dir) throws IOException
    {
        String nameAttr = "";
        if (name != null) {
            nameAttr = "name=\"" + name + "\" ";
        }

        writer.write("<stream " + nameAttr + "type=\"" + type + "\" dir=\"" +
                     dir + "\" >\n");
        writer.write("</stream>\n");
    }

    private void checkExpectedControlChange(String expectedChanged) {
        for (String name : sAllControls) {
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
        for (String name : sAllControls) {
            if (expectedChanged1.equals(name) || expectedChanged2.equals(name)) {
                assertTrue(name + " was not changed", mAlsaMock.isChanged(name));
            } else {
                assertFalse(name + " should not have changed",
                            mAlsaMock.isChanged(name));
            }
        }
    }

    private void checkExpectedControlChange(List<String> expectedChanged) {
        for (String name : sAllControls) {
            if (expectedChanged.contains(name)) {
                assertTrue(name + " was not changed", mAlsaMock.isChanged(name));
            } else {
                assertFalse(name + " should not have changed",
                            mAlsaMock.isChanged(name));
            }
        }
    }

    private void checkNoControlsChanged() {
        for (String name : sAllControls) {
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
     * Open and close a single output stream to each available initial device.
     */
    @Test
    public void testOneOutputStreamSingleInitialDevice()
    {
        for (String d : CConfigMgr.OUTPUT_DEVICES) {
            long deviceBits = CConfigMgr.deviceFromName(d);
            String control = d + "Switch";

            long stream = openTestStream(mTestStreams[0], deviceBits);
            assertTrue("Failed to get stream", stream >= 0);
            checkExpectedControlChange(control);
            assertEquals(control + " not written correctly",
                         1,
                         mAlsaMock.getBool(control, 0));
            mAlsaMock.clearChangedFlag(control);

            closeStream(stream);
            checkExpectedControlChange(control);
            assertEquals(control + " not written correctly",
                         0,
                         mAlsaMock.getBool(control, 0));
            mAlsaMock.clearChangedFlag(control);
        }
    }

    /**
     * Open single output stream multiple initial devices.
     */
    @Test
    public void testOneOutputStreamMultipleInitialDevices()
    {
        long deviceBits = 0;
        List<String> controls = new ArrayList<String>();
        for (String d : CConfigMgr.OUTPUT_DEVICES) {
            deviceBits |= CConfigMgr.deviceFromName(d);
            controls.add(d + "Switch");

            long stream = openTestStream(mTestStreams[0], deviceBits);
            assertTrue("Failed to get stream", stream >= 0);
            checkExpectedControlChange(controls);
            for (String c : controls) {
                assertEquals(c + " not written correctly",
                             1,
                             mAlsaMock.getBool(c, 0));
                mAlsaMock.clearChangedFlag(c);
            }

            closeStream(stream);
            checkExpectedControlChange(controls);
            for (String c : controls) {
                assertEquals(c + " not written correctly",
                             0,
                             mAlsaMock.getBool(c, 0));
                mAlsaMock.clearChangedFlag(c);
            }
        }
    }

    /**
     * Open single input stream multiple initial devices.
     */
    @Test
    public void testOneInputStreamMultipleInitialDevices()
    {
        long deviceBits = 0;
        List<String> controls = new ArrayList<String>();
        for (String d : CConfigMgr.INPUT_DEVICES) {
            deviceBits |= CConfigMgr.deviceFromName(d);
            controls.add(d + "Switch");

            long stream = openTestStream(mTestStreams[0], deviceBits);
            assertTrue("Failed to get stream", stream >= 0);
            checkExpectedControlChange(controls);
            for (String c : controls) {
                assertEquals(c + " not written correctly",
                             1,
                             mAlsaMock.getBool(c, 0));
                mAlsaMock.clearChangedFlag(c);
            }

            closeStream(stream);
            checkExpectedControlChange(controls);
            for (String c : controls) {
                assertEquals(c + " not written correctly",
                             0,
                             mAlsaMock.getBool(c, 0));
                mAlsaMock.clearChangedFlag(c);
            }
        }
    }

    /**
     * Open and close a single input stream to each available initial device.
     */
    @Test
    public void testOneInputStreamSingleInitialDevice()
    {
        for (String d : CConfigMgr.INPUT_DEVICES) {
            long deviceBits = CConfigMgr.deviceFromName(d);
            String control = d + "Switch";

            long stream = openTestStream(mTestStreams[0], deviceBits);
            assertTrue("Failed to get stream", stream >= 0);
            checkExpectedControlChange(control);
            assertEquals(control + " not written correctly",
                         1,
                         mAlsaMock.getBool(control, 0));
            mAlsaMock.clearChangedFlag(control);

            closeStream(stream);
            checkExpectedControlChange(control);
            assertEquals(control + " not written correctly",
                         0,
                         mAlsaMock.getBool(control, 0));
            mAlsaMock.clearChangedFlag(control);
        }
    }

    /**
     * Open and close two output streams to different initial devices.
     */
    @Test
    public void testTwoOutputStreamsSingleInitialDevice()
    {
        final int numDevices = CConfigMgr.OUTPUT_DEVICES.length;
        for (int i = 0; i < numDevices; ++i) {
            long bits1 = CConfigMgr.deviceFromName(CConfigMgr.OUTPUT_DEVICES[i]);
            String control1 = CConfigMgr.OUTPUT_DEVICES[i] + "Switch";

            // Reverse order for other stream to ensure this is testing
            // different devices
            long bits2 = CConfigMgr.deviceFromName(CConfigMgr.OUTPUT_DEVICES[numDevices - 1 - i]);
            String control2 = CConfigMgr.OUTPUT_DEVICES[numDevices - 1 - i] + "Switch";

            // Skip this iteration if the bits are the same for both streams
            if (bits1 == bits2) {
                continue;
            }

            long stream1 = openTestStream(mTestStreams[0], bits1);
            assertTrue("Failed to get stream1", stream1 >= 0);
            checkExpectedControlChange(control1);
            assertEquals(control1 + " not written correctly",
                         1,
                         mAlsaMock.getBool(control1, 0));
            mAlsaMock.clearChangedFlag(control1);

            long stream2 = openTestStream(mTestStreams[1], bits2);
            assertTrue("Failed to get stream2", stream2 >= 0);
            checkExpectedControlChange(control2);
            assertEquals(control2 + " not written correctly",
                         1,
                         mAlsaMock.getBool(control2, 0));
            mAlsaMock.clearChangedFlag(control2);

            closeStream(stream1);
            checkExpectedControlChange(control1);
            assertEquals(control1 + " not written correctly",
                         0,
                         mAlsaMock.getBool(control1, 0));
            mAlsaMock.clearChangedFlag(control1);

            closeStream(stream2);
            checkExpectedControlChange(control2);
            assertEquals(control2 + " not written correctly",
                         0,
                         mAlsaMock.getBool(control2, 0));
            mAlsaMock.clearChangedFlag(control2);
        }
    }

    /**
     * Open and close one input stream and one output stream to different
     * initial devices.
     */
    @Test
    public void testInputOutputStreamsSingleInitialDevice()
    {
        // Only do iterations that can pick an input and an output device
        final int numDevices = Math.min(CConfigMgr.OUTPUT_DEVICES.length,
                                        CConfigMgr.INPUT_DEVICES.length);
        for (int i = 0; i < numDevices; ++i) {
            long outBits = CConfigMgr.deviceFromName(CConfigMgr.OUTPUT_DEVICES[i]);
            String outControl = CConfigMgr.OUTPUT_DEVICES[i] + "Switch";

            // Reverse order for input stream to ensure this is testing
            // different device bit patterns.
            long inBits = CConfigMgr.deviceFromName(CConfigMgr.INPUT_DEVICES[numDevices - 1 - i]);
            String inControl = CConfigMgr.INPUT_DEVICES[numDevices - 1 - i] + "Switch";

            long outStream = openTestStream(mTestStreams[0], outBits);
            assertTrue("Failed to get output stream", outStream >= 0);
            checkExpectedControlChange(outControl);
            assertEquals(outControl + " not written correctly",
                         1,
                         mAlsaMock.getBool(outControl, 0));
            mAlsaMock.clearChangedFlag(outControl);

            long inStream = openTestStream(mTestStreams[1], inBits);
            assertTrue("Failed to get input stream", inStream >= 0);
            checkExpectedControlChange(inControl);
            assertEquals(inControl + " not written correctly",
                         1,
                         mAlsaMock.getBool(inControl, 0));
            mAlsaMock.clearChangedFlag(inControl);

            closeStream(outStream);
            checkExpectedControlChange(outControl);
            assertEquals(outControl + " not written correctly",
                         0,
                         mAlsaMock.getBool(outControl, 0));
            mAlsaMock.clearChangedFlag(outControl);

            closeStream(inStream);
            checkExpectedControlChange(inControl);
            assertEquals(inControl + " not written correctly",
                         0,
                         mAlsaMock.getBool(inControl, 0));
            mAlsaMock.clearChangedFlag(inControl);
        }
    }

    /**
     * Open a single output stream and connect to multiple devices sequentially.
     */
    @Test
    public void testOneOutputStreamApplyRouteSequential()
    {
        long stream = openTestStream(mTestStreams[0], CConfigMgr.AUDIO_DEVICE_NONE);
        assertTrue("Failed to get stream", stream >= 0);

        // Add devices
        long deviceBits = 0;
        for (String d : CConfigMgr.OUTPUT_DEVICES) {
            deviceBits |= CConfigMgr.deviceFromName(d);
            String control = d + "Switch";

            mConfigMgr.apply_route(stream, deviceBits);
            checkExpectedControlChange(control);
            assertEquals(control + " not written correctly",
                         1,
                         mAlsaMock.getBool(control, 0));
            mAlsaMock.clearChangedFlag(control);
        }

        // Remove devices
        for (String d : CConfigMgr.OUTPUT_DEVICES) {
            deviceBits &= ~CConfigMgr.deviceFromName(d);
            String control = d + "Switch";

            mConfigMgr.apply_route(stream, deviceBits);
            checkExpectedControlChange(control);
            assertEquals(control + " not written correctly",
                         0,
                         mAlsaMock.getBool(control, 0));
            mAlsaMock.clearChangedFlag(control);
        }
    }

    /**
     * Close an output stream that is connected to multiple devices.
     */
    @Test
    public void testCloseOneOutputStreamMultipleDevices()
    {
        long stream = openTestStream(mTestStreams[0], CConfigMgr.AUDIO_DEVICE_NONE);
        assertTrue("Failed to get stream", stream >= 0);

        // Accumulate devices
        long deviceBits = 0;
        List<String> controls = new ArrayList<String>();
        for (String d : CConfigMgr.OUTPUT_DEVICES) {
            deviceBits |= CConfigMgr.deviceFromName(d);
            controls.add(d + "Switch");
        }

        mConfigMgr.apply_route(stream, deviceBits);
        checkExpectedControlChange(controls);
        for (String c : controls) {
            assertEquals(c + " not written correctly",
                         1,
                         mAlsaMock.getBool(c, 0));
            mAlsaMock.clearChangedFlag(c);
        }

        closeStream(stream);
        checkExpectedControlChange(controls);
        for (String c : controls) {
            assertEquals(c + " not written correctly",
                         0,
                         mAlsaMock.getBool(c, 0));
        }
    }

    /**
     * Open a single input stream and connect to multiple devices sequentially.
     */
    @Test
    public void testOneInputStreamApplyRouteSequential()
    {
        long stream = openTestStream(mTestStreams[0], CConfigMgr.AUDIO_DEVICE_BIT_IN);
        assertTrue("Failed to get stream", stream >= 0);

        // Add devices
        long deviceBits = 0;
        for (String d : CConfigMgr.INPUT_DEVICES) {
            deviceBits |= CConfigMgr.deviceFromName(d);
            String control = d + "Switch";

            mConfigMgr.apply_route(stream, deviceBits);
            checkExpectedControlChange(control);
            assertEquals(control + " not written correctly",
                         1,
                         mAlsaMock.getBool(control, 0));
            mAlsaMock.clearChangedFlag(control);
        }

        // Remove devices
        for (String d : CConfigMgr.INPUT_DEVICES) {
            deviceBits &= ~CConfigMgr.deviceFromName(d);
            // Restore the BIT_IN flag removed by previous line
            deviceBits |= CConfigMgr.AUDIO_DEVICE_BIT_IN;
            String control = d + "Switch";

            mConfigMgr.apply_route(stream, deviceBits);
            checkExpectedControlChange(control);
            assertEquals(control + " not written correctly",
                         0,
                         mAlsaMock.getBool(control, 0));
            mAlsaMock.clearChangedFlag(control);
        }
    }

    /**
     * Close an input stream that is connected to multiple devices.
     */
    @Test
    public void testCloseOneInputStreamMultipleDevices()
    {
        long stream = openTestStream(mTestStreams[0], CConfigMgr.AUDIO_DEVICE_BIT_IN);
        assertTrue("Failed to get stream", stream >= 0);

        // Accumulate devices
        long deviceBits = 0;
        List<String> controls = new ArrayList<String>();
        for (String d : CConfigMgr.INPUT_DEVICES) {
            deviceBits |= CConfigMgr.deviceFromName(d);
            controls.add(d + "Switch");
        }

        mConfigMgr.apply_route(stream, deviceBits);
        checkExpectedControlChange(controls);
        for (String c : controls) {
            assertEquals(c + " not written correctly",
                         1,
                         mAlsaMock.getBool(c, 0));
            mAlsaMock.clearChangedFlag(c);
        }

        closeStream(stream);
        checkExpectedControlChange(controls);
        for (String c : controls) {
            assertEquals(c + " not written correctly",
                         0,
                         mAlsaMock.getBool(c, 0));
        }
    }

    /**
     * Test that output on/off paths only invoked for first/last connected
     * stream when using apply_route().
     */
    @Test
    public void testOutputApplyRouteRefCount()
    {
        long stream1 = openTestStream(mTestStreams[0], CConfigMgr.AUDIO_DEVICE_NONE);
        assertTrue("Failed to get stream1", stream1 >= 0);

        long stream2 = openTestStream(mTestStreams[1], CConfigMgr.AUDIO_DEVICE_NONE);
        assertTrue("Failed to get stream2", stream2 >= 0);

        long deviceBits = 0;
        for (String d : CConfigMgr.OUTPUT_DEVICES) {
            deviceBits |= CConfigMgr.deviceFromName(d);
            String control = d + "Switch";

            // First connected stream should invoke the "on" path
            mConfigMgr.apply_route(stream1, deviceBits);
            checkExpectedControlChange(control);
            assertEquals(control + " not written correctly",
                         1,
                         mAlsaMock.getBool(control, 0));
            mAlsaMock.clearChangedFlag(control);

            // Second connected stream will not invoke "on" path
            mConfigMgr.apply_route(stream2, deviceBits);
            checkNoControlsChanged();
        }

        for (String d : CConfigMgr.OUTPUT_DEVICES) {
            deviceBits &= ~CConfigMgr.deviceFromName(d);
            String control = d + "Switch";

            // First disconnected stream will not invoke "off" path
            mConfigMgr.apply_route(stream1, deviceBits);
            checkNoControlsChanged();

            // Last disconnected stream should invoke the "off" path
            mConfigMgr.apply_route(stream2, deviceBits);
            checkExpectedControlChange(control);
            assertEquals(control + " not written correctly",
                         0,
                         mAlsaMock.getBool(control, 0));
            mAlsaMock.clearChangedFlag(control);
        }
    }

    /**
     * Test that input on/off paths only invoked for first/last connected
     * stream when using apply_route().
     */
    @Test
    public void testInputApplyRouteRefCount()
    {
        long stream1 = openTestStream(mTestStreams[0], CConfigMgr.AUDIO_DEVICE_BIT_IN);
        assertTrue("Failed to get stream1", stream1 >= 0);

        long stream2 = openTestStream(mTestStreams[1], CConfigMgr.AUDIO_DEVICE_BIT_IN);
        assertTrue("Failed to get stream2", stream2 >= 0);

        long deviceBits = 0;
        for (String d : CConfigMgr.INPUT_DEVICES) {
            deviceBits |= CConfigMgr.deviceFromName(d);
            String control = d + "Switch";

            // First connected stream should invoke the "on" path
            mConfigMgr.apply_route(stream1, deviceBits);
            checkExpectedControlChange(control);
            assertEquals(control + " not written correctly",
                         1,
                         mAlsaMock.getBool(control, 0));
            mAlsaMock.clearChangedFlag(control);

            // Second connected stream will not invoke "on" path
            mConfigMgr.apply_route(stream2, deviceBits);
            checkNoControlsChanged();
        }

        for (String d : CConfigMgr.INPUT_DEVICES) {
            deviceBits &= ~CConfigMgr.deviceFromName(d);
            // Restore the BIT_IN flag removed by previous line
            deviceBits |= CConfigMgr.AUDIO_DEVICE_BIT_IN;
            String control = d + "Switch";

            // First disconnected stream will not invoke "off" path
            mConfigMgr.apply_route(stream1, deviceBits);
            checkNoControlsChanged();

            // Last disconnected stream should invoke the "off" path
            mConfigMgr.apply_route(stream2, deviceBits);
            checkExpectedControlChange(control);
            assertEquals(control + " not written correctly",
                         0,
                         mAlsaMock.getBool(control, 0));
            mAlsaMock.clearChangedFlag(control);
        }
    }

    /**
     * Test that output off paths are only invoked for last disconnected
     * stream when closing streams.
     */
    @Test
    public void testOutputCloseRefCount()
    {
        long deviceBits = 0;
        List<String> controls = new ArrayList<String>();
        for (String d : CConfigMgr.OUTPUT_DEVICES) {
            deviceBits |= CConfigMgr.deviceFromName(d);
            controls.add(d + "Switch");

            long stream1 = openTestStream(mTestStreams[0], deviceBits);
            assertTrue("Failed to get stream1", stream1 >= 0);

            long stream2 = openTestStream(mTestStreams[1], deviceBits);
            assertTrue("Failed to get stream2", stream2 >= 0);

            checkExpectedControlChange(controls);
            for (String c : controls) {
                assertEquals(c + " not written correctly",
                             1,
                             mAlsaMock.getBool(c, 0));
                mAlsaMock.clearChangedFlag(c);
            }

            // First closed stream will not invoke "off" path
            closeStream(stream1);
            checkNoControlsChanged();

            // Last closed stream should invoke the "off" path
            closeStream(stream2);
            checkExpectedControlChange(controls);
            for (String c : controls) {
                assertEquals(c + " not written correctly",
                             0,
                             mAlsaMock.getBool(c, 0));
                mAlsaMock.clearChangedFlag(c);
            }
        }
    }

    /**
     * Test that input off paths are only invoked for last disconnected
     * stream when closing streams.
     */
    @Test
    public void testInputCloseRefCount()
    {
        long deviceBits = 0;
        List<String> controls = new ArrayList<String>();
        for (String d : CConfigMgr.INPUT_DEVICES) {
            deviceBits |= CConfigMgr.deviceFromName(d);
            controls.add(d + "Switch");

            long stream1 = openTestStream(mTestStreams[0], deviceBits);
            assertTrue("Failed to get stream1", stream1 >= 0);

            long stream2 = openTestStream(mTestStreams[1], deviceBits);
            assertTrue("Failed to get stream2", stream2 >= 0);

            checkExpectedControlChange(controls);
            for (String c : controls) {
                assertEquals(c + " not written correctly",
                             1,
                             mAlsaMock.getBool(c, 0));
                mAlsaMock.clearChangedFlag(c);
            }

            // First closed stream will not invoke "off" path
            closeStream(stream1);
            checkNoControlsChanged();

            // Last closed stream should invoke the "off" path
            closeStream(stream2);
            checkExpectedControlChange(controls);
            for (String c : controls) {
                assertEquals(c + " not written correctly",
                             0,
                             mAlsaMock.getBool(c, 0));
                mAlsaMock.clearChangedFlag(c);
            }
        }
    }
};
