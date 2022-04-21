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
// MatrixTracer.cpp

#include "MatrixTracer.h"

#include <jni.h>
#include <sys/utsname.h>
#include <unistd.h>
#include <pthread.h>
#include <android/log.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <xhook_ext.h>
#include <linux/prctl.h>
#include <sys/prctl.h>
#include <sys/resource.h>

#include <cstdio>
#include <ctime>
#include <csignal>
#include <thread>
#include <memory>
#include <string>
#include <optional>
#include <sstream>
#include <fstream>

#include "Logging.h"
#include "Support.h"
#include "nativehelper/managed_jnienv.h"
#include "nativehelper/scoped_utf_chars.h"
#include "nativehelper/scoped_local_ref.h"
#include "AnrDumper.h"
#include "TouchEventTracer.h"

#define PROP_VALUE_MAX  92
#define PROP_SDK_NAME "ro.build.version.sdk"
#define HOOK_CONNECT_PATH "/dev/socket/tombstoned_java_trace"
#define HOOK_OPEN_PATH "/data/anr/traces.txt"
#define VALIDATE_RET 50

#define HOOK_REQUEST_GROUPID_THREAD_PRIO_TRACE 0x01
#define HOOK_REQUEST_GROUPID_TOUCH_EVENT_TRACE 0x07

using namespace MatrixTracer;
using namespace std;

static std::optional<AnrDumper> sAnrDumper;
static bool isTraceWrite = false;
static bool fromMyPrintTrace = false;
static bool isHooking = false;
static std::string anrTracePathString;
static std::string printTracePathString;
static int signalCatcherTid;
static int currentTouchFd;
static bool inputHasSent;

//一个结构体，用来保存java层 类，方法地址
static struct StacktraceJNI {
    jclass AnrDetective;
    jclass ThreadPriorityDetective;
    jclass TouchEventLagTracer;
    jmethodID AnrDetector_onANRDumped;
    jmethodID AnrDetector_onANRDumpTrace;
    jmethodID AnrDetector_onPrintTrace;

    jmethodID AnrDetector_onNativeBacktraceDumped;

    jmethodID ThreadPriorityDetective_onMainThreadPriorityModified;
    jmethodID ThreadPriorityDetective_onMainThreadTimerSlackModified;

    jmethodID TouchEventLagTracer_onTouchEvenLag;
    jmethodID TouchEventLagTracer_onTouchEvenLagDumpTrace;
} gJ;

//region MainThreadPriorityModified相关的东西
//原来的方法句柄
int (*original_setpriority)(int __which, id_t __who, int __priority);

int my_setpriority(int __which, id_t __who, int __priority) {

    if ((__who == 0 && getpid() == gettid()) || __who == getpid()) {
        int priorityBefore = getpriority(__which, __who);
        JNIEnv *env = JniInvocation::getEnv();
        env->CallStaticVoidMethod(gJ.ThreadPriorityDetective, gJ.ThreadPriorityDetective_onMainThreadPriorityModified, priorityBefore, __priority);
    }

    return original_setpriority(__which, __who, __priority);
}

int (*original_prctl)(int option, unsigned long arg2, unsigned long arg3,
                      unsigned long arg4, unsigned long arg5);

//我们还hook了设置TimerSlack的prctl方法，确保主线程的TimerSlack值不被设置的过大：
int my_prctl(int option, unsigned long arg2, unsigned long arg3,
             unsigned long arg4, unsigned long arg5) {

    if (option == PR_SET_TIMERSLACK) {
        if (gettid() == getpid() && arg2 > 50000) {
            JNIEnv *env = JniInvocation::getEnv();
            env->CallStaticVoidMethod(gJ.ThreadPriorityDetective, gJ.ThreadPriorityDetective_onMainThreadTimerSlackModified, arg2);
        }
    }

    return original_prctl(option, arg2, arg3, arg4, arg5);
}
//endregion

/**
 *
 * @param content 内容
 * @param filePath 文件地址
 *
 * step 4.2.1
 */
void writeAnr(const std::string &content, const std::string &filePath) {
    ALOGV("native writeAnr");
    //unhook write
    unHookAnrTraceWrite();
    std::string to;
    std::ofstream outfile;
    outfile.open(filePath);
    outfile << content;
}

//region step 4.1 my_connect  original_connect
int (*original_connect)(int __fd, const struct sockaddr *__addr, socklen_t __addr_length);

int my_connect(int __fd, const struct sockaddr *__addr, socklen_t __addr_length) {
    if (__addr != nullptr) {
        //hook connect方法，检测sockaddr地址是否为HOOK_CONNECT_PATH，表明是signal检测线程
        if (strcmp(__addr->sa_data, HOOK_CONNECT_PATH) == 0) {
            //设置signal检测线程id
            signalCatcherTid = gettid();
            //标记开始打印
            isTraceWrite = true;
        }
    }
    return original_connect(__fd, __addr, __addr_length);
}
//endregion

//region step 4.1 my_open original_open
int (*original_open)(const char *pathname, int flags, mode_t mode);

int my_open(const char *pathname, int flags, mode_t mode) {
    if (pathname != nullptr) {
        //hook connect方法，检测sockaddr地址是否为HOOK_OPEN_PATH，表明是signal检测线程
        if (strcmp(pathname, HOOK_OPEN_PATH) == 0) {
            //设置signal检测线程id
            signalCatcherTid = gettid();
            //标记开始打印
            isTraceWrite = true;
        }
    }
    return original_open(pathname, flags, mode);
}
//endregion

//region step 4.2 original_write my_write
ssize_t (*original_write)(int fd, const void *const __pass_object_size0 buf, size_t count);

ssize_t my_write(int fd, const void *const buf, size_t count) {
    ALOGV("native my_write");
    //如果标记为isTraceWrite为true，在signalCatcher线程第一个write调用即为打印trace的地方
    if (isTraceWrite && gettid() == signalCatcherTid) {
        isTraceWrite = false;
        signalCatcherTid = 0;
        if (buf != nullptr) {
            std::string targetFilePath;
            if (fromMyPrintTrace) {
                targetFilePath = printTracePathString;
            } else {
                targetFilePath = anrTracePathString;
            }
            if (!targetFilePath.empty()) {
                char *content = (char *) buf;

                writeAnr(content, targetFilePath);
                if (!fromMyPrintTrace) {
                    anrDumpTraceCallback();
                } else {
                    printTraceCallback();
                }
                fromMyPrintTrace = false;
            }
        }
    }
    return original_write(fd, buf, count);
}
//endregion

void onTouchEventLag(int fd) {
    JNIEnv *env = JniInvocation::getEnv();
    if (!env) return;
    env->CallStaticVoidMethod(gJ.TouchEventLagTracer, gJ.TouchEventLagTracer_onTouchEvenLag, fd);
}

void onTouchEventLagDumpTrace(int fd) {
    JNIEnv *env = JniInvocation::getEnv();
    if (!env) return;
    env->CallStaticVoidMethod(gJ.TouchEventLagTracer, gJ.TouchEventLagTracer_onTouchEvenLagDumpTrace, fd);
}

ssize_t (*original_recvfrom)(int sockfd, void *buf, size_t len, int flags,
                             struct sockaddr *src_addr, socklen_t *addrlen);
ssize_t my_recvfrom(int sockfd, void *buf, size_t len, int flags,
                    struct sockaddr *src_addr, socklen_t *addrlen) {
    long ret = original_recvfrom(sockfd, buf, len, flags, src_addr, addrlen);

    if (currentTouchFd == sockfd && inputHasSent && ret > VALIDATE_RET) {
        TouchEventTracer::touchRecv(sockfd);
    }

    if (currentTouchFd != sockfd) {
        TouchEventTracer::touchSendFinish(sockfd);
    }

    if (ret > 0) {
        currentTouchFd = sockfd;
    } else if (ret == 0) {
        TouchEventTracer::touchSendFinish(sockfd);
    }
    return ret;
}

ssize_t (*original_sendto)(int sockfd, const void *buf, size_t len, int flags,
                           const struct sockaddr *dest_addr, socklen_t addrlen);
ssize_t my_sendto(int sockfd, const void *buf, size_t len, int flags,
                  const struct sockaddr *dest_addr, socklen_t addrlen) {

    long ret = original_sendto(sockfd, buf, len, flags, dest_addr, addrlen);
    if (ret >= 0) {
        inputHasSent = true;
        TouchEventTracer::touchSendFinish(sockfd);
    }
    return ret;
}

bool anrDumpCallback() {
    JNIEnv *env = JniInvocation::getEnv();
    if (!env) return false;
    env->CallStaticVoidMethod(gJ.AnrDetective, gJ.AnrDetector_onANRDumped);
    return true;
}

//调用java的onANRDumpTrace，my_write里调用
//step 4.2.2
bool anrDumpTraceCallback() {
    JNIEnv *env = JniInvocation::getEnv();
    if (!env) return false;
    env->CallStaticVoidMethod(gJ.AnrDetective, gJ.AnrDetector_onANRDumpTrace);
    return true;
}

bool nativeBacktraceDumpCallback() {
    JNIEnv *env = JniInvocation::getEnv();
    if (!env) return false;
    env->CallStaticVoidMethod(gJ.AnrDetective, gJ.AnrDetector_onNativeBacktraceDumped);
    std::string to;
    std::ofstream outfile;
    return true;
}


bool printTraceCallback() {
    JNIEnv *env = JniInvocation::getEnv();
    if (!env) return false;
    env->CallStaticVoidMethod(gJ.AnrDetective, gJ.AnrDetector_onPrintTrace);
    return true;
}

//ok
int getApiLevel() {
    char buf[PROP_VALUE_MAX];
    int len = __system_property_get(PROP_SDK_NAME, buf);
    if (len <= 0)
        return 0;

    return atoi(buf);
}

/**
 * step 4 @param isSiUser true为自己的进程
 * AnrDumper.cc 里handleSignal里调用anrCallback方法，或者调用siUserCallback，然后调用这个hookAnrTraceWrite回调
 */
void hookAnrTraceWrite(bool isSiUser) {
    ALOGV("native hookAnrTraceWrite");
    int apiLevel = getApiLevel();
    if (apiLevel < 19) {
        return;
    }

    //isSiUser为true，表示自己进程发的，通过kill发的，但是标记为fromMyPrintTrace却为false，此处不符合逻辑，返回
    if (!fromMyPrintTrace && isSiUser) {
        return;
    }

    if (isHooking) {
        return;
    }

    isHooking = true;

    if (apiLevel >= 27) {
        void *libcutils_info = xhook_elf_open("/system/lib64/libcutils.so");
        if (!libcutils_info) {
            libcutils_info = xhook_elf_open("/system/lib/libcutils.so");
        }
        xhook_got_hook_symbol(libcutils_info, "connect", (void *) my_connect,
                              (void **) (&original_connect));
    } else {
        void *libart_info = xhook_elf_open("libart.so");
        xhook_got_hook_symbol(libart_info, "open", (void *) my_open, (void **) (&original_open));
    }

    if (apiLevel >= 30 || apiLevel == 25 || apiLevel == 24) {
        void *libc_info = xhook_elf_open("libc.so");
        xhook_got_hook_symbol(libc_info, "write", (void *) my_write, (void **) (&original_write));
    } else if (apiLevel == 29) {
        void *libbase_info = xhook_elf_open("/system/lib64/libbase.so");
        if (!libbase_info) {
            libbase_info = xhook_elf_open("/system/lib/libbase.so");
        }
        xhook_got_hook_symbol(libbase_info, "write", (void *) my_write,
                              (void **) (&original_write));
        xhook_elf_close(libbase_info);
    } else {
        void *libart_info = xhook_elf_open("libart.so");
        xhook_got_hook_symbol(libart_info, "write", (void *) my_write, (void **) (&original_write));
    }
    ALOGV("native hookAnrTraceWrite success");
}

//unhook
void unHookAnrTraceWrite() {
    int apiLevel = getApiLevel();
    if (apiLevel >= 27) {
        void *libcutils_info = xhook_elf_open("/system/lib64/libcutils.so");
        xhook_got_hook_symbol(libcutils_info, "connect", (void *) original_connect, nullptr);
    } else {
        void *libart_info = xhook_elf_open("libart.so");
        xhook_got_hook_symbol(libart_info, "open", (void *) original_connect, nullptr);
    }

    if (apiLevel >= 30 || apiLevel == 25 || apiLevel == 24) {
        void *libc_info = xhook_elf_open("libc.so");
        xhook_got_hook_symbol(libc_info, "write", (void *) original_write, nullptr);
    } else if (apiLevel == 29) {
        void *libbase_info = xhook_elf_open("/system/lib64/libbase.so");
        xhook_got_hook_symbol(libbase_info, "write", (void *) original_write, nullptr);
    } else {
        void *libart_info = xhook_elf_open("libart.so");
        xhook_got_hook_symbol(libart_info, "write", (void *) original_write, nullptr);
    }
    isHooking = false;
}

static void nativeInitSignalAnrDetective(JNIEnv *env, jclass, jstring anrTracePath, jstring printTracePath) {
    const char* anrTracePathChar = env->GetStringUTFChars(anrTracePath, nullptr);
    const char* printTracePathChar = env->GetStringUTFChars(printTracePath, nullptr);
    anrTracePathString = std::string(anrTracePathChar);
    printTracePathString = std::string(printTracePathChar);
    sAnrDumper.emplace(anrTracePathChar, printTracePathChar);
}

static void nativeChangeAnrPath(JNIEnv *env, jclass, jstring anrTracePath, jstring printTracePath) {
    const char* anrTracePathChar = env->GetStringUTFChars(anrTracePath, nullptr);
    const char* printTracePathChar = env->GetStringUTFChars(printTracePath, nullptr);
    anrTracePathString = std::string(anrTracePathChar);
    printTracePathString = std::string(printTracePathChar);
    sAnrDumper->changeFile(anrTracePathChar, printTracePathChar);
}

//Free step 6 Signal Anr Detective 重置，释放
static void nativeFreeSignalAnrDetective(JNIEnv *env, jclass) {
    //重置，释放
    sAnrDumper.reset();
}

//region MainThreadPriority相关 ，先不看
static void nativeInitMainThreadPriorityDetective(JNIEnv *env, jclass) {
    //setpriority是修改priority的
    xhook_grouped_register(HOOK_REQUEST_GROUPID_THREAD_PRIO_TRACE, ".*\\.so$", "setpriority",
                           (void *) my_setpriority, (void **) (&original_setpriority));
    //修改TimerSlack的prctl方法
    xhook_grouped_register(HOOK_REQUEST_GROUPID_THREAD_PRIO_TRACE, ".*\\.so$", "prctl",
                           (void *) my_prctl, (void **) (&original_prctl));
    xhook_refresh(true);
}

static void nativeInitTouchEventLagDetective(JNIEnv *env, jclass, jint threshold) {
    xhook_grouped_register(HOOK_REQUEST_GROUPID_TOUCH_EVENT_TRACE, ".*libinput\\.so$", "__sendto_chk",
                           (void *) my_sendto, (void **) (&original_sendto));
    xhook_grouped_register(HOOK_REQUEST_GROUPID_TOUCH_EVENT_TRACE, ".*libinput\\.so$", "sendto",
                           (void *) my_sendto, (void **) (&original_sendto));
    xhook_grouped_register(HOOK_REQUEST_GROUPID_TOUCH_EVENT_TRACE, ".*libinput\\.so$", "recvfrom",
                           (void *) my_recvfrom, (void **) (&original_recvfrom));
    xhook_refresh(true);

    TouchEventTracer::start(threshold);

}

//step 5 自己打印trace，发送自己的进程发送SIGQUIT
static void nativePrintTrace() {
    fromMyPrintTrace = true;
    kill(getpid(), SIGQUIT);
}

template<typename T, std::size_t sz>
//todo
static inline constexpr std::size_t NELEM(const T(&)[sz]) { return sz; }//todo

//JNINativeMethod 数组 anr相关的
static const JNINativeMethod ANR_METHODS[] = {
        {"nativeInitSignalAnrDetective", "(Ljava/lang/String;Ljava/lang/String;)V", (void *) nativeInitSignalAnrDetective},
        {"nativeChangeAnrPath", "(Ljava/lang/String;Ljava/lang/String;)V", (void *) nativeChangeAnrPath},
        {"nativeFreeSignalAnrDetective", "()V",                                     (void *) nativeFreeSignalAnrDetective},
        {"nativePrintTrace",             "()V",                                     (void *) nativePrintTrace},
};

//MainThreadPriority相关的，先不看
static const JNINativeMethod THREAD_PRIORITY_METHODS[] = {
        {"nativeInitMainThreadPriorityDetective", "()V", (void *) nativeInitMainThreadPriorityDetective},

};

static const JNINativeMethod TOUCH_EVENT_TRACE_METHODS[] = {
        {"nativeInitTouchEventLagDetective", "(I)V", (void *) nativeInitTouchEventLagDetective},

};

//step 1 JNI_OnLoad 初始化jni环境
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *) {
    JniInvocation::init(vm);

    JNIEnv *env;
    //获取env环境，如果env环境没有获取成功，返回-1
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK)
        return -1;

    //获取SignalAnrTracer变为jclass
    jclass anrDetectiveCls = env->FindClass("com/tencent/matrix/trace/tracer/SignalAnrTracer");
    if (!anrDetectiveCls)
        return -1;
    //保存SignalAnrTracer为jclass
    gJ.AnrDetective = static_cast<jclass>(env->NewGlobalRef(anrDetectiveCls));
    //保存方法
    gJ.AnrDetector_onANRDumped =
            env->GetStaticMethodID(anrDetectiveCls, "onANRDumped", "()V");
    gJ.AnrDetector_onANRDumpTrace =
            env->GetStaticMethodID(anrDetectiveCls, "onANRDumpTrace", "()V");
    gJ.AnrDetector_onPrintTrace =
            env->GetStaticMethodID(anrDetectiveCls, "onPrintTrace", "()V");

    gJ.AnrDetector_onNativeBacktraceDumped =
            env->GetStaticMethodID(anrDetectiveCls, "onNativeBacktraceDumped", "()V");


    if (env->RegisterNatives(
            anrDetectiveCls, ANR_METHODS, static_cast<jint>(NELEM(ANR_METHODS))) != 0)
        return -1;

    //删除anrDetectiveCls引用
    env->DeleteLocalRef(anrDetectiveCls);


    jclass threadPriorityDetectiveCls = env->FindClass("com/tencent/matrix/trace/tracer/ThreadPriorityTracer");

    jclass touchEventLagTracerCls = env->FindClass("com/tencent/matrix/trace/tracer/TouchEventLagTracer");

    if (!threadPriorityDetectiveCls || !touchEventLagTracerCls)
        return -1;
    //保存java类
    gJ.ThreadPriorityDetective = static_cast<jclass>(env->NewGlobalRef(threadPriorityDetectiveCls));
    gJ.TouchEventLagTracer = static_cast<jclass>(env->NewGlobalRef(touchEventLagTracerCls));


    gJ.ThreadPriorityDetective_onMainThreadPriorityModified =
            env->GetStaticMethodID(threadPriorityDetectiveCls, "onMainThreadPriorityModified", "(II)V");
    gJ.ThreadPriorityDetective_onMainThreadTimerSlackModified =
            env->GetStaticMethodID(threadPriorityDetectiveCls, "onMainThreadTimerSlackModified",
                                   "(J)V");

    gJ.TouchEventLagTracer_onTouchEvenLag =
            env->GetStaticMethodID(touchEventLagTracerCls, "onTouchEventLag", "(I)V");

    gJ.TouchEventLagTracer_onTouchEvenLagDumpTrace =
            env->GetStaticMethodID(touchEventLagTracerCls, "onTouchEventLagDumpTrace", "(I)V");

    if (env->RegisterNatives(
            threadPriorityDetectiveCls, THREAD_PRIORITY_METHODS,
            static_cast<jint>(NELEM(THREAD_PRIORITY_METHODS))) != 0)
        return -1;

    if (env->RegisterNatives(
            touchEventLagTracerCls, TOUCH_EVENT_TRACE_METHODS, static_cast<jint>(NELEM(TOUCH_EVENT_TRACE_METHODS))) != 0)
        return -1;

    env->DeleteLocalRef(threadPriorityDetectiveCls);
    env->DeleteLocalRef(touchEventLagTracerCls);

    return JNI_VERSION_1_6;
}   // namespace MatrixTracer
