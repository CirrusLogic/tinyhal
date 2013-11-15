/*
 * Copyright (C) 2013 Wolfson Microelectronics plc
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

#define LOG_TAG "tinyhal-vctrig"
/*#define LOG_NDEBUG 0*/

#include <errno.h>
#include <pthread.h>
#include <stdint.h>
#include <stdlib.h>
#include <sys/time.h>

#include <cutils/log.h>
#include <sysutils/FrameworkListener.h>
#include <sysutils/FrameworkCommand.h>
#include <utils/Mutex.h>
#include <utils/Condition.h>

#include "voice_trigger.h"

static CVoiceTriggerService* TriggerService;

CVoiceTriggerCommand::CVoiceTriggerCommand()
    : FrameworkCommand("wait")
{
}

CVoiceTriggerCommand::~CVoiceTriggerCommand()
{
}

int CVoiceTriggerCommand::runCommand(SocketClient *c, int argc, char **argv)
{
    return 0;
}

CVoiceTriggerService::CVoiceTriggerService()
    : FrameworkListener("voice-trigger", true)
{
    registerCmd(&mTriggerCommand);
}

CVoiceTriggerService::~CVoiceTriggerService()
{
}


// As the rest of TinyHAL is currently written in C we provide a
// C interface to the voice trigger service

extern "C"
void send_voice_trigger()
{
    if (TriggerService != NULL) {
        ALOGV("trigger");
        TriggerService->sendBroadcast(0, "trig", 0);
    }
}

extern "C"
int init_voice_trigger_service()
{
    ALOGV("init_voice_trigger_service");

    CVoiceTriggerService *svc = new CVoiceTriggerService;
    if (svc->startListener() != 0) {
        int ret = errno;
        delete svc;
        return ret;
    }

    TriggerService = svc;
    return 0;
}




