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
 * Tests that the number of open instances of a &lt;stream&gt; can be limited
 * using the <code>instances</code> attribute.
 */
@RunWith(Parameterized.class)
public class ThcmStreamInstanceTest
{
    /*
     *  Some test values for each matchable attribute.
     * NOTE: Currently only type and direction is matched.
     * Where the attribute is optional "null" means omitted
     */
    private static final String[] STREAM_TYPES = { "pcm", "compress", "hw" };
    private static final String[] STREAM_DIRECTIONS = { "in", "out" };
    private static final int[] INSTANCE_LIMITS = { 0, 1, 14 };

    private static final String HW_STREAM_NAME = "nm";

    // It's impractical to open a stream 2^^32 times to prove there's no limit
    // so instead open it an unusually large number of times.
    private static final int UNLIMITED_TEST_OPEN_COUNT = 100;

    private static final File sWorkFilesPath = ThcmPlatform.workFilesPath();
    private static final File sControlsFile = new File(sWorkFilesPath, "thcm_stream_inst_controls.csv");
    private static final File sXmlFile = new File(sWorkFilesPath, "thcm_stream_inst.xml");

    private CAlsaMock mAlsaMock = new CAlsaMock();
    private CConfigMgr mConfigMgr = new CConfigMgr();

    String mStreamType;
    String mAttrDirection;
    int mInstances;

    @Parameterized.Parameters
    public static Collection parameters()
    {
        List<String[]> params = new ArrayList<String[]>();

        for (String type : STREAM_TYPES) {
            for (String dir : STREAM_DIRECTIONS) {
                for (int instances : INSTANCE_LIMITS) {
                    // pass limit as a string to simplify paramaterization
                    params.add(new String[] {
                        type,
                        dir,
                        Integer.toString(instances)
                    });
                }
            }
        }

        return params;
    }

    public ThcmStreamInstanceTest(String type,
                                  String dir,
                                  String instances)
    {
        mStreamType = type;
        mAttrDirection = dir;
        mInstances = Integer.parseInt(instances);
    }

    @BeforeClass
    public static void setUpClass() throws IOException
    {
        createAlsaControlsFile();
    }

    @AfterClass
    public static void tearDownClass()
    {
        if (sControlsFile.exists()) {
            sControlsFile.delete();
        }
    }

    @Before
    public void setUp() throws IOException
    {
        createXmlFile();

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
        }

        if (mAlsaMock != null) {
            mAlsaMock.closeMixer();
            mAlsaMock = null;
        }

        if (sXmlFile.exists()) {
            sXmlFile.delete();
        }
    }

    private static void createAlsaControlsFile() throws IOException
    {
        FileWriter writer = new FileWriter(sControlsFile);

        writer.write("dummy,bool,1,0,0:1\n");

        writer.close();
    }

    private void createXmlFile() throws IOException
    {
        FileWriter writer = new FileWriter(sXmlFile);

        // Header elements
        writer.write("<audiohal>\n<mixer card=\"0\" />\n");

        writer.write("<stream type=\"" + mStreamType + "\" dir=\"" +
                     mAttrDirection + "\"");
        if (mStreamType.equals("hw")) {
            writer.write(" name=\"" + HW_STREAM_NAME + "\"");
        }
        if (mInstances != 0) {
            writer.write(" instances=\"" + mInstances + "\"");
        }
        writer.write("/>\n");

        // Footer elements
        writer.write("\n</audiohal>\n");

        writer.close();
    }

    private long openTestStream()
    {
        if (mStreamType.equals("hw")) {
            return mConfigMgr.get_named_stream(HW_STREAM_NAME);
        }

        CConfigMgr.AudioConfig config = new CConfigMgr.AudioConfig();
        config.sample_rate = 48000;
        config.channel_mask = CConfigMgr.AUDIO_CHANNEL_OUT_FRONT_LEFT;
        if (mStreamType.equals("pcm")) {
            config.format = CConfigMgr.AUDIO_FORMAT_PCM;
        } else {
            config.format = CConfigMgr.AUDIO_FORMAT_MP3;
        }

        long device = CConfigMgr.AUDIO_DEVICE_NONE;
        if (mAttrDirection.equals("in")) {
            device |= CConfigMgr.AUDIO_DEVICE_BIT_IN;
        }

        return mConfigMgr.get_stream(device, 0, config);
    }

    /**
     * Test the stream can be opened the expected number of times but no more.
     */
    @Test
    public void testOpenInstances()
    {
        int instancesToOpen = mInstances;
        if (instancesToOpen == 0) {
            instancesToOpen = UNLIMITED_TEST_OPEN_COUNT;
        }

        long[] streams = new long[instancesToOpen];

        // open the expected number of instances
        for (int i = 0; i < instancesToOpen; ++i) {
            streams[i] = openTestStream();
            assertTrue("Failed to get stream", streams[i] >= 0);
        }

        // If not unlimited instances: test that we can't open more
        if (instancesToOpen != UNLIMITED_TEST_OPEN_COUNT) {
            assertTrue("Should not have opened another stream",
                       openTestStream() < 0);
        }

        // Close one stream and test that we can open another instance
        mConfigMgr.release_stream(streams[0]);
        streams[0] = openTestStream();
        assertTrue("Failed to get stream", streams[0] >= 0);

        // Close all streams and re-open the same number
        for (long s : streams) {
            mConfigMgr.release_stream(s);
        }

        for (int i = 0; i < instancesToOpen; ++i) {
            assertTrue("Failed to get stream", openTestStream() >= 0);
        }
    }
};
