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
import java.util.Arrays;
import java.util.Collection;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.junit.runner.RunWith;

import com.cirrus.tinyhal.test.thcm.CAlsaMock;
import com.cirrus.tinyhal.test.thcm.CConfigMgr;
import com.cirrus.tinyhal.test.thcm.ThcmPlatform;

/**
 * Tests that volume control &lt;ctl&gt; elements in a &lt;stream&gt; can be
 * invoked and the volume percentages are scaled correctly.
 */
@RunWith(Parameterized.class)
public class ThcmVolumeControlsTest
{
    // A selection of control ranges to test with
    private static final Integer[][] TEST_MINMAX = {
        { 0, 32 },
        { -32, 32 },
        { -32, 0 },
        { -64, -32 },
        { -64, 50 },
        { -50, 64 },
        { 0, 0x7fffffff },
        { -0x7fffffff, 0x7fffffff },
        { -0x7fffffff, 0 },
        { 0, 65535 },
        { -65536, 65535 },
        { -65535, 65536 },
    };

    // Subset of test percentages sufficient to prove conversion is correct
    private static final int[] TEST_POINTS = {
        0, 1, 5, 45, 50, 51, 70, 90, 99, 100
    };

    private static final int EINVAL = 22;

    private static final File sWorkFilesPath = ThcmPlatform.workFilesPath();
    private static final File sControlsFile = new File(sWorkFilesPath, "thcm_vol_controls.csv");
    private static final File sXmlFile = new File(sWorkFilesPath, "thcm_vol_controls.xml");

    private CAlsaMock mAlsaMock = new CAlsaMock();
    private CConfigMgr mConfigMgr = new CConfigMgr();

    private int mMin = 0;
    private int mMax = 32;

    private long mStream;

    @Parameterized.Parameters
    public static Collection parameters() {
        return Arrays.asList(TEST_MINMAX);
    }

    public ThcmVolumeControlsTest(Integer min, Integer max)
    {
        mMin = min;
        mMax = max;
    }

    @Before
    public void setUp() throws IOException
    {
        createAlsaControlsFile();

        assertEquals("Failed to create CAlsaMock",
                     0,
                     mAlsaMock.createMixer(sControlsFile.toPath().toString()));

        mStream = -1;
    }

    @After
    public void tearDown()
    {
        if (mStream >= 0) {
            closeStream();
        }

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

        if (sControlsFile.exists()) {
            sControlsFile.delete();
        }
    }

    private void createAlsaControlsFile() throws IOException
    {
        FileWriter writer = new FileWriter(sControlsFile);

        writer.write("VolA,int,1,0," + mMin + ":" + mMax + "\n");
        writer.write("VolB,int,1,0," + mMin + ":" + mMax + "\n");
        writer.write("VolC,int,2,0," + mMin + ":" + mMax + "\n");

        // Full range for testing overridden min/max
        int min = 1 - 0x7fffffff;
        int max = 0x7fffffff;
        writer.write("VolD,int,1,0," + min + ":" + max + "\n");
        writer.write("VolE,int,1,0," + min + ":" + max + "\n");
        writer.write("VolF,int,2,0," + min + ":" + max + "\n");

        writer.close();
    }

    private void writeXmlHeader(FileWriter writer) throws IOException
    {
        // Header elements
        writer.write("<audiohal><mixer card=\"0\" />\n");

        // A simple stream element
        writer.write("<stream type=\"pcm\" dir=\"out\" card=\"0\" device=\"0\" rate=\"48000\">");
    }

    private void writeXmlFooter(FileWriter writer) throws IOException
    {
        // Footer elements
        writer.write("\n</stream></audiohal>\n");
    }

    private void writeCtl(FileWriter writer,
                          String function,
                          String controlName,
                          String index,
                          boolean limitRange) throws IOException
    {
        String attribs = "function = \"" + function + "\" name=\"" +
                         controlName + "\"";

        if (index != null) {
            attribs = attribs + " index=\"" + index + "\"";
        }

        if (limitRange) {
            attribs = attribs + " min=\"" + mMin + "\" max=\"" + mMax + "\"";
        }

        writer.write("<ctl " + attribs + " />\n");
    }

    private void writeXml(String[][] controls, boolean limitRange)
    {
        try {
            FileWriter writer = new FileWriter(sXmlFile);
            writeXmlHeader(writer);
            for (String[] ctl : controls) {
                writeCtl(writer, ctl[0], ctl[1], ctl[2], limitRange);
            }
            writeXmlFooter(writer);
            writer.close();
        } catch (IOException e) {
            fail("Failed to write xml file: " + e);
        }
    }

    private int calcExpectedValue(int percent)
    {
        long lmin = mMin;
        long lmax = mMax;
        long result =  lmin + (((lmax - lmin) * percent) / 100);
        return (int)result;
    }

    private boolean openStream()
    {
        CConfigMgr.AudioConfig config = new CConfigMgr.AudioConfig();
        config.sample_rate = 48000;
        config.channel_mask = CConfigMgr.AUDIO_CHANNEL_OUT_FRONT_LEFT;
        config.format = CConfigMgr.AUDIO_FORMAT_PCM;

        mStream = mConfigMgr.get_stream(CConfigMgr.AUDIO_DEVICE_BIT_DEFAULT,
                                        0,
                                        config);
        if (mStream < 0) {
            return false;
        }

        return true;
    }

    private void closeStream()
    {
        mConfigMgr.release_stream(mStream);
        mStream = -1;
    }

    /**
     * Write a volume to a single non-indexed left-channel control.
     * Requested left and right percentages are the same
     */
    @Test
    public void testSingleLeft()
    {
        String[][] controls = {
            { "leftvol", "VolA", null },
        };
        writeXml(controls, false);

        assertEquals("VolA not initially 0", 0, mAlsaMock.getInt("VolA", 0));

        assertEquals("Failed to open CConfigMgr",
                     0,
                     mConfigMgr.init_audio_config(sXmlFile.toPath().toString()));

        assertTrue("Failed to open stream", openStream());

        for (int percent : TEST_POINTS) {
            mConfigMgr.set_hw_volume(mStream, percent, percent);
            assertTrue("VolA was not changed", mAlsaMock.isChanged("VolA"));
            assertEquals("VolA not written correctly for " + percent + "%",
                         calcExpectedValue(percent),
                         mAlsaMock.getInt("VolA", 0));
        }
    }

    /**
     * Write a volume to a single indexed left-channel control.
     * Requested left and right percentages are the same
     */
    @Test
    public void testSingleIndexedLeft()
    {
        String[][] controls = {
            { "leftvol", "VolC", "1" },
        };
        writeXml(controls, false);

        assertEquals("VolC[0] not initially 0", 0, mAlsaMock.getInt("VolC", 0));
        assertEquals("VolC[1] not initially 0", 0, mAlsaMock.getInt("VolC", 1));

        assertEquals("Failed to open CConfigMgr",
                     0,
                     mConfigMgr.init_audio_config(sXmlFile.toPath().toString()));

        assertTrue("Failed to open stream", openStream());

        for (int percent : TEST_POINTS) {
            mConfigMgr.set_hw_volume(mStream, percent, percent);
            assertTrue("VolC was not changed", mAlsaMock.isChanged("VolC"));
            assertEquals("VolC[1] not written correctly for " + percent + "%",
                         calcExpectedValue(percent),
                         mAlsaMock.getInt("VolC", 1));
            assertEquals("VolC[0] should not have changed",
                         0,
                         mAlsaMock.getInt("VolC", 0));
        }
    }

    /**
     * Write a volume to a single non-indexed right-channel control.
     * Requested left and right percentages are the same
     */
    @Test
    public void testSingleRight()
    {
        String[][] controls = {
            { "rightvol", "VolA", null },
        };
        writeXml(controls, false);

        assertEquals("VolA not initially 0", 0, mAlsaMock.getInt("VolA", 0));

        assertEquals("Failed to open CConfigMgr",
                     0,
                     mConfigMgr.init_audio_config(sXmlFile.toPath().toString()));

        assertTrue("Failed to open stream", openStream());

        for (int percent : TEST_POINTS) {
            mConfigMgr.set_hw_volume(mStream, percent, percent);
            assertTrue("VolA was not changed", mAlsaMock.isChanged("VolA"));
            assertEquals("VolA not written correctly for " + percent + "%",
                         calcExpectedValue(percent),
                         mAlsaMock.getInt("VolA", 0));
        }
    }

    /**
     * Write a volume to a single indexed right-channel control.
     * Requested left and right percentages are the same
     */
    @Test
    public void testSingleIndexedRight()
    {
        String[][] controls = {
            { "rightvol", "VolC", "1" },
        };
        writeXml(controls, false);

        assertEquals("VolC[0] not initially 0", 0, mAlsaMock.getInt("VolC", 0));
        assertEquals("VolC[1] not initially 0", 0, mAlsaMock.getInt("VolC", 1));

        assertEquals("Failed to open CConfigMgr",
                     0,
                     mConfigMgr.init_audio_config(sXmlFile.toPath().toString()));

        assertTrue("Failed to open stream", openStream());

        for (int percent : TEST_POINTS) {
            mConfigMgr.set_hw_volume(mStream, percent, percent);
            assertTrue("VolC was not changed", mAlsaMock.isChanged("VolC"));
            assertEquals("VolC[1] not written correctly for " + percent + "%",
                         calcExpectedValue(percent),
                         mAlsaMock.getInt("VolC", 1));
            assertEquals("VolC[0] should not have changed",
                         0,
                         mAlsaMock.getInt("VolC", 0));
        }
    }

    /**
     * Write volumes to a pair of controls.
     * All possible combinations of left and right volume are written to
     * separate controls for left and right.
     */
    @Test
    public void testStereoPair()
    {
        String[][] controls = {
            { "leftvol", "VolA", null },
            { "rightvol", "VolB", null },
        };
        writeXml(controls, false);

        assertEquals("VolA not initially 0", 0, mAlsaMock.getInt("VolA", 0));
        assertEquals("VolB not initially 0", 0, mAlsaMock.getInt("VolB", 0));

        assertEquals("Failed to open CConfigMgr",
                     0,
                     mConfigMgr.init_audio_config(sXmlFile.toPath().toString()));

        assertTrue("Failed to open stream", openStream());

        for (int left_pc : TEST_POINTS) {
            for (int right_pc : TEST_POINTS) {
                mConfigMgr.set_hw_volume(mStream, left_pc, right_pc);
                assertTrue("VolA was not changed", mAlsaMock.isChanged("VolA"));
                assertEquals("VolA not written correctly for " + left_pc + "%",
                             calcExpectedValue(left_pc),
                             mAlsaMock.getInt("VolA", 0));

                assertTrue("VolB was not changed", mAlsaMock.isChanged("VolB"));
                assertEquals("VolB not written correctly for " + right_pc + "%",
                             calcExpectedValue(right_pc),
                             mAlsaMock.getInt("VolB", 0));
            }
        }
    }

    /**
     * Write volumes to a 2-element control.
     * All possible combinations of left and right volume are written to
     * different indexes in a single control.
     */
    @Test
    public void testStereoIndexed()
    {
        String[][] controls = {
            { "leftvol", "VolC", "0" },
            { "rightvol", "VolC", "1" },
        };
        writeXml(controls, false);

        assertEquals("VolC[0] not initially 0", 0, mAlsaMock.getInt("VolC", 0));
        assertEquals("VolC[1] not initially 0", 0, mAlsaMock.getInt("VolC", 1));

        assertEquals("Failed to open CConfigMgr",
                     0,
                     mConfigMgr.init_audio_config(sXmlFile.toPath().toString()));

        assertTrue("Failed to open stream", openStream());

        for (int left_pc : TEST_POINTS) {
            for (int right_pc : TEST_POINTS) {
                mConfigMgr.set_hw_volume(mStream, left_pc, right_pc);
                assertTrue("VolC was not changed", mAlsaMock.isChanged("VolC"));
                assertEquals("VolC[0] not written correctly for " + left_pc + "%",
                             calcExpectedValue(left_pc),
                             mAlsaMock.getInt("VolC", 0));

                assertEquals("VolC[1] not written correctly for " + right_pc + "%",
                             calcExpectedValue(right_pc),
                             mAlsaMock.getInt("VolC", 1));
            }
        }
    }

    /**
     * Write unbalanced stereo volume to a single non-indexed left-channel
     * control.
     * Requested left and right percentages are different and the resulting
     * mono volume should be the average of left and right.
     *
     * Note that to use averaging the single control must be "leftvol".
     */
    @Test
    public void testAveragedMonoLeft()
    {
        String[][] controls = {
            { "leftvol", "VolA", null },
        };
        writeXml(controls, false);

        assertEquals("VolA not initially 0", 0, mAlsaMock.getInt("VolA", 0));

        assertEquals("Failed to open CConfigMgr",
                     0,
                     mConfigMgr.init_audio_config(sXmlFile.toPath().toString()));

        assertTrue("Failed to open stream", openStream());

        for (int left_pc : TEST_POINTS) {
            for (int right_pc : TEST_POINTS) {
                int average_pc = (left_pc + right_pc) / 2;
                mConfigMgr.set_hw_volume(mStream, left_pc, right_pc);
                assertTrue("VolA was not changed", mAlsaMock.isChanged("VolA"));
                assertEquals("VolA not written correctly for " + average_pc + "%",
                             calcExpectedValue(average_pc),
                             mAlsaMock.getInt("VolA", 0));
            }
        }
    }

    /**
     * Write a volume to a single non-indexed left-channel control with
     * reduced min-max range.
     * The &lt;ctl&gt; entry specifies a min and max that are not the full range
     * of the control.
     * Requested left and right percentages are the same
     */
    @Test
    public void testSingleLeftRangeLimit()
    {
        String[][] controls = {
            { "leftvol", "VolD", null },
        };
        writeXml(controls, true);

        assertEquals("VolD not initially 0", 0, mAlsaMock.getInt("VolD", 0));

        assertEquals("Failed to open CConfigMgr",
                     0,
                     mConfigMgr.init_audio_config(sXmlFile.toPath().toString()));

        assertTrue("Failed to open stream", openStream());

        for (int percent : TEST_POINTS) {
            mConfigMgr.set_hw_volume(mStream, percent, percent);
            assertTrue("VolD was not changed", mAlsaMock.isChanged("VolD"));
            assertEquals("VolD not written correctly for " + percent + "%",
                         calcExpectedValue(percent),
                         mAlsaMock.getInt("VolD", 0));
        }
    }

    /**
     * Write a volume to a single non-indexed right-channel control with
     * reduced min-max range.
     * The &lt;ctl&gt; entry specifies a min and max that are not the full range
     * of the control.
     * Requested left and right percentages are the same
     */
    @Test
    public void testSingleRightRangeLimit()
    {
        String[][] controls = {
            { "rightvol", "VolD", null },
        };
        writeXml(controls, true);

        assertEquals("VolD not initially 0", 0, mAlsaMock.getInt("VolD", 0));

        assertEquals("Failed to open CConfigMgr",
                     0,
                     mConfigMgr.init_audio_config(sXmlFile.toPath().toString()));

        assertTrue("Failed to open stream", openStream());

        for (int percent : TEST_POINTS) {
            mConfigMgr.set_hw_volume(mStream, percent, percent);
            assertTrue("VolD was not changed", mAlsaMock.isChanged("VolD"));
            assertEquals("VolD not written correctly for " + percent + "%",
                         calcExpectedValue(percent),
                         mAlsaMock.getInt("VolD", 0));
        }
    }

    /**
     * Write a volume to a pair of controls with reduced min-max range.
     * The &lt;ctl&gt; entry specifies a min and max that are not the full range
     * of the control.
     * All possible combinations of left and right volume are written to
     * separate controls for left and right.
     */
    @Test
    public void testStereoPairRangeLimit()
    {
        String[][] controls = {
            { "leftvol", "VolD", null },
            { "rightvol", "VolE", null },
        };
        writeXml(controls, true);

        assertEquals("VolD not initially 0", 0, mAlsaMock.getInt("VolD", 0));
        assertEquals("VolE not initially 0", 0, mAlsaMock.getInt("VolE", 0));

        assertEquals("Failed to open CConfigMgr",
                     0,
                     mConfigMgr.init_audio_config(sXmlFile.toPath().toString()));

        assertTrue("Failed to open stream", openStream());

        for (int left_pc : TEST_POINTS) {
            for (int right_pc : TEST_POINTS) {
                mConfigMgr.set_hw_volume(mStream, left_pc, right_pc);
                assertTrue("VolD was not changed", mAlsaMock.isChanged("VolD"));
                assertEquals("VolD not written correctly for " + left_pc + "%",
                             calcExpectedValue(left_pc),
                             mAlsaMock.getInt("VolD", 0));

                assertTrue("VolE was not changed", mAlsaMock.isChanged("VolE"));
                assertEquals("VolE not written correctly for " + right_pc + "%",
                             calcExpectedValue(right_pc),
                             mAlsaMock.getInt("VolE", 0));
            }
        }
    }

    /**
     * Write a volume to a 2-element control with reduced min-max range.
     * The &lt;ctl&gt; entry specifies a min and max that are not the full range
     * of the control.
     * All possible combinations of left and right volume are written to
     * different indexes in a single control.
     */
    @Test
    public void testStereoIndexedRangeLimit()
    {
        String[][] controls = {
            { "leftvol", "VolF", "0" },
            { "rightvol", "VolF", "1" },
        };
        writeXml(controls, true);

        assertEquals("VolF[0] not initially 0", 0, mAlsaMock.getInt("VolF", 0));
        assertEquals("VolF[1] not initially 0", 0, mAlsaMock.getInt("VolF", 1));

        assertEquals("Failed to open CConfigMgr",
                     0,
                     mConfigMgr.init_audio_config(sXmlFile.toPath().toString()));

        assertTrue("Failed to open stream", openStream());

        for (int left_pc : TEST_POINTS) {
            for (int right_pc : TEST_POINTS) {
                mConfigMgr.set_hw_volume(mStream, left_pc, right_pc);
                assertTrue("VolF was not changed", mAlsaMock.isChanged("VolF"));
                assertEquals("VolF[0] not written correctly for " + left_pc + "%",
                             calcExpectedValue(left_pc),
                             mAlsaMock.getInt("VolF", 0));

                assertEquals("VolF[1] not written correctly for " + right_pc + "%",
                             calcExpectedValue(right_pc),
                             mAlsaMock.getInt("VolF", 1));
            }
        }
    }

    /**
     * Write out-of-range percentages.
     */
    @Test
    public void testIllegalPercent()
    {
        String[][] controls = {
            { "leftvol", "VolA", null },
            { "rightvol", "VolB", null },
        };
        writeXml(controls, false);

        assertEquals("VolA not initially 0", 0, mAlsaMock.getInt("VolA", 0));
        assertEquals("VolB not initially 0", 0, mAlsaMock.getInt("VolB", 0));

        assertEquals("Failed to open CConfigMgr",
                     0,
                     mConfigMgr.init_audio_config(sXmlFile.toPath().toString()));

        assertTrue("Failed to open stream", openStream());

        assertEquals("Left percent <0 not rejected",
                     -EINVAL,
                     mConfigMgr.set_hw_volume(mStream, -1, 0));

        assertEquals("Right percent <0 not rejected",
                     -EINVAL,
                     mConfigMgr.set_hw_volume(mStream, 0, -1));

        assertEquals("Both percent <0 not rejected",
                     -EINVAL,
                     mConfigMgr.set_hw_volume(mStream, -1, -1));

        assertEquals("Left percent >100 not rejected",
                     -EINVAL,
                     mConfigMgr.set_hw_volume(mStream, 101, 0));

        assertEquals("Right percent >100 not rejected",
                     -EINVAL,
                     mConfigMgr.set_hw_volume(mStream, 0, 101));

        assertEquals("Both percent >100 not rejected",
                     -EINVAL,
                     mConfigMgr.set_hw_volume(mStream, 101, 101));

        assertEquals("Left percent >100 and right percent <0 not rejected",
                     -EINVAL,
                     mConfigMgr.set_hw_volume(mStream, 101, -1));

        assertEquals("Right percent >100 and left percent <0 not rejected",
                     -EINVAL,
                     mConfigMgr.set_hw_volume(mStream, -1, 101));

        assertFalse("VolA should not have changed", mAlsaMock.isChanged("VolA"));
        assertFalse("VolB should not have changed", mAlsaMock.isChanged("VolB"));
    }
};
