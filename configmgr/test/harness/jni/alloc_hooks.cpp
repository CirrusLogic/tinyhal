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

#define LOG_TAG "tinyhal_test_harness"

#include <cstdarg>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <dirent.h>
#include <map>
#include <mutex>
#include <string>

#include "alloc_hooks.h"

#ifdef ANDROID
#include <utils/Log.h>
#else
#include <exception>
#include "../../../audio_logging.h"
#endif

namespace cirrus {
static std::mutex gAllocSetMutex;
static std::map<void*, int> gAllocMap;
static bool gSawAlloc;

std::string gRedirectedProcPath;

void harnessSetRedirectedProcPath(const std::string& name)
{
    gRedirectedProcPath = name;
}

#ifndef ANDROID
class BadFreeException : public std::exception
{
    const char* what() const throw()
    {
        return "Free of unallocated address";
    }
};

class DoubleAllocException : public std::exception
{
    const char* what() const throw()
    {
        return "Allocated address already allocated";
    }
};

class AllocHooksNotCalledException : public std::exception
{
    const char* what() const throw()
    {
        return "Alloc hooks were not called";
    }
};
#endif

void print_leaked_allocs_l()
{
    for (auto it : gAllocMap) {
        ALOGW("Leaked alloc @%p from line %u", it.first, it.second);
    }
}

bool harness_are_allocs_leaked()
{
    std::lock_guard<std::mutex> _l(gAllocSetMutex);
    if (!gSawAlloc) {
        // A false return would be meaningless if the hooks were never called.
        // This indicates a build error, so throw an exception.
        // Android doesn't support exceptions so do a deathful memory write
#ifdef ANDROID
        *((int*)0) = 0xBADABADA;
#else
        throw AllocHooksNotCalledException();
#endif
    }

    if (gAllocMap.size() != 0) {
        print_leaked_allocs_l();
        return true;
    }

    return false;
}

void remove_address_l(void* p)
{
    if (p == nullptr) {
        return;
    }

    auto found = gAllocMap.find(p);
    if (found == gAllocMap.end()) {
#ifdef ANDROID
        *((int*)0) = 0xBADABADA;
#else
        throw BadFreeException();
#endif
    }
    gAllocMap.erase(found);
}

void add_address_l(void* p, int line)
{
    gSawAlloc = true;

    if (p == nullptr) {
        return;
    }

    auto result = gAllocMap.insert(std::pair<void*, int>(p, line));
    if (!result.second) {
#ifdef ANDROID
        *((int*)0) = 0xBADABADA;
#else
        throw DoubleAllocException();
#endif
    }
}

extern "C" {
void* harness_malloc(size_t n, int line)
{
    void* p = malloc(n);
    add_address_l(p, line);

    return p;
}

void* harness_calloc(size_t n, size_t m, int line)
{
    std::lock_guard<std::mutex> _l(gAllocSetMutex);
    void* p = calloc(n, m);
    add_address_l(p, line);

    return p;
}

void* harness_realloc(void* oldp, size_t n, int line)
{
    std::lock_guard<std::mutex> _l(gAllocSetMutex);
    void* p = realloc(oldp, n);
    if ((p != nullptr) && (p != oldp)) {
        // Allocated address was changed or this is a new alloc (oldp was NULL)
        remove_address_l(oldp);
        add_address_l(p, line);
    }

    return p;
}

char* harness_strdup(const char* olds, int line)
{
    std::lock_guard<std::mutex> _l(gAllocSetMutex);
    char* s = strdup(olds);
    add_address_l(s, line);

    return s;
}

int harness_asprintf(char** dest, const char* fmt, int c, const char* s1, const char* s2, int line)
{
    std::lock_guard<std::mutex> _l(gAllocSetMutex);
    int ret = asprintf(dest, fmt, c, s1, s2);
    if (ret >= 0) {
        add_address_l(*dest, line);
    }

    return ret;
}

void harness_free(void* p)
{
    std::lock_guard<std::mutex> _l(gAllocSetMutex);
    remove_address_l(p);
    free(p);
}

FILE* harness_fopen(const char* name, const char* attr, int line)
{
    std::string newName;

    // This is a hack to redirect /proc/asound/ requests during testing
    if (strstr(name, "/proc/asound") == name) {
        newName = gRedirectedProcPath + name;
        name = newName.c_str();
    }

    FILE* fp = fopen(name, attr);
    int err = errno;

    std::lock_guard<std::mutex> _l(gAllocSetMutex);
    add_address_l(fp, line);

    errno = err;

    return fp;
}

void harness_fclose(FILE* fp)
{
    fclose(fp);

    std::lock_guard<std::mutex> _l(gAllocSetMutex);
    remove_address_l(fp);
}

DIR* harness_opendir(const char* name, int line)
{
    std::string newName;

    // This is a hack to redirect /proc/asound requests during testing
    if (strstr(name, "/proc/asound") == name) {
        newName = gRedirectedProcPath + name;
        name = newName.c_str();
    }

    DIR* dir = opendir(name);
    int err = errno;

    std::lock_guard<std::mutex> _l(gAllocSetMutex);
    add_address_l(dir, line);

    errno = err;

    return dir;
}

void harness_closedir(DIR* dir)
{
    closedir(dir);

    std::lock_guard<std::mutex> _l(gAllocSetMutex);
    remove_address_l(dir);
}

} // extern "C"
} // namespace cirrus

