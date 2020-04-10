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
import java.lang.Math;
import java.lang.String;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * Tests that &lt;ctl&gt; elements are executed correctly for byte controls.
 * This assumes that other tinyhal features required to invoke the &lt;ctl&gt;
 * element are working correctly and tested elsewhere.
 */
@RunWith(Parameterized.class)
public class ThcmByteControlTest
{
    private static final int[] SIZES = {
        1, 2, 4, 8, 10, 11, 12, 16, 511, 512, 513, 516, 2048
    };

    private static String PREFIX_DIRECT = "bytes_";
    private static String PREFIX_FILE = "filebytes_";

    private static final File sWorkFilesPath = ThcmPlatform.workFilesPath();
    private static final File sControlsFile = new File(sWorkFilesPath, "thcm_byte_controls.csv");
    private static final File sXmlFile = new File(sWorkFilesPath, "thcm_byte_controls.xml");


    private static Map<Integer, byte[]> sDirectDataMap = new HashMap<Integer, byte[]>();
    private static Map<Integer, byte[]> sFileDataMap = new HashMap<Integer, byte[]>();

    private CAlsaMock mAlsaMock = new CAlsaMock();
    private CConfigMgr mConfigMgr = new CConfigMgr();
    private int mTestSize;
    private String mTestCoeffName;

    @Parameterized.Parameters
    public static Collection parameters() {
        // parameters must be an array of arrays
        List<Integer[]> params = new ArrayList<Integer[]>();
        for (int s : SIZES) {
            params.add(new Integer[] { s });
        }

        return params;
    }

    public ThcmByteControlTest(Integer size)
    {
        mTestSize = size;
    }

    @BeforeClass
    public static void setUpClass() throws IOException
    {
        createRandomData();
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

        for (int s : SIZES) {
            File f = new File(randomFilePathForSize(s).toString());
            if (f.exists()) {
                f.delete();
            }
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

        mTestCoeffName = coeffNameForSize(mTestSize);
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

    private static Path randomFilePathForSize(int size)
    {
        File f = new File(sWorkFilesPath, "random" + size + ".bin");
        return f.toPath();
    }

    private static String coeffNameForSize(int size)
    {
        int i = 0;
        for (int s : SIZES) {
            if (s == size) {
                return "Coeff " + i;
            }
            ++i;
        }

        return null;
    }

    private static String streamNameForSize(String prefix, int controlSize, int dataSize)
    {
        return prefix + controlSize + "_" + dataSize;
    }

    private static void createRandomData() throws IOException
    {
        Random rand = new Random();

        for (int s : SIZES) {
            // Create different random data for in-xml and file writes so
            // the test case can check that the correct write was issued
            byte[] arr = new byte[s];
            rand.nextBytes(arr);
            sDirectDataMap.put(s, arr);

            rand.nextBytes(arr);
            sFileDataMap.put(s, arr);
            Files.write(randomFilePathForSize(s), arr,
                        StandardOpenOption.CREATE);
        }
    }

    private static void createAlsaControlsFile() throws IOException
    {
        FileWriter writer = new FileWriter(sControlsFile);
        int i = 0;
        for (int s : SIZES) {
            writer.write("Coeff " + i + ",byte," + s + ",0,\n");
            ++i;
        }
        writer.close();
    }

    private static void createXmlFile() throws IOException
    {
        FileWriter writer = new FileWriter(sXmlFile);

        // Header elements
        writer.write("<audiohal>\n<mixer card=\"0\" />\n<device name=\"global\">");

        int i = 0;
        for (int ctlSize : SIZES) {
            // Create paths with a <ctl> for all sizes up to control size
            for (int writeSize : SIZES) {
                // xml load will fail for in-xml data >control length
                if (writeSize <= ctlSize) {
                    writePathDataEntry(writer,
                                       i,
                                       streamNameForSize(PREFIX_DIRECT, ctlSize, writeSize),
                                       sDirectDataMap.get(writeSize));
                }

                // but files >control length are handled
                writePathFileEntry(writer,
                                   i,
                                   writeSize,
                                   streamNameForSize(PREFIX_FILE, ctlSize, writeSize));
            }

            ++i;
        }

        writer.write("</device>");

        // Create a stream for each path
        for (int ctlSize : SIZES) {
            for (int writeSize : SIZES) {
                if (writeSize <= ctlSize) {
                    writeStreamEntry(writer, streamNameForSize(PREFIX_DIRECT, ctlSize, writeSize));
                }

                writeStreamEntry(writer, streamNameForSize(PREFIX_FILE, ctlSize, writeSize));
            }

            ++i;
        }

        // Footer elements
        writer.write("\n</audiohal>\n");

        writer.close();
    }

    private static void writePathDataEntry(FileWriter writer,
                                           int controlNum,
                                           String pathName,
                                           byte[] data) throws IOException
    {
            writer.write("<path name=\"" + pathName + "\">\n");
            writer.write("<ctl name=\"Coeff " + controlNum + "\" val=\"");

            boolean comma = false;
            for (byte b : data) {
                int value = b & 0xff;
                if (comma) {
                    writer.write(",0x" + Integer.toHexString(value));
                } else {
                    writer.write("0x" + Integer.toHexString(value));
                    comma = true;
                }
            }
            writer.write("\" />\n</path>\n");
    }

    private static void writePathFileEntry(FileWriter writer,
                                           int controlNum,
                                           int size,
                                           String pathName) throws IOException
    {
            writer.write("<path name=\"" + pathName + "\">\n");
            writer.write("<ctl name=\"Coeff " + controlNum + "\" file=\"" +
                         randomFilePathForSize(size) + "\" />\n</path>\n");
    }

    private static void writeStreamEntry(FileWriter writer,
                                         String name) throws IOException
    {
            writer.write("<stream name=\"" + name + "\" type=\"hw\" dir=\"out\" >\n");
            writer.write("<enable path=\"" + name + "\" />\n");
            writer.write("</stream>\n");
    }

    private static byte[] readByteFile(String name)
    {
        try {
            File file = new File(sWorkFilesPath, name);
            return Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Write a control from data defined in the xml.
     */
    @Test
    public void testWriteDataInXml()
    {
        byte[] expected = new byte[mTestSize];

        assertArrayEquals(mTestCoeffName + " not initially zero",
                          expected,
                          mAlsaMock.getData(mTestCoeffName));

        for (int writeSize : SIZES) {
            if (writeSize <= mTestSize) {
                String streamName = streamNameForSize(PREFIX_DIRECT, mTestSize, writeSize);
                long stream = mConfigMgr.get_named_stream(streamName);
                assertFalse("Failed to get " + streamName + " stream", stream < 0);

                assertTrue(mTestCoeffName + " was not changed (n=" + writeSize + ")",
                           mAlsaMock.isChanged(mTestCoeffName));

                System.arraycopy(sDirectDataMap.get(writeSize), 0,
                                 expected, 0, writeSize);

                assertArrayEquals(mTestCoeffName + " not written correctly (n=" + writeSize + ")",
                                  expected,
                                  mAlsaMock.getData(mTestCoeffName));

                assertEquals("Failed to close " + streamName + " stream",
                             0,
                             mConfigMgr.release_stream(stream));

                mAlsaMock.clearChangedFlag(mTestCoeffName);
            }
        }
    }

    /**
     * Write a control from data in a file.
     */
    @Test
    public void testByteShortWritesFile()
    {
        byte[] expected = new byte[mTestSize];

        assertArrayEquals(mTestCoeffName + " not initially zero",
                          expected,
                          mAlsaMock.getData(mTestCoeffName));

        for (int writeSize : SIZES) {
            String streamName = streamNameForSize(PREFIX_FILE, mTestSize, writeSize);
            long stream = mConfigMgr.get_named_stream(streamName);
            assertFalse("Failed to get " + streamName + " stream", stream < 0);

            assertTrue(mTestCoeffName + " was not changed (n=" + writeSize + ")",
                       mAlsaMock.isChanged(mTestCoeffName));

            System.arraycopy(sFileDataMap.get(writeSize), 0,
                             expected, 0, Math.min(writeSize, mTestSize));

            assertArrayEquals(mTestCoeffName + " not written correctly (n=" + writeSize + ")",
                              expected,
                              mAlsaMock.getData(mTestCoeffName));

            assertEquals("Failed to close " + streamName + " stream",
                         0,
                         mConfigMgr.release_stream(stream));

            mAlsaMock.clearChangedFlag(mTestCoeffName);
        }
    }
};
