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
#include <stdio.h>
#include <sys/utsname.h>
#include <unistd.h>
#include <pthread.h>
#include <android/log.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <time.h>
#include <signal.h>
#include <xhook.h>
#include <linux/prctl.h>
#include <sys/prctl.h>

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

#define PROP_VALUE_MAX  92                      //用于求getApiLevel
#define PROP_SDK_NAME "ro.build.version.sdk"    //用于求getApiLevel
#define HOOK_CONNECT_PATH "/dev/socket/tombstoned_java_trace"   //socket文件地址
#define HOOK_OPEN_PATH "/data/anr/traces.txt"                   //socket文件地址

using namespace MatrixTracer;

static std::optional<AnrDumper> sAnrDumper; //AnrDumper，是自定义的SignalHandler
static bool isTraceWrite = false;           //isTraceWrite my_connect my_open设置为true，my_write设置为false
static bool fromMyPrintTrace = false;       //fromMyPrintTrace 是否是自己想打的
static bool isHooking = false;              //是否hooking，unHookAnrTraceWrite设置为false
static std::string anrTracePathstring;      //新的anrTracePathstring，系统用的
static std::string printTracePathstring;    //新的printTracePathstring，我自己想打印的时候用的
static int signalCatcherTid;                //signalCatcherTid的线程id

//一个结构体，用来保存java层 类，方法地址
static struct StacktraceJNI {
    jclass AnrDetective;                    //SignalAnrTracer
    jclass ThreadPriorityDetective;         //ThreadPriorityTracer
    jmethodID AnrDetector_onANRDumped;      //SignalAnrTracer 里的
    jmethodID AnrDetector_onANRDumpTrace;   //SignalAnrTracer 里的
    jmethodID AnrDetector_onPrintTrace;     //SignalAnrTracer 里的

    jmethodID ThreadPriorityDetective_onMainThreadPriorityModified;     //修改了优先级
    jmethodID ThreadPriorityDetective_onMainThreadTimerSlackModified;   //修改了TimerSlack
} gJ;

//region MainThreadPriorityModified相关的东西
//原来的方法句柄
int (*original_setpriority)(int __which, id_t __who, int __priority);

int my_setpriority(int __which, id_t __who, int __priority) {
    //优先级<=0的时候不上报
    if (__priority <= 0) {
        return original_setpriority(__which, __who, __priority);
    }
    //其他情况上报
//    首先我们需要确保住主线程优先级不被设置的过低，hook系统调用setpriority，如果对主线程设置过低的优先级（过高的nice值），则直接报错：
    if (__who == 0 && getpid() == gettid()) {
        JNIEnv *env = JniInvocation::getEnv();

        env->CallStaticVoidMethod(gJ.ThreadPriorityDetective,
                                  gJ.ThreadPriorityDetective_onMainThreadPriorityModified,
                                  __priority);
    } else if (__who == getpid()) {
        JNIEnv *env = JniInvocation::getEnv();
        env->CallStaticVoidMethod(gJ.ThreadPriorityDetective,
                                  gJ.ThreadPriorityDetective_onMainThreadPriorityModified,
                                  __priority);
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
            env->CallStaticVoidMethod(gJ.ThreadPriorityDetective,
                                      gJ.ThreadPriorityDetective_onMainThreadTimerSlackModified,
                                      arg2);

        }
    }

    return original_prctl(option, arg2, arg3, arg4, arg5);
}
//endregion

/**
 *
 * @param content 内容
 * @param filePath 文件地址
 */
void writeAnr(const std::string &content, const std::string &filePath) {
    //unhook write
    unHookAnrTraceWrite();
    std::stringstream stringStream(content);
    std::string to;
    std::ofstream outfile;
    outfile.open(filePath);
    outfile << content;
}

//region my_connect  original_connect
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

//region my_open original_open
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

//region original_write my_write
ssize_t (*original_write)(int fd, const void *const __pass_object_size0 buf, size_t count);

ssize_t my_write(int fd, const void *const buf, size_t count) {
    //如果标记为isTraceWrite为true，第一个signalCatcher线程，write调用即为打印trace的地方
    if (isTraceWrite && gettid() == signalCatcherTid) {
        isTraceWrite = false;
        signalCatcherTid = 0;
        if (buf != nullptr) {
            std::string targetFilePath;
            if (fromMyPrintTrace) {
                targetFilePath = printTracePathstring;
            } else {
                targetFilePath = anrTracePathstring;
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

//调用java的onANRDumped，AnrDumper.cc 里handleSignal里调用anrCallback然后调用这个anrDumpCallback回调
bool anrDumpCallback() {
    JNIEnv *env = JniInvocation::getEnv();
    if (!env) return false;
    env->CallStaticVoidMethod(gJ.AnrDetective, gJ.AnrDetector_onANRDumped);
    return true;
}

//调用java的onANRDumpTrace，my_write里调用
bool anrDumpTraceCallback() {
    JNIEnv *env = JniInvocation::getEnv();
    if (!env) return false;
    env->CallStaticVoidMethod(gJ.AnrDetective, gJ.AnrDetector_onANRDumpTrace);
    return true;
}

//调用java的onPrintTrace，my_write里调用
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
 * @param isSiUser true为自己的进程
 * AnrDumper.cc 里handleSignal里调用anrCallback方法，或者调用siUserCallback，然后调用这个hookAnrTraceWrite回调
 */
void hookAnrTraceWrite(bool isSiUser) {
    int apiLevel = getApiLevel();
    if (apiLevel < 19) {
        return;
    }

    //isSiUser为true，表示自己进程发的时候是通过kill发的，此处不符合逻辑，返回
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
        xhook_hook_symbol(libcutils_info, "connect", (void *) my_connect,
                          (void **) (&original_connect));
    } else {
        void *libart_info = xhook_elf_open("libart.so");
        xhook_hook_symbol(libart_info, "open", (void *) my_open, (void **) (&original_open));
    }

    if (apiLevel >= 30 || apiLevel == 25 || apiLevel == 24) {
        void *libc_info = xhook_elf_open("libc.so");
        xhook_hook_symbol(libc_info, "write", (void *) my_write, (void **) (&original_write));
    } else if (apiLevel == 29) {
        void *libbase_info = xhook_elf_open("/system/lib64/libbase.so");
        if (!libbase_info) {
            libbase_info = xhook_elf_open("/system/lib/libbase.so");
        }
        xhook_hook_symbol(libbase_info, "write", (void *) my_write, (void **) (&original_write));
        xhook_elf_close(libbase_info);
    } else {
        void *libart_info = xhook_elf_open("libart.so");
        xhook_hook_symbol(libart_info, "write", (void *) my_write, (void **) (&original_write));
    }
}

//unhook
void unHookAnrTraceWrite() {
    int apiLevel = getApiLevel();
    if (apiLevel >= 27) {
        void *libcutils_info = xhook_elf_open("/system/lib64/libcutils.so");
        xhook_hook_symbol(libcutils_info, "connect", (void *) original_connect, nullptr);
    } else {
        void *libart_info = xhook_elf_open("libart.so");
        xhook_hook_symbol(libart_info, "open", (void *) original_connect, nullptr);
    }

    if (apiLevel >= 30 || apiLevel == 25 || apiLevel == 24) {
        void *libc_info = xhook_elf_open("libc.so");
        xhook_hook_symbol(libc_info, "write", (void *) original_write, nullptr);
    } else if (apiLevel == 29) {
        void *libbase_info = xhook_elf_open("/system/lib64/libbase.so");
        xhook_hook_symbol(libbase_info, "write", (void *) original_write, nullptr);
    } else {
        void *libart_info = xhook_elf_open("libart.so");
        xhook_hook_symbol(libart_info, "write", (void *) original_write, nullptr);
    }
    isHooking = false;
}

//初始化，开启检测Signalanr检测，真正检测的地方在AnrDumper.cc
static void
nativeInitSignalAnrDetective(JNIEnv *env, jclass, jstring anrTracePath, jstring printTracePath) {
    //anr发生时，打印path
    const char *anrTracePathChar = env->GetStringUTFChars(anrTracePath, nullptr);
    //手动发送SIGQUIT，打印的trace地址
    const char *printTracePathChar = env->GetStringUTFChars(printTracePath, nullptr);
    anrTracePathstring = std::string(anrTracePathChar);
    printTracePathstring = std::string(printTracePathChar);
    //开启检测，真正检测的地方在AnrDumper.cc
    sAnrDumper.emplace(anrTracePathChar, printTracePathChar, anrDumpCallback);
}

//Free Signal Anr Detective 重置，释放
static void nativeFreeSignalAnrDetective(JNIEnv *env, jclass) {
    //重置，释放
    sAnrDumper.reset();
}

//region MainThreadPriority相关 ，先不看
static void nativeInitMainThreadPriorityDetective(JNIEnv *env, jclass) {
    //setpriority是修改priority的
    xhook_register(".*\\.so$", "setpriority", (void *) my_setpriority,
                   (void **) (&original_setpriority));
    //修改TimerSlack的prctl方法
    xhook_register(".*\\.so$", "prctl", (void *) my_prctl, (void **) (&original_prctl));
    xhook_refresh(true);
}
//endregion

//自己打印trace，发送自己的进程发送SIGQUIT
static void nativePrintTrace() {
    fromMyPrintTrace = true;
    kill(getpid(), SIGQUIT);
}

template<typename T, std::size_t sz>//todo
static inline constexpr std::size_t NELEM(const T(&)[sz]) { return sz; }//todo

//JNINativeMethod 数组 anr相关的
static const JNINativeMethod ANR_METHODS[] = {
        {"nativeInitSignalAnrDetective", "(Ljava/lang/String;Ljava/lang/String;)V", (void *) nativeInitSignalAnrDetective},
        {"nativeFreeSignalAnrDetective", "()V",                                     (void *) nativeFreeSignalAnrDetective},
        {"nativePrintTrace",             "()V",                                     (void *) nativePrintTrace},
};

//MainThreadPriority相关的，先不看
static const JNINativeMethod THREAD_PRIORITY_METHODS[] = {
        {"nativeInitMainThreadPriorityDetective", "()V", (void *) nativeInitMainThreadPriorityDetective},
};

//JNI_OnLoad 初始化jni环境
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

    //注册native方法，使得java可以调用native
    if (env->RegisterNatives(
            anrDetectiveCls, ANR_METHODS, static_cast<jint>(NELEM(ANR_METHODS))) != 0)
        return -1;

    //删除anrDetectiveCls
    env->DeleteLocalRef(anrDetectiveCls);


//    ThreadPriorityTracer
    jclass threadPriorityDetectiveCls = env->FindClass(
            "com/tencent/matrix/trace/tracer/ThreadPriorityTracer");
    if (!threadPriorityDetectiveCls)
        return -1;
    //保存java类
    gJ.ThreadPriorityDetective = static_cast<jclass>(env->NewGlobalRef(threadPriorityDetectiveCls));
    //java方法
    gJ.ThreadPriorityDetective_onMainThreadPriorityModified =
            env->GetStaticMethodID(threadPriorityDetectiveCls, "onMainThreadPriorityModified",
                                   "(I)V");
    gJ.ThreadPriorityDetective_onMainThreadTimerSlackModified =
            env->GetStaticMethodID(threadPriorityDetectiveCls, "onMainThreadTimerSlackModified",
                                   "(J)V");

    //让java可以调用native的nativeInitMainThreadPriorityDetective
    if (env->RegisterNatives(
            threadPriorityDetectiveCls, THREAD_PRIORITY_METHODS,
            static_cast<jint>(NELEM(THREAD_PRIORITY_METHODS))) != 0)
        return -1;

    env->DeleteLocalRef(threadPriorityDetectiveCls);


    return JNI_VERSION_1_6;
}   // namespace MatrixTracer
