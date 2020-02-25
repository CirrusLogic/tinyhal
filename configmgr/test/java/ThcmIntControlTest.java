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
 * Tests that &lt;ctl&gt; elements are executed correctly for integer controls.
 * This assumes that other tinyhal features required to invoke the &lt;ctl&gt;
 * element are working correctly and tested elsewhere.
 */
@RunWith(Parameterized.class)
public class ThcmIntControlTest
{
    // Spot-check a few significant integer values to prove that configmgr
    // isn't doing anything silly to corrupt the value
    private static final int[] TEST_VALUES = {
        -0x10000, -0xffff, -0x101, -0xff, -1, 0, 1, 0xff, 0x100, 0x101,
        0x8000, 0x8001, 0xffff, 0x1000, 0x1001, 0xffffff, 0x1000000,
        0x10000000, 0x7fffffff
    };

    private static final int[] INDICES = { 0, 1, 2, 3 };

    private static final File sWorkFilesPath = ThcmPlatform.workFilesPath();
    private static final File sControlsFile = new File(sWorkFilesPath, "thcm_int_controls.csv");
    private static final File sXmlFile = new File(sWorkFilesPath, "thcm_int_controls.xml");


    private CAlsaMock mAlsaMock = new CAlsaMock();
    private CConfigMgr mConfigMgr = new CConfigMgr();
    private int mTestValue;

    @Parameterized.Parameters
    public static Collection parameters() {
        // parameters must be an array of arrays
        List<Integer[]> params = new ArrayList<Integer[]>();
        for (int v : TEST_VALUES) {
            params.add(new Integer[] { v });
        }

        return params;
    }

    public ThcmIntControlTest(Integer value)
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

        int min = 1 - 0x7fffffff;
        int max = 0x7fffffff;

        // A 1-element control that accepts the entire integer range
        writer.write("CoeffA,int,1,0," + min + ":" + max + "\n");

        // A 2-element control that accepts the entire integer range
        writer.write("CoeffB,int,2,0," + min + ":" + max + "\n");

        // Multiple multi-element controls that accept the entire integer range
        int numIndices = INDICES.length;
        for (int index : INDICES) {
            writer.write("CoeffC" + index + ",int," + numIndices +
                         ",0," + min + ":" + max + "\n");
        }

        writer.close();
    }

    private static void createXmlFile() throws IOException
    {
        FileWriter writer = new FileWriter(sXmlFile);

        // Header elements
        writer.write("<audiohal>\n<mixer card=\"0\" />\n<device name=\"global\">\n");

        for (int testValue : TEST_VALUES) {
            // non-indexed write to 1-element control
            writePathEntry(writer, "N1", "CoeffA", testValue);

            // non-indexed write to multi-element control
            writePathEntry(writer, "N2", "CoeffB", testValue);

            // indexed write to multi-element controls
            for (int index : INDICES) {
                writePathIndexedEntry(writer, "I" + index,
                                      "CoeffC" + index,
                                      index,
                                      testValue);
            }
        }

        writer.write("</device>\n");

        // Create a stream for each path
        for (int testValue : TEST_VALUES) {
            writeStreamEntry(writer, "N1", testValue);
            writeStreamEntry(writer, "N2", testValue);
            for (int index : INDICES) {
                writeStreamEntry(writer, "I" + index, testValue);
            }
        }

        // Footer elements
        writer.write("\n</audiohal>\n");

        writer.close();
    }

    private static void writePathEntry(FileWriter writer,
                                       String prefix,
                                       String coeffName,
                                       int value) throws IOException
    {
            writer.write("<path name=\"" + prefix + "_" + value + "\">\n");
            writer.write("<ctl name=\"" + coeffName + "\" val=\"" + value + "\"/>\n");
            writer.write("</path>\n");
    }

    private static void writePathIndexedEntry(FileWriter writer,
                                              String prefix,
                                              String coeffName,
                                              int index,
                                              int value) throws IOException
    {
            writer.write("<path name=\"" + prefix + "_" + value + "\">\n");
            writer.write("<ctl name=\"" + coeffName + "\" index=\"" + index +
                         "\" val=\"" + value + "\"/>\n");
            writer.write("</path>\n");
    }

    private static void writeStreamEntry(FileWriter writer,
                                         String prefix,
                                         int value) throws IOException
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
        assertEquals("CoeffA not initially zero",
                     0,
                     mAlsaMock.getInt("CoeffA", 0));

        String streamName = "N1-" + mTestValue;
        long stream = mConfigMgr.get_named_stream(streamName);
        assertFalse("Failed to get " + streamName + " stream", stream < 0);

        assertTrue("CoeffA was not changed", mAlsaMock.isChanged("CoeffA"));

        assertEquals("CoeffA not written correctly",
                     mTestValue,
                     mAlsaMock.getInt("CoeffA", 0));

        assertEquals("Failed to close " + streamName + " stream",
                     0,
                     mConfigMgr.release_stream(stream));
    }

    /**
     * Write a single test value to a 2-element control.
     * Both elements should change to the test value.
     */
    @Test
    public void testWriteOneValueToMultiElement()
    {
        assertEquals("CoeffB[0] not initially zero",
                     0,
                     mAlsaMock.getInt("CoeffB", 0));
        assertEquals("CoeffB[1] not initially zero",
                     0,
                     mAlsaMock.getInt("CoeffB", 1));

        String streamName = "N2-" + mTestValue;
        long stream = mConfigMgr.get_named_stream(streamName);
        assertFalse("Failed to get " + streamName + " stream", stream < 0);

        assertTrue("CoeffB was not changed", mAlsaMock.isChanged("CoeffB"));

        assertEquals("CoeffB[0] not written correctly",
                     mTestValue,
                     mAlsaMock.getInt("CoeffB", 0));
        assertEquals("CoeffB[1] not written correctly",
                     mTestValue,
                     mAlsaMock.getInt("CoeffB", 1));

        assertEquals("Failed to close " + streamName + " stream",
                     0,
                     mConfigMgr.release_stream(stream));
    }

    /**
     * Write a test value to individual elements of a multi-element control.
     * Only the indexed element should change.
     */
    @Test
    public void testWriteIndexedElements()
    {
        for (int index : INDICES) {
            // There is a different control for each index test position
            // so that we can test that only the expected index position
            // has changed in the control.
            String coeffName = "CoeffC" + index;

            for (int i : INDICES) {
                assertEquals(coeffName + "[" + i + "] not initially zero",
                             0,
                             mAlsaMock.getInt(coeffName, i));
            }

            String streamName = "I" + index + "-" + mTestValue;
            long stream = mConfigMgr.get_named_stream(streamName);
            assertFalse("Failed to get " + streamName + " stream", stream < 0);

            assertTrue(coeffName + " was not changed",
                       mAlsaMock.isChanged(coeffName));

            assertEquals(coeffName + "[" + index + "] not written correctly",
                         mTestValue,
                         mAlsaMock.getInt(coeffName, index));

            for (int otherIndex : INDICES) {
                if (otherIndex != index) {
                    assertEquals(coeffName + "[" + index + "] should not have changed",
                                 0,
                                 mAlsaMock.getInt(coeffName, otherIndex));
                }
            }

            assertEquals("Failed to close " + streamName + " stream",
                         0,
                         mConfigMgr.release_stream(stream));
        }
    }
};
