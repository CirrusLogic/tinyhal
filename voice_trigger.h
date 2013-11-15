/*
 * Copyright (C) 2012-13 Wolfson Microelectronics plc
 *
 * This code is heavily based on AOSP HAL for the asus/grouper
 *
 * Copyright (C) 2012 The Android Open Source Project
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

#ifndef VOICE_TRIGGER_H
#define VOICE_TRIGGER_H

#if defined(__cplusplus)
#include <sysutils/FrameworkListener.h>
#include <sysutils/FrameworkCommand.h>
#include <utils/Mutex.h>
#include <utils/Condition.h>

class CVoiceTriggerCommand : public FrameworkCommand
{
public:
    CVoiceTriggerCommand();
    virtual ~CVoiceTriggerCommand();

private:
    int runCommand(SocketClient *c, int argc, char **argv);
};

class CVoiceTriggerService : public FrameworkListener
{
public:
    CVoiceTriggerService();
    virtual ~CVoiceTriggerService();

public:
    CVoiceTriggerCommand mTriggerCommand;
};

extern "C" {
#endif

void send_voice_trigger();
int init_voice_trigger_service();

#if defined(__cplusplus)
}
#endif

#endif
