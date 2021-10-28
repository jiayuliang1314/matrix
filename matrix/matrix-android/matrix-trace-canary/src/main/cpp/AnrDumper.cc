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

#include "AnrDumper.h"

#include <dirent.h>
#include <pthread.h>
#include <cxxabi.h>
#include <unistd.h>
#include <syscall.h>
#include <cstdlib>

#include <optional>
#include <cinttypes>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <iosfwd>
#include <string>
#include <fcntl.h>
#include <nativehelper/scoped_utf_chars.h>

#include "nativehelper/scoped_local_ref.h"
#include "MatrixTracer.h"
#include "Logging.h"
#include "Support.h"

#define SIGNAL_CATCHER_THREAD_NAME "Signal Catcher"
#define SIGNAL_CATCHER_THREAD_SIGBLK 0x1000 //得到SignalCatcherThreadId，todo 没看明白
#define O_WRONLY 00000001
#define O_CREAT 00000100
#define O_TRUNC 00001000

namespace MatrixTracer {
    static sigset_t old_sigSet;
    const char *mAnrTraceFile;
    const char *mPrintTraceFile;

//step 0
// 建立了Signal Handler之后，我们发现在同时有sigwait和signal handler的情况下，
// 信号没有走到我们的signal handler而是依然被系统的Signal Catcher线程捕获到了，这是什么原因呢？
//
//原来是Android默认把SIGQUIT设置成了BLOCKED，所以只会响应sigwait而不会进入到我们设置的handler方法中。
// 我们通过pthread_sigmask或者sigprocmask把SIGQUIT设置为UNBLOCK，那么再次收到SIGQUIT时，就一定会进入到我们的handler方法中。需要这样设置：
    AnrDumper::AnrDumper(const char *anrTraceFile, const char *printTraceFile,
                         AnrDumper::DumpCallbackFunction &&callback) : mCallback(callback) {
        // must unblocked SIGQUIT, otherwise the signal handler can not capture SIGQUIT
        // 必须unblock，否则signal handler无法接收到信号，而是由signal_cahcher线程中的sigwait接收信号，走一般的ANR流程
        mAnrTraceFile = anrTraceFile;
        mPrintTraceFile = printTraceFile;
        sigset_t sigSet;
        sigemptyset(&sigSet);
        sigaddset(&sigSet, SIGQUIT);
        pthread_sigmask(SIG_UNBLOCK, &sigSet, &old_sigSet);
    }

    //step 3.1
    //得到SignalCatcherThreadId，todo 没看明白
    static int getSignalCatcherThreadId() {
        char taskDirPath[128];
        DIR *taskDir;
        long long sigblk;
        int signalCatcherTid = -1;
        int firstSignalCatcherTid = -1;

        snprintf(taskDirPath, sizeof(taskDirPath), "/proc/%d/task", getpid());
        if ((taskDir = opendir(taskDirPath)) == nullptr) {
            return -1;
        }
        struct dirent *dent;
        pid_t tid;
        while ((dent = readdir(taskDir)) != nullptr) {
            tid = atoi(dent->d_name);
            if (tid <= 0) {
                continue;
            }

            char threadName[1024];
            char commFilePath[1024];
            snprintf(commFilePath, sizeof(commFilePath), "/proc/%d/task/%d/comm", getpid(), tid);

            Support::readFileAsString(commFilePath, threadName, sizeof(threadName));

            if (strncmp(SIGNAL_CATCHER_THREAD_NAME, threadName,
                        sizeof(SIGNAL_CATCHER_THREAD_NAME) - 1) != 0) {
                continue;
            }

            if (firstSignalCatcherTid == -1) {
                firstSignalCatcherTid = tid;
            }

            sigblk = 0;
            char taskPath[128];
            snprintf(taskPath, sizeof(taskPath), "/proc/%d/status", tid);

            ScopedFileDescriptor fd(open(taskPath, O_RDONLY, 0));
            LineReader lr(fd.get());
            const char *line;
            size_t len;
            while (lr.getNextLine(&line, &len)) {
                if (1 == sscanf(line, "SigBlk: %" SCNx64, &sigblk)) {
                    break;
                }
                lr.popLine(len);
            }
            if (SIGNAL_CATCHER_THREAD_SIGBLK != sigblk) {
                continue;
            }
            signalCatcherTid = tid;
            break;
        }
        closedir(taskDir);

        if (signalCatcherTid == -1) {
            signalCatcherTid = firstSignalCatcherTid;
        }
        return signalCatcherTid;
    }

    //step 3
//我们通过Signal Handler抢到了SIGQUIT后，原本的Signal Catcher线程中的sigwait就不再能收到SIGQUIT了，
// 原本的dump堆栈的逻辑就无法完成了，我们为了ANR的整个逻辑和流程跟原来完全一致，需要在Signal Handler里面重新向Signal Catcher线程发送一个SIGQUIT：
    static void sendSigToSignalCatcher() {
        //遍历/proc/[pid]目录，找到SignalCatcher线程的tid
        int tid = getSignalCatcherThreadId();
        syscall(SYS_tgkill, getpid(), tid, SIGQUIT);
    }

    //step 2.1 SIGQUIT发生了，其他进程发来的，anr是system_server进程发来的消息，不是自己进程发来的
    static void *anrCallback(void *arg) {
        //anr可能发生了，通知SignalAnrTracer检测ui线程是否block或者状态为NOT_RESPONDING
        anrDumpCallback();

        if (strlen(mAnrTraceFile) > 0) {
            //开始hook write socket
            hookAnrTraceWrite(false);
        }
        //转发SIGQUIT
        sendSigToSignalCatcher();
        return nullptr;
    }

    //step 2.2
    //SIGQUIT发生了，自己进程发来的，不是anr
    static void *siUserCallback(void *arg) {
        //这里没有调用anrDumpCallback，因为是自己触发的
        if (strlen(mPrintTraceFile) > 0) {
            //开始hook write socket
            hookAnrTraceWrite(true);
        }
        //转发SIGQUIT
        sendSigToSignalCatcher();
        return nullptr;
    }

//step 1
// 另外，Signal Handler回调的第二个参数siginfo_t，也包含了一些有用的信息，该结构体的第三个字段si_code表示该信号被
// 发送的方法，SI_USER表示信号是通过kill发送的，SI_QUEUE表示信号是通过sigqueue发送的。但在Android的ANR流程中，
// 高版本使用的是sigqueue发送的信号，某些低版本使用的是kill发送的信号，并不统一。
//
//而第五个字段（极少数机型上是第四个字段）si_pid表示的是发送该信号的进程的pid，这里适用几乎所有Android版本和机型的
// 一个条件是：如果发送信号的进程是自己的进程，那么一定不是一个ANR。可以通过这个条件排除自己发送SIGQUIT，
// 而导致误报的情况。
    SignalHandler::Result AnrDumper::handleSignal(int sig, const siginfo_t *info, void *uc) {
        // Only process SIGQUIT, which indicates an ANR.
        if (sig != SIGQUIT) return NOT_HANDLED;
        //Got An ANR
        int fromPid1 = info->_si_pad[3];
        int fromPid2 = info->_si_pad[4];
        int myPid = getpid();

        pthread_t thd;

        if (fromPid1 != myPid && fromPid2 != myPid) {
            //一个条件是：如果发送信号的进程是自己的进程，那么一定不是一个ANR。可以通过这个条件排除自己发送SIGQUIT，
            pthread_create(&thd, nullptr, anrCallback, nullptr);
        } else {
            //自己的进程
            pthread_create(&thd, nullptr, siUserCallback, nullptr);
        }
        pthread_detach(thd);

        return HANDLED_NO_RETRIGGER;
    }

    //没用到
    static void *anr_trace_callback(void *args) {
        anrDumpTraceCallback();
        return nullptr;
    }

    //没用到
    static void *print_trace_callback(void *args) {
        printTraceCallback();
        return nullptr;
    }


    AnrDumper::~AnrDumper() {
        pthread_sigmask(SIG_SETMASK, &old_sigSet, nullptr);
    }

}   // namespace MatrixTracer
