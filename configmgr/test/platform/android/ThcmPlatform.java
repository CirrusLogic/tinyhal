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

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import java.io.File;
import java.lang.String;
import java.nio.file.Path;

/**
 * Platform-dependent definitions for Android.
 */
public class ThcmPlatform
{
    private static final File SYSTEM_ETC_PATH = new File("/system/etc");
    private static final File VENDOR_ETC_PATH = new File("/vendor/etc");

    /**
     * Path the tests can use for temporary files created during the test.
     * This is returned as a File object because it is more generally usable
     * than a Path object.
     *
     * @return File object representing the path
     */
    public static File workFilesPath()
    {
        Context ctx = InstrumentationRegistry.getContext();
        return ctx.getCacheDir().toPath().toAbsolutePath().normalize().toFile();
    }

    /**
     * Path that configmgr looks in for root config file if it is not given
     * an absolute path.
     * For Android builds this is either /system/etc or /vendor/etc.
     * This is returned as a File object because it is more generally usable
     * than a Path object.
     *
     * @return File object representing the path
     */
    public static File defaultSystemConfigPath()
    {
        if (VENDOR_ETC_PATH.exists()) {
            return VENDOR_ETC_PATH;
        } else {
            return SYSTEM_ETC_PATH;
        }
    }

    /**
     * Report whether test files in system config directory are pre-installed.
     * If the path returned by defaultSystemConfigPath() is not writeable at
     * runtime the test files located there will have been populated at build
     * time.
     *
     * @return true if the files have been pre-installed.
     */
    public static boolean isSystemConfigPathPrePopulated()
    {
        return true;
    }
};
