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
 * Tests that &lt;ctl&gt; elements in a &lt;case&gt; are all executed and that
 * the order of execution is correct.
 * This assumes that &lt;ctl&gt; elements execute corectly as do other tinyhal
 * features required to invoke the &lt;path&gt; element, and that these features
 * are tested elsewhere.
 */
public class ThcmUsecaseControlsTest
{
    private static final File sWorkFilesPath = ThcmPlatform.workFilesPath();
    private static final File sControlsFile = new File(sWorkFilesPath, "thcm_usecase_controls.csv");
    private static final File sXmlFile = new File(sWorkFilesPath, "thcm_usecase_controls.xml");

    private static final int sNumBigCaseControls = 100;
    private static byte[] sBigCaseValues;

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

        // Create many controls for testing a case with a large number of
        // controls
        for (int i = 0; i < sNumBigCaseControls; ++i) {
            writer.write("Thing" + i + ",int,1,0,0:32\n");
        }

        writer.close();
    }

    private static void createXmlFile() throws IOException
    {
        FileWriter writer = new FileWriter(sXmlFile);

        // Header elements
        writer.write("<audiohal>\n<mixer card=\"0\" />\n");

        // A stream to contain the usecase
        writer.write("<stream name=\"test\" type=\"hw\" dir=\"out\" >\n");

        // Usecase
        writer.write("<usecase name=\"test\">\n");

        // Write four different values to the same control
        writer.write("<case name=\"S4\">\n");
        writeCtl(writer, "VolA", "16");
        writeCtl(writer, "VolA", "7");
        writeCtl(writer, "VolA", "0");
        writeCtl(writer, "VolA", "13");
        writer.write("</case>\n");

        // Write four different values to one control, then four values to
        // another control
        writer.write("<case name=\"TO4\">\n");
        writeCtl(writer, "VolA", "4");
        writeCtl(writer, "VolA", "1");
        writeCtl(writer, "VolA", "30");
        writeCtl(writer, "VolA", "6");
        writeCtl(writer, "VolB", "8");
        writeCtl(writer, "VolB", "7");
        writeCtl(writer, "VolB", "5");
        writeCtl(writer, "VolB", "31");
        writer.write("</case>\n");

        // Write four different values to two controls, with the writes for
        // the controls interleaved
        writer.write("<case name=\"TI4\">\n");
        writeCtl(writer, "VolA", "4");
        writeCtl(writer, "VolB", "8");
        writeCtl(writer, "VolA", "1");
        writeCtl(writer, "VolB", "7");
        writeCtl(writer, "VolA", "30");
        writeCtl(writer, "VolB", "5");
        writeCtl(writer, "VolA", "9");
        writeCtl(writer, "VolB", "29");
        writer.write("</case>\n");

        // Write four controls with different values, forward and reverse order
        writer.write("<case name=\"4F\">\n");
        writeCtl(writer, "VolA", "3");
        writeCtl(writer, "VolB", "15");
        writeCtl(writer, "VolC", "22");
        writeCtl(writer, "VolD", "11");
        writer.write("</case>\n");

        writer.write("<case name=\"4R\">\n");
        writeCtl(writer, "VolD", "11");
        writeCtl(writer, "VolC", "22");
        writeCtl(writer, "VolB", "15");
        writeCtl(writer, "VolA", "3");
        writer.write("</case>\n");

        // Write four different control types in different orders
        writer.write("<case name=\"X4F\">\n");
        writeCtl(writer, "VolA", "18");
        writeCtl(writer, "SwitchA", "1");
        writeCtl(writer, "MuxA", "IN2L");
        writeCtl(writer, "CoeffA", "0xa,0xb,0xc,0xd");
        writer.write("</case>\n");

        writer.write("<case name=\"X4R\">\n");
        writeCtl(writer, "CoeffA", "0x1a,0x1b,0x1c,0x1d");
        writeCtl(writer, "MuxA", "IN1L");
        writeCtl(writer, "SwitchA", "1");
        writeCtl(writer, "VolA", "17");
        writer.write("</case>\n");

        // Path with a very large number of controls
        // with random data value so we can tell every control was written
        sBigCaseValues = new byte[sNumBigCaseControls];
        Random rand = new Random();
        rand.nextBytes(sBigCaseValues);
        writer.write("<case name=\"Biggles\">\n");
        for (int i = 0; i < sNumBigCaseControls; ++i) {
            writeCtl(writer, "Thing" + i, Integer.toString(sBigCaseValues[i]));
        }
        writer.write("</case>\n");

        writer.write("</usecase></stream></audiohal>\n");

        writer.close();
    }

    private static void writeCtl(FileWriter writer,
                                 String controlName,
                                 String value) throws IOException
    {
            writer.write("<ctl name=\"" + controlName + "\" val=\"" + value + "\"/>\n");
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

        long stream = mConfigMgr.get_named_stream("test");
        assertFalse("Failed to get stream", stream < 0);

        assertEquals("Failed to invoke usecase S4",
                     0,
                     mConfigMgr.apply_use_case(stream, "test", "S4"));

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

        long stream = mConfigMgr.get_named_stream("test");
        assertFalse("Failed to get stream", stream < 0);

        assertEquals("Failed to invoke usecase TO4",
                     0,
                     mConfigMgr.apply_use_case(stream, "test", "TO4"));

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

        long stream = mConfigMgr.get_named_stream("test");
        assertFalse("Failed to get stream", stream < 0);

        assertEquals("Failed to invoke usecase TI4",
                     0,
                     mConfigMgr.apply_use_case(stream, "test", "TI4"));

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

        long stream = mConfigMgr.get_named_stream("test");
        assertFalse("Failed to get stream", stream < 0);

        assertEquals("Failed to invoke usecase 4F",
                     0,
                     mConfigMgr.apply_use_case(stream, "test", "4F"));

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

        long stream = mConfigMgr.get_named_stream("test");
        assertFalse("Failed to get stream", stream < 0);

        assertEquals("Failed to invoke usecase 4R",
                     0,
                     mConfigMgr.apply_use_case(stream, "test", "4R"));

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

        long stream = mConfigMgr.get_named_stream("test");
        assertFalse("Failed to get stream", stream < 0);

        assertEquals("Failed to invoke usecase X4F",
                     0,
                     mConfigMgr.apply_use_case(stream, "test", "X4F"));

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

        long stream = mConfigMgr.get_named_stream("test");
        assertFalse("Failed to get stream", stream < 0);

        assertEquals("Failed to invoke usecase X4R",
                     0,
                     mConfigMgr.apply_use_case(stream, "test", "X4R"));

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
     * Invoke case with a large number of &lt;ctl&gt; entries.
     */
    @Test
    public void testBigCase()
    {
        for (int i = 0; i < sNumBigCaseControls; ++i) {
            assertEquals("Thing" + i + " not initially 0",
                         0,
                         mAlsaMock.getInt("Thing" + i, 0));
        }

        long stream = mConfigMgr.get_named_stream("test");
        assertFalse("Failed to get stream", stream < 0);

        assertEquals("Failed to invoke usecase Biggles",
                     0,
                     mConfigMgr.apply_use_case(stream, "test", "Biggles"));

        for (int i = 0; i < sNumBigCaseControls; ++i) {
            assertTrue("Thing" + i + " was not changed",
                       mAlsaMock.isChanged("Thing" + i));
            assertEquals("Thing" + i + " not written correctly",
                         sBigCaseValues[i],
                         mAlsaMock.getInt("Thing" + i, 0));
        }

        assertEquals("Failed to close stream",
                     0,
                     mConfigMgr.release_stream(stream));
    }
};
