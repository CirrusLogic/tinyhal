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
import java.nio.file.Files;
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
 * Test opening the mixer card by number and by name.
 * This tests the <code>name</code> and <code>number</code> attributes of the
 * <code>&lt;mixer&gt;</code> element.
 */
public class ThcmOpenMixerTest
{
    private static String[] TEST_CARD_NAMES = {
        "apple", "pear", "banana"
    };
    private static int[] TEST_NAMED_CARD_NUMBERS = {
        1, 4, 7
    };
    private static String[] TEST_NOCARD_NAMES = {
        "leek", "onion", "chive"
    };

    private static final File sWorkFilesPath = ThcmPlatform.workFilesPath();
    private static final File sDummyProcPath = new File(sWorkFilesPath, "proc");
    private static final File sControlsFile = new File(sWorkFilesPath, "thcm_open_mixer_controls.csv");
    private static final File sXmlFile = new File(sWorkFilesPath, "thcm_open_mixer_config.xml");

    private CAlsaMock mAlsaMock = new CAlsaMock();
    private CConfigMgr mConfigMgr = new CConfigMgr();

    @BeforeClass
    public static void setUpClass() throws IOException
    {
        sDummyProcPath.mkdirs();
        CAlsaMock.setRedirectedProcPath(sWorkFilesPath.toString());

        createAlsaControlsFile();

        for (int i = 0; i < TEST_CARD_NAMES.length; ++i) {
            createCard(TEST_CARD_NAMES[i], TEST_NAMED_CARD_NUMBERS[i]);
        }
    }

    @AfterClass
    public static void tearDownClass() throws IOException
    {
        if (sControlsFile.exists()) {
            sControlsFile.delete();
        }

        if (sDummyProcPath.exists()) {
            deleteAllUnder(sDummyProcPath);
            sDummyProcPath.delete();
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

        if (sXmlFile.exists()) {
            sXmlFile.delete();
        }
    }

    private static void deleteAllUnder(File parent)
    {
        for (File f : parent.listFiles()) {
            if (f.isDirectory()) {
                deleteAllUnder(f);
            }
            f.delete();
        }
    }

    private static void createAlsaControlsFile() throws IOException
    {
        FileWriter writer = new FileWriter(sControlsFile);
        writer.write("dummy,bool,1,0,0:1\n");
        writer.close();
    }

    private static void createCard(String id, int number)  throws IOException
    {
        File idFile = new File(sDummyProcPath, "/asound/card" + number + "/id");
        idFile.getParentFile().mkdirs();
        FileWriter writer = new FileWriter(idFile);
        writer.write(id);
        writer.close();
    }

    private void createXmlFile(String[] attribs) throws IOException
    {
        FileWriter writer = new FileWriter(sXmlFile);

        writer.write("<audiohal>\n");
        writer.write("<mixer ");
        for (String attrib : attribs) {
            writer.write(attrib);
            writer.write(" ");
        }
        writer.write("/>\n");
        writer.write("</audiohal>\n");

        writer.close();
    }

    private void createXmlFile(int cardNumber) throws IOException
    {
        createXmlFile(new String[] {"card=\"" + cardNumber + "\""});
    }

    private void createXmlFile(String cardName) throws IOException
    {
        createXmlFile(new String[] {"name=\"" + cardName + "\""});
    }

    private void createXmlFile(String cardName, int cardNumber) throws IOException
    {
        createXmlFile(new String[] {
            "name=\"" + cardName + "\"",
            "card=\"" + cardNumber + "\""
        });
    }

    /**
     * Open mixer declared by <code>card</code> attribute.
     * Several card numbers are tested to ensure that it is passing the
     * defined card number to tinyalsa.
     *
     * @throws IOException If XML file cannot be created or deleted.
     */
    @Test
    public void testOpenByNumber() throws IOException
    {
        for (int num : new int[] {42, 0, 5}) {
            assertEquals("Failed to create CAlsaMock",
                         0,
                         mAlsaMock.createMixer(sControlsFile.toPath().toString(),
                                               num));
            createXmlFile(num);
            assertEquals("Failed to open CConfigMgr for " + num,
                         0,
                         mConfigMgr.init_audio_config(sXmlFile.toPath().toString()));
            mConfigMgr.free_audio_config();
            sXmlFile.delete();
            mAlsaMock.closeMixer();
        }
    }

    /**
     * Attempt to open a <code>card</code> attribute that is not a valid
     * card number.
     *
     * @throws IOException If XML file cannot be created or deleted.
     */
    @Test
    public void testNotFoundByNumber() throws IOException
    {
        assertEquals("Failed to create CAlsaMock",
                     0,
                     mAlsaMock.createMixer(sControlsFile.toPath().toString(),
                                           42));

        for (int num : new int[] {7, 0, 5}) {
            createXmlFile(num);
            assertTrue("Expected CConfigMgr open to fail for " + num,
                       mConfigMgr.init_audio_config(sXmlFile.toPath().toString()) < 0);
            sXmlFile.delete();
        }
    }

    /**
     * Open mixer declared by <code>name</code> attribute.
     * Several card names are tested to ensure that it is scanning the available
     * cards for a match.
     *
     * @throws IOException If XML file cannot be created or deleted.
     */
    @Test
    public void testOpenByName() throws IOException
    {
        for (int i = 0; i < TEST_CARD_NAMES.length; ++i) {
            assertEquals("Failed to create CAlsaMock",
                         0,
                         mAlsaMock.createMixer(sControlsFile.toPath().toString(),
                                               TEST_NAMED_CARD_NUMBERS[i]));
            createXmlFile(TEST_CARD_NAMES[i]);
            assertEquals("Failed to open CConfigMgr for " + TEST_CARD_NAMES[i],
                         0,
                         mConfigMgr.init_audio_config(sXmlFile.toPath().toString()));
            mConfigMgr.free_audio_config();
            sXmlFile.delete();
            mAlsaMock.closeMixer();
        }
    }

    /**
     * Attempt to open a <code>name</code> attribute that does not match any
     * available cards.
     *
     * @throws IOException If XML file cannot be created or deleted.
     */
    @Test
    public void testNotFoundByName() throws IOException
    {
        for (int i = 0; i < TEST_CARD_NAMES.length; ++i) {
            assertEquals("Failed to create CAlsaMock",
                         0,
                         mAlsaMock.createMixer(sControlsFile.toPath().toString(),
                                               TEST_NAMED_CARD_NUMBERS[i]));
            createXmlFile(TEST_NOCARD_NAMES[i]);
            assertTrue("Expected CConfigMgr open to fail for " + TEST_NOCARD_NAMES[i],
                       mConfigMgr.init_audio_config(sXmlFile.toPath().toString()) < 0);
            sXmlFile.delete();
            mAlsaMock.closeMixer();
        }
    }

    /**
     * Test that <code>card</code> and <code>name</code> cannot both be given.
     * The XML should be rejected if both attributes are given in the
     * <code>&lt;mixer&gt;</code> element.
     *
     * @throws IOException If XML file cannot be created or deleted.
     */
    @Test
    public void testCardAttributesExclusive() throws IOException
    {
        assertEquals("Failed to create CAlsaMock",
                     0,
                     mAlsaMock.createMixer(sControlsFile.toPath().toString(),
                                           0));

        // Create XML with name and card set to valid values
        createXmlFile(TEST_CARD_NAMES[0], TEST_NAMED_CARD_NUMBERS[0]);

        assertTrue("Expected CConfigMgr open to fail",
                   mConfigMgr.init_audio_config(sXmlFile.toPath().toString()) < 0);
        sXmlFile.delete();
        mAlsaMock.closeMixer();
    }

    /**
     * Test that one of <code>card</code> or <code>name</code> must be given.
     * The XML should be rejected if neither attribute is given in the
     * <code>&lt;mixer&gt;</code> element.
     *
     * @throws IOException If XML file cannot be created or deleted.
     */
    @Test
    public void testCardAttributesMandatory() throws IOException
    {
        assertEquals("Failed to create CAlsaMock",
                     0,
                     mAlsaMock.createMixer(sControlsFile.toPath().toString(),
                                           0));

        createXmlFile(new String[0]);

        assertTrue("Expected CConfigMgr open to fail",
                   mConfigMgr.init_audio_config(sXmlFile.toPath().toString()) < 0);
        sXmlFile.delete();
        mAlsaMock.closeMixer();
    }

    /**
     * <code>get_mixer()</code> returns the correct mixer pointer.
     *
     * @throws IOException If XML file cannot be created or deleted.
     */
    @Test
    public void testGetMixerPointer() throws IOException
    {
        assertEquals("Failed to create CAlsaMock",
                     0,
                     mAlsaMock.createMixer(sControlsFile.toPath().toString(),
                                           1));
        final long pointer = mAlsaMock.getMixerPointer();
        // Sanity check the harness to make sure we didn't get a NULL
        assertTrue("Harness mixer pointer is NULL", pointer != 0);

        createXmlFile(1);
        assertEquals("Failed to open CConfigMgr",
                     0,
                     mConfigMgr.init_audio_config(sXmlFile.toPath().toString()));
        assertEquals("get_mixer return not correct", pointer, mConfigMgr.get_mixer());

        mConfigMgr.free_audio_config();
        sXmlFile.delete();
        mAlsaMock.closeMixer();
    }
};
