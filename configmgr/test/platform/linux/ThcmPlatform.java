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
import java.io.IOException;
import java.nio.file.Files;
import java.lang.String;

/**
 * Platform-dependent definitions for GNU/Linux.
 */
public class ThcmPlatform
{
    private static File sWorkPath = null;
    /**
     * Path the tests can use for temporary files created during the test.
     * This is returned as a File object because it is more generally usable
     * than a Path object.
     *
     * @return File object representing the path
     */
    public static File workFilesPath()
    {
        if (sWorkPath == null) {
            try {
                sWorkPath = Files.createTempDirectory("thcm")
                            .toAbsolutePath()
                            .normalize()
                            .toFile();
            } catch (final Exception e) {
                throw new RuntimeException("Failed to create temp directory ", e);
            }
        }

        return sWorkPath;
    }
};
