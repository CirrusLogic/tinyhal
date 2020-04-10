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
 * Test that get_current_routes() correctly reports the devices that a stream
 * is currently connected to.
 */
@RunWith(Parameterized.class)
public class ThcmReportedRoutesTest
{
    private static final File sWorkFilesPath = ThcmPlatform.workFilesPath();
    private static final File sControlsFile = new File(sWorkFilesPath, "thcm_reported_routes_controls.csv");
    private static final File sXmlFile = new File(sWorkFilesPath, "thcm_reported_routes_config.xml");

    private static final String PCM_STREAM_TYPE = "pcm";
    private static final String COMPRESSED_STREAM_TYPE = "compressed";
    private static final String HW_STREAM_TYPE = "hw";

    private CAlsaMock mAlsaMock = new CAlsaMock();
    private CConfigMgr mConfigMgr = new CConfigMgr();

    private String[] mTestStreams;

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

    public ThcmReportedRoutesTest(String stream1, String stream2)
    {
        mTestStreams = new String[] { stream1, stream2 };
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

        // dummy control
        writer.write("VolA,int,1,0,0:32\n");

        writer.close();
    }

    private static void createXmlFile() throws IOException
    {
        FileWriter writer = new FileWriter(sXmlFile);

        // Header elements
        writer.write("<audiohal>\n<mixer card=\"0\" />\n");

        for (String d : CConfigMgr.OUTPUT_DEVICES) {
            writer.write("<device name=\"" + d + "\">\n");
            writer.write("</device>\n");
        }

        for (String d : CConfigMgr.INPUT_DEVICES) {
            writer.write("<device name=\"" + d + "\">\n");
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
     * Open one output stream many times with different initial devices.
     */
    @Test
    public void testOneOutputStreamInitialDevice()
    {
        long deviceBits = 0;
        for (String d : CConfigMgr.OUTPUT_DEVICES) {
            deviceBits |= CConfigMgr.deviceFromName(d);
            long stream = openTestStream(mTestStreams[0], deviceBits);
            assertTrue("Failed to get stream", stream >= 0);
            assertEquals("Wrong get_current_routes",
                         deviceBits,
                         mConfigMgr.get_current_routes(stream));
            closeStream(stream);
        }
    }

    /**
     * Open one input stream many times with different initial devices.
     */
    @Test
    public void testOneInputStreamInitialDevice()
    {
        long deviceBits = 0;
        for (String d : CConfigMgr.INPUT_DEVICES) {
            deviceBits |= CConfigMgr.deviceFromName(d);
            long stream = openTestStream(mTestStreams[0], deviceBits);
            assertTrue("Failed to get stream", stream >= 0);
            assertEquals("Wrong get_current_routes",
                         deviceBits,
                         mConfigMgr.get_current_routes(stream));
            closeStream(stream);
        }
    }

    /**
     * Open one input and one output stream many times with different initial
     * devices.
     */
    @Test
    public void testOneInputOneOutputStreamInitialDevice()
    {
        long outBits = 0;
        long inBits = 0;
        int maxNumDevices = Math.max(CConfigMgr.OUTPUT_DEVICES.length,
                                     CConfigMgr.INPUT_DEVICES.length);
        for (int i = 0; i < maxNumDevices; ++i) {
            if (i < CConfigMgr.OUTPUT_DEVICES.length) {
                outBits |= CConfigMgr.deviceFromName(CConfigMgr.OUTPUT_DEVICES[i]);
            }
            // Add input devices in opposite order to guarantee that it isn't
            // generating the same bit sequence as the outputs.
            if (i < CConfigMgr.INPUT_DEVICES.length) {
                int j = CConfigMgr.INPUT_DEVICES.length - 1 - i;
                inBits |= CConfigMgr.deviceFromName(CConfigMgr.INPUT_DEVICES[j]);
            }
            long in = openTestStream(mTestStreams[0], inBits);
            assertTrue("Failed to get in streeam", in >= 0);
            long out = openTestStream(mTestStreams[1], outBits);
            assertTrue("Failed to get out streeam", out >= 0);
            assertEquals("Wrong get_current_routes for input",
                         inBits,
                         mConfigMgr.get_current_routes(in));
            assertEquals("Wrong get_current_routes for output",
                         outBits,
                         mConfigMgr.get_current_routes(out));
            closeStream(in);
            closeStream(out);
        }
    }

    /**
     * Open two output streams many times with different initial devices.
     */
    @Test
    public void testTwoOutputStreamsInitialDevice()
    {
        long out1Bits = 0;
        long out2Bits = 0;
        for (int i = 0; i < CConfigMgr.OUTPUT_DEVICES.length; ++i) {
            out1Bits |= CConfigMgr.deviceFromName(CConfigMgr.OUTPUT_DEVICES[i]);

            // Add out2 devices in opposite order to guarantee that it isn't
            // generating the same bit sequence.
            int j = CConfigMgr.OUTPUT_DEVICES.length - 1 - i;
            out2Bits |= CConfigMgr.deviceFromName(CConfigMgr.OUTPUT_DEVICES[j]);

            long out1 = openTestStream(mTestStreams[0], out1Bits);
            assertTrue("Failed to get in streeam", out1 >= 0);
            long out2 = openTestStream(mTestStreams[1], out2Bits);
            assertTrue("Failed to get out streeam", out2 >= 0);
            assertEquals("Wrong get_current_routes for out1",
                         out1Bits,
                         mConfigMgr.get_current_routes(out1));
            assertEquals("Wrong get_current_routes for out2",
                         out2Bits,
                         mConfigMgr.get_current_routes(out2));
            closeStream(out1);
            closeStream(out2);
        }
    }

    /**
     * Open two input streams many times with different initial devices.
     */
    @Test
    public void testTwoInputStreamsInitialDevice()
    {
        long in1Bits = 0;
        long in2Bits = 0;
        for (int i = 0; i < CConfigMgr.INPUT_DEVICES.length; ++i) {
            in1Bits |= CConfigMgr.deviceFromName(CConfigMgr.INPUT_DEVICES[i]);

            // Add out2 devices in opposite order to guarantee that it isn't
            // generating the same bit sequence.
            int j = CConfigMgr.INPUT_DEVICES.length - 1 - i;
            in2Bits |= CConfigMgr.deviceFromName(CConfigMgr.INPUT_DEVICES[j]);

            long in1 = openTestStream(mTestStreams[0], in1Bits);
            assertTrue("Failed to get in streeam", in1 >= 0);
            long in2 = openTestStream(mTestStreams[1], in2Bits);
            assertTrue("Failed to get out streeam", in2 >= 0);
            assertEquals("Wrong get_current_routes for in1",
                         in1Bits,
                         mConfigMgr.get_current_routes(in1));
            assertEquals("Wrong get_current_routes for in2",
                         in2Bits,
                         mConfigMgr.get_current_routes(in2));
            closeStream(in1);
            closeStream(in2);
        }
    }

    /**
     * Open one output stream and apply a sequence of devices.
     */
    @Test
    public void testOneOutputStreamApplyRoute()
    {
        long stream = openTestStream(mTestStreams[0], CConfigMgr.AUDIO_DEVICE_NONE);
        assertTrue("Failed to get stream", stream >= 0);

        // Add devices
        long deviceBits = 0;
        for (String d : CConfigMgr.OUTPUT_DEVICES) {
            deviceBits |= CConfigMgr.deviceFromName(d);
            mConfigMgr.apply_route(stream, deviceBits);
            assertEquals("Wrong get_current_routes",
                         deviceBits,
                         mConfigMgr.get_current_routes(stream));
        }

        // Remove the devices
        for (String d : CConfigMgr.OUTPUT_DEVICES) {
            deviceBits &= ~CConfigMgr.deviceFromName(d);
            mConfigMgr.apply_route(stream, deviceBits);
            assertEquals("Wrong get_current_routes",
                         deviceBits,
                         mConfigMgr.get_current_routes(stream));
        }

        closeStream(stream);
    }

    /**
     * Open one input stream and apply a sequence of devices.
     */
    @Test
    public void testOneInputStreamApplyRoute()
    {
        long stream = openTestStream(mTestStreams[0], CConfigMgr.AUDIO_DEVICE_BIT_IN);
        assertTrue("Failed to get stream", stream >= 0);

        // Add devices
        long deviceBits = 0;
        for (String d : CConfigMgr.INPUT_DEVICES) {
            deviceBits |= CConfigMgr.deviceFromName(d);
            mConfigMgr.apply_route(stream, deviceBits);
            assertEquals("Wrong get_current_routes",
                         deviceBits,
                         mConfigMgr.get_current_routes(stream));
        }

        // Remove devices
        for (String d : CConfigMgr.INPUT_DEVICES) {
            deviceBits &= ~CConfigMgr.deviceFromName(d);
            // Restore the BIT_IN flag
            deviceBits |= CConfigMgr.AUDIO_DEVICE_BIT_IN;
            mConfigMgr.apply_route(stream, deviceBits);
            assertEquals("Wrong get_current_routes",
                         deviceBits,
                         mConfigMgr.get_current_routes(stream));
        }

        closeStream(stream);
    }

    /**
     * Open one input and one output stream and apply a sequence of devices.
     */
    @Test
    public void testOneInputOneOutputStreamApplyRoute()
    {
        long in = openTestStream(mTestStreams[0], CConfigMgr.AUDIO_DEVICE_BIT_IN);
        assertTrue("Failed to get input stream", in >= 0);

        long out = openTestStream(mTestStreams[1], CConfigMgr.AUDIO_DEVICE_NONE);
        assertTrue("Failed to get output stream", out >= 0);

        // Add devices
        long outBits = 0;
        long inBits = 0;
        int maxNumDevices = Math.max(CConfigMgr.OUTPUT_DEVICES.length,
                                     CConfigMgr.INPUT_DEVICES.length);
        for (int i = 0; i < maxNumDevices; ++i) {
            if (i < CConfigMgr.OUTPUT_DEVICES.length) {
                outBits |= CConfigMgr.deviceFromName(CConfigMgr.OUTPUT_DEVICES[i]);
            }
            // Add input devices in opposite order to guarantee that it isn't
            // generating the same bit sequence as the outputs.
            if (i < CConfigMgr.INPUT_DEVICES.length) {
                int j = CConfigMgr.INPUT_DEVICES.length - 1 - i;
                inBits |= CConfigMgr.deviceFromName(CConfigMgr.INPUT_DEVICES[j]);
            }

            mConfigMgr.apply_route(in, inBits);
            assertEquals("Wrong get_current_routes for input",
                         inBits,
                         mConfigMgr.get_current_routes(in));
            mConfigMgr.apply_route(out, outBits);
            assertEquals("Wrong get_current_routes for output",
                         outBits,
                         mConfigMgr.get_current_routes(out));
        }

        // Remove devices
        for (int i = 0; i < maxNumDevices; ++i) {
            if (i < CConfigMgr.OUTPUT_DEVICES.length) {
                outBits &= ~CConfigMgr.deviceFromName(CConfigMgr.OUTPUT_DEVICES[i]);
            }
            // Add input devices in opposite order to guarantee that it isn't
            // generating the same bit sequence as the outputs.
            if (i < CConfigMgr.INPUT_DEVICES.length) {
                int j = CConfigMgr.INPUT_DEVICES.length - 1 - i;
                inBits &= ~CConfigMgr.deviceFromName(CConfigMgr.INPUT_DEVICES[j]);
                // Restore the BIT_IN flag
                inBits |= CConfigMgr.AUDIO_DEVICE_BIT_IN;
            }

            mConfigMgr.apply_route(in, inBits);
            assertEquals("Wrong get_current_routes for input",
                         inBits,
                         mConfigMgr.get_current_routes(in));
            mConfigMgr.apply_route(out, outBits);
            assertEquals("Wrong get_current_routes for output",
                         outBits,
                         mConfigMgr.get_current_routes(out));
        }

        closeStream(in);
        closeStream(out);
    }

    /**
     * Open two output stream and apply a sequence of devices to one stream.
     */
    @Test
    public void testTwoOutputStreamApplyRouteOneStream()
    {
        long stream1 = openTestStream(mTestStreams[0], CConfigMgr.AUDIO_DEVICE_NONE);
        assertTrue("Failed to get stream1", stream1 >= 0);

        long stream2 = openTestStream(mTestStreams[1], CConfigMgr.AUDIO_DEVICE_NONE);
        assertTrue("Failed to get stream2", stream2 >= 0);

        // Add devices
        long deviceBits = 0;
        for (String d : CConfigMgr.OUTPUT_DEVICES) {
            deviceBits |= CConfigMgr.deviceFromName(d);
            mConfigMgr.apply_route(stream1, deviceBits);
            assertEquals("Wrong get_current_routes for stream1",
                         deviceBits,
                         mConfigMgr.get_current_routes(stream1));
            assertEquals("Wrong get_current_routes for stream2",
                         CConfigMgr.AUDIO_DEVICE_NONE,
                         mConfigMgr.get_current_routes(stream2));
        }

        // Remove the devices
        for (String d : CConfigMgr.OUTPUT_DEVICES) {
            deviceBits &= ~CConfigMgr.deviceFromName(d);
            mConfigMgr.apply_route(stream1, deviceBits);
            assertEquals("Wrong get_current_routes for stream1",
                         deviceBits,
                         mConfigMgr.get_current_routes(stream1));
            assertEquals("Wrong get_current_routes for stream2",
                         CConfigMgr.AUDIO_DEVICE_NONE,
                         mConfigMgr.get_current_routes(stream2));
        }

        closeStream(stream1);
        closeStream(stream2);
    }

    /**
     * Open two input streams and apply a sequence of devices to one stream.
     */
    @Test
    public void testTwoInputStreamApplyRouteOneStream()
    {
        long stream1 = openTestStream(mTestStreams[0], CConfigMgr.AUDIO_DEVICE_BIT_IN);
        assertTrue("Failed to get stream1", stream1 >= 0);

        long stream2 = openTestStream(mTestStreams[1], CConfigMgr.AUDIO_DEVICE_BIT_IN);
        assertTrue("Failed to get stream2", stream2 >= 0);

        // Add devices
        long deviceBits = 0;
        for (String d : CConfigMgr.INPUT_DEVICES) {
            deviceBits |= CConfigMgr.deviceFromName(d);
            mConfigMgr.apply_route(stream1, deviceBits);
            assertEquals("Wrong get_current_routes for stream1",
                         deviceBits,
                         mConfigMgr.get_current_routes(stream1));
            assertEquals("Wrong get_current_routes for stream2",
                         CConfigMgr.AUDIO_DEVICE_BIT_IN,
                         mConfigMgr.get_current_routes(stream2));
        }

        // Remove the devices
        for (String d : CConfigMgr.INPUT_DEVICES) {
            deviceBits &= ~CConfigMgr.deviceFromName(d);
            // Restore the BIT_IN flag
            deviceBits |= CConfigMgr.AUDIO_DEVICE_BIT_IN;
            mConfigMgr.apply_route(stream1, deviceBits);
            assertEquals("Wrong get_current_routes for stream1",
                         deviceBits,
                         mConfigMgr.get_current_routes(stream1));
            assertEquals("Wrong get_current_routes for stream2",
                         CConfigMgr.AUDIO_DEVICE_BIT_IN,
                         mConfigMgr.get_current_routes(stream2));
        }

        closeStream(stream1);
        closeStream(stream2);
    }
};
