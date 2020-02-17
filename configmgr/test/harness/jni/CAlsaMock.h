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

#include <map>
#include <memory>
#include <string>
#include <vector>

namespace cirrus {

class CMockControl
{
public:
    enum EType {
        eBool,
        eInt,
        eEnum,
        eByte
    };

public:
    CMockControl(unsigned int id,
                 const std::string& name,
                 size_t numElements,
                 bool initialValue);
    CMockControl(unsigned int id,
                 const std::string& name,
                 size_t numElements,
                 int initialValue,
                 int min, int max);
    CMockControl(unsigned int id,
                 const std::string& name,
                 std::vector<uint8_t>& initialData);
    CMockControl(unsigned int id,
                 const std::string& name,
                 std::vector<std::string>& enumStrings,
                 const std::string& initialValue);

    const std::string& name() const { return mName; }

    bool isBool() const { return mType == eBool; }
    bool isInt() const { return mType == eInt; }
    bool isEnum() const { return mType == eEnum; }
    bool isByte() const { return mType == eByte; }
    unsigned int id() const { return mId; }
    size_t numElements() const { return mNumElements; }
    int min() const { return mIntMin; }
    int max() const { return mIntMax; }
    size_t numEnumStrings() const { return mEnumStrings.size(); }
    bool isValidIndex(size_t index) const { return index < mNumElements; }

    int getInt(size_t index) const { return mIntValues[index]; }
    const std::vector<int>& getIntArray() const { return mIntValues; }
    const std::string& getEnum() const { return mEnumStrings[mIntValues[0]]; }
    const std::vector<uint8_t>& getData() const { return mData; }

    void clearChangedFlag() { mChanged = false; }
    bool isChanged() const { return mChanged; }

    int set(size_t index, int value);
    int set(const std::string& value);
    int setArray(const std::vector<int>& values);
    int setArray(const std::vector<uint8_t>& data);

    void dump() const;

private:
    const unsigned int mId;
    const std::string  mName;
    const EType        mType;
    const size_t       mNumElements;
    const int          mIntMin;
    const int          mIntMax;
    const std::vector<std::string> mEnumStrings;

    std::vector<int>  mIntValues;
    std::vector<uint8_t> mData;

    bool        mChanged;
};

class CAlsaMock
{
public:
    CAlsaMock();
    ~CAlsaMock();

    int readFromFile(const std::string& fileName);
    void dump() const;

    size_t numControls() const { return mControls.size(); }
    CMockControl* getControlByName(const std::string& name);
    CMockControl* getControlById(unsigned int id);

private:
    std::map<std::string, std::shared_ptr<CMockControl>> mControls;
    std::vector<std::shared_ptr<CMockControl>> mControlsById;
};

} // namespace cirrus
