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

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

// To run on vanilla GNU/Linux-based system use:
//    make runall
//
// To run on Android use:
//    am instrument -w -e class com.cirrus.tinyhal.test.thcm.ThcmUnitTest com.cirrus.tinyhal.test.thcm/android.support.test.runner.AndroidJUnitRunner

@RunWith(Suite.class)
@Suite.SuiteClasses({
    ThcmByteControlTest.class,
    ThcmIntControlTest.class,
    ThcmEnumControlTest.class,
    ThcmBoolControlTest.class,
    ThcmPathControlsTest.class
})
public class ThcmUnitTest {
}
