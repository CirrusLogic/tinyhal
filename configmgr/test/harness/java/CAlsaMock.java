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

import java.lang.String;
import java.lang.Throwable;

public class CAlsaMock
{
    private final int DEFAULT_CARD_NUMBER = 0;
    private final long mNativeMixerPtr = 0;

    static {
        System.loadLibrary("com.cirrus.tinyhal.test.thcm_jni");
    }

    public CAlsaMock()
    {
        native_setup();
    }

    public int createMixer(String controlsFileName)
    {
        return createMixer(controlsFileName, DEFAULT_CARD_NUMBER);
    }

    private native final void native_setup();

    public static native final void setRedirectedProcPath(String path);

    public native final int createMixer(String controlsFileName, int cardNum);
    public native final void closeMixer();
    public native final long getMixerPointer();

    public native final boolean isChanged(String controlName);
    public native final void clearChangedFlag(String controlName);
    public native final int getBool(String controlName, int index);
    public native final int getInt(String controlName, int index);
    public native final String getEnum(String controlName);
    public native final byte[] getData(String controlName);

};
