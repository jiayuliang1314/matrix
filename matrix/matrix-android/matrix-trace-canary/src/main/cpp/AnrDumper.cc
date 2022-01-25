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
static const int NATIVE_DUMP_TIMEOUT = 2; // 2 seconds
static sigset_t old_sigSet;
const char* mAnrTraceFile;
const char* mPrintTraceFile;

AnrDumper::AnrDumper(const char* anrTraceFile, const char* printTraceFile) {
    // must unblock SIGQUIT, otherwise the signal handler can not capture SIGQUIT
    mAnrTraceFile = anrTraceFile;
    mPrintTraceFile = printTraceFile;
    sigset_t sigSet;
    sigemptyset(&sigSet);
    sigaddset(&sigSet, SIGQUIT);
    pthread_sigmask(SIG_UNBLOCK, &sigSet , &old_sigSet);
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

static void *anrCallback(void* arg) {
    anrDumpCallback();
    if (strlen(mAnrTraceFile) > 0) {
        hookAnrTraceWrite(false);
    }
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

void* AnrDumper::nativeBacktraceCallback(void* arg) {
    nativeBacktraceDumpCallback();
    restoreNativeBacktraceHandlersLocked();
    sigval val;
    val.sival_int = 1;

    siginfo_t info;
    memset(&info, 0, sizeof(siginfo_t));
    info.si_signo = BIONIC_SIGNAL_DEBUGGER;
    info.si_code = SI_QUEUE;
    info.si_pid = getpid();
    info.si_uid = getuid();
    info.si_value = val;
    syscall(SYS_rt_sigqueueinfo, getpid(), BIONIC_SIGNAL_DEBUGGER, &info);
    sleep(NATIVE_DUMP_TIMEOUT);
    installNativeBacktraceHandlersLocked();
    return nullptr;
}

void AnrDumper::handleSignal(int sig, const siginfo_t *info, void *uc) {
    int fromPid1 = info->_si_pad[3];
    int fromPid2 = info->_si_pad[4];
    int myPid = getpid();
    bool fromMySelf = fromPid1 == myPid || fromPid2 == myPid;
    if (sig == SIGQUIT) {
        pthread_t thd;
        if (!fromMySelf) {
            pthread_create(&thd, nullptr, anrCallback, nullptr);
        } else {
            pthread_create(&thd, nullptr, siUserCallback, nullptr);
        }
        pthread_detach(thd);
    }
}


void AnrDumper::handleDebuggerSignal(int sig, const siginfo_t *info, void *uc) {
    if (sig == BIONIC_SIGNAL_DEBUGGER) {
        int fromPid1 = info->_si_pad[3];
        int fromPid2 = info->_si_pad[4];
        int myPid = getpid();
        bool fromMySelf = fromPid1 == myPid || fromPid2 == myPid;
        if (!fromMySelf) {
            pthread_t thd;
            pthread_create(&thd, nullptr, nativeBacktraceCallback, nullptr);
            pthread_detach(thd);
        }
    }
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
