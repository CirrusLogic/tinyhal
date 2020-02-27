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
import java.util.Arrays;
import java.util.Collection;
import java.util.Random;
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
 * Tests that &lt;ctl&gt; elements in &lt;init&gt; and &lt;pre_init&gt; are all
 * executed and that the order of execution is correct.
 * &lt;init&gt; and &lt;pre_init&gt; apply controls in the same way so the same
 * tests cases can be used to test both types of elements.
 *
 * This assumes that &lt;ctl&gt; elements execute correctly and that this is
 * tested elsewhere.
 */
@RunWith(Parameterized.class)
public class ThcmInitControlsTest
{
    // Parameterization requires an array of arrays
    private static final String[][] ELEMENTS = { {"init"}, {"pre_init"} };

    private static final File sWorkFilesPath = ThcmPlatform.workFilesPath();
    private static final File sControlsFile = new File(sWorkFilesPath, "thcm_init_controls.csv");
    private static final File sXmlFile = new File(sWorkFilesPath, "thcm_init_controls.xml");

    private static final int sNumBigInitControls = 100;

    private CAlsaMock mAlsaMock = new CAlsaMock();
    private CConfigMgr mConfigMgr = new CConfigMgr();

    private String mTestElement;

    @Parameterized.Parameters
    public static Collection parameters() {
        return Arrays.asList(ELEMENTS);
    }

    public ThcmInitControlsTest(String element)
    {
        mTestElement = element;
    }

    @BeforeClass
    public static void setUpClass() throws IOException
    {
        createAlsaControlsFile();
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

        // Four simple int controls to prove that writes executed in order
        writer.write("VolA,int,1,0,0:32\n");
        writer.write("VolB,int,1,0,0:32\n");
        writer.write("VolC,int,1,0,0:32\n");
        writer.write("VolD,int,1,0,0:32\n");

        // Some controls of other type for testing mixed controls
        writer.write("SwitchA,bool,1,0,0:1\n");
        writer.write("MuxA,enum,1,None,None:IN1L:IN1R:IN2L:IN2R\n");
        writer.write("CoeffA,byte,4,0,\n");

        // Create many controls for testing a path with a large number of
        // controls
        for (int i = 0; i < sNumBigInitControls; ++i) {
            writer.write("Thing" + i + ",int,1,0,0:32\n");
        }

        writer.close();
    }

    private void writeXmlHeader(FileWriter writer) throws IOException
    {
        // Header elements
        writer.write("<audiohal><mixer card=\"0\"><" + mTestElement + ">\n");
    }

    private void writeXmlFooter(FileWriter writer) throws IOException
    {
        // Footer elements
        writer.write("\n</" + mTestElement + "></mixer></audiohal>\n");
    }

    private void writeCtl(FileWriter writer,
                          String controlName,
                          String value) throws IOException
    {
        writer.write("<ctl name=\"" + controlName + "\" val=\"" + value + "\"/>\n");
    }

    private void writeXml(String[][] controls)
    {
        try {
            FileWriter writer = new FileWriter(sXmlFile);
            writeXmlHeader(writer);
            for (String[] ctl : controls) {
                writeCtl(writer, ctl[0], ctl[1]);
            }
            writeXmlFooter(writer);
            writer.close();
        } catch (IOException e) {
            fail("Failed to write xml file: " + e);
        }
    }

    /**
     * Write the same control multiple times with different values.
     * Writes should happen in the order they are listed in the xml so the
     * control should have the value from the last &lt;ctl&gt; line.
     */
    @Test
    public void testMultiWriteOneControl()
    {
        String[][] controls = {
            { "VolA", "16" },
            { "VolA", "7" },
            { "VolA", "0" },
            { "VolA", "13" }
        };
        writeXml(controls);

        assertEquals("VolA not initially 0", 0, mAlsaMock.getInt("VolA", 0));

        assertEquals("Failed to open CConfigMgr",
                     0,
                     mConfigMgr.init_audio_config(sXmlFile.toPath().toString()));

        assertTrue("VolA was not changed", mAlsaMock.isChanged("VolA"));
        assertEquals("VolA not written correctly", 13, mAlsaMock.getInt("VolA", 0));
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
        String[][] controls = {
            { "VolA", "4" },
            { "VolA", "1" },
            { "VolA", "30" },
            { "VolA", "6" },
            { "VolB", "8" },
            { "VolB", "7" },
            { "VolB", "5" },
            { "VolB", "31" }
        };
        writeXml(controls);

        assertEquals("VolA not initially 0", 0, mAlsaMock.getInt("VolA", 0));
        assertEquals("VolB not initially 0", 0, mAlsaMock.getInt("VolB", 0));

        assertEquals("Failed to open CConfigMgr",
                     0,
                     mConfigMgr.init_audio_config(sXmlFile.toPath().toString()));

        assertTrue("VolA was not changed", mAlsaMock.isChanged("VolA"));
        assertEquals("VolA not written correctly", 6, mAlsaMock.getInt("VolA", 0));
        assertTrue("VolB was not changed", mAlsaMock.isChanged("VolB"));
        assertEquals("VolB not written correctly", 31, mAlsaMock.getInt("VolB", 0));
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
        String[][] controls = {
            { "VolA", "4" },
            { "VolB", "8" },
            { "VolA", "1" },
            { "VolB", "7" },
            { "VolA", "30" },
            { "VolB", "5" },
            { "VolA", "9" },
            { "VolB", "29" }
        };
        writeXml(controls);

        assertEquals("VolA not initially 0", 0, mAlsaMock.getInt("VolA", 0));
        assertEquals("VolB not initially 0", 0, mAlsaMock.getInt("VolB", 0));

        assertEquals("Failed to open CConfigMgr",
                     0,
                     mConfigMgr.init_audio_config(sXmlFile.toPath().toString()));

        assertTrue("VolA was not changed", mAlsaMock.isChanged("VolA"));
        assertEquals("VolA not written correctly", 9, mAlsaMock.getInt("VolA", 0));
        assertTrue("VolB was not changed", mAlsaMock.isChanged("VolB"));
        assertEquals("VolB not written correctly", 29, mAlsaMock.getInt("VolB", 0));
    }

    /**
     * Write four controls once with different values for each control.
     */
    @Test
    public void testSingleWriteMultiControls()
    {
        String[][] controls = {
            { "VolA", "3" },
            { "VolB", "15" },
            { "VolC", "22" },
            { "VolD", "11" },
        };
        writeXml(controls);

        assertEquals("VolA not initially 0", 0, mAlsaMock.getInt("VolA", 0));
        assertEquals("VolB not initially 0", 0, mAlsaMock.getInt("VolB", 0));
        assertEquals("VolC not initially 0", 0, mAlsaMock.getInt("VolC", 0));
        assertEquals("VolD not initially 0", 0, mAlsaMock.getInt("VolD", 0));

        assertEquals("Failed to open CConfigMgr",
                     0,
                     mConfigMgr.init_audio_config(sXmlFile.toPath().toString()));

        assertTrue("VolA was not changed", mAlsaMock.isChanged("VolA"));
        assertEquals("VolA not written correctly", 3, mAlsaMock.getInt("VolA", 0));

        assertTrue("VolB was not changed", mAlsaMock.isChanged("VolB"));
        assertEquals("VolB not written correctly", 15, mAlsaMock.getInt("VolB", 0));

        assertTrue("VolC was not changed", mAlsaMock.isChanged("VolC"));
        assertEquals("VolC not written correctly", 22, mAlsaMock.getInt("VolC", 0));

        assertTrue("VolD was not changed", mAlsaMock.isChanged("VolD"));
        assertEquals("VolD not written correctly", 11, mAlsaMock.getInt("VolD", 0));
    }

    /**
     * Write four controls once with different values for each control.
     * This is the same as testSingleWriteMultiControls() but the controls
     * are written in reverse order.
     */
    @Test
    public void testSingleWriteMultiControlsReverse()
    {
        String[][] controls = {
            { "VolD", "11" },
            { "VolC", "22" },
            { "VolB", "15" },
            { "VolA", "3" },
        };
        writeXml(controls);

        assertEquals("VolA not initially 0", 0, mAlsaMock.getInt("VolA", 0));
        assertEquals("VolB not initially 0", 0, mAlsaMock.getInt("VolB", 0));
        assertEquals("VolC not initially 0", 0, mAlsaMock.getInt("VolC", 0));
        assertEquals("VolD not initially 0", 0, mAlsaMock.getInt("VolD", 0));

        assertEquals("Failed to open CConfigMgr",
                     0,
                     mConfigMgr.init_audio_config(sXmlFile.toPath().toString()));

        assertTrue("VolA was not changed", mAlsaMock.isChanged("VolA"));
        assertEquals("VolA not written correctly", 3, mAlsaMock.getInt("VolA", 0));

        assertTrue("VolB was not changed", mAlsaMock.isChanged("VolB"));
        assertEquals("VolB not written correctly", 15, mAlsaMock.getInt("VolB", 0));

        assertTrue("VolC was not changed", mAlsaMock.isChanged("VolC"));
        assertEquals("VolC not written correctly", 22, mAlsaMock.getInt("VolC", 0));

        assertTrue("VolD was not changed", mAlsaMock.isChanged("VolD"));
        assertEquals("VolD not written correctly", 11, mAlsaMock.getInt("VolD", 0));
    }

    /**
     * Write four controls of different types.
     */
    @Test
    public void testMixedTypes()
    {
        String[][] controls = {
            { "VolA", "18" },
            { "SwitchA", "1" },
            { "MuxA", "IN2L" },
            { "CoeffA", "0xa,0xb,0xc,0xd" }
        };
        writeXml(controls);

        assertEquals("VolA not initially 0", 0, mAlsaMock.getInt("VolA", 0));
        assertEquals("SwitchA not initially 0", 0, mAlsaMock.getBool("SwitchA", 0));
        assertEquals("MuxA not initially None", "None", mAlsaMock.getEnum("MuxA"));
        assertArrayEquals("CoeffA not initially 0", new byte[4], mAlsaMock.getData("CoeffA"));

        assertEquals("Failed to open CConfigMgr",
                     0,
                     mConfigMgr.init_audio_config(sXmlFile.toPath().toString()));

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
    }

    /**
     * Write four controls of different types.
     * Same as testMixedTypes() but controls are written in reverse order.
     */
    @Test
    public void testMixedTypesReverse()
    {
        String[][] controls = {
            { "CoeffA", "0x1a,0x1b,0x1c,0x1d" },
            { "MuxA", "IN1L" },
            { "SwitchA", "1" },
            { "VolA", "17" }
        };
        writeXml(controls);

        assertEquals("VolA not initially 0", 0, mAlsaMock.getInt("VolA", 0));
        assertEquals("SwitchA not initially 0", 0, mAlsaMock.getBool("SwitchA", 0));
        assertEquals("MuxA not initially None", "None", mAlsaMock.getEnum("MuxA"));
        assertArrayEquals("CoeffA not initially 0", new byte[4], mAlsaMock.getData("CoeffA"));

        assertEquals("Failed to open CConfigMgr",
                     0,
                     mConfigMgr.init_audio_config(sXmlFile.toPath().toString()));

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
    }

    /**
     * Invoke an init list with a large number of &lt;ctl&gt; entries.
     */
    @Test
    public void testBigInit()
    {
        byte[] values = new byte[sNumBigInitControls];
        Random rand = new Random();
        rand.nextBytes(values);

        try {
            FileWriter writer = new FileWriter(sXmlFile);
            writeXmlHeader(writer);
            for (int i = 0; i < values.length; ++i) {
                writeCtl(writer, "Thing" + i, Integer.toString(values[i]));
            }
            writeXmlFooter(writer);
            writer.close();
        } catch (IOException e) {
            fail("Failed to write xml file: " + e);
        }

        for (int i = 0; i < values.length; ++i) {
            assertEquals("Thing" + i + " not initially 0",
                         0,
                         mAlsaMock.getInt("Thing" + i, 0));
        }

        assertEquals("Failed to open CConfigMgr",
                     0,
                     mConfigMgr.init_audio_config(sXmlFile.toPath().toString()));

        for (int i = 0; i < values.length; ++i) {
            assertTrue("Thing" + i + " was not changed",
                       mAlsaMock.isChanged("Thing" + i));
            assertEquals("Thing" + i + " not written correctly",
                         values[i],
                         mAlsaMock.getInt("Thing" + i, 0));
        }
    }
};
