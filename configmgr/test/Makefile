# Copyright (C) 2020 Cirrus Logic, Inc. and
#                    Cirrus Logic International Semiconductor Ltd.
#                    All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Programs and build environment
JAVAC ?= javac
JAR ?= jar
JAVA ?= java
JAVADOC ?= javadoc
JUNIT_RUNNER ?= org.junit.runner.JUnitCore
JAVA_C_INCLUDE_PATHS=$(JAVA_HOME)/include $(JAVA_HOME)/include/linux

# Sources and targets that can't be found automatically
JARPACKAGE = com.cirrus.tinyhal.test.thcm
JARTARGET = ThcmUnitTest.jar
JARSRC_PATH = java
JNISRC_PATH = harness/jni
JNIJAVASRC_PATH = harness/java
JAVAPLATFORMSRC_PATH = platform/linux
CONFIGMGRSRC_PATH = ..
CONFIGMGRSRC_INCLUDE_PATH = ../../include
JAVADOC_OUT_PATH = javadoc

# Local C++ compiler and linker options
LOCAL_CXXFLAGS = -std=c++17 -fPIC -Wall -Wextra -Wunused
LOCAL_CFLAGS = -fPIC -Wall -Wextra -Wunused -DTHCM_TEST_HARNESS_BUILD
LOCAL_GENDEPS_FLAGS = -MM
LOCAL_JNI_LIBS = -lexpat
LOCAL_LDFLAGS = -shared

# Check that we have a JAVA_HOME
ifeq ($(strip $(JAVA_HOME)),)
_JAVA_HOME=$(shell readlink -f /usr/bin/javac | sed 's:/bin/javac::')
ifeq ($(strip $(_JAVA_HOME)),)
$(error JAVA_HOME not defined and couldn't be found)
endif
export JAVA_HOME=$(_JAVA_HOME)
endif

# By default (on Debian anyway) javac doesn't know where jars are so
# CLASSPATH has to be set to point to junit4.jar
# Hint:
# export CLASSPATH=$(find /usr -iname junit4.jar)
ifeq ($(findstring junit4.jar,$(CLASSPATH)),)
$(error junit4.jar not on CLASSPATH)
endif

# Auto-generated java stuff
JARSRC = $(wildcard $(JARSRC_PATH)/*.java)
JARSRC += $(wildcard $(JAVAPLATFORMSRC_PATH)/*.java)
JARPACKAGE_PATHIZED=$(subst .,/,$(JARPACKAGE))
JAR_CLASS_PATH_BASEDIR=$(firstword $(subst /, ,$(JARPACKAGE_PATHIZED)))
JNIJAVACLASS_SRC=$(wildcard $(JNIJAVASRC_PATH)/*.java)

# Auto-generated JNI stuff
JNITARGET = lib$(JARPACKAGE)_jni.so
JNISRC = $(wildcard $(JNISRC_PATH)/*.cpp)
JNISRC += $(wildcard $(CONFIGMGRSRC_PATH)/*.c)
JNIOBJ_1 = $(JNISRC:.cpp=.o)
JNIOBJ = $(JNIOBJ_1:.c=.o)
JNIDEP = $(JNIOBJ:.o=.d)
JNI_HEADER_PREFIX = $(subst .,_,$(JARPACKAGE))_
JNI_JAVA_CLASS_NAMES_ONLY = $(foreach f,$(JNIJAVACLASS_SRC),$(basename $(notdir $(f))))
JNI_HEADERS = $(patsubst %,$(JNISRC_PATH)/$(JNI_HEADER_PREFIX)%.h,$(JNI_JAVA_CLASS_NAMES_ONLY))
CONFIGMGRSRC_INCLUDES = $(patsubst %,-I%,$(CONFIGMGRSRC_INCLUDE_PATH))
LOCAL_JAVA_C_INCLUDES = $(patsubst %,-I%,$(JAVA_C_INCLUDE_PATHS))

######################################################################
# Top-level build targets
.PHONY: all build clean clean_jni clean_java dep depend run runall javadoc
all: build javadoc

build: $(JNITARGET) $(JARTARGET)

dep: depend

clean: clean_jni clean_java

######################################################################
# The java command line is very clumsy so here is a helper target
# TEST_CLASS_NAME must be defined as the name of the test class to run
# The path to our JNI .so must be in LD_LIBRARY_PATH so Java can find it.
ifeq ($(LD_LIBRARY_PATH),)
_JAVA_LIBRARY_PATH=${CURDIR}
else
_JAVA_LIBRARY_PATH=${CURDIR}:$(LD_LIBRARY_PATH)
endif

run:
	$(JAVA) -Djava.library.path=$(_JAVA_LIBRARY_PATH) -cp ./$(JARTARGET):$(CLASSPATH) $(JUNIT_RUNNER) $(JARPACKAGE).$(TEST_CLASS_NAME)

######################################################################
# As you might expect, this runs all tests
runall:
	$(JAVA) -Djava.library.path=$(_JAVA_LIBRARY_PATH) -cp ./$(JARTARGET):$(CLASSPATH) $(JUNIT_RUNNER) $(JARPACKAGE).$(basename $(JARTARGET))

######################################################################
# Build JNI

# Cancel possible built-in make rules so our ones with prerequisites are used
%.o: %.cpp

%.o: %.c

# Build JNI include headers from the Java wrapper classes
$(JNISRC_PATH)/$(JNI_HEADER_PREFIX)%.h : $(JNIJAVASRC_PATH)/%.java
	$(JAVAC) -h $(JNISRC_PATH) $<

# Build JNI .so
# LOCAL_JNI_LIBS must come after $^ otherwise some linker versions discard
# the libraries as unused
$(JNITARGET): $(JNIOBJ)
	$(CXX) $(LOCAL_LDFLAGS) $^ $(LOCAL_JNI_LIBS) -o $@

# Build .d files
%.d: %.cpp $(JNI_HEADERS)
	$(CXX) $(LOCAL_JAVA_C_INCLUDES) $(CONFIGMGRSRC_INCLUDES) $(LOCAL_CXXFLAGS) $(CXXFLAGS) $(LOCAL_GENDEPS_FLAGS) $(INCLUDEDIRS) -o $@ $<

%.d: %.c
	$(CC) $(CONFIGMGRSRC_INCLUDES) $(LOCAL_CFLAGS) $(CFLAGS) $(LOCAL_GENDEPS_FLAGS) $(INCLUDEDIRS) -o $@ $<

# A phony rule that depends on all the .d stops make treating them as
# intermediates and deleting them after the make. Also allows traditional
# 'make depend' invocation.
depend: $(JNIDEP)

# Include only .d files that were generated by other rules otherwise make will
# build all .d files just to satisfy this include
include $(wildcard $(JNIDEP))

# Build .o files from .cpp/.c and also require .d to be built
%.o: %.cpp %.d $(JNI_HEADERS)
	$(CXX) $(LOCAL_JAVA_C_INCLUDES) $(CONFIGMGRSRC_INCLUDES) $(LOCAL_CXXFLAGS) $(CXXFLAGS) $(INCLUDEDIRS) -c -o $@ $<

%.o: %.c %.d
	$(CC) $(CONFIGMGRSRC_INCLUDES) $(LOCAL_CFLAGS) $(CFLAGS) $(INCLUDEDIRS) -c -o $@ $<


clean_jni:
	$(RM) $(JNITARGET) $(JNIOBJ) $(JNIDEP) $(JNI_HEADERS)

######################################################################
# Build test JAR

# Build jar - do it all in one step to make life easier
$(JARTARGET) : $(JARSRC) $(JNIJAVACLASS_SRC)
	$(JAVAC) -cp $(CLASSPATH) -d . $(JARSRC) $(JNIJAVACLASS_SRC)
	$(JAR) -cf $(JARTARGET) $(JARPACKAGE_PATHIZED)/*.class

######################################################################
# Build test Javadoc
javadoc:
	$(JAVADOC) -d $(JAVADOC_OUT_PATH) $(JARSRC) $(JNIJAVACLASS_SRC)

clean_java:
	$(RM) $(JARTARGET)
	$(RM) -rf $(JAR_CLASS_PATH_BASEDIR)
	$(RM) -rf $(JAVADOC_OUT_PATH)
