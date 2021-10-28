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
// SignalHandler.h
//ok
#ifndef LAGDETECTOR_LAG_DETECTOR_MAIN_CPP_SIGNALHANDLER_H_
#define LAGDETECTOR_LAG_DETECTOR_MAIN_CPP_SIGNALHANDLER_H_

#include <signal.h>

namespace MatrixTracer {

    class SignalHandler {
    public:
        SignalHandler();

        virtual ~SignalHandler();//析构函数：
//    当一个类的对象离开作用域时，析构函数将被调用(系统自动调用)。析构函数的名字和类名一样，不过要在前面加上 ~ 。
//    对一个类来说，只能允许一个析构函数，析构函数不能有参数，并且也没有返回值。
//    析构函数的作用是完成一个清理工作，如释放从堆中分配的内存。

    protected:
        enum Result {
            NOT_HANDLED = 0, HANDLED, HANDLED_NO_RETRIGGER
        };//retrigger
        virtual Result handleSignal(int sig, const siginfo_t *info, void *uc) = 0;

    private:
        static void signalHandler(int sig, siginfo_t *info, void *uc);

        static bool installHandlersLocked();

        //https://blog.csdn.net/lmb1612977696/article/details/80035487
        SignalHandler(const SignalHandler &) = delete;//禁止生成该函数，默认拷贝构造函数
        SignalHandler &operator=(const SignalHandler &) = delete;//禁止生成该函数，默认赋值函数
    };

}   // namespace MatrixTracer

#endif  // LAGDETECTOR_LAG_DETECTOR_MAIN_CPP_SIGNALHANDLER_H_
