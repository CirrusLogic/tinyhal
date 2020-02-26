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
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

import com.cirrus.tinyhal.test.thcm.CAlsaMock;
import com.cirrus.tinyhal.test.thcm.CConfigMgr;
import com.cirrus.tinyhal.test.thcm.ThcmPlatform;

/**
 * Tests that &lt;ctl&gt; elements are executed correctly for bool controls.
 * This assumes that other tinyhal features required to invoke the &lt;ctl&gt;
 * element are working correctly and tested elsewhere.
 */
public class ThcmBoolControlTest
{
    private static final int[] INDICES = { 0, 1, 2, 3 };

    private static final File sWorkFilesPath = ThcmPlatform.workFilesPath();
    private static final File sControlsFile = new File(sWorkFilesPath, "thcm_bool_controls.csv");
    private static final File sXmlFile = new File(sWorkFilesPath, "thcm_bool_controls.xml");

    private CAlsaMock mAlsaMock = new CAlsaMock();
    private CConfigMgr mConfigMgr = new CConfigMgr();

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

        // A 1-element control
        writer.write("SwitchA,bool,1,0,0:1\n");

        // A 2-element control
        writer.write("SwitchB,bool,2,0,0:1\n");

        // A multiple n-element control
        int n = INDICES.length;
        for (int index : INDICES) {
            writer.write("SwitchC" + index + ",bool," + n + ",0,0:1\n");
        }

        writer.close();
    }

    private static void createXmlFile() throws IOException
    {
        FileWriter writer = new FileWriter(sXmlFile);

        // Header elements
        writer.write("<audiohal>\n<mixer card=\"0\" />\n<device name=\"global\">\n");

        // non-indexed write to 1-element control
        writePathEntry(writer, "N1-false", "SwitchA", 0);
        writePathEntry(writer, "N1-true", "SwitchA", 1);

        // non-indexed write to 2-element control
        writePathEntry(writer, "N2-false", "SwitchB", 0);
        writePathEntry(writer, "N2-true", "SwitchB", 1);

        // indexed write to n-element control
        for (int index : INDICES) {
            writePathIndexedEntry(writer,
                                  "Nn" + index + "-false",
                                  "SwitchC" + index,
                                  index,
                                  0);
            writePathIndexedEntry(writer,
                                  "Nn" + index + "-true",
                                  "SwitchC" + index,
                                  index,
                                  1);
        }

        writer.write("</device>\n");

        // Create a stream for each path
        writeStreamEntry(writer, "N1-false");
        writeStreamEntry(writer, "N1-true");
        writeStreamEntry(writer, "N2-false");
        writeStreamEntry(writer, "N2-true");

        for (int index : INDICES) {
            writeStreamEntry(writer, "Nn" + index + "-false");
            writeStreamEntry(writer, "Nn" + index + "-true");
        }

        // Footer elements
        writer.write("\n</audiohal>\n");

        writer.close();
    }

    private static void writePathEntry(FileWriter writer,
                                       String pathName,
                                       String controlName,
                                       int value) throws IOException
    {
            writer.write("<path name=\"" + pathName + "\">\n");
            writer.write("<ctl name=\"" + controlName + "\" val=\"" + value + "\"/>\n");
            writer.write("</path>\n");
    }

    private static void writePathIndexedEntry(FileWriter writer,
                                              String pathName,
                                              String controlName,
                                              int index,
                                              int value) throws IOException
    {
            writer.write("<path name=\"" + pathName + "\">\n");
            writer.write("<ctl name=\"" + controlName + "\" index=\"" + index +
                         "\" val=\"" + value + "\"/>\n");
            writer.write("</path>\n");
    }

    private static void writeStreamEntry(FileWriter writer,
                                         String name) throws IOException
    {
            writer.write("<stream name=\"" + name +
                         "\" type=\"hw\" dir=\"out\" >\n");
            writer.write("<enable path=\"" + name + "\" />\n");
            writer.write("</stream>\n");
    }

    /**
     * Simple write of a 1-element control.
     */
    @Test
    public void testWriteOneElement()
    {
        assertEquals("SwitchA not initially 0", 0, mAlsaMock.getBool("SwitchA", 0));

        long stream = mConfigMgr.get_named_stream("N1-true");
        assertFalse("Failed to get N1-true stream", stream < 0);

        assertTrue("SwitchA was not changed", mAlsaMock.isChanged("SwitchA"));
        assertEquals("SwitchA not written correctly", 1, mAlsaMock.getBool("SwitchA", 0));

        assertEquals("Failed to close N1-true stream",
                     0,
                     mConfigMgr.release_stream(stream));

        mAlsaMock.clearChangedFlag("SwitchA");

        stream = mConfigMgr.get_named_stream("N1-false");
        assertFalse("Failed to get N1-false stream", stream < 0);

        assertTrue("SwitchA was not changed", mAlsaMock.isChanged("SwitchA"));
        assertEquals("SwitchA not written correctly", 0, mAlsaMock.getBool("SwitchA", 0));

        assertEquals("Failed to close N1-false stream",
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
        assertEquals("SwitchB[0] not initially 0", 0, mAlsaMock.getBool("SwitchB", 0));
        assertEquals("SwitchB[1] not initially 0", 0, mAlsaMock.getBool("SwitchB", 1));

        long stream = mConfigMgr.get_named_stream("N2-true");
        assertFalse("Failed to get N2-true stream", stream < 0);

        assertTrue("SwitchB was not changed", mAlsaMock.isChanged("SwitchB"));
        assertEquals("SwitchB[0] not written correctly", 1, mAlsaMock.getBool("SwitchB", 0));
        assertEquals("SwitchB[1] not written correctly", 1, mAlsaMock.getBool("SwitchB", 1));

        assertEquals("Failed to close N2-true stream",
                     0,
                     mConfigMgr.release_stream(stream));

        mAlsaMock.clearChangedFlag("SwitchB");

        stream = mConfigMgr.get_named_stream("N2-false");
        assertFalse("Failed to get N2-false stream", stream < 0);

        assertTrue("SwitchB was not changed", mAlsaMock.isChanged("SwitchB"));
        assertEquals("SwitchB[0] not written correctly", 0, mAlsaMock.getBool("SwitchB", 0));
        assertEquals("SwitchB[1] not written correctly", 0, mAlsaMock.getBool("SwitchB", 1));

        assertEquals("Failed to close N2-false stream",
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
            String controlName = "SwitchC" + index;

            for (int i : INDICES) {
                assertEquals(controlName + "[" + i + "] not initially 0",
                             0,
                             mAlsaMock.getBool(controlName, i));
            }

            String streamName = "Nn" + index + "-true";
            long stream = mConfigMgr.get_named_stream(streamName);
            assertFalse("Failed to get " + streamName + " stream", stream < 0);

            assertTrue(controlName + " was not changed",
                       mAlsaMock.isChanged(controlName));
            assertEquals(controlName + "[" + index + "] not written correctly",
                         1,
                         mAlsaMock.getBool(controlName, index));

            for (int i : INDICES) {
                if (i != index) {
                    assertEquals(controlName + "[" + i + "] should not have changed",
                                 0,
                                 mAlsaMock.getBool(controlName, i));
                }
            }

            assertEquals("Failed to close " + streamName + " stream",
                         0,
                         mConfigMgr.release_stream(stream));

            mAlsaMock.clearChangedFlag(controlName);

            streamName = "Nn" + index + "-false";
            stream = mConfigMgr.get_named_stream(streamName);
            assertFalse("Failed to get " + streamName + " stream", stream < 0);

            assertTrue(controlName + " was not changed",
                       mAlsaMock.isChanged(controlName));
            assertEquals(controlName + "[" + index + "] not written correctly",
                         0,
                         mAlsaMock.getBool(controlName, index));

            for (int i : INDICES) {
                if (i != index) {
                    assertEquals(controlName + "[" + i + "] should not have changed",
                                 0,
                                 mAlsaMock.getBool(controlName, i));
                }
            }

            assertEquals("Failed to close " + streamName + " stream",
                         0,
                         mConfigMgr.release_stream(stream));
        }
    }
};
