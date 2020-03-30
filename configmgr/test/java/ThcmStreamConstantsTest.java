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
 * Test &lt;set&gt; definitions in &lt;stream&gt; elements.
 */
@RunWith(Parameterized.class)
public class ThcmStreamConstantsTest
{
    // List of streams to test - a type or the name of a hw stream
    // excludes global which is a special case
    private static final String[] STREAM_TYPES = { "pcm", "compress", "hw" };
    private static final String[] STREAM_NAMES = { null, "nim", "nom" };

    private static final String[] DIRECTIONS = { "in", "out" };

    // Integer constants
    private static final String[] INT_CONST_NAMES = {
            "doh", "re", "mi"
    };

    private static final String[] NEG_INT_CONST_NAMES = {
            "nvedoh", "nvere", "nvemi"
    };

    // Hex constants
    private static final String[] HEX_CONST_NAMES = {
            "fee", "fi", "fum"
    };


    private static final File sWorkFilesPath = ThcmPlatform.workFilesPath();
    private static final File sControlsFile = new File(sWorkFilesPath, "thcm_stream_const_controls.csv");
    private static final File sXmlFile = new File(sWorkFilesPath, "thcm_stream_const.xml");

    private CAlsaMock mAlsaMock = new CAlsaMock();
    private CConfigMgr mConfigMgr = new CConfigMgr();

    String mStreamName;
    String mStreamType;
    String mAttrDirection;
    long mValuePrefix;

    @Parameterized.Parameters
    public static Collection parameters()
    {
        List<String[]> params = new ArrayList<String[]>();

        int valuePrefix = 1;
        for (String type : STREAM_TYPES) {
            for (String name : STREAM_NAMES) {
                // Hw streams can't be anonymous
                if (type.equals("hw") && (name == null)) {
                    continue;
                }
                for (String dir : DIRECTIONS) {
                    String streamName = makeNameForStream(type, dir, name);
                    params.add(new String[] {
                        type,
                        streamName,
                        dir,
                        Integer.toString(valuePrefix)
                    });
                    ++valuePrefix;
                }
            }
        }

        // Global stream is special because it doesn't have a direction
        params.add(new String[] { "hw", "global", null, "0" });

        return params;
    }

    public ThcmStreamConstantsTest(String type,
                                   String name,
                                   String dir,
                                   String valuePrefix)
    {
        mStreamName = name;
        mStreamType = type;
        mAttrDirection = dir;
        // Value passed as a string representation of a decimal to simplify
        // the parameterization to an array of strings.
        mValuePrefix = Long.parseLong(valuePrefix);
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

    private static String makeNameForStream(String type,
                                            String dir,
                                            String baseName)
    {
        if (baseName == null) {
            return null;
        } else if (baseName.equals("global")) {
            return baseName;
        } else {
            return baseName + type.charAt(0)+ dir.charAt(0);
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

        int valuePrefix = 1;
        for (String type : STREAM_TYPES) {
            for (String name : STREAM_NAMES) {
                // Hw streams can't be anonymous
                if (type.equals("hw") && (name == null)) {
                    continue;
                }
                for (String dir : DIRECTIONS) {
                    writer.write("<stream type=\"" + type + "\" ");
                    if (name != null) {
                        writer.write(" name=\"" +
                                     name + type.charAt(0) + dir.charAt(0) + "\"");
                    }
                    writer.write(" dir=\"" + dir + "\">\n");
                    writeIntConstants(writer, valuePrefix);
                    writeHexConstants(writer, valuePrefix);
                    writer.write("</stream>\n");
                    ++valuePrefix;
                }
            }
        }

        // Global stream is special: it doesn't have a direction
        writer.write("<stream name=\"global\" type=\"hw\">");
        writeIntConstants(writer, 0);
        writeHexConstants(writer, 0);
        writer.write("</stream>\n");

        // Footer elements
        writer.write("\n</audiohal>\n");

        writer.close();
    }

    private void writeIntConstants(FileWriter writer,
                                   int valuePrefix) throws IOException
    {
        String prefixString = Integer.toString(valuePrefix);

        for (int i = 0; i < INT_CONST_NAMES.length; ++i) {
            writer.write("<set name=\"" + INT_CONST_NAMES[i] +
                         "\" val=\"" + prefixString + Integer.toString(i) +
                         "\"/>\n");
        }

        for (int i = 0; i < NEG_INT_CONST_NAMES.length; ++i) {
            writer.write("<set name=\"" + NEG_INT_CONST_NAMES[i] +
                         "\" val=\"-" + prefixString + Integer.toString(i) +
                         "\"/>\n");
        }
    }

    private void writeHexConstants(FileWriter writer,
                                   int valuePrefix) throws IOException
    {
        String prefixString = Integer.toHexString(valuePrefix);

        for (int i = 0; i < HEX_CONST_NAMES.length; ++i) {
            writer.write("<set name=\"" + HEX_CONST_NAMES[i] +
                         "\" val=\"0x" + prefixString + Integer.toHexString(i) +
                         "\"/>\n");
        }
    }

    private long openAnonStream()
    {
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

    private long openTestStream()
    {
        if (mStreamName != null) {
            return mConfigMgr.get_named_stream(mStreamName);
        } else {
            return openAnonStream();
        }
    }

    /**
     * Test the raw string values.
     */
    @Test
    public void testStrings()
    {
        long stream = openTestStream();
        assertTrue("Failed to open stream", stream >= 0);

        for (int i = 0; i < INT_CONST_NAMES.length; ++i) {
            String expected = Long.toString(mValuePrefix) + Integer.toString(i);
            assertEquals(INT_CONST_NAMES[i] + " has wrong value",
                         expected,
                         mConfigMgr.get_stream_constant_string(stream,
                                                               INT_CONST_NAMES[i]));
        }

        for (int i = 0; i < HEX_CONST_NAMES.length; ++i) {
            String expected = "0x" + Long.toHexString(mValuePrefix) +
                              Integer.toHexString(i);
            assertEquals(HEX_CONST_NAMES[i] + " has wrong value",
                         expected,
                         mConfigMgr.get_stream_constant_string(stream,
                                                               HEX_CONST_NAMES[i]));
        }
    }

    /**
     * Test values declared as positive decimal integers.
     */
    @Test
    public void testPositiveDecimalIntegers()
    {
        long stream = openTestStream();
        assertTrue("Failed to open stream", stream >= 0);

        for (int i = 0; i < INT_CONST_NAMES.length; ++i) {
            long expected = Long.parseLong(Long.toString(mValuePrefix) +
                                           Integer.toString(i));
            assertEquals(INT_CONST_NAMES[i] + " has wrong value as a uint32",
                         expected,
                         mConfigMgr.get_stream_constant_uint32(stream,
                                                               INT_CONST_NAMES[i]));

            assertEquals(INT_CONST_NAMES[i] + " has wrong value as a int32",
                         expected,
                         mConfigMgr.get_stream_constant_int32(stream,
                                                               INT_CONST_NAMES[i]));
        }
    }

    /**
     * Test values declared as negative decimal integers.
     */
    @Test
    public void testNegativeDecimalIntegers()
    {
        long stream = openTestStream();
        assertTrue("Failed to open stream", stream >= 0);

        for (int i = 0; i < NEG_INT_CONST_NAMES.length; ++i) {
            long expected = 0 - Long.parseLong(Long.toString(mValuePrefix) +
                                               Integer.toString(i));
            assertEquals(NEG_INT_CONST_NAMES[i] + " has wrong value",
                         expected,
                         mConfigMgr.get_stream_constant_int32(stream,
                                                              NEG_INT_CONST_NAMES[i]));
        }
    }

    /**
     * Test values declared as hex integers.
     */
    @Test
    public void testHexIntegers()
    {
        long stream = openTestStream();
        assertTrue("Failed to open stream", stream >= 0);

        for (int i = 0; i < HEX_CONST_NAMES.length; ++i) {
            String hex = Long.toHexString(mValuePrefix) +
                         Integer.toHexString(i);
            long expected = Long.parseLong(hex, 16);
            assertEquals(HEX_CONST_NAMES[i] + " has wrong value",
                         expected,
                         mConfigMgr.get_stream_constant_uint32(stream,
                                                               HEX_CONST_NAMES[i]));
        }
    }
};

