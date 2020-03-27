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
 * Tests that the attributes of a &lt;stream&gt; element are reported
 * correctly.
 */
@RunWith(Parameterized.class)
public class ThcmStreamAttributesTest
{
    // Some test values for each attribute
    // Where the attribute is optional "null" means omitted
    private static final String[] STREAM_TYPES = { "pcm", "compress", "hw" };
    private static final String[] DIRECTIONS = { "in", "out" };
    private static final String[] CARDS = { null, "0", "9" };
    private static final String[] DEVICES = { null, "0", "1", "77" };
    private static final String[] RATES = { null, "16000", "44100", "96000" };
    private static final String[] PERIOD_SIZES = { null, "768", "3192" };
    private static final String[] PERIOD_COUNTS = { null, "1", "12", "100" };

    private static final int TEST_MIXER_CARD_NUMBER = 43;

    // Default values reported by configmgr for unspecified attributes
    private static final int DEFAULT_REPORTED_SAMPLE_RATE = 0;
    private static final int DEFAULT_REPORTED_PERIOD_SIZE = 0;
    private static final int DEFAULT_REPORTED_PERIOD_COUNT = 0;
    private static final int DEFAULT_REPORTED_CARD = TEST_MIXER_CARD_NUMBER;
    private static final long DEFAULT_REPORTED_DEVICE = -1;

    private static final File sWorkFilesPath = ThcmPlatform.workFilesPath();
    private static final File sControlsFile = new File(sWorkFilesPath, "thcm_stream_attributes_controls.csv");
    private static final File sXmlFile = new File(sWorkFilesPath, "thcm_stream_attributes.xml");

    private CAlsaMock mAlsaMock = new CAlsaMock();
    private CConfigMgr mConfigMgr = new CConfigMgr();

    String mStreamType;
    String mAttrDirection;
    String mAttrCard;
    String mAttrDevice;
    String mAttrRate;
    String mAttrPeriodSize;
    String mAttrPeriodCount;

    @Parameterized.Parameters
    public static Collection parameters()
    {
        List<String[]> params = new ArrayList<String[]>();

        for (String type : STREAM_TYPES) {
            for (String dir : DIRECTIONS) {
                for (String card : CARDS) {
                    for (String dev : DEVICES) {
                        for (String rate : RATES) {
                            for (String psize : PERIOD_SIZES) {
                                for (String pcount : PERIOD_COUNTS) {
                                    params.add(new String[] {
                                        type,
                                        dir,
                                        card,
                                        dev,
                                        rate,
                                        psize,
                                        pcount
                                    });
                                }
                            }
                        }
                    }
                }
            }
        }
        return params;
    }

    public ThcmStreamAttributesTest(String type,
                                    String dir,
                                    String card,
                                    String dev,
                                    String rate,
                                    String psize,
                                    String pcount)
    {
        mStreamType = type;
        mAttrDirection = dir;
        mAttrCard = card;
        mAttrDevice = dev;
        mAttrRate = rate;
        mAttrPeriodSize = psize;
        mAttrPeriodCount = pcount;
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
        writer.write("<audiohal>\n<mixer card=\"" + TEST_MIXER_CARD_NUMBER + "\" />\n");

        // Test stream
        writer.write("<stream type=\"" + mStreamType + "\" dir=\"" +
                     mAttrDirection + "\" ");
        writeOptionalStreamAttr(writer, "card", mAttrCard);
        writeOptionalStreamAttr(writer, "device", mAttrDevice);
        writeOptionalStreamAttr(writer, "rate", mAttrRate);
        writeOptionalStreamAttr(writer, "period_size", mAttrPeriodSize);
        writeOptionalStreamAttr(writer, "period_count", mAttrPeriodCount);
        if (mStreamType.equals("hw")) {
            writer.write(" name=\"named\"");
        }
        writer.write("/>\n");

        // Footer elements
        writer.write("\n</audiohal>\n");

        writer.close();
    }

    private void writeOptionalStreamAttr(FileWriter writer,
                                         String name,
                                         String value) throws IOException
    {
        if (value != null) {
            writer.write(name + "=\"" + value + "\" ");
        }
    }

    private long openAnonStream()
    {
        CConfigMgr.AudioConfig config = new CConfigMgr.AudioConfig();
        if (mAttrRate != null) {
            config.sample_rate = Integer.parseInt(mAttrRate);
        } else {
            config.sample_rate = 48000;
        }
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

    private long openTestStream()
    {
        if (mStreamType.equals("hw")) {
            return mConfigMgr.get_named_stream("named");
        } else {
            return openAnonStream();
        }
    }

    /**
     * Test the stream type indication macros.
     * Tests that the is_hardware, is_pcm and is_compressed macros.
     */
    @Test
    public void testStreamType()
    {
        long stream = openTestStream();
        assertTrue("Failed to get stream", stream >= 0);
        assertEquals("Wrong hardware stream indication",
                     mStreamType.equals("hw"),
                     mConfigMgr.stream_is_hardware(stream));
        assertEquals("Wrong pcm indication",
                     mStreamType.equals("pcm"),
                     mConfigMgr.stream_is_pcm(stream));
        assertEquals("Wrong compressed indication",
                     mStreamType.equals("compress"),
                     mConfigMgr.stream_is_compressed(stream));
    }

    /**
     * Test the stream direction indication macros.
     * Tests that the is_input, is_pcm_in/out and is_compressed_in/out macros.
     */
    @Test
    public void testStreamDirection()
    {
        long stream = openTestStream();
        assertTrue("Failed to get stream", stream >= 0);

        final boolean isPcm = mStreamType.equals("pcm");
        final boolean isCompressed = mStreamType.equals("compress");
        final boolean isIn = mAttrDirection.equals("in");
        final boolean isOut = mAttrDirection.equals("out");

        assertEquals("Wrong direction indication",
                     isIn,
                     mConfigMgr.stream_is_input(stream));
        assertEquals("Wrong pcm_in indication",
                     isPcm && isIn,
                     mConfigMgr.stream_is_pcm_in(stream));
        assertEquals("Wrong pcm_out indication",
                     isPcm && isOut,
                     mConfigMgr.stream_is_pcm_out(stream));
        assertEquals("Wrong compressed_in indication",
                     isCompressed && isIn,
                     mConfigMgr.stream_is_compressed_in(stream));
        assertEquals("Wrong compressed_out indication",
                     isCompressed && isOut,
                     mConfigMgr.stream_is_compressed_out(stream));
    }

    /**
     * Test the stream sample rate.
     * Tests the <code>rate</code> field of <code>struct hw_stream</code>.
     */
    @Test
    public void testStreamRate()
    {
        long stream = openTestStream();
        assertTrue("Failed to get stream", stream >= 0);
        if (mAttrRate != null) {
            assertEquals("Wrong sample rate",
                         Integer.parseInt(mAttrRate),
                         mConfigMgr.getHwStreamStruct_rate(stream));
        } else {
            assertEquals("Wrong default sample rate",
                         DEFAULT_REPORTED_SAMPLE_RATE,
                         mConfigMgr.getHwStreamStruct_rate(stream));
        }
    }

    /**
     * Test the stream period size.
     * Tests the <code>period_size</code> field of <code>struct hw_stream</code>.
     */
    @Test
    public void testStreamPeriodSize()
    {
        long stream = openTestStream();
        assertTrue("Failed to get stream", stream >= 0);
        if (mAttrPeriodSize != null) {
            assertEquals("Wrong period size",
                         Integer.parseInt(mAttrPeriodSize),
                         mConfigMgr.getHwStreamStruct_period_size(stream));
        } else {
            assertEquals("Wrong default period size",
                         DEFAULT_REPORTED_PERIOD_SIZE,
                         mConfigMgr.getHwStreamStruct_period_size(stream));
        }
    }

    /**
     * Test the stream period count.
     * Tests the <code>period_count</code> field of <code>struct hw_stream</code>.
     */
    @Test
    public void testStreamPeriodCount()
    {
        long stream = openTestStream();
        assertTrue("Failed to get stream", stream >= 0);
        if (mAttrPeriodCount != null) {
            assertEquals("Wrong period count",
                         Integer.parseInt(mAttrPeriodCount),
                         mConfigMgr.getHwStreamStruct_period_count(stream));
        } else {
            assertEquals("Wrong default period count",
                         DEFAULT_REPORTED_PERIOD_COUNT,
                         mConfigMgr.getHwStreamStruct_period_count(stream));
        }
    }

    @Test
    public void testStreamCard()
    {
        long stream = openTestStream();
        assertTrue("Failed to get stream", stream >= 0);
        if (mAttrCard != null) {
            assertEquals("Wrong card",
                         Integer.parseInt(mAttrCard),
                         mConfigMgr.getHwStreamStruct_card_number(stream));
        } else {
            assertEquals("Wrong default card",
                         DEFAULT_REPORTED_CARD,
                         mConfigMgr.getHwStreamStruct_card_number(stream));
        }
    }

    @Test
    public void testStreamDevice()
    {
        long stream = openTestStream();
        assertTrue("Failed to get stream", stream >= 0);
        if (mAttrDevice != null) {
            assertEquals("Wrong device",
                         Integer.parseInt(mAttrDevice),
                         mConfigMgr.getHwStreamStruct_device_number(stream));
        } else {
            assertEquals("Wrong default device",
                         DEFAULT_REPORTED_DEVICE,
                         mConfigMgr.getHwStreamStruct_device_number(stream));
        }
    }
};
