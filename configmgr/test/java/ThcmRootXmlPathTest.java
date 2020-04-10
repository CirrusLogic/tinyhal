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
 * Test that the XML file path passed to init_audio_config() is interpreted
 * correctly. On Android a non-absolute path is relative to the etc/ directory.
 * On GNU/Linux is is relative to current working directory.
 */
public class ThcmRootXmlPathTest
{
    private static final String XML_FILE_NAME = "thcm_root_xml_config.xml";
    private static final String TEST_STREAM_NAME = "global";
    private static final String TEST_CONST_NAME = "thing";
    private static final String DEFAULT_PATH_CONST_VALUE = "Mallard";
    private static final String OTHER_PATH_CONST_VALUE = "Teal";

    private static final File sWorkFilesPath = ThcmPlatform.workFilesPath();
    private static final File sSystemConfigFilesPath = ThcmPlatform.defaultSystemConfigPath();
    private static final File sControlsFile = new File(sWorkFilesPath, "thcm_root_xml_controls.csv");

    private CAlsaMock mAlsaMock = new CAlsaMock();
    private CConfigMgr mConfigMgr = new CConfigMgr();

    private File mXmlFile;

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
        mXmlFile = null;

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
            assertFalse("Configmgr leaked memory", CConfigMgr.are_allocs_leaked());
        }

        if (mAlsaMock != null) {
            mAlsaMock.closeMixer();
            mAlsaMock = null;
        }

        if (mXmlFile != null) {
            if (mXmlFile.exists()) {
                mXmlFile.delete();
            }
        }
    }

    private static void createAlsaControlsFile() throws IOException
    {
        FileWriter writer = new FileWriter(sControlsFile);

        writer.write("dummy,bool,1,0,0:1\n");

        writer.close();
    }

    private void createXmlFile(String constValue) throws IOException
    {
        // The unique prefix is added to the <case> target files and the const
        // value to make this file unique from others generated from the same
        // template

        FileWriter writer = new FileWriter(mXmlFile);

        // Header
        writer.write("<audiohal>\n");

        // Mandatory <mixer> element
        writer.write("<mixer card=\"0\" />\n");

        // Const value for this file
        writer.write("<stream name=\"" + TEST_STREAM_NAME + "\" type=\"hw\">\n");
        writer.write("<set name=\"" + TEST_CONST_NAME + "\" val=\"" +
                     constValue + "\"/>\n");
        writer.write("</stream>\n");

        // Footer
        writer.write("</audiohal>\n");
        writer.close();
    }

    /**
     * XML file not in system config file path, absolute path passed to
     * init_audio_config().
     *
     * @throws IOException if cannot create XML file
     */
    @Test
    public void testAbsoluteNonDefaultPath() throws IOException
    {
        // Get absolute path and normalize to remove redundant . and ..
        mXmlFile = sWorkFilesPath.toPath()
                                 .toAbsolutePath()
                                 .normalize()
                                 .resolve(XML_FILE_NAME)
                                 .toFile();
        createXmlFile(OTHER_PATH_CONST_VALUE);

        assertEquals("Failed to open CConfigMgr with " + mXmlFile.toString(),
                     0,
                     mConfigMgr.init_audio_config(mXmlFile.toString()));

        long stream = mConfigMgr.get_named_stream(TEST_STREAM_NAME);
        assertTrue("Failed to open stream", stream >= 0);

        assertEquals("Not expected const value",
                     OTHER_PATH_CONST_VALUE,
                     mConfigMgr.get_stream_constant_string(stream,
                                                           TEST_CONST_NAME));
    }

    /**
     * XML file in system config file path, absolute path passed to
     * init_audio_config().
     *
     * @throws IOException if cannot create XML file
     */
    @Test
    public void testAbsoluteDefaultPath() throws IOException
    {
        // Get absolute path and normalize to remove redundant . and ..
        mXmlFile = sSystemConfigFilesPath.toPath()
                                         .toAbsolutePath()
                                         .normalize()
                                         .resolve(XML_FILE_NAME)
                                         .toFile();
        if (ThcmPlatform.isSystemConfigPathPrePopulated()) {
            assertTrue("Pre-populated file not found: " + mXmlFile.toString(),
                       mXmlFile.exists());
        } else {
            createXmlFile(DEFAULT_PATH_CONST_VALUE);
        }

        assertEquals("Failed to open CConfigMgr with " + mXmlFile.toString(),
                     0,
                     mConfigMgr.init_audio_config(mXmlFile.toString()));

        long stream = mConfigMgr.get_named_stream(TEST_STREAM_NAME);
        assertTrue("Failed to open stream", stream >= 0);

        assertEquals("Not expected const value",
                     DEFAULT_PATH_CONST_VALUE,
                     mConfigMgr.get_stream_constant_string(stream,
                                                           TEST_CONST_NAME));
    }

    /**
     * XML file not in system config file path, relative path passed to
     * init_audio_config().
     * The given path is relative to the system config path.
     *
     * @throws IOException if cannot create XML file
     */
    @Test
    public void testRelativeNonDefaultPath() throws IOException
    {
        // Get absolute path and normalize to remove redundant . and ..
        mXmlFile = sWorkFilesPath.toPath()
                                 .toAbsolutePath()
                                 .resolve(XML_FILE_NAME)
                                 .normalize()
                                 .toFile();
        createXmlFile(OTHER_PATH_CONST_VALUE);

        // Normalized path relative to default system config file path
        String relativePath = sSystemConfigFilesPath
                                .toPath()
                                .toAbsolutePath()
                                .normalize()
                                .relativize(mXmlFile.toPath())
                                .normalize()
                                .toString();

        assertEquals("Failed to open CConfigMgr with " + relativePath,
                     0,
                     mConfigMgr.init_audio_config(relativePath));

        long stream = mConfigMgr.get_named_stream(TEST_STREAM_NAME);
        assertTrue("Failed to open stream", stream >= 0);

        assertEquals("Not expected const value",
                     OTHER_PATH_CONST_VALUE,
                     mConfigMgr.get_stream_constant_string(stream,
                                                           TEST_CONST_NAME));
    }

    /**
     * XML file in system config file path, only filename passed to
     * init_audio_config().
     *
     * @throws IOException if cannot create XML file
     */
    @Test
    public void testRelativeDefaultPath() throws IOException
    {
        // Location of XML - only used to create the file
        mXmlFile = sSystemConfigFilesPath.toPath()
                                         .normalize()
                                         .resolve(XML_FILE_NAME)
                                         .toFile();

        if (ThcmPlatform.isSystemConfigPathPrePopulated()) {
            assertTrue("Pre-populated file not found: " + mXmlFile.toString(),
                       mXmlFile.exists());
        } else {
            createXmlFile(DEFAULT_PATH_CONST_VALUE);
        }

        // File is in default system config path so we should only need to
        // pass the name to configmgr
        assertEquals("Failed to open CConfigMgr with " + XML_FILE_NAME,
                     0,
                     mConfigMgr.init_audio_config(XML_FILE_NAME));

        long stream = mConfigMgr.get_named_stream(TEST_STREAM_NAME);
        assertTrue("Failed to open stream", stream >= 0);

        assertEquals("Not expected const value",
                     DEFAULT_PATH_CONST_VALUE,
                     mConfigMgr.get_stream_constant_string(stream,
                                                           TEST_CONST_NAME));
    }
};
