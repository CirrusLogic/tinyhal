=====================================================
Running the TinyHAL Configuration Manager JUnit Tests
=====================================================

|Copyright (C) 2020 Cirrus Logic, Inc. and
|              Cirrus Logic International Semiconductor Ltd.
|              All rights reserved.


~~~~~~~~~~~~~~~~~~~~~~~
GNU/LINUX BASED SYSTEMS
~~~~~~~~~~~~~~~~~~~~~~~

Prerequisites
-------------
1. JUnit4 package must be installed.
2. Set CLASSPATH to include the location of junit4.jar.
   This command will usually work:

   export CLASSPATH=$(find /usr -iname junit4.jar)

3. Set JAVA_HOME. The Makefile will attempt to find this automatically if it
   is not set, but it is safer, and faster, to explicitly set it to the correct
   location.
   This command will usually work:

   export JAVA_HOME=$(readlink -f /usr/bin/javac | sed 's:/bin/javac::')

4. To test support for old TinyALSA versions without the mixer_ctl_get_id()
   and mixer_add_new_ctls() functions, add these definition to CFLAGS and
   CXXFLAGS:

   export CFLAGS="$CFLAGS -DTINYALSA_NO_ADD_NEW_CTRLS -DTINYALSA_NO_CTL_GET_ID"
   export CXXFLAGS="$CXXFLAGS -DTINYALSA_NO_ADD_NEW_CTRLS -DTINYALSA_NO_CTL_GET_ID"

5. If the tinyalsa headers are not in the default include file locations, add
   the path to CFLAGS:

   export CFLAGS="$CFLAGS -Ipath/to/tinyalsa/include"
   export CXXFLAGS="$CXXFLAGS -Ipath/to/tinyalsa/include"

Building
--------
1. cd into the tinyhal/configmgr/test directory.
2. Execute make:

   make

Note - to do a clean make execute:

   make clean
   make

Running
-------
The makefile contains a phony target for running all tests:

    make runall

There is also a phony target for running a single test class:

    make run TEST_CLASS_NAME=ThcmEnumControlTest

The test runner can be changed using the JUNIT_RUNNER define:

   make runall JUNIT_RUNNER=org.someone.MyOtherRunner

~~~~~~~~~~~~~~~~~~~~~~~
ANDROID
~~~~~~~~~~~~~~~~~~~~~~~

Prerequisites
-------------
1. Add the packages to device.mk:

   BUILD_CIRRUS_TEST_VOICE_INTERACTION_SERVICE := true
   PRODUCT_PACKAGES += \
       libcom.cirrus.tinyhal.test.thcm_jni \
       com.cirrus.tinyhal.test.thcm

2. Up to and including Android 10 the version of TinyALSA included with AOSP
   source does not have the mixer_ctl_get_id() and mixer_add_new_ctls()
   functions, so add these definition to BoardConfig.mk:

   TINYALSA_NO_ADD_NEW_CTRLS := true
   TINYALSA_NO_CTL_GET_ID := true

Building
--------
Use the normal Android build system. Copy it to the target platform with
'adb sync' or whatever alternative system is used for the target.

Running
-------
To run all tests use ThcmUnitTest as the class to run:

    am instrument -w -e class com.cirrus.tinyhal.test.thcm.ThcmUnitTest com.cirrus.tinyhal.test.thcm/android.support.test.runner.AndroidJUnitRunner

Or specify an exact test class to run, for example this will run the tests in
the ThcmEnumControlTest class:

    am instrument -w -e class com.cirrus.tinyhal.test.thcm.ThcmEnumControlTest com.cirrus.tinyhal.test.thcm/android.support.test.runner.AndroidJUnitRunner
