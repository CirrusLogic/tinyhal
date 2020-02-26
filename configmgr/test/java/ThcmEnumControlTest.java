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
 * Tests that &lt;ctl&gt; elements are executed correctly for enum controls.
 * This assumes that other tinyhal features required to invoke the &lt;ctl&gt;
 * element are working correctly and tested elsewhere.
 */
@RunWith(Parameterized.class)
public class ThcmEnumControlTest
{
    // Some test values for the enum controls
    private static final String[] TEST_VALUES = {
        "IN1L", "IN1R", "IN2L", "IN2R"
    };

    private static final String DEFAULT_ENUM_VALUE = "None";

    private static final File sWorkFilesPath = ThcmPlatform.workFilesPath();
    private static final File sControlsFile = new File(sWorkFilesPath, "thcm_enum_controls.csv");
    private static final File sXmlFile = new File(sWorkFilesPath, "thcm_enum_controls.xml");

    private CAlsaMock mAlsaMock = new CAlsaMock();
    private CConfigMgr mConfigMgr = new CConfigMgr();
    private String mTestValue;

    @Parameterized.Parameters
    public static Collection parameters() {
        // parameters must be an array of arrays
        List<String[]> params = new ArrayList<String[]>();
        for (String v : TEST_VALUES) {
            params.add(new String[] { v });
        }

        return params;
    }

    public ThcmEnumControlTest(String value)
    {
        mTestValue = value;
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
        }

        if (mAlsaMock != null) {
            mAlsaMock.closeMixer();
            mAlsaMock = null;
        }
    }

    private static void createAlsaControlsFile() throws IOException
    {
        FileWriter writer = new FileWriter(sControlsFile);

        // Generate colon-separated list of enum values and append the
        // default value
        String values = String.join(":", Arrays.asList(TEST_VALUES));
        values = values + ":" + DEFAULT_ENUM_VALUE;

        // A 1-element control that accepts all enum values
        writer.write("MuxA,enum,1," + DEFAULT_ENUM_VALUE +"," + values + "\n");

        // tinyalsa does not support multi-element enum controls

        writer.close();
    }

    private static void createXmlFile() throws IOException
    {
        // For each TEST_VALUES create a path to write that value

        FileWriter writer = new FileWriter(sXmlFile);

        // Header elements
        writer.write("<audiohal>\n<mixer card=\"0\" />\n<device name=\"global\">\n");

        for (String testValue : TEST_VALUES) {
            // non-indexed write to 1-element control
            writePathEntry(writer, "N1", "MuxA", testValue);
        }

        writer.write("</device>\n");

        // Create a stream for each path
        for (String testValue : TEST_VALUES) {
            writeStreamEntry(writer, "N1", testValue);
        }

        // Footer elements
        writer.write("\n</audiohal>\n");

        writer.close();
    }

    private static void writePathEntry(FileWriter writer,
                                       String prefix,
                                       String controlName,
                                       String value) throws IOException
    {
            writer.write("<path name=\"" + prefix + "_" + value + "\">\n");
            writer.write("<ctl name=\"" + controlName + "\" val=\"" + value + "\"/>\n");
            writer.write("</path>\n");
    }

    private static void writePathIndexedEntry(FileWriter writer,
                                              String prefix,
                                              String controlName,
                                              int index,
                                              String value) throws IOException
    {
            writer.write("<path name=\"" + prefix + "_" + value + "\">\n");
            writer.write("<ctl name=\"" + controlName + "\" index=\"" + index +
                         "\" val=\"" + value + "\"/>\n");
            writer.write("</path>\n");
    }

    private static void writeStreamEntry(FileWriter writer,
                                         String prefix,
                                         String value) throws IOException
    {
            writer.write("<stream name=\"" + prefix + "-" + value +
                         "\" type=\"hw\" dir=\"out\" >\n");
            writer.write("<enable path=\"" + prefix + "_" + value + "\" />\n");
            writer.write("</stream>\n");
    }

    /**
     * Simple write of a test value to a 1-element control.
     */
    @Test
    public void testWriteOneElement()
    {
        assertEquals("MuxA not initially " + DEFAULT_ENUM_VALUE,
                     DEFAULT_ENUM_VALUE,
                     mAlsaMock.getEnum("MuxA"));

        String streamName = "N1-" + mTestValue;
        long stream = mConfigMgr.get_named_stream(streamName);
        assertFalse("Failed to get " + streamName + " stream", stream < 0);

        assertTrue("MuxA was not changed", mAlsaMock.isChanged("MuxA"));

        assertEquals("MuxA not written correctly",
                     mTestValue,
                     mAlsaMock.getEnum("MuxA"));

        assertEquals("Failed to close " + streamName + " stream",
                     0,
                     mConfigMgr.release_stream(stream));
    }
};
