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
 * Tests that <code>get_stream()</code> finds the &lt;stream&gt; based on the
 * given attributes.
 */
@RunWith(Parameterized.class)
public class ThcmStreamMatchingTest
{
    /*
     *  Some test values for each matchable attribute.
     * NOTE: Currently only type and direction is matched.
     * Where the attribute is optional "null" means omitted
     */
    private static final String[] STREAM_TYPES = { "pcm", "compress", "hw" };
    private static final String[] STREAM_NAMES = { null, "nim", "nom" };
    private static final String[] DIRECTIONS = { "in", "out" };
    private static final String DUMMY_USECASE_NAME = "foo";
    private static final String DUMMY_CASE_NAME = "doit";

    private static final File sWorkFilesPath = ThcmPlatform.workFilesPath();
    private static final File sControlsFile = new File(sWorkFilesPath, "thcm_stream_matching_controls.csv");
    private static final File sXmlFile = new File(sWorkFilesPath, "thcm_stream_matching.xml");
    private static final List<String> sAllControls = new ArrayList<String>();

    private CAlsaMock mAlsaMock = new CAlsaMock();
    private CConfigMgr mConfigMgr = new CConfigMgr();

    String mStreamName;
    String mStreamType;
    String mAttrDirection;
    String mTestControlName;

    @Parameterized.Parameters
    public static Collection<String[]> parameters()
    {
        List<String[]> params = new ArrayList<String[]>();

        int controlNum = 0;
        for (String type : STREAM_TYPES) {
            for (String name : STREAM_NAMES) {
                // hw streams can't be anonymous
                if (type.equals("hw") && (name == null)) {
                    continue;
                }
                for (String dir : DIRECTIONS) {
                    params.add(new String[] {
                        type,
                        makeNameForStream(type, dir, name),
                        dir,
                        "Control" + Integer.toString(controlNum)
                    });
                    ++controlNum;
                }
            }
        }

        // global is a special case because it doesn't have a direction
        params.add(new String[] {
            "hw", "global", null, "Control" + Integer.toString(controlNum)
        });

        return params;
    }

    public ThcmStreamMatchingTest(String type,
                                  String name,
                                  String dir,
                                  String controlName)
    {
        mStreamName = name;
        mStreamType = type;
        mAttrDirection = dir;
        mTestControlName = controlName;
    }

    @BeforeClass
    public static void setUpClass() throws IOException
    {
        // One control for each stream
        final Collection<String[]> params = parameters();
        for (String[] set : params) {
            // Get control name from params
            sAllControls.add(set[3]);
        }

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

        for (String name : sAllControls) {
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
            // Stream names must be unique but type/dir doesn't have to be
            // unique, so combine base name with type/dir to make a unique name
            return baseName + type.charAt(0)+ dir.charAt(0);
        }
    }

    private static void createAlsaControlsFile() throws IOException
    {
        FileWriter writer = new FileWriter(sControlsFile);

        // One control for each stream
        for (String control : sAllControls) {
            writer.write(control + ",bool,1,0,0:1\n");
        }

        writer.close();
    }

    private void createXmlFile() throws IOException
    {
        FileWriter writer = new FileWriter(sXmlFile);

        // Header elements
        writer.write("<audiohal>\n<mixer card=\"0\" />\n");

        // One stream for all combinations of matchable attributes
        // With a <usecase> element to invoke a control write
        int controlNum = 0;
        for (String type : STREAM_TYPES) {
            for (String name : STREAM_NAMES) {
                // hw streams cannoy be anonymous
                if (type.equals("hw") && (name == null)) {
                    continue;
                }
                for (String dir : DIRECTIONS) {
                    writer.write("<stream type=\"" + type + "\" dir=\"" + dir + "\"");
                    if (name != null) {
                        writer.write(" name=\"" + makeNameForStream(type, dir, name) + "\"");
                    }
                    writer.write(">\n");
                    writeUseCase(writer, controlNum);
                    writer.write("</stream>\n");
                    ++controlNum;
                }
            }
        }

        // global is a special case because it doesn't have a direction
        writer.write("<stream type=\"hw\" name=\"global\">\n");
        writeUseCase(writer, controlNum);
        writer.write("</stream>\n");

        // Footer elements
        writer.write("\n</audiohal>\n");

        writer.close();
    }

    private void writeUseCase(FileWriter writer, int controlNum) throws IOException
    {
        writer.write("<usecase name=\"" + DUMMY_USECASE_NAME + "\">\n");
        writer.write("<case name=\"" + DUMMY_CASE_NAME + "\">\n");
        writer.write("<ctl name=\"Control" + Integer.toString(controlNum) +
                     "\" val=\"1\" />\n");
        writer.write("</case></usecase>\n");
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

    private void checkNoControlsChanged() {
        for (String name : sAllControls) {
            assertFalse(name + " should not have changed",
                        mAlsaMock.isChanged(name));
        }
    }

    private long openTestStream()
    {
        if (mStreamName != null) {
            return mConfigMgr.get_named_stream(mStreamName);
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
     * Test the correct stream is opened.
     */
    @Test
    public void testStreamMatch()
    {
        long stream = openTestStream();
        assertTrue("Failed to get stream", stream >= 0);
        checkNoControlsChanged();

        // invoke usecase to write the unique control
        assertEquals("Failed to invoke usecase",
                     0,
                     mConfigMgr.apply_use_case(stream,
                                               DUMMY_USECASE_NAME,
                                               DUMMY_CASE_NAME));
        checkExpectedControlChange(mTestControlName);
    }

    /**
     * Test is_named_stream_defined()
     */
    @Test
    public void testIsNamedStreamDefined()
    {
        assertFalse("Reported non-existent stream as existing",
                    mConfigMgr.is_named_stream_defined("fiddlesticks"));

        if (mStreamName != null) {
            assertTrue("Stream " + mStreamName + "was not reported as existing",
                       mConfigMgr.is_named_stream_defined(mStreamName));
        }
    }
};
