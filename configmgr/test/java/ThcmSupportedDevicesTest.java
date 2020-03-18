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
import java.lang.Math;
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
 * Tests that the set of available input and output devices defined by
 * &lt;device&gt; elements are correctly reported.
 */
@RunWith(Parameterized.class)
public class ThcmSupportedDevicesTest
{
    private static final File sWorkFilesPath = ThcmPlatform.workFilesPath();
    private static final File sControlsFile = new File(sWorkFilesPath, "thcm_supported_devices.csv");
    private static final File sXmlFile = new File(sWorkFilesPath, "thcm_supported_devices.xml");

    private CAlsaMock mAlsaMock = new CAlsaMock();
    private CConfigMgr mConfigMgr = new CConfigMgr();

    private String[] mOutputDevices;
    private String[] mInputDevices;
    private long mAllOutputBits;
    private long mAllInputBits;

    @Parameterized.Parameters
    public static Collection parameters() {
        // No need to test every combination. Just add one more device to the
        // test set on each pass.
        List<Integer[]> list = new ArrayList<Integer[]>();
        int maxNumDevices = Math.max(CConfigMgr.OUTPUT_DEVICES.length,
                                     CConfigMgr.INPUT_DEVICES.length);
        for (int i = 1; i < maxNumDevices; ++i) {
            list.add(new Integer[] {i});
        }
        return list;
    }

    public ThcmSupportedDevicesTest(int numDevices)
    {
        mOutputDevices = Arrays.copyOfRange(CConfigMgr.OUTPUT_DEVICES, 0, numDevices);
        mInputDevices = Arrays.copyOfRange(CConfigMgr.INPUT_DEVICES, 0, numDevices);

        mAllOutputBits = 0;
        for (String name : mOutputDevices) {
            mAllOutputBits |= CConfigMgr.deviceFromName(name);
        }

        mAllInputBits = 0;
        for (String name : mInputDevices) {
            mAllInputBits |= CConfigMgr.deviceFromName(name);
        }
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

    private void createXmlFileOutputsOnly() throws IOException
    {
        createXmlFile(true, false);
    }

    private void createXmlFileInputsOnly() throws IOException
    {
        createXmlFile(false, true);
    }

    private void createXmlFileOutputsAndInputs() throws IOException
    {
        createXmlFile(true, true);
    }

    private void createXmlFile(boolean outputs, boolean inputs) throws IOException
    {
        FileWriter writer = new FileWriter(sXmlFile);

        // Header elements
        writer.write("<audiohal>\n<mixer card=\"0\" />\n");

        if (outputs) {
            for (String name : mOutputDevices) {
                writer.write("<device name=\"" + name + "\" />\n");
            }
        }

        if (inputs) {
            for (String name : mInputDevices) {
                writer.write("<device name=\"" + name + "\" />\n");
            }
        }

        // Footer elements
        writer.write("\n</audiohal>\n");

        writer.close();
    }

    /**
     * Test a configuration containing only output devices.
     * @throws java.io.IOException if unable to write the XML file.
     */
    @Test
    public void testOutputsOnly() throws IOException
    {
        createXmlFileOutputsOnly();
        assertEquals("Failed to open CConfigMgr",
                     0,
                     mConfigMgr.init_audio_config(sXmlFile.toPath().toString()));

        assertEquals("get_supported_output_devices not correct",
                     mAllOutputBits,
                     mConfigMgr.get_supported_output_devices());
        assertEquals("get_supported_input_devices was not zero",
                     0,
                     mConfigMgr.get_supported_input_devices());
    }

    /**
     * Test a configuration containing only input devices.
     * @throws java.io.IOException if unable to write the XML file.
     */
    @Test
    public void testReportedInputsOnly() throws IOException
    {
        createXmlFileInputsOnly();
        assertEquals("Failed to open CConfigMgr",
                     0,
                     mConfigMgr.init_audio_config(sXmlFile.toPath().toString()));

        assertEquals("get_supported_output_devices was not zero",
                     0,
                     mConfigMgr.get_supported_output_devices());
        assertEquals("get_supported_input_devices not correct",
                     mAllInputBits,
                     mConfigMgr.get_supported_input_devices());
    }

    /**
     * Test a configuration containing output and input devices.
     * @throws java.io.IOException if unable to write the XML file.
     */
    @Test
    public void testReportedOutputsAndInputs() throws IOException
    {
        createXmlFileOutputsAndInputs();
        assertEquals("Failed to open CConfigMgr",
                     0,
                     mConfigMgr.init_audio_config(sXmlFile.toPath().toString()));

        assertEquals("get_supported_output_devices not correct",
                     mAllOutputBits,
                     mConfigMgr.get_supported_output_devices());
        assertEquals("get_supported_input_devices not correct",
                     mAllInputBits,
                     mConfigMgr.get_supported_input_devices());
    }
};
