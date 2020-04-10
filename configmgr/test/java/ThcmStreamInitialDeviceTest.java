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
import java.util.List;
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
 * Test invoking the initial &lt;path&gt; elements for a &lt;stream&gt;.
 * Tests that &lt;enable&gt; and &lt;disable&gt; in &lt;stream&gt; elements
 * invoke the correct &lt;path&gt; elements for the initial device passed in
 * get_stream().
 * This assumes that &lt;ctl&gt; elements execute corectly and that this
 * is tested elsewhere.
 */
public class ThcmStreamInitialDeviceTest
{
    private static final String[] TEST_CONTROLS = {
        "SwitchA", "SwitchB", "SwitchC", "SwitchD", "SwitchE", "SwitchF",
        "SwitchG", "SwitchH", "SwitchI", "SwitchJ", "SwitchK", "SwitchL",
    };

    private static final File sWorkFilesPath = ThcmPlatform.workFilesPath();
    private static final File sControlsFile = new File(sWorkFilesPath, "thcm_init_device_controls.csv");
    private static final File sXmlFile = new File(sWorkFilesPath, "thcm_init_device_config.xml");

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

        for (String name : TEST_CONTROLS) {
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
    }

    private static void createAlsaControlsFile() throws IOException
    {
        FileWriter writer = new FileWriter(sControlsFile);

        // Some simple controls to prove that paths were invoked
        for (String name : TEST_CONTROLS) {
            writer.write(name + ",bool,1,0,0:1\n");
        }

        writer.close();
    }

    private static void createXmlFile() throws IOException
    {
        FileWriter writer = new FileWriter(sXmlFile);

        // Header elements
        writer.write("<audiohal>\n<mixer card=\"0\" />\n");

        // PCM and compressed streams are used as a simple way to create two
        // streams - there is no other significance.

        // Speaker device that has paths for both PCM and compressed
        writer.write("<device name=\"speaker\">\n");
        writePathEntry(writer, "pcm_on", "SwitchA", 1);
        writePathEntry(writer, "pcm_off", "SwitchA", 0);
        writePathEntry(writer, "comp_on", "SwitchB", 1);
        writePathEntry(writer, "comp_off", "SwitchB", 0);
        writer.write("</device>\n");

        // Earpiece device that has paths for both PCM and compressed
        writer.write("<device name=\"earpiece\">\n");
        writePathEntry(writer, "pcm_on", "SwitchC", 1);
        writePathEntry(writer, "pcm_off", "SwitchC", 0);
        writePathEntry(writer, "comp_on", "SwitchD", 1);
        writePathEntry(writer, "comp_off", "SwitchD", 0);
        writer.write("</device>\n");

        // Headset device that only has path for PCM stream
        writer.write("<device name=\"headset\">\n");
        writePathEntry(writer, "pcm_on", "SwitchE", 1);
        writePathEntry(writer, "pcm_off", "SwitchE", 0);
        writer.write("</device>\n");

        // Headphone device that only has enable path for PCM stream
        writer.write("<device name=\"headphone\">\n");
        writePathEntry(writer, "pcm_on", "SwitchF", 1);
        writer.write("</device>\n");

        // SCO device that only has disable path for PCM stream
        // The control is written as 1 so we can test that the value changed
        writer.write("<device name=\"sco\">\n");
        writePathEntry(writer, "pcm_off", "SwitchG", 1);
        writer.write("</device>\n");

        // Mic device that has paths for both PCM and compressed
        writer.write("<device name=\"mic\">\n");
        writePathEntry(writer, "pcm_on", "SwitchH", 1);
        writePathEntry(writer, "pcm_off", "SwitchH", 0);
        writePathEntry(writer, "comp_on", "SwitchI", 1);
        writePathEntry(writer, "comp_off", "SwitchI", 0);
        writer.write("</device>\n");

        // Back Mic device that has paths for both PCM and compressed
        writer.write("<device name=\"back mic\">\n");
        writePathEntry(writer, "pcm_on", "SwitchJ", 1);
        writePathEntry(writer, "pcm_off", "SwitchJ", 0);
        writePathEntry(writer, "comp_on", "SwitchK", 1);
        writePathEntry(writer, "comp_off", "SwitchK", 0);
        writer.write("</device>\n");

        // Test streams
        writeStreamEntry(writer, "pcm", "out", "pcm_on", "pcm_off");
        writeStreamEntry(writer, "compress", "out", "comp_on", "comp_off");
        writeStreamEntry(writer, "pcm", "in", "pcm_on", "pcm_off");
        writeStreamEntry(writer, "compress", "in", "comp_on", "comp_off");

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

    private static void writePathEntry(FileWriter writer,
                                         String name,
                                         String control,
                                         int value) throws IOException
    {
        writer.write("<path name=\"" + name + "\">\n");
        writeCtl(writer, control, Integer.toString(value));
        writer.write("</path>\n");
    }

    private static void writeStreamEntry(FileWriter writer,
                                         String type,
                                         String dir,
                                         String enable,
                                         String disable) throws IOException
    {
            writer.write("<stream type=\"" + type + "\" dir=\"" + dir + "\" >\n");
            writer.write("<enable path=\"" + enable + "\" />\n");
            writer.write("<disable path=\"" + disable + "\" />\n");
            writer.write("</stream>\n");
    }

    private void checkExpectedChangeSet(List<String> expectedChanged) {
        for (String name : TEST_CONTROLS) {
            if (expectedChanged.contains(name)) {
                assertTrue(name + " was not changed", mAlsaMock.isChanged(name));
            } else {
                assertFalse(name + " should not have changed",
                            mAlsaMock.isChanged(name));
            }
        }
    }

    private void checkNoControlsChanged() {
        for (String name : TEST_CONTROLS) {
            assertFalse(name + " should not have changed",
                        mAlsaMock.isChanged(name));
        }
    }

private void clearChangedFlags(List<String> controls)
    {
        for (String name : controls) {
            mAlsaMock.clearChangedFlag(name);
        }
    }

private long openPcmStream(long device)
    {
        CConfigMgr.AudioConfig config = new CConfigMgr.AudioConfig();
        config.sample_rate = 48000;
        config.channel_mask = CConfigMgr.AUDIO_CHANNEL_OUT_FRONT_LEFT;
        config.format = CConfigMgr.AUDIO_FORMAT_PCM;

        return mConfigMgr.get_stream(device, 0, config);
    }

    private long openCompressedStream(long device)
    {
        CConfigMgr.AudioConfig config = new CConfigMgr.AudioConfig();
        config.sample_rate = 48000;
        config.channel_mask = CConfigMgr.AUDIO_CHANNEL_OUT_FRONT_LEFT;
        config.format = CConfigMgr.AUDIO_FORMAT_MP3;

        return mConfigMgr.get_stream(device, 0, config);
    }

    private void closeStream(long stream)
    {
        if (stream >= 0) {
            mConfigMgr.release_stream(stream);
        }
    }

    /**
     * Open an output stream connected to a single device and then close it.
     */
    @Test
    public void testOutOneDevice()
    {
        final List<String> changedControls = Arrays.asList("SwitchA");

        long stream = openPcmStream(CConfigMgr.AUDIO_DEVICE_OUT_SPEAKER);
        assertTrue("Failed to get PCM stream", stream >= 0);
        checkExpectedChangeSet(changedControls);
        assertEquals("SwitchA not written correctly", 1, mAlsaMock.getBool("SwitchA", 0));
        clearChangedFlags(changedControls);

        closeStream(stream);
        checkExpectedChangeSet(changedControls);
        assertEquals("SwitchA not written correctly", 0, mAlsaMock.getBool("SwitchA", 0));
    }

    /**
     * Open an output stream connected to a single device that only has an
     * enable path, and then close it.
     */
    @Test
    public void testOutOneDeviceEnableOnly()
    {
        final List<String> changedControls = Arrays.asList("SwitchF");

        long stream = openPcmStream(CConfigMgr.AUDIO_DEVICE_OUT_WIRED_HEADPHONE);
        assertTrue("Failed to get PCM stream", stream >= 0);
        checkExpectedChangeSet(changedControls);
        assertEquals("SwitchF not written correctly", 1, mAlsaMock.getBool("SwitchF", 0));
        clearChangedFlags(changedControls);

        closeStream(stream);
        checkNoControlsChanged();
    }

    /**
     * Open an output stream connected to a single device that only has a
     * disable path, and then close it.
     */
    @Test
    public void testOutOneDeviceDisableOnly()
    {
        final List<String> changedControls = Arrays.asList("SwitchG");

        long stream = openPcmStream(CConfigMgr.AUDIO_DEVICE_OUT_ALL_SCO);
        assertTrue("Failed to get PCM stream", stream >= 0);
        checkNoControlsChanged();

        closeStream(stream);
        checkExpectedChangeSet(changedControls);
        // The control is written as 1 so we can test that the value changed
        assertEquals("SwitchG not written correctly", 1, mAlsaMock.getBool("SwitchG", 0));
    }

    /**
     * Open an output stream connected to a two devices and then close it.
     */
    @Test
    public void testOutTwoDevices()
    {
        final List<String> changedControls = Arrays.asList("SwitchA", "SwitchC");

        long stream = openPcmStream(CConfigMgr.AUDIO_DEVICE_OUT_SPEAKER |
                                    CConfigMgr.AUDIO_DEVICE_OUT_EARPIECE);
        assertTrue("Failed to get PCM stream", stream >= 0);
        checkExpectedChangeSet(changedControls);
        assertEquals("SwitchA not written correctly", 1, mAlsaMock.getBool("SwitchA", 0));
        assertEquals("SwitchC not written correctly", 1, mAlsaMock.getBool("SwitchC", 0));
        clearChangedFlags(changedControls);

        closeStream(stream);
        checkExpectedChangeSet(changedControls);
        assertEquals("SwitchA not written correctly", 0, mAlsaMock.getBool("SwitchA", 0));
        assertEquals("SwitchC not written correctly", 0, mAlsaMock.getBool("SwitchC", 0));
    }

    /**
     * Open an output stream connected to a single device without a
     * path and then close it.
     */
    @Test
    public void testOutOneDeviceNoPath()
    {
        long stream = openCompressedStream(CConfigMgr.AUDIO_DEVICE_OUT_WIRED_HEADSET);
        assertTrue("Failed to get compressed stream", stream >= 0);
        checkNoControlsChanged();

        closeStream(stream);
        checkNoControlsChanged();
    }

    /**
     * Open an output stream connected to a two devices, where only
     * one device has a path, and then close it.
     */
    @Test
    public void testOutTwoDevicesOnePath()
    {
        final List<String> changedControls = Arrays.asList("SwitchB");

        long stream = openCompressedStream(CConfigMgr.AUDIO_DEVICE_OUT_SPEAKER |
                                           CConfigMgr.AUDIO_DEVICE_OUT_WIRED_HEADSET);
        assertTrue("Failed to get compressed stream", stream >= 0);
        checkExpectedChangeSet(changedControls);
        assertEquals("SwitchB not written correctly", 1, mAlsaMock.getBool("SwitchB", 0));
        clearChangedFlags(changedControls);

        closeStream(stream);
        checkExpectedChangeSet(changedControls);
        assertEquals("SwitchB not written correctly", 0, mAlsaMock.getBool("SwitchB", 0));
    }

    /**
     * Open two output streams connected to the same single device and then
     * close them.
     */
    @Test
    public void testMultipleOutOneDevice()
    {
        final List<String> pcmControls = Arrays.asList("SwitchA");
        final List<String> comprControls = Arrays.asList("SwitchB");

        long pcm = openPcmStream(CConfigMgr.AUDIO_DEVICE_OUT_SPEAKER);
        assertTrue("Failed to get PCM stream", pcm >= 0);
        checkExpectedChangeSet(pcmControls);
        assertEquals("SwitchA not written correctly", 1, mAlsaMock.getBool("SwitchA", 0));
        clearChangedFlags(pcmControls);

        long compr = openCompressedStream(CConfigMgr.AUDIO_DEVICE_OUT_SPEAKER);
        assertTrue("Failed to get compressed stream", compr >= 0);
        checkExpectedChangeSet(comprControls);
        assertEquals("SwitchB not written correctly", 1, mAlsaMock.getBool("SwitchB", 0));
        clearChangedFlags(comprControls);

        closeStream(pcm);
        checkExpectedChangeSet(pcmControls);
        assertEquals("SwitchA not written correctly", 0, mAlsaMock.getBool("SwitchA", 0));
        clearChangedFlags(pcmControls);

        closeStream(compr);
        checkExpectedChangeSet(comprControls);
        assertEquals("SwitchB not written correctly", 0, mAlsaMock.getBool("SwitchB", 0));
        clearChangedFlags(comprControls);

        pcm = openPcmStream(CConfigMgr.AUDIO_DEVICE_OUT_SPEAKER);
        assertTrue("Failed to get PCM stream", pcm >= 0);
        checkExpectedChangeSet(pcmControls);
        assertEquals("SwitchA not written correctly", 1, mAlsaMock.getBool("SwitchA", 0));
        clearChangedFlags(pcmControls);

        compr = openCompressedStream(CConfigMgr.AUDIO_DEVICE_OUT_SPEAKER);
        assertTrue("Failed to get compressed stream", compr >= 0);
        checkExpectedChangeSet(comprControls);
        assertEquals("SwitchB not written correctly", 1, mAlsaMock.getBool("SwitchB", 0));
        clearChangedFlags(comprControls);

        closeStream(compr);
        checkExpectedChangeSet(comprControls);
        assertEquals("SwitchB not written correctly", 0, mAlsaMock.getBool("SwitchB", 0));
        clearChangedFlags(comprControls);

        closeStream(pcm);
        checkExpectedChangeSet(pcmControls);
        assertEquals("SwitchA not written correctly", 0, mAlsaMock.getBool("SwitchA", 0));
    }

    /**
     * Open two output streams both connected to the same two devices and then
     * close them.
     */
    @Test
    public void testMultipleOutTwoDevices()
    {
        final List<String> pcmControls = Arrays.asList("SwitchA", "SwitchC");
        final List<String> comprControls = Arrays.asList("SwitchB", "SwitchD");

        long pcm = openPcmStream(CConfigMgr.AUDIO_DEVICE_OUT_SPEAKER |
                                 CConfigMgr.AUDIO_DEVICE_OUT_EARPIECE);
        checkExpectedChangeSet(pcmControls);
        assertEquals("SwitchA not written correctly", 1, mAlsaMock.getBool("SwitchA", 0));
        assertEquals("SwitchC not written correctly", 1, mAlsaMock.getBool("SwitchC", 0));
        clearChangedFlags(pcmControls);

        long compr = openCompressedStream(CConfigMgr.AUDIO_DEVICE_OUT_SPEAKER |
                                          CConfigMgr.AUDIO_DEVICE_OUT_EARPIECE);
        checkExpectedChangeSet(comprControls);
        assertEquals("SwitchB not written correctly", 1, mAlsaMock.getBool("SwitchB", 0));
        assertEquals("SwitchD not written correctly", 1, mAlsaMock.getBool("SwitchD", 0));
        clearChangedFlags(comprControls);

        closeStream(pcm);
        checkExpectedChangeSet(pcmControls);
        assertEquals("SwitchA not written correctly", 0, mAlsaMock.getBool("SwitchA", 0));
        assertEquals("SwitchC not written correctly", 0, mAlsaMock.getBool("SwitchC", 0));
        clearChangedFlags(pcmControls);

        closeStream(compr);
        checkExpectedChangeSet(comprControls);
        assertEquals("SwitchB not written correctly", 0, mAlsaMock.getBool("SwitchB", 0));
        assertEquals("SwitchD not written correctly", 0, mAlsaMock.getBool("SwitchD", 0));
        clearChangedFlags(comprControls);

        pcm = openPcmStream(CConfigMgr.AUDIO_DEVICE_OUT_SPEAKER |
                            CConfigMgr.AUDIO_DEVICE_OUT_EARPIECE);
        assertTrue("Failed to get PCM stream", pcm >= 0);
        checkExpectedChangeSet(pcmControls);
        assertEquals("SwitchA not written correctly", 1, mAlsaMock.getBool("SwitchA", 0));
        assertEquals("SwitchC not written correctly", 1, mAlsaMock.getBool("SwitchC", 0));
        clearChangedFlags(pcmControls);

        compr = openCompressedStream(CConfigMgr.AUDIO_DEVICE_OUT_SPEAKER |
                                     CConfigMgr.AUDIO_DEVICE_OUT_EARPIECE);
        assertTrue("Failed to get compressed stream", compr >= 0);
        checkExpectedChangeSet(comprControls);
        assertEquals("SwitchB not written correctly", 1, mAlsaMock.getBool("SwitchB", 0));
        assertEquals("SwitchD not written correctly", 1, mAlsaMock.getBool("SwitchD", 0));
        clearChangedFlags(comprControls);

        closeStream(compr);
        checkExpectedChangeSet(comprControls);
        assertEquals("SwitchB not written correctly", 0, mAlsaMock.getBool("SwitchB", 0));
        assertEquals("SwitchD not written correctly", 0, mAlsaMock.getBool("SwitchD", 0));
        clearChangedFlags(comprControls);

        closeStream(pcm);
        checkExpectedChangeSet(pcmControls);
        assertEquals("SwitchA not written correctly", 0, mAlsaMock.getBool("SwitchA", 0));
        assertEquals("SwitchC not written correctly", 0, mAlsaMock.getBool("SwitchC", 0));
    }

    /**
     * Open two output streams each connected to a different device and then
     * close them.
     */
    @Test
    public void testMultipleOutDifferentDevices()
    {
        final List<String> pcmSpeakerControls = Arrays.asList("SwitchA");
        final List<String> pcmEarpieceControls = Arrays.asList("SwitchC");
        final List<String> comprSpeakerControls = Arrays.asList("SwitchB");
        final List<String> comprEarpieceControls = Arrays.asList("SwitchD");

        long pcm = openPcmStream(CConfigMgr.AUDIO_DEVICE_OUT_SPEAKER);
        checkExpectedChangeSet(pcmSpeakerControls);
        assertEquals("SwitchA not written correctly", 1, mAlsaMock.getBool("SwitchA", 0));
        clearChangedFlags(pcmSpeakerControls);

        long compr = openCompressedStream(CConfigMgr.AUDIO_DEVICE_OUT_EARPIECE);
        checkExpectedChangeSet(comprEarpieceControls);
        assertEquals("SwitchD not written correctly", 1, mAlsaMock.getBool("SwitchD", 0));
        clearChangedFlags(comprEarpieceControls);

        closeStream(pcm);
        checkExpectedChangeSet(pcmSpeakerControls);
        assertEquals("SwitchA not written correctly", 0, mAlsaMock.getBool("SwitchA", 0));
        clearChangedFlags(pcmSpeakerControls);

        closeStream(compr);
        checkExpectedChangeSet(comprEarpieceControls);
        assertEquals("SwitchD not written correctly", 0, mAlsaMock.getBool("SwitchD", 0));
        clearChangedFlags(comprEarpieceControls);

        pcm = openPcmStream(CConfigMgr.AUDIO_DEVICE_OUT_EARPIECE);
        assertTrue("Failed to get PCM stream", pcm >= 0);
        checkExpectedChangeSet(pcmEarpieceControls);
        assertEquals("SwitchC not written correctly", 1, mAlsaMock.getBool("SwitchC", 0));
        clearChangedFlags(pcmEarpieceControls);

        compr = openCompressedStream(CConfigMgr.AUDIO_DEVICE_OUT_SPEAKER);
        assertTrue("Failed to get compressed stream", compr >= 0);
        checkExpectedChangeSet(comprSpeakerControls);
        assertEquals("SwitchB not written correctly", 1, mAlsaMock.getBool("SwitchB", 0));
        clearChangedFlags(comprSpeakerControls);

        closeStream(compr);
        checkExpectedChangeSet(comprSpeakerControls);
        assertEquals("SwitchB not written correctly", 0, mAlsaMock.getBool("SwitchB", 0));
        clearChangedFlags(comprSpeakerControls);

        closeStream(pcm);
        checkExpectedChangeSet(pcmEarpieceControls);
        assertEquals("SwitchC not written correctly", 0, mAlsaMock.getBool("SwitchC", 0));
    }

    /**
     * Open an input stream connected to a single device and then close it.
     */
    @Test
    public void testInOneDevice()
    {
        final List<String> changedControls = Arrays.asList("SwitchH");

        long stream = openPcmStream(CConfigMgr.AUDIO_DEVICE_IN_BUILTIN_MIC);
        assertTrue("Failed to get PCM stream", stream >= 0);
        checkExpectedChangeSet(changedControls);
        assertEquals("SwitchH not written correctly", 1, mAlsaMock.getBool("SwitchH", 0));
        clearChangedFlags(changedControls);

        closeStream(stream);
        checkExpectedChangeSet(changedControls);
        assertEquals("SwitchH not written correctly", 0, mAlsaMock.getBool("SwitchH", 0));
    }

    /**
     * Open two input streams connected to the same single device and then
     * close them.
     */
    @Test
    public void testMultipleInOneDevice()
    {
        final List<String> pcmControls = Arrays.asList("SwitchH");
        final List<String> comprControls = Arrays.asList("SwitchI");

        long pcm = openPcmStream(CConfigMgr.AUDIO_DEVICE_IN_BUILTIN_MIC);
        assertTrue("Failed to get PCM stream", pcm >= 0);
        checkExpectedChangeSet(pcmControls);
        assertEquals("SwitchH not written correctly", 1, mAlsaMock.getBool("SwitchH", 0));
        clearChangedFlags(pcmControls);

        long compr = openCompressedStream(CConfigMgr.AUDIO_DEVICE_IN_BUILTIN_MIC);
        assertTrue("Failed to get compressed stream", compr >= 0);
        checkExpectedChangeSet(comprControls);
        assertEquals("SwitchI not written correctly", 1, mAlsaMock.getBool("SwitchI", 0));
        clearChangedFlags(comprControls);

        closeStream(pcm);
        checkExpectedChangeSet(pcmControls);
        assertEquals("SwitchH not written correctly", 0, mAlsaMock.getBool("SwitchH", 0));
        clearChangedFlags(pcmControls);

        closeStream(compr);
        checkExpectedChangeSet(comprControls);
        assertEquals("SwitchI not written correctly", 0, mAlsaMock.getBool("SwitchI", 0));
        clearChangedFlags(comprControls);

        pcm = openPcmStream(CConfigMgr.AUDIO_DEVICE_IN_BUILTIN_MIC);
        assertTrue("Failed to get PCM stream", pcm >= 0);
        checkExpectedChangeSet(pcmControls);
        assertEquals("SwitchH not written correctly", 1, mAlsaMock.getBool("SwitchH", 0));
        clearChangedFlags(pcmControls);

        compr = openCompressedStream(CConfigMgr.AUDIO_DEVICE_IN_BUILTIN_MIC);
        assertTrue("Failed to get compressed stream", compr >= 0);
        checkExpectedChangeSet(comprControls);
        assertEquals("SwitchI not written correctly", 1, mAlsaMock.getBool("SwitchI", 0));
        clearChangedFlags(comprControls);

        closeStream(compr);
        checkExpectedChangeSet(comprControls);
        assertEquals("SwitchI not written correctly", 0, mAlsaMock.getBool("SwitchI", 0));
        clearChangedFlags(comprControls);

        closeStream(pcm);
        checkExpectedChangeSet(pcmControls);
        assertEquals("SwitchH not written correctly", 0, mAlsaMock.getBool("SwitchH", 0));
    }

    /**
     * Open two input streams both connected to the same two devices and then
     * close them.
     */
    @Test
    public void testMultipleInTwoDevices()
    {
        final List<String> pcmControls = Arrays.asList("SwitchH", "SwitchJ");
        final List<String> comprControls = Arrays.asList("SwitchI", "SwitchK");

        long pcm = openPcmStream(CConfigMgr.AUDIO_DEVICE_IN_BUILTIN_MIC |
                                 CConfigMgr.AUDIO_DEVICE_IN_BACK_MIC);
        checkExpectedChangeSet(pcmControls);
        assertEquals("SwitchH not written correctly", 1, mAlsaMock.getBool("SwitchH", 0));
        assertEquals("SwitchJ not written correctly", 1, mAlsaMock.getBool("SwitchJ", 0));
        clearChangedFlags(pcmControls);

        long compr = openCompressedStream(CConfigMgr.AUDIO_DEVICE_IN_BUILTIN_MIC |
                                          CConfigMgr.AUDIO_DEVICE_IN_BACK_MIC);
        checkExpectedChangeSet(comprControls);
        assertEquals("SwitchI not written correctly", 1, mAlsaMock.getBool("SwitchI", 0));
        assertEquals("SwitchK not written correctly", 1, mAlsaMock.getBool("SwitchK", 0));
        clearChangedFlags(comprControls);

        closeStream(pcm);
        checkExpectedChangeSet(pcmControls);
        assertEquals("SwitchH not written correctly", 0, mAlsaMock.getBool("SwitchH", 0));
        assertEquals("SwitchJ not written correctly", 0, mAlsaMock.getBool("SwitchJ", 0));
        clearChangedFlags(pcmControls);

        closeStream(compr);
        checkExpectedChangeSet(comprControls);
        assertEquals("SwitchI not written correctly", 0, mAlsaMock.getBool("SwitchI", 0));
        assertEquals("SwitchK not written correctly", 0, mAlsaMock.getBool("SwitchK", 0));
        clearChangedFlags(comprControls);

        pcm = openPcmStream(CConfigMgr.AUDIO_DEVICE_IN_BUILTIN_MIC |
                            CConfigMgr.AUDIO_DEVICE_IN_BACK_MIC);
        assertTrue("Failed to get PCM stream", pcm >= 0);
        checkExpectedChangeSet(pcmControls);
        assertEquals("SwitchH not written correctly", 1, mAlsaMock.getBool("SwitchH", 0));
        assertEquals("SwitchJ not written correctly", 1, mAlsaMock.getBool("SwitchJ", 0));
        clearChangedFlags(pcmControls);

        compr = openCompressedStream(CConfigMgr.AUDIO_DEVICE_IN_BUILTIN_MIC |
                                     CConfigMgr.AUDIO_DEVICE_IN_BACK_MIC);
        assertTrue("Failed to get compressed stream", compr >= 0);
        checkExpectedChangeSet(comprControls);
        assertEquals("SwitchI not written correctly", 1, mAlsaMock.getBool("SwitchI", 0));
        assertEquals("SwitchK not written correctly", 1, mAlsaMock.getBool("SwitchK", 0));
        clearChangedFlags(comprControls);

        closeStream(compr);
        checkExpectedChangeSet(comprControls);
        assertEquals("SwitchI not written correctly", 0, mAlsaMock.getBool("SwitchI", 0));
        assertEquals("SwitchK not written correctly", 0, mAlsaMock.getBool("SwitchK", 0));
        clearChangedFlags(comprControls);

        closeStream(pcm);
        checkExpectedChangeSet(pcmControls);
        assertEquals("SwitchH not written correctly", 0, mAlsaMock.getBool("SwitchH", 0));
        assertEquals("SwitchJ not written correctly", 0, mAlsaMock.getBool("SwitchJ", 0));
    }

    /**
     * Open one input stream and one output stream connected to different
     * devices and then close them.
     */
    @Test
    public void testOutInDifferentDevices()
    {
        final List<String> pcmControls = Arrays.asList("SwitchA");
        final List<String> comprControls = Arrays.asList("SwitchI");

        long pcm = openPcmStream(CConfigMgr.AUDIO_DEVICE_OUT_SPEAKER);
        assertTrue("Failed to get PCM stream", pcm >= 0);
        checkExpectedChangeSet(pcmControls);
        assertEquals("SwitchH not written correctly", 1, mAlsaMock.getBool("SwitchA", 0));
        clearChangedFlags(pcmControls);

        long compr = openCompressedStream(CConfigMgr.AUDIO_DEVICE_IN_BUILTIN_MIC);
        assertTrue("Failed to get compressed stream", compr >= 0);
        checkExpectedChangeSet(comprControls);
        assertEquals("SwitchI not written correctly", 1, mAlsaMock.getBool("SwitchI", 0));
        clearChangedFlags(comprControls);

        closeStream(pcm);
        checkExpectedChangeSet(pcmControls);
        assertEquals("SwitchH not written correctly", 0, mAlsaMock.getBool("SwitchA", 0));
        clearChangedFlags(pcmControls);

        closeStream(compr);
        checkExpectedChangeSet(comprControls);
        assertEquals("SwitchI not written correctly", 0, mAlsaMock.getBool("SwitchI", 0));
        clearChangedFlags(comprControls);

        pcm = openPcmStream(CConfigMgr.AUDIO_DEVICE_OUT_SPEAKER);
        assertTrue("Failed to get PCM stream", pcm >= 0);
        checkExpectedChangeSet(pcmControls);
        assertEquals("SwitchH not written correctly", 1, mAlsaMock.getBool("SwitchA", 0));
        clearChangedFlags(pcmControls);

        compr = openCompressedStream(CConfigMgr.AUDIO_DEVICE_IN_BUILTIN_MIC);
        assertTrue("Failed to get compressed stream", compr >= 0);
        checkExpectedChangeSet(comprControls);
        assertEquals("SwitchI not written correctly", 1, mAlsaMock.getBool("SwitchI", 0));
        clearChangedFlags(comprControls);

        closeStream(compr);
        checkExpectedChangeSet(comprControls);
        assertEquals("SwitchI not written correctly", 0, mAlsaMock.getBool("SwitchI", 0));
        clearChangedFlags(comprControls);

        closeStream(pcm);
        checkExpectedChangeSet(pcmControls);
        assertEquals("SwitchH not written correctly", 0, mAlsaMock.getBool("SwitchA", 0));
    }

    /**
     * Open a stream not connected to any device and then close it.
     */
    @Test
    public void testNoDevice()
    {
        long stream = openPcmStream(0);
        assertTrue("Failed to get PCM stream", stream >= 0);
        checkNoControlsChanged();

        closeStream(stream);
        checkNoControlsChanged();
    }

    /**
     * Open a stream connected to AUDIO_DEVICE_OUT_DEFAULT and then close it.
     */
    @Test
    public void testOutDefaultDevice()
    {
        long stream = openPcmStream(CConfigMgr.AUDIO_DEVICE_OUT_DEFAULT);
        assertTrue("Failed to get PCM stream", stream >= 0);
        checkNoControlsChanged();

        closeStream(stream);
        checkNoControlsChanged();
    }

    /**
     * Open a stream connected to AUDIO_DEVICE_IN_DEFAULT and then close it.
     */
    @Test
    public void testInDefaultDevice()
    {
        long stream = openPcmStream(CConfigMgr.AUDIO_DEVICE_IN_DEFAULT);
        assertTrue("Failed to get PCM stream", stream >= 0);
        checkNoControlsChanged();

        closeStream(stream);
        checkNoControlsChanged();
    }
};
