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
import java.util.Random;
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
 * Tests that &lt;ctl&gt; elements in a path are all executed and that the order
 * of execution is correct.
 * This assumes that &lt;ctl&gt; elements execute corectly as do other tinyhal
 * features required to invoke the &lt;path&gt; element, and that these features
 * are tested elsewhere.
 */
public class ThcmPathControlsTest
{
    private static final File sWorkFilesPath = ThcmPlatform.workFilesPath();
    private static final File sControlsFile = new File(sWorkFilesPath, "thcm_path_controls.csv");
    private static final File sXmlFile = new File(sWorkFilesPath, "thcm_path_controls.xml");

    private static final int sNumBigPathControls = 100;
    private static byte[] sBigPathValues;

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

        // Four simple int controls to prove that writes executed in order
        writer.write("VolA,int,1,0,0:32\n");
        writer.write("VolB,int,1,0,0:32\n");
        writer.write("VolC,int,1,0,0:32\n");
        writer.write("VolD,int,1,0,0:32\n");

        // Some controls of other type for testing a path with mixed controls
        writer.write("SwitchA,bool,1,0,0:1\n");
        writer.write("MuxA,enum,1,None,None:IN1L:IN1R:IN2L:IN2R\n");
        writer.write("CoeffA,byte,4,0,\n");

        // Create many controls for testing a path with a large number of
        // controls
        for (int i = 0; i < sNumBigPathControls; ++i) {
            writer.write("Thing" + i + ",int,1,0,0:32\n");
        }

        writer.close();
    }

    private static void createXmlFile() throws IOException
    {
        FileWriter writer = new FileWriter(sXmlFile);

        // Header elements
        writer.write("<audiohal>\n<mixer card=\"0\" />\n<device name=\"global\">\n");

        // Write four different values to the same control
        writer.write("<path name=\"S4\">\n");
        writeCtl(writer, "VolA", "16");
        writeCtl(writer, "VolA", "7");
        writeCtl(writer, "VolA", "0");
        writeCtl(writer, "VolA", "13");
        writer.write("</path>\n");

        // Write four different values to one control, then four values to
        // another control
        writer.write("<path name=\"TO4\">\n");
        writeCtl(writer, "VolA", "4");
        writeCtl(writer, "VolA", "1");
        writeCtl(writer, "VolA", "30");
        writeCtl(writer, "VolA", "6");
        writeCtl(writer, "VolB", "8");
        writeCtl(writer, "VolB", "7");
        writeCtl(writer, "VolB", "5");
        writeCtl(writer, "VolB", "31");
        writer.write("</path>\n");

        // Write four different values to two controls, with the writes for
        // the controls interleaved
        writer.write("<path name=\"TI4\">\n");
        writeCtl(writer, "VolA", "4");
        writeCtl(writer, "VolB", "8");
        writeCtl(writer, "VolA", "1");
        writeCtl(writer, "VolB", "7");
        writeCtl(writer, "VolA", "30");
        writeCtl(writer, "VolB", "5");
        writeCtl(writer, "VolA", "9");
        writeCtl(writer, "VolB", "29");
        writer.write("</path>\n");

        // Write four controls with different values, forward and reverse order
        writer.write("<path name=\"4F\">\n");
        writeCtl(writer, "VolA", "3");
        writeCtl(writer, "VolB", "15");
        writeCtl(writer, "VolC", "22");
        writeCtl(writer, "VolD", "11");
        writer.write("</path>\n");

        writer.write("<path name=\"4R\">\n");
        writeCtl(writer, "VolD", "11");
        writeCtl(writer, "VolC", "22");
        writeCtl(writer, "VolB", "15");
        writeCtl(writer, "VolA", "3");
        writer.write("</path>\n");

        // Write four different control types in different orders
        writer.write("<path name=\"X4F\">\n");
        writeCtl(writer, "VolA", "18");
        writeCtl(writer, "SwitchA", "1");
        writeCtl(writer, "MuxA", "IN2L");
        writeCtl(writer, "CoeffA", "0xa,0xb,0xc,0xd");
        writer.write("</path>\n");

        writer.write("<path name=\"X4R\">\n");
        writeCtl(writer, "CoeffA", "0x1a,0x1b,0x1c,0x1d");
        writeCtl(writer, "MuxA", "IN1L");
        writeCtl(writer, "SwitchA", "1");
        writeCtl(writer, "VolA", "17");
        writer.write("</path>\n");

        // Path with a very large number of controls
        // with random data value so we can tell every control was written
        sBigPathValues = new byte[sNumBigPathControls];
        Random rand = new Random();
        rand.nextBytes(sBigPathValues);
        writer.write("<path name=\"Biggles\">\n");
        for (int i = 0; i < sNumBigPathControls; ++i) {
            writeCtl(writer, "Thing" + i, Integer.toString(sBigPathValues[i]));
        }
        writer.write("</path>\n");

        writer.write("</device>\n");

        // Create a stream for each path
        writeStreamEntry(writer, "S4");
        writeStreamEntry(writer, "TO4");
        writeStreamEntry(writer, "TI4");
        writeStreamEntry(writer, "4F");
        writeStreamEntry(writer, "4R");
        writeStreamEntry(writer, "X4F");
        writeStreamEntry(writer, "X4R");
        writeStreamEntry(writer, "Biggles");

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

    private static void writeStreamEntry(FileWriter writer,
                                         String name) throws IOException
    {
            writer.write("<stream name=\"" + name +
                         "\" type=\"hw\" dir=\"out\" >\n");
            writer.write("<enable path=\"" + name + "\" />\n");
            writer.write("</stream>\n");
    }

    /**
     * Write the same control multiple times with different values.
     * Writes should happen in the order they are listed in the xml so the
     * control should have the value from the last &lt;ctl&gt; line.
     */
    @Test
    public void testMultiWriteOneControl()
    {
        assertEquals("VolA not initially 0", 0, mAlsaMock.getInt("VolA", 0));

        long stream = mConfigMgr.get_named_stream("S4");
        assertFalse("Failed to get stream", stream < 0);

        assertTrue("VolA was not changed", mAlsaMock.isChanged("VolA"));
        assertEquals("VolA not written correctly", 13, mAlsaMock.getInt("VolA", 0));

        assertEquals("Failed to close stream",
                     0,
                     mConfigMgr.release_stream(stream));
    }

    /**
     * Write two controls multiple times with different values.
     * All writes to the first control are done before writing to the second
     * control.
     * Writes should happen in the order they are listed in the xml so the
     * control should have the value from the last &lt;ctl&gt; line for that
     * control.
     */
    @Test
    public void testMultiWriteTwoControls()
    {
        assertEquals("VolA not initially 0", 0, mAlsaMock.getInt("VolA", 0));
        assertEquals("VolB not initially 0", 0, mAlsaMock.getInt("VolB", 0));

        long stream = mConfigMgr.get_named_stream("TO4");
        assertFalse("Failed to get stream", stream < 0);

        assertTrue("VolA was not changed", mAlsaMock.isChanged("VolA"));
        assertEquals("VolA not written correctly", 6, mAlsaMock.getInt("VolA", 0));
        assertTrue("VolB was not changed", mAlsaMock.isChanged("VolB"));
        assertEquals("VolB not written correctly", 31, mAlsaMock.getInt("VolB", 0));

        assertEquals("Failed to close stream",
                     0,
                     mConfigMgr.release_stream(stream));
    }

    /**
     * Write two controls multiple times with different values.
     * The writes of each control are interleaved.
     * Writes should happen in the order they are listed in the xml so the
     * control should have the value from the last &lt;ctl&gt; line for that
     * control.
     */
    @Test
    public void testMultiWriteTwoControlsInterleaved()
    {
        assertEquals("VolA not initially 0", 0, mAlsaMock.getInt("VolA", 0));
        assertEquals("VolB not initially 0", 0, mAlsaMock.getInt("VolB", 0));

        long stream = mConfigMgr.get_named_stream("TI4");
        assertFalse("Failed to get stream", stream < 0);

        assertTrue("VolA was not changed", mAlsaMock.isChanged("VolA"));
        assertEquals("VolA not written correctly", 9, mAlsaMock.getInt("VolA", 0));
        assertTrue("VolB was not changed", mAlsaMock.isChanged("VolB"));
        assertEquals("VolB not written correctly", 29, mAlsaMock.getInt("VolB", 0));

        assertEquals("Failed to close stream",
                     0,
                     mConfigMgr.release_stream(stream));
    }

    /**
     * Write four controls once with different values for each control.
     */
    @Test
    public void testSingleWriteMultiControls()
    {
        assertEquals("VolA not initially 0", 0, mAlsaMock.getInt("VolA", 0));
        assertEquals("VolB not initially 0", 0, mAlsaMock.getInt("VolB", 0));
        assertEquals("VolC not initially 0", 0, mAlsaMock.getInt("VolC", 0));
        assertEquals("VolD not initially 0", 0, mAlsaMock.getInt("VolD", 0));

        long stream = mConfigMgr.get_named_stream("4F");
        assertFalse("Failed to get stream", stream < 0);

        assertTrue("VolA was not changed", mAlsaMock.isChanged("VolA"));
        assertEquals("VolA not written correctly", 3, mAlsaMock.getInt("VolA", 0));

        assertTrue("VolB was not changed", mAlsaMock.isChanged("VolB"));
        assertEquals("VolB not written correctly", 15, mAlsaMock.getInt("VolB", 0));

        assertTrue("VolC was not changed", mAlsaMock.isChanged("VolC"));
        assertEquals("VolC not written correctly", 22, mAlsaMock.getInt("VolC", 0));

        assertTrue("VolD was not changed", mAlsaMock.isChanged("VolD"));
        assertEquals("VolD not written correctly", 11, mAlsaMock.getInt("VolD", 0));

        assertEquals("Failed to close stream",
                     0,
                     mConfigMgr.release_stream(stream));
    }

    /**
     * Write four controls once with different values for each control.
     * This is the same as testSingleWriteMultiControls() but the controls
     * are written in reverse order.
     */
    @Test
    public void testSingleWriteMultiControlsReverse()
    {
        assertEquals("VolA not initially 0", 0, mAlsaMock.getInt("VolA", 0));
        assertEquals("VolB not initially 0", 0, mAlsaMock.getInt("VolB", 0));
        assertEquals("VolC not initially 0", 0, mAlsaMock.getInt("VolC", 0));
        assertEquals("VolD not initially 0", 0, mAlsaMock.getInt("VolD", 0));

        long stream = mConfigMgr.get_named_stream("4R");
        assertFalse("Failed to get stream", stream < 0);

        assertTrue("VolA was not changed", mAlsaMock.isChanged("VolA"));
        assertEquals("VolA not written correctly", 3, mAlsaMock.getInt("VolA", 0));

        assertTrue("VolB was not changed", mAlsaMock.isChanged("VolB"));
        assertEquals("VolB not written correctly", 15, mAlsaMock.getInt("VolB", 0));

        assertTrue("VolC was not changed", mAlsaMock.isChanged("VolC"));
        assertEquals("VolC not written correctly", 22, mAlsaMock.getInt("VolC", 0));

        assertTrue("VolD was not changed", mAlsaMock.isChanged("VolD"));
        assertEquals("VolD not written correctly", 11, mAlsaMock.getInt("VolD", 0));

        assertEquals("Failed to close stream",
                     0,
                     mConfigMgr.release_stream(stream));
    }

    /**
     * Write four controls of different types.
     */
    @Test
    public void testMixedTypes()
    {
        assertEquals("VolA not initially 0", 0, mAlsaMock.getInt("VolA", 0));
        assertEquals("SwitchA not initially 0", 0, mAlsaMock.getBool("SwitchA", 0));
        assertEquals("MuxA not initially None", "None", mAlsaMock.getEnum("MuxA"));
        assertArrayEquals("CoeffA not initially 0", new byte[4], mAlsaMock.getData("CoeffA"));

        long stream = mConfigMgr.get_named_stream("X4F");
        assertFalse("Failed to get stream", stream < 0);

        assertTrue("VolA was not changed", mAlsaMock.isChanged("VolA"));
        assertEquals("VolA not written correctly", 18, mAlsaMock.getInt("VolA", 0));

        assertTrue("SwitchA was not changed", mAlsaMock.isChanged("SwitchA"));
        assertEquals("SwitchA not written correctly", 1, mAlsaMock.getBool("SwitchA", 0));

        assertTrue("MuxA was not changed", mAlsaMock.isChanged("MuxA"));
        assertEquals("MuxA not written correctly", "IN2L", mAlsaMock.getEnum("MuxA"));

        assertTrue("CoeffA was not changed", mAlsaMock.isChanged("CoeffA"));
        byte[] expectedData = { 0xa, 0xb, 0xc, 0xd };
        assertArrayEquals("CoeffA not written correctly",
                          expectedData,
                          mAlsaMock.getData("CoeffA"));

        assertEquals("Failed to close stream",
                     0,
                     mConfigMgr.release_stream(stream));
    }

    /**
     * Write four controls of different types.
     * Same as testMixedTypes() but controls are written in reverse order.
     */
    @Test
    public void testMixedTypesReverse()
    {
        assertEquals("VolA not initially 0", 0, mAlsaMock.getInt("VolA", 0));
        assertEquals("SwitchA not initially 0", 0, mAlsaMock.getBool("SwitchA", 0));
        assertEquals("MuxA not initially None", "None", mAlsaMock.getEnum("MuxA"));
        assertArrayEquals("CoeffA not initially 0", new byte[4], mAlsaMock.getData("CoeffA"));

        long stream = mConfigMgr.get_named_stream("X4R");
        assertFalse("Failed to get stream", stream < 0);

        assertTrue("VolA was not changed", mAlsaMock.isChanged("VolA"));
        assertEquals("VolA not written correctly", 17, mAlsaMock.getInt("VolA", 0));

        assertTrue("SwitchA was not changed", mAlsaMock.isChanged("SwitchA"));
        assertEquals("SwitchA not written correctly", 1, mAlsaMock.getBool("SwitchA", 0));

        assertTrue("MuxA was not changed", mAlsaMock.isChanged("MuxA"));
        assertEquals("MuxA not written correctly", "IN1L", mAlsaMock.getEnum("MuxA"));

        assertTrue("CoeffA was not changed", mAlsaMock.isChanged("CoeffA"));
        byte[] expectedData = { 0x1a, 0x1b, 0x1c, 0x1d };
        assertArrayEquals("CoeffA not written correctly",
                          expectedData,
                          mAlsaMock.getData("CoeffA"));

        assertEquals("Failed to close stream",
                     0,
                     mConfigMgr.release_stream(stream));
    }

    /**
     * Invoke path with a large number of &lt;ctl&gt; entries.
     */
    @Test
    public void testBigPath()
    {
        for (int i = 0; i < sNumBigPathControls; ++i) {
            assertEquals("Thing" + i + " not initially 0",
                         0,
                         mAlsaMock.getInt("Thing" + i, 0));
        }

        long stream = mConfigMgr.get_named_stream("Biggles");
        assertFalse("Failed to get stream", stream < 0);

        for (int i = 0; i < sNumBigPathControls; ++i) {
            assertTrue("Thing" + i + " was not changed",
                       mAlsaMock.isChanged("Thing" + i));
            assertEquals("Thing" + i + " not written correctly",
                         sBigPathValues[i],
                         mAlsaMock.getInt("Thing" + i, 0));
        }

        assertEquals("Failed to close stream",
                     0,
                     mConfigMgr.release_stream(stream));
    }
};
