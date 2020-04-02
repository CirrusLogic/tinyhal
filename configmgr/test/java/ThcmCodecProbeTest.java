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
import java.util.ArrayList;
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
 * Test that &lt;codec_probe&gt; redirects to another xml file.
 * The testing assumes that &lt;set&gt; elements execute correctly.
 */
@RunWith(Parameterized.class)
public class ThcmCodecProbeTest
{
    // XML file organization is:
    //
    // - Root XML file with a codec_probe pointing to a set of level-1 XMLs
    // - Level-1 XMLs have a codec_probe pointing to level-2 XMLs
    //
    // As there is only one root XML, it will have only 1 set of level-1 XMLs.
    // However, each level-1 XML will point to a set of level-2 XMLs, so there
    // are multiple sets of level-2 XMLs. The level-2 XMLs are all generated
    // from the same template but are made unique based on which level-1 file
    // they are referenced from.
    //
    // Every XML has a unique <set> value to prove which file was finally
    // parsed.

    // Cases in root XML that point to level-1 files
    private static final String[][] LEVEL1_CASES = {
        //  root XML case value, L1 xml file, test const value in L1 file
        { "doh", "thcm_codec_probe_configA.xml", "Alpha" },
        { "re", "thcm_codec_probe_configB.xml", "Bravo" },
        { "mi", "thcm_codec_probe_configC.xml", "Charlie" },
        { "fa", "thcm_codec_probe_configD.xml", "Delta" },
    };

    // Cases in level-1 XML that point to level-2 files
    // This is a template and the %s are subsistitued to make each set of
    // generated files unique to the level-1 file that pointed to them.
    private static final String[][] LEVEL2_CASES = {
        //  L1 case value, L2 xml file, test const value in L2 file
        { "apple", "thcm_codec_probe_config%sF.xml", "Foxtrot%s" },
        { "pear", "thcm_codec_probe_config%sG.xml", "Golf%s" },
        { "grape", "thcm_codec_probe_config%sH.xml", "Hotel%s" },
    };

    private static final String TEST_STREAM_NAME = "global";
    private static final String TEST_CONST_NAME = "tcn";
    private static final String ROOT_XML_CONST_VALUE = "Zebra";
    private static final String NOMATCH_PROBE_VALUE = "Omega";

    // Dummy indexes: put something in the probe file that won't match any case
    private static final int EMPTY_PROBE_FILE = -1;
    private static final int NOMATCH_PROBE_FILE = -2;

    // Location and type of reference to subfiles:
    // ABSOLUTE_xxx = XML reference is an absolute path
    // RELATIVE_xxx = XML reference is relative to the XML location
    // xxx_PATH = target file is in sWorkFilesPath
    // xxx_SUBPATH = target file is in sWorkFilesSubpath
    private static final int ABSOLUTE_PATH = 0;
    private static final int RELATIVE_PATH = 1;
    private static final int ABSOLUTE_SUBPATH = 2;
    private static final int RELATIVE_SUBPATH = 3;

    // Run a test pass with leading spaces on filenames, primarily to check
    // that an absolute path is still detected correctly if the initial /
    // is preceeded by whitespace
    private static final int NUM_LEADING_SPACES = 3;

    private static final int[] ALL_PATH_TYPES = {
        ABSOLUTE_PATH, RELATIVE_PATH, ABSOLUTE_SUBPATH, RELATIVE_SUBPATH
    };

    private static final File sWorkFilesPath = ThcmPlatform.workFilesPath();
    private static final File sWorkFilesSubpath = new File(sWorkFilesPath, "/subp");
    private static final File sControlsFile = new File(sWorkFilesPath, "thcm_codec_probe_controls.csv");
    private static final File sRootXmlFile = new File(sWorkFilesPath, "thcm_codec_probe_config.xml");
    private static final File sLevel1ProbeFile = new File("thcm_codec_probe_l1.probe");
    private static final File sLevel2ProbeFile = new File("thcm_codec_probe_l2.probe");

    private static final String PCM_STREAM_TYPE = "pcm";
    private static final String COMPRESSED_STREAM_TYPE = "compressed";
    private static final String HW_STREAM_TYPE = "hw";

    private CAlsaMock mAlsaMock = new CAlsaMock();
    private CConfigMgr mConfigMgr = new CConfigMgr();

    int mLevel1ProbeIndex;
    int mLevel2ProbeIndex;
    int mLevel1PathType;
    int mLevel2PathType;
    int mLevel1ProbeFilePathType;
    int mLevel2ProbeFilePathType;
    int mLeadingSpaces;

    Path mLevel1XmlParentPath;
    Path mLevel2XmlParentPath;
    Path mLevel1ProbeParentPath;
    Path mLevel2ProbeParentPath;

    @Parameterized.Parameters
    public static Collection parameters() {
        List<Integer[]> params = new ArrayList<Integer[]>();

        for (Integer l1XmlPathType : ALL_PATH_TYPES) {
            for (Integer l2XmlPathType : ALL_PATH_TYPES) {
                for (Integer l1ProbePathType : ALL_PATH_TYPES) {
                    for (Integer l2ProbePathType : ALL_PATH_TYPES) {
                        for (int i = 0; i < LEVEL1_CASES.length; ++i) {
                            for (int j = 0; j < LEVEL2_CASES.length; ++j) {
                                params.add(new Integer[] {
                                    i,
                                    j,
                                    l1XmlPathType,
                                    l2XmlPathType,
                                    l1ProbePathType,
                                    l2ProbePathType,
                                    0 // no leading spaces on file names
                                });

                                params.add(new Integer[] {
                                    i,
                                    j,
                                    l1XmlPathType,
                                    l2XmlPathType,
                                    l1ProbePathType,
                                    l2ProbePathType,
                                    NUM_LEADING_SPACES
                                });
                            }
                        }
                    }
                }
            }
        }

        return params;
    }

    public ThcmCodecProbeTest(Integer level1Index, Integer level2Index,
                              Integer level1PathType, Integer level2PathType,
                              Integer level1ProbeFilePathType,
                              Integer level2ProbeFilePathType,
                              Integer leadingSpaces)
    {
        mLevel1ProbeIndex = level1Index;
        mLevel2ProbeIndex = level2Index;
        mLevel1PathType = level1PathType;
        mLevel2PathType = level2PathType;
        mLevel1ProbeFilePathType = level1ProbeFilePathType;
        mLevel2ProbeFilePathType = level2ProbeFilePathType;
        mLeadingSpaces = leadingSpaces;
    }

    @BeforeClass
    public static void setUpClass() throws IOException
    {
        sWorkFilesSubpath.mkdirs();

        createAlsaControlsFile();
    }

    @AfterClass
    public static void tearDownClass()
    {
        if (sControlsFile.exists()) {
            sControlsFile.delete();
        }

        if (sWorkFilesSubpath.exists()) {
            // bit hacky - assumes it's only 1 level deep and empty
            sWorkFilesSubpath.delete();
        }
    }

    @Before
    public void setUp() throws IOException
    {
        // Work out what all the parent paths are for this pass
        mLevel1XmlParentPath = figureOutOneWorkPath(mLevel1PathType);
        mLevel2XmlParentPath = figureOutOneWorkPath(mLevel2PathType);
        mLevel1ProbeParentPath = figureOutOneWorkPath(mLevel1ProbeFilePathType);
        mLevel2ProbeParentPath = figureOutOneWorkPath(mLevel2ProbeFilePathType);

        // Create root XML file
        createXmlFile(sRootXmlFile.toPath().toAbsolutePath(),
                      LEVEL1_CASES,
                      mLevel1XmlParentPath,
                      mLevel1PathType,
                      "",
                      mLevel1ProbeParentPath.resolve(sLevel1ProbeFile.toPath()),
                      mLevel1ProbeFilePathType,
                      ROOT_XML_CONST_VALUE);

        // Create all the level-1 XML files referenced by the root XML
        for (String[] c : LEVEL1_CASES) {
            // Suffix on target L2 filename to make it unique to this L1 file
            String l2FileUniqueSuffix = c[2].substring(0,1);

            createXmlFile(mLevel1XmlParentPath.resolve(c[1]),
                          LEVEL2_CASES,
                          mLevel2XmlParentPath,
                          mLevel2PathType,
                          l2FileUniqueSuffix,
                          mLevel2ProbeParentPath.resolve(sLevel2ProbeFile.toPath()),
                          mLevel2ProbeFilePathType,
                          c[2]);

            // Create all the level-2 XML files associated with this level-1 XML
            for (String[] d : LEVEL2_CASES) {
                String l2XmlName = String.format(d[1], l2FileUniqueSuffix);
                String constVal = String.format(d[2], l2FileUniqueSuffix);
                createXmlFile(mLevel2XmlParentPath.resolve(l2XmlName),
                              null,
                              null,
                              ABSOLUTE_PATH,
                              null,
                              null,
                              0,
                              constVal);
            }
        }

        // Create level-1 probe content without trailing newline and level-2
        // with trailing newline so we can test that the presence or absence
        // of a newline doesn't affect the value extracted from the file.
        createProbeFile(mLevel1ProbeParentPath.resolve(sLevel1ProbeFile.toPath()),
                        LEVEL1_CASES,
                        mLevel1ProbeIndex,
                        false);
        createProbeFile(mLevel2ProbeParentPath.resolve(sLevel2ProbeFile.toPath()),
                        LEVEL2_CASES,
                        mLevel2ProbeIndex,
                        true);

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

        for (String[] c : LEVEL1_CASES) {
            File f = mLevel1XmlParentPath.resolve(c[1]).toFile();
            if (f.exists()) {
                f.delete();
            }

            // Delete the associated L2 files
            String l2FileUniqueSuffix = c[2].substring(0,1);
            for (String[] d : LEVEL2_CASES) {
                File ff =
                    mLevel2XmlParentPath.resolve(
                        String.format(d[1], l2FileUniqueSuffix)).toFile();
                if (ff.exists()) {
                    ff.delete();
                }
            }
        }

        if (sRootXmlFile.exists()) {
            sRootXmlFile.delete();
        }

        File f = mLevel1ProbeParentPath.resolve(sLevel1ProbeFile.toPath()).toFile();
        if (f.exists()) {
            f.delete();
        }

        f = mLevel2ProbeParentPath.resolve(sLevel2ProbeFile.toPath()).toFile();
        if (f.exists()) {
            f.delete();
        }
    }

    private static void createAlsaControlsFile() throws IOException
    {
        FileWriter writer = new FileWriter(sControlsFile);

        writer.write("dummy,bool,1,0,0:1\n");

        writer.close();
    }

    // Get an absolute working path for a given PathType settings
    private Path figureOutOneWorkPath(int pathType)
    {
        if ((pathType == ABSOLUTE_PATH || pathType == RELATIVE_PATH)) {
            return sWorkFilesPath.toPath().toAbsolutePath();
        } else {
            // ABSOLUTE_SUBPATH or RELATIVE_SUBPATH
            return sWorkFilesSubpath.toPath().toAbsolutePath();
        }
    }

    // Given an absolute parent path and an absolute target file path, create
    // the string for the XML 'file' attribute depending whether it should be
    // an absolute file reference or relative to the parent file.
    private static String getPathRefForXml(Path parentPath,
                                           Path targetPath,
                                           int pathRefType)
    {
        // Pick up coding errors to save debugging time
        assertTrue("getPathRefForXml parentPath is not absolute",
                   parentPath.isAbsolute());
        assertTrue("getPathRefForXml targetPath is not absolute",
                   targetPath.isAbsolute());

        // If we were given a parent file, strip it to the parent directory
        if (parentPath.toFile().isFile()) {
            parentPath = parentPath.getParent();
        }

        if (pathRefType == RELATIVE_PATH) {
            // Make it relative to parentPath
            return parentPath.relativize(targetPath).toString();
        } else if (pathRefType == RELATIVE_SUBPATH) {
            targetPath = sWorkFilesSubpath.toPath().resolve(targetPath);
            return parentPath.relativize(targetPath).toString();
        } else if (pathRefType == ABSOLUTE_SUBPATH) {
            targetPath = sWorkFilesSubpath.toPath().resolve(targetPath);
            return parentPath.toAbsolutePath().resolve(targetPath).toString();
        } else if (pathRefType == ABSOLUTE_PATH) {
            if (targetPath.isAbsolute()) {
                return targetPath.toString();
            }

            // Ensure parent path is absolute and then join to it
            return parentPath.toAbsolutePath().resolve(targetPath).toString();
        } else {
            fail("Bad path type " + Integer.toString(pathRefType));
            return null;
        }
    }

    private void createXmlFile(Path xmlFile,
                               String[][] cases,
                               Path targetXmlPath,
                               int targetXmlPathRefType,
                               String targetFileUniqueSuffix,
                               Path probeFile,
                               int probeFileRefType,
                               String constValue) throws IOException
    {
        String leadingSpace = "";
        for (int c = 0; c < mLeadingSpaces; ++c) {
            leadingSpace += " ";
        }

        FileWriter writer = new FileWriter(xmlFile.toFile());

        // Header element
        writer.write("<audiohal>\n");

        // <codec_probe> block
        if (cases != null) {
            String targetProbe = getPathRefForXml(xmlFile,
                                                  probeFile,
                                                  probeFileRefType);
            writer.write("<codec_probe file=\"" + leadingSpace + targetProbe + "\">\n");

            for (String[] c : cases) {
                String targetXmlName = String.format(c[1], targetFileUniqueSuffix);
                File targetXmlFile = new File(targetXmlName);
                targetXmlName =
                    getPathRefForXml(xmlFile,
                                     targetXmlPath.resolve(targetXmlFile.toPath()),
                                     targetXmlPathRefType);
                writer.write("<case name=\"" + c[0] +
                             "\" file=\"" + leadingSpace + targetXmlName + "\"/>\n");
            }
            writer.write("</codec_probe>\n");
        }

        // Mandatory <mixer> element
        writer.write("<mixer card=\"0\" />\n");

        // Const value for this file
        writer.write("<stream name=\"" + TEST_STREAM_NAME + "\" type=\"hw\">\n");
        writer.write("<set name=\"" + TEST_CONST_NAME + "\" val=\"" +
                     constValue + "\"/>\n");
        writer.write("</stream>\n");

        // Footer elements
        writer.write("</audiohal>\n");

        writer.close();
    }

    private static void writeCtl(FileWriter writer,
                                 String controlName,
                                 String value) throws IOException
    {
            writer.write("<ctl name=\"" + controlName + "\" val=\"" + value + "\"/>\n");
    }

    private static void writeStreamEntry(FileWriter writer,
                                         String name,
                                         String type,
                                         String dir,
                                         String enable,
                                         String disable) throws IOException
    {
        String nameAttr = "";
        if (name != null) {
            nameAttr = "name=\"" + name + "\" ";
        }

        writer.write("<stream " + nameAttr + "type=\"" + type + "\" dir=\"" +
                     dir + "\" >\n");
        if (enable != null) {
            writer.write("<enable path=\"" + enable + "\" />\n");
        }
        if (disable != null) {
            writer.write("<disable path=\"" + disable + "\" />\n");
        }
        writer.write("</stream>\n");
    }

    private static void createProbeFile(Path file,
                                        String[][] cases,
                                        int index,
                                        boolean appendNewline) throws IOException
    {
        FileWriter writer = new FileWriter(file.toFile());
        String line = null;

        if (index == NOMATCH_PROBE_FILE) {
            // A probe file value that won't match any case
            line = NOMATCH_PROBE_VALUE;
        } else if (index != EMPTY_PROBE_FILE) {
            line = cases[index][0];
        }

        if (appendNewline) {
            line.concat("\n");
        }

        if (line != null) {
            writer.write(line);
        }

        writer.close();
    }

    /**
     * Check that the correct XML file was parsed.
     * This uses the defined constant, which has a different value in each XML
     * XML file, to check which file was parsed.
     */
    @Test
    public void testSelectedFile()
    {
        assertEquals("Failed to open CConfigMgr",
                     0,
                     mConfigMgr.init_audio_config(sRootXmlFile.toPath().toString()));

        long stream = mConfigMgr.get_named_stream(TEST_STREAM_NAME);
        assertTrue("Failed to open global stream", stream >= 0);

        String constVal = mConfigMgr.get_stream_constant_string(stream,
                                                                TEST_CONST_NAME);
        if ((mLevel1ProbeIndex == EMPTY_PROBE_FILE) ||
            (mLevel1ProbeIndex == NOMATCH_PROBE_FILE)) {
            // won't match any case, will return const from root XML
            assertEquals("Not root file const",
                         ROOT_XML_CONST_VALUE,
                         constVal);
        } else if ((mLevel2ProbeIndex == EMPTY_PROBE_FILE) ||
                   (mLevel2ProbeIndex == NOMATCH_PROBE_FILE)) {
            // won't match any case, will return const from level1 XML
            assertEquals("Not level1 const",
                         LEVEL1_CASES[mLevel1ProbeIndex][2],
                         constVal);
        } else {
            // Should return the const from the level2 file
            String l2FileUniqueSuffix = LEVEL1_CASES[mLevel1ProbeIndex][2].substring(0,1);
            assertEquals("Const has wrong value",
                         String.format(LEVEL2_CASES[mLevel2ProbeIndex][2],
                                       l2FileUniqueSuffix),
                         constVal);
        }
    }
};

