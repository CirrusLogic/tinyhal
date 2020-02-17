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

#include <algorithm>
#include <fstream>
#include <limits>
#include <map>
#include <memory>
#include <sstream>
#include <string>
#include <vector>

#include <tinyalsa/asoundlib.h>

#ifdef ANDROID
#include <cutils/log.h>
#else
#include "../../../audio_logging.h"
#endif

#include "CAlsaMock.h"

namespace cirrus {

CAlsaMock* gAlsaMock = nullptr;

// Utility function to find the index of an entry in a vector
template<class T>
int findIndex(const std::vector<T>& vec, const T& value)
{
    int i = 0;
    for (const auto& item : vec) {
        if (item == value) {
            return i;
        }
        ++i;
    }

    return -1;
}

CMockControl::CMockControl(unsigned int id,
                           const std::string& name,
                           size_t numElements,
                           bool initialValue)
    : mId(id),
      mName(name),
      mType(eBool),
      mNumElements(numElements),
      mIntMin(0),
      mIntMax(0),
      mIntValues(numElements, static_cast<int>(initialValue)),
      mChanged(false)
{
}

CMockControl::CMockControl(unsigned int id,
                           const std::string& name,
                           size_t numElements,
                           int initialValue,
                           int min,
                           int max)
    : mId(id),
      mName(name),
      mType(eInt),
      mNumElements(numElements),
      mIntMin(min),
      mIntMax(max),
      mIntValues(numElements, static_cast<int>(initialValue)),
      mChanged(false)
{
}

CMockControl::CMockControl(unsigned int id,
                           const std::string& name,
                           std::vector<std::string>& enumStrings,
                           const std::string& initialValue)
    : mId(id),
      mName(name),
      mType(eEnum),
      mNumElements(1),
      mIntMin(0),
      mIntMax(0),
      mEnumStrings(std::move(enumStrings)),
      mIntValues(1, findIndex<std::string>(mEnumStrings, initialValue)),
      mChanged(false)
{
}

CMockControl::CMockControl(unsigned int id,
                           const std::string& name,
                           std::vector<uint8_t>& initialData)
    : mId(id),
      mName(name),
      mType(eByte),
      mNumElements(initialData.size()),
      mIntMin(0),
      mIntMax(0),
      mData(std::move(initialData)),
      mChanged(false)
{
}

int CMockControl::set(size_t index, int value)
{
    if (index >= mIntValues.size()) {
        ALOGE("%s: index %zu out of range (0..%zu)",
              __func__, index, mIntValues.size() - 1);
        return -EINVAL;
    }

    mIntValues[index] = value;
    mChanged = true;

    return 0;
}

int CMockControl::set(const std::string& value)
{
    int index = findIndex(mEnumStrings, value);
    if (index < 0) {
        ALOGE("%s: '%s' not a valid enum", __func__, value.c_str());
        return -EINVAL;
    }

    mIntValues[0] = index;
    mChanged = true;

    return 0;
}

int CMockControl::setArray(const std::vector<int>& values)
{
    if (values.size() > mIntValues.size()) {
        ALOGE("%s: size %zu > maximum %zu", __func__, values.size(), mIntValues.size());
        return -EINVAL;
    }

    std::copy(values.begin(), values.end(), mIntValues.begin());
    mChanged = true;

    return 0;
}

int CMockControl::setArray(const std::vector<uint8_t>& data)
{
    if (data.size() > mData.size()) {
        ALOGE("%s: size %zu > maximum %zu", __func__, data.size(), mData.size());
        return -EINVAL;
    }

    ALOGV("%s: Writing %zu bytes to '%s'", __func__, data.size(), mName.c_str());
    std::copy(data.begin(), data.end(), mData.begin());
    mChanged = true;

    return 0;
}

void CMockControl::dump() const
{
    std::ostringstream s;

    s << "   " << mId << ": ";

    switch(mType) {
    case eBool:
        {
            s << "BOOL: (";
            bool comma = false;
            for (int v : mIntValues) {
                if (comma) {
                    s << ',';
                }
                s << v;
                comma = true;
            }
            s << ')';
        }
        break;

    case eInt:
        {
            s << "INT: (";
            bool comma = false;
            for (int v : mIntValues) {
                if (comma) {
                    s << ',';
                }
                s << v;
                comma = true;
            }
            s << ") min=" << mIntMin << " max=" << mIntMax;
        }
        break;

    case eEnum:
        {
            s << "ENUM: '" << mEnumStrings[mIntValues[0]] << "\' (";
            bool comma = false;
            for (const std::string& str : mEnumStrings) {
                if (comma) {
                    s << ',';
                }
                s << str;
                comma = true;
            }
            s << ')';
        }
        break;

    case eByte:
        {
            s << "BYTE: (" << std::hex;
            bool comma = false;
            for (uint8_t v : mData) {
                if (comma) {
                    s << ',';
                }
                s << static_cast<unsigned int>(v);
                comma = true;
            }
            s << ')';
        }
        break;

    default:
        break;
    }

    ALOGV("%s", s.str().c_str());
}

CAlsaMock::CAlsaMock()
{
    gAlsaMock = this;
}

CAlsaMock::~CAlsaMock()
{
}

static int getValueSetField(std::istringstream& sstr, std::vector<std::string>& v)
{
    std::string field;
    if (!std::getline(sstr, field, ',')) {
        return -EINVAL;
    }
    std::istringstream sitems(field);
    std::string evalue;
    while(std::getline(sitems, evalue, ':')) {
        v.emplace_back(evalue);
    }

    return 0;
}

int CAlsaMock::readFromFile(const std::string& fileName)
{
    std::ifstream fin(fileName);

    if (!fin) {
        ALOGE("%s: failed to open '%s'", __func__, fileName.c_str());
        return -EINVAL;
    }

    unsigned int mockControlId = 0;
    std::string line;
    int lineNum = 1;

    while(std::getline(fin, line)) {
        std::istringstream sstr(line);
        std::string name;
        if (!std::getline(sstr, name, ',')) {
            ALOGE("%s: ERROR on line %d: could not read name field",
                  __func__, lineNum);
            return -EINVAL;
        }

        std::string type;
        if (!std::getline(sstr, type, ',')) {
            ALOGE("%s: ERROR on line %d: could not read type field",
                  __func__, lineNum);
            return -EINVAL;
        }

        std::string elemStr;
        if (!std::getline(sstr, elemStr, ',')) {
            ALOGE("%s: ERROR on line %d: could not read numElements field",
                  __func__, lineNum);
            return -EINVAL;
        }

        size_t numElements = static_cast<size_t>(std::stoul(elemStr, 0, 0));

        std::string initialValue;
        if (!std::getline(sstr, initialValue, ',')) {
            ALOGE("%s: ERROR on line %d: could not read initial value field",
                  __func__, lineNum);
            return -EINVAL;
        }

        if (type == "bool") {
            bool boolVal = std::stoi(initialValue, 0, 0);
            auto c = std::make_shared<CMockControl>(mockControlId,
                                                    name,
                                                    numElements,
                                                    boolVal);
            mControls.emplace(name, c);
        } else if (type == "int") {
            std::vector<std::string> intRange;
            if (getValueSetField(sstr, intRange) < 0) {
                ALOGE("%s: ERROR on line %d: could not read value set field",
                      __func__, lineNum);
                return -EINVAL;
            }
            int min = 0;
            int max = 0xFFFFFF;
            if (intRange.size() > 0) {
                if (intRange.size() != 2) {
                    ALOGE("%s: ERROR on line %d: int value set field must have zero or two entries",
                          __func__, lineNum);
                    return -EINVAL;
                }
                min = std::stoi(intRange[0], 0, 0);
                max = std::stoi(intRange[1], 0, 0);
            }
            auto c = std::make_shared<CMockControl>(mockControlId,
                                                    name,
                                                    numElements,
                                                    std::stoi(initialValue, 0, 0),
                                                    min,
                                                    max);
            mControls.emplace(name, c);
        } else if (type == "enum") {
            std::vector<std::string> enumStrings;
            if (getValueSetField(sstr, enumStrings) < 0) {
                ALOGE("%s: ERROR on line %d: could not read value set field",
                      __func__, lineNum);
                return -EINVAL;
            }
            auto c = std::make_shared<CMockControl>(mockControlId,
                                                    name,
                                                    enumStrings,
                                                    initialValue);
            mControls.emplace(name, c);
        } else if (type == "byte") {
            // init byte control to all elements = initialValue
            std::vector<uint8_t> data(numElements, std::stoi(initialValue, 0, 0));
            auto c = std::make_shared<CMockControl>(mockControlId, name, data);
            mControls.emplace(name ,c);
        } else {
            ALOGE("%s: ERROR on line %d: '%s' not a valid type",
                  __func__, lineNum, type.c_str());
            return -EINVAL;
        }

        ++lineNum;
        ++mockControlId;
    }

    mControlsById.resize(mockControlId);
    for (auto iter : mControls) {
        auto c = iter.second;
        mControlsById[c->id()] = c;
    }

    return 0;
}

void CAlsaMock::dump() const
{
    for (const auto& ctl : mControls) {
        ALOGV("Control '%s':", ctl.second->name().c_str());
        ctl.second->dump();
    }

    ALOGV("%zu controls", mControlsById.size());
}

CMockControl* CAlsaMock::getControlByName(const std::string& name)
{
    auto iter = mControls.find(name);
    if (iter == mControls.end()) {
        return nullptr;
    }

    return iter->second.get();
}

CMockControl* CAlsaMock::getControlById(unsigned int id)
{
    if (id >= mControlsById.size()) {
        return nullptr;
    }

    auto c = mControlsById[id];

    return mControlsById[id].get();
}

extern "C" {
struct mixer *mixer_open(unsigned int card)
{
    (void)card;

    return reinterpret_cast<struct mixer*>(gAlsaMock);
}

void mixer_close(struct mixer *mixer)
{
    (void)mixer;
}

const char *mixer_get_name(struct mixer *mixer)
{
    (void)mixer;

    return "Mock mixer";
}

unsigned int mixer_get_num_ctls(struct mixer *mixer)
{
    (void)mixer;

    if (gAlsaMock == nullptr) {
        return 0;
    }

    return gAlsaMock->numControls();
}

struct mixer_ctl *mixer_get_ctl(struct mixer *mixer, unsigned int id)
{
    (void)mixer;

    if (gAlsaMock == nullptr) {
        return nullptr;
    }

    auto* c = gAlsaMock->getControlById(id);
    return reinterpret_cast<struct mixer_ctl*>(c);
}

struct mixer_ctl *mixer_get_ctl_by_name(struct mixer *mixer, const char *name)
{
    (void)mixer;

    if (gAlsaMock == nullptr) {
        return nullptr;
    }

    auto* c = gAlsaMock->getControlByName(name);
    return reinterpret_cast<struct mixer_ctl*>(c);
}

const char *mixer_ctl_get_name(struct mixer_ctl *ctl)
{
    if (gAlsaMock == nullptr) {
        return nullptr;
    }

    auto* c = reinterpret_cast<CMockControl*>(ctl);

    return c->name().c_str();
}

enum mixer_ctl_type mixer_ctl_get_type(struct mixer_ctl *ctl)
{
    if (gAlsaMock == nullptr) {
        return MIXER_CTL_TYPE_UNKNOWN;
    }

    auto* c = reinterpret_cast<CMockControl*>(ctl);
    if (c->isBool()) {
        return MIXER_CTL_TYPE_BOOL;
    }

    if (c->isInt()) {
        return MIXER_CTL_TYPE_INT;
    }

    if (c->isEnum()) {
        return MIXER_CTL_TYPE_ENUM;
    }

    if (c->isByte()) {
        return MIXER_CTL_TYPE_BYTE;
    }

    return MIXER_CTL_TYPE_UNKNOWN;
}

unsigned int mixer_ctl_get_id(const struct mixer_ctl *ctl)
{
    if (gAlsaMock == nullptr) {
        return 0;
    }

    auto* c = reinterpret_cast<const CMockControl*>(ctl);
    return c->id();
}

unsigned int mixer_ctl_get_num_values(struct mixer_ctl *ctl)
{
    if (gAlsaMock == nullptr) {
        return MIXER_CTL_TYPE_UNKNOWN;
    }

    auto* c = reinterpret_cast<CMockControl*>(ctl);
    return c->numElements();
}

unsigned int mixer_ctl_get_num_enums(struct mixer_ctl *ctl)
{
    if (gAlsaMock == nullptr) {
        return MIXER_CTL_TYPE_UNKNOWN;
    }

    auto* c = reinterpret_cast<CMockControl*>(ctl);
    if (!c->isEnum()) {
        return 0;
    }

    return c->numEnumStrings();
}

int mixer_ctl_get_value(struct mixer_ctl *ctl, unsigned int id)
{
    if (gAlsaMock == nullptr) {
        return MIXER_CTL_TYPE_UNKNOWN;
    }

    auto* c = reinterpret_cast<CMockControl*>(ctl);
    return c->getInt(id);
}

int mixer_ctl_get_array(struct mixer_ctl *ctl, void *array, size_t count)
{
    if (gAlsaMock == nullptr) {
        return MIXER_CTL_TYPE_UNKNOWN;
    }

    auto* c = reinterpret_cast<CMockControl*>(ctl);
    if (count > c->numElements()) {
        errno = EINVAL;
        return -EINVAL;
    }


    if (c->isByte()) {
        auto* p8 = reinterpret_cast<uint8_t*>(array);
        size_t num = std::min(count, c->numElements());
        const auto& src = c->getData();
        std::copy_n(src.begin(), num, p8);
        return 0;
    }

    errno = EINVAL;
    return -EINVAL;
}

int mixer_ctl_set_value(struct mixer_ctl *ctl, unsigned int id, int value)
{
    if (gAlsaMock == nullptr) {
        return MIXER_CTL_TYPE_UNKNOWN;
    }

    auto* c = reinterpret_cast<CMockControl*>(ctl);
    if (!c->isValidIndex(id)) {
        errno = EINVAL;
        return -EINVAL;
    }

    if (c->isBool()) {
        value = !!value; // emulate tinyalsa
    }

    return c->set(id, value);
}

int mixer_ctl_set_array(struct mixer_ctl *ctl, const void *array, size_t count)
{
    if (gAlsaMock == nullptr) {
        return MIXER_CTL_TYPE_UNKNOWN;
    }

    auto* c = reinterpret_cast<CMockControl*>(ctl);
    if (count > c->numElements()) {
        ALOGE("%s: '%s' write %zu bytes > max size %zu",
              __func__, c->name().c_str(), count, c->numElements());
        errno = EINVAL;
        return -EINVAL;
    }


    if (c->isByte()) {
        auto* p8 = reinterpret_cast<const uint8_t*>(array);
        size_t num = std::min(count, c->numElements());
        std::vector<uint8_t> v(p8, p8 + num);
        return c->setArray(v);
    }

    ALOGE("%s: '%s' not a byte control", __func__, c->name().c_str());
    errno = EINVAL;
    return -EINVAL;
}

int mixer_ctl_set_enum_by_string(struct mixer_ctl *ctl, const char *str)
{
    if (gAlsaMock == nullptr) {
        return MIXER_CTL_TYPE_UNKNOWN;
    }

    auto* c = reinterpret_cast<CMockControl*>(ctl);
    return c->set(std::string(str));
}

int mixer_ctl_get_range_min(struct mixer_ctl *ctl)
{
    if (gAlsaMock == nullptr) {
        return MIXER_CTL_TYPE_UNKNOWN;
    }

    auto* c = reinterpret_cast<CMockControl*>(ctl);
    if (!c->isInt()) {
        ALOGE("%s: '%s' not an int control", __func__, c->name().c_str());
        errno = EINVAL;
        return -EINVAL;
    }

    return c->min();
}

int mixer_ctl_get_range_max(struct mixer_ctl *ctl)
{
    if (gAlsaMock == nullptr) {
        return MIXER_CTL_TYPE_UNKNOWN;
    }

    auto* c = reinterpret_cast<CMockControl*>(ctl);
    if (!c->isInt()) {
        ALOGE("%s: '%s' not an int control", __func__, c->name().c_str());
        errno = EINVAL;
        return -EINVAL;
    }

    return c->max();
}

} // extern "C"

} // namespace cirrus
