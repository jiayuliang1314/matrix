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
// SignalHandler.cpp

#include "SignalHandler.h"

#include <malloc.h>
#include <syscall.h>
#include <dirent.h>
#include <unistd.h>

#include <mutex>
#include <vector>
#include <algorithm>
#include <cinttypes>

#include "Logging.h"
#include "Support.h"
//线程名字，todo，得到SignalCatcherThreadId，todo 没看明白
#define SIGNAL_CATCHER_THREAD_NAME "Signal Catcher"
//退出线程标记，todo，得到SignalCatcherThreadId，todo 没看明白
#define SIGNAL_CATCHER_THREAD_SIGBLK 0x1000

namespace MatrixTracer {
//信号
    const int TARGET_SIG = SIGQUIT;//3
//使用sigaction方法注册signal handler进行异步监听，sOldHandlers是保存老的sigaction
    struct sigaction sOldHandlers;//todo
    bool sHandlerInstalled = false;

// The global signal handler stack. This is needed because there may exist
// multiple SignalHandler instances in a process. Each will have itself
// registered in this stack.
//  全局信号处理程序堆栈。 这是必需的，因为一个进程中可能存在多个 SignalHandler 实例。 每个都将在此堆栈中注册自己。
    static std::vector<SignalHandler *> *sHandlerStack = nullptr;//todo
// C++11中新增了<mutex>，它是C++标准程序库中的一个头文件，定义了C++11标准中的一些互斥访问的类与方法等。
// 其中std::mutex就是lock、unlock。std::lock_guard与std::mutex配合使用，把锁放到lock_guard中时，
// mutex自动上锁，lock_guard析构时，同时把mutex解锁。mutex又称互斥量。
    static std::mutex sHandlerStackMutex;//todo
    static bool sStackInstalled = false;
// InstallAlternateStackLocked will store the newly installed stack in new_stack
// and (if it exists) the previously installed stack in old_stack.
    static stack_t sOldStack;//todo
    static stack_t sNewStack;//todo

    //step 2
    static void installAlternateStackLocked() {//todo
        if (sStackInstalled)
            return;
        //重置
        memset(&sOldStack, 0, sizeof(sOldStack));
        memset(&sNewStack, 0, sizeof(sNewStack));
        static constexpr unsigned kSigStackSize = std::max(16384, SIGSTKSZ);
        //取到老的sOldStack
        if (sigaltstack(nullptr, &sOldStack) == -1 || !sOldStack.ss_sp ||
            sOldStack.ss_size < kSigStackSize) {
            sNewStack.ss_sp = calloc(1, kSigStackSize);
            sNewStack.ss_size = kSigStackSize;
            //设置新的sNewStack
            if (sigaltstack(&sNewStack, nullptr) == -1) {
                free(sNewStack.ss_sp);
                return;
            }
        }

        sStackInstalled = true;
        ALOGV("Alternative stack installed.");
    }

//  step 3 Runs before crashing: normal context.
//    我们通过可以sigaction方法，建立一个Signal Handler：ok
    bool SignalHandler::installHandlersLocked() {
        if (sHandlerInstalled) {
            return false;
        }
        // Fail if unable to store all the old handlers.
        //取到老的sOldHandlers
        if (sigaction(TARGET_SIG, nullptr, &sOldHandlers) == -1) {
            return false;
        }

        struct sigaction sa{};//sigaction结构体
        sa.sa_sigaction = signalHandler;//方法地址，收到信号的地方
        sa.sa_flags = SA_ONSTACK | SA_SIGINFO | SA_RESTART;
        //我们通过可以sigaction方法，建立一个Signal Handler
        if (sigaction(TARGET_SIG, &sa, nullptr) == -1) {//sigaction方法，将sa设置为Signal Handler
            ALOGV("Signal handler cannot be installed");

            // At this point it is impractical to back out changes, and so failure to
            // install a signal is intentionally ignored.
        }

        sHandlerInstalled = true;
        ALOGV("Signal handler installed.");
        return true;
    }

    //todo
    static void installDefaultHandler(int sig) {

        // Android L+ expose signal and sigaction symbols that override the system
        // ones. There is a bug in these functions where a request to set the handler
        // to SIG_DFL is ignored. In that case, an infinite loop is entered as the
        // signal is repeatedly sent to breakpad's signal handler.
        // To work around this, directly call the system's sigaction.
        struct sigaction sa;
        memset(&sa, 0, sizeof(sa));
        sigemptyset(&sa.sa_mask);
        sa.sa_handler = SIG_DFL;
        sa.sa_flags = SA_RESTART;
        sigaction(sig, &sa, nullptr);
    }

// This function runs in a compromised context: see the top of the file.
// Runs on the crashing thread.
    //step 6
    static void restoreHandlersLocked() {//todo
        if (!sHandlerInstalled)
            return;
        //将老的sOldHandlers重新sigaction上
        if (sigaction(TARGET_SIG, &sOldHandlers, nullptr) == -1) {
            //todo
            installDefaultHandler(TARGET_SIG);
        }

        sHandlerInstalled = false;
        ALOGV("Signal handler restored.");
    }

    //step 5
    static void restoreAlternateStackLocked() {//todo
        if (!sStackInstalled)
            return;

        stack_t current_stack;
        if (sigaltstack(nullptr, &current_stack) == -1)
            return;
        // Only restore the old_stack if the current alternative stack is the one
        // installed by the call to InstallAlternateStackLocked.
        if (current_stack.ss_sp == sNewStack.ss_sp) {
            if (sOldStack.ss_sp) {
                if (sigaltstack(&sOldStack, nullptr) == -1)
                    return;
            } else {
                stack_t disable_stack;
                disable_stack.ss_flags = SS_DISABLE;
                if (sigaltstack(&disable_stack, nullptr) == -1)
                    return;
            }
        }

        free(sNewStack.ss_sp);
        sStackInstalled = false;
    }

// This function runs in a compromised context: see the top of the file.
// Runs on the crashing thread.
// step 1.1 发生信号处理的地方，转发给各sHandlerStack的handleSignal ok
    void SignalHandler::signalHandler(int sig, siginfo_t *info, void *uc) {
        ALOGV("Entered signal handler.");
// All the exception signals are blocked at this point.
        std::unique_lock<std::mutex> lock(sHandlerStackMutex);

        for (auto it = sHandlerStack->rbegin(); it != sHandlerStack->rend(); ++it) {
            (*it)->handleSignal(sig, info, uc);
        }

        lock.unlock();
    }

    //step 1
    SignalHandler::SignalHandler() {
        //上锁
        std::lock_guard<std::mutex> lock(sHandlerStackMutex);

        //建一个sHandlerStack
        if (!sHandlerStack)
            sHandlerStack = new std::vector<SignalHandler *>;

        //todo
        installAlternateStackLocked();
        //todo 安装新的signalhandler
        installHandlersLocked();
        //将自己放进去
        sHandlerStack->push_back(this);
    }

    //step 4
    SignalHandler::~SignalHandler() {
        std::lock_guard<std::mutex> lock(sHandlerStackMutex);

        auto it = std::find(sHandlerStack->begin(), sHandlerStack->end(), this);
        sHandlerStack->erase(it);
        if (sHandlerStack->empty()) {
            delete sHandlerStack;
            sHandlerStack = nullptr;
            restoreAlternateStackLocked();
            restoreHandlersLocked();
        }
    }

}   // namespace MatrixTracer
