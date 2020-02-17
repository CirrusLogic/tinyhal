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
import com.cirrus.tinyhal.test.thcm.CAlsaMock;
import com.cirrus.tinyhal.test.thcm.CConfigMgr;
import com.cirrus.tinyhal.test.thcm.ThcmPlatform;
import static org.junit.Assert.*;

public class ThcmControlTest
{
    private static final File sWorkFilesPath = ThcmPlatform.workFilesPath();
    private static File sControlsFile = new File(sWorkFilesPath, "alsacontrols.csv");
    private static File sXmlFile = new File(sWorkFilesPath, "test_config1.xml");

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

    private static void createAlsaControlsFile() throws IOException
    {
        FileWriter writer = new FileWriter(sControlsFile);

        writer.write("OUT1 Volume,int,2,32,0:32\n");
        writer.write("OUT2 Volume,int,2,32,0:32\n");
        writer.write("OUT1 Switch,bool,2,0,\n");
        writer.write("OUT2 Switch,bool,2,0,\n");
        writer.write("IN1 Volume,int,2,32,0:32\n");
        writer.write("IN2 Volume,int,2,32,0:32\n");
        writer.write("OUT1L Source,enum,1,IN1L,IN1L:IN1R:IN2L:IN2R:HPF1:HPF2:HPF3:HPF4:TG1:TG2\n");
        writer.write("OUT1R Source,enum,1,IN1L,IN1L:IN1R:IN2L:IN2R:HPF1:HPF2:HPF3:HPF4:TG1:TG2\n");
        writer.write("OUT2L Source,enum,1,IN1L,IN1L:IN1R:IN2L:IN2R:HPF1:HPF2:HPF3:HPF4:TG1:TG2\n");
        writer.write("OUT2R Source,enum,1,IN1L,IN1L:IN1R:IN2L:IN2R:HPF1:HPF2:HPF3:HPF4:TG1:TG2\n");
        writer.write("HPF1 Coeff,byte,16,0,\n");
        writer.write("HPF2 Coeff,byte,16,0,\n");
        writer.write("HPF3 Coeff,byte,16,0,\n");
        writer.write("HPF4 Coeff,byte,16,0,\n");

        writer.close();
    }

    private static void createXmlFile() throws IOException
    {
        // For each TEST_VALUES create a path to write that value

        FileWriter writer = new FileWriter(sXmlFile);

        // Dummy empty config only so the configmgr has a valid file to load
        writer.write("<audiohal></audiohal>\n");

        writer.close();
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

    // Dummy to prove we can call a configmgr function
    @Test
    public void testSupportedDevices()
    {
        assertEquals("Unexpected value of get_supported_input_devices",
                     0,
                     mConfigMgr.get_supported_input_devices());

        assertEquals("Unexpected value of get_supported_output_devices",
                     0,
                     mConfigMgr.get_supported_output_devices());
    }

    // Dummy just to prove the CAlsaMock works
    @Test
    public void testDumpControls()
    {
        assertEquals("OUT1 Volume[0] has wrong value", 32, mAlsaMock.getInt("OUT1 Volume", 0));
        assertEquals("OUT1 Volume[1] has wrong value", 32, mAlsaMock.getInt("OUT1 Volume", 1));
        assertEquals("OUT2 Volume[0] has wrong value", 32, mAlsaMock.getInt("OUT2 Volume", 0));
        assertEquals("OUT2 Volume[1] has wrong value", 32, mAlsaMock.getInt("OUT2 Volume", 1));

        assertEquals("OUT1 Switch[0] has wrong value", 0, mAlsaMock.getBool("OUT1 Switch", 0));
        assertEquals("OUT1 Switch[1] has wrong value", 0, mAlsaMock.getBool("OUT1 Switch", 1));
        assertEquals("OUT2 Switch[0] has wrong value", 0, mAlsaMock.getBool("OUT2 Switch", 0));
        assertEquals("OUT2 Switch[1] has wrong value", 0, mAlsaMock.getBool("OUT2 Switch", 1));

        assertEquals("OUT1L Source has wrong value", "IN1L", mAlsaMock.getEnum("OUT1L Source"));
        assertEquals("OUT1R Source has wrong value", "IN1L", mAlsaMock.getEnum("OUT1R Source"));
        assertEquals("OUT2L Source has wrong value", "IN1L", mAlsaMock.getEnum("OUT2L Source"));
        assertEquals("OUT2R Source has wrong value", "IN1L", mAlsaMock.getEnum("OUT2R Source"));

        byte[] coeffExpected = new byte[16];
        assertArrayEquals("HPF1 Coeff has wrong value", coeffExpected, mAlsaMock.getData("HPF1 Coeff"));
        assertArrayEquals("HPF2 Coeff has wrong value", coeffExpected, mAlsaMock.getData("HPF2 Coeff"));
        assertArrayEquals("HPF3 Coeff has wrong value", coeffExpected, mAlsaMock.getData("HPF3 Coeff"));
        assertArrayEquals("HPF4 Coeff has wrong value", coeffExpected, mAlsaMock.getData("HPF4 Coeff"));
    }
};
