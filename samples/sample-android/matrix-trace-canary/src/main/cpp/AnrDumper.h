/*
 * Tencent is pleased to support the open source community by making wechat-matrix available.
 * Copyright (C) 2021 THL A29 Limited, a Tencent company. All rights reserved.
 * Licensed under the BSD 3-Clause License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Author: leafjia@tencent.com
//
// AnrDumper.h

#ifndef LAGDETECTOR_LAG_DETECTOR_MAIN_CPP_ANRDUMPER_H_
#define LAGDETECTOR_LAG_DETECTOR_MAIN_CPP_ANRDUMPER_H_

#include "SignalHandler.h"
#include <functional>
#include <string>
#include <optional>
#include <jni.h>

namespace MatrixTracer {

class AnrDumper : public SignalHandler {
 public:
    //定义回调方法
    using DumpCallbackFunction = std::function<bool()>;

    AnrDumper(const char* anrTraceFile, const char* printTraceFile, DumpCallbackFunction&& callback);//&&引用。这个功能是C++的补充，常用在函数传参（C中一般用指针）、临时变量引用等。
    virtual ~AnrDumper();

 private:
    //处理signal地方
    Result handleSignal(int sig, const siginfo_t *info, void *uc) final;
    const DumpCallbackFunction mCallback;
};
}   // namespace MatrixTracer

#endif  // LAGDETECTOR_LAG_DETECTOR_MAIN_CPP_ANRDUMPER_H_