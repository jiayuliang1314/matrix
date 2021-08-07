/*
 * Copyright (C) 2010 The Android Open Source Project
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

#ifndef LAGDETECTOR_LAG_DETECTOR_MAIN_CPP_NATIVEHELPER_SCOPED_LOCAL_FRAME_H_
#define LAGDETECTOR_LAG_DETECTOR_MAIN_CPP_NATIVEHELPER_SCOPED_LOCAL_FRAME_H_

#include "jni.h"
//  在学习C++的过程中我们经常会用到.和::和：和->，在此整理一下这些常用符号的区别。
//
//    1、A.B则A为对象或者结构体；
//
//    2、A->B则A为指针，->是成员提取，A->B是提取A中的成员B，A只能是指向类、结构、联合的指针；
//
//    3、::是作用域运算符，A::B表示作用域A中的名称B，A可以是名字空间、类、结构；
//
//    4、：一般用来表示继承；
class ScopedLocalFrame {
 public:
    explicit ScopedLocalFrame(JNIEnv* env) : mEnv(env) {// explicit关键字只需用于类内的单参数构造函数前面。由于无参数的构造函数和多参数的构造函数总是显示调用，这种情况在构造函数前加explicit无意义。
        mEnv->PushLocalFrame(128);
    }

    ~ScopedLocalFrame() {
        mEnv->PopLocalFrame(NULL);
    }

 private:
    JNIEnv* const mEnv;//C++ const 允许指定一个语义约束，编译器会强制实施这个约束，允许程序员告诉编译器某值是保持不变的。如果在编程中确实有某个值保持不变，就应该明确使用const，这样可以获得编译器的帮助。

    DISALLOW_COPY_AND_ASSIGN(ScopedLocalFrame);// DISALLOW 不准许; 不接受; 驳回;
};

#endif  // LAGDETECTOR_LAG_DETECTOR_MAIN_CPP_NATIVEHELPER_SCOPED_LOCAL_FRAME_H_