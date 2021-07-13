/*
 * Tencent is pleased to support the open source community by making wechat-matrix available.
 * Copyright (C) 2018 THL A29 Limited, a Tencent company. All rights reserved.
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

package com.tencent.matrix.trace.config;

/**
 * Created by zhangshaowen on 17/7/13.
 */

public class SharePluginInfo {

    public static final String TAG_PLUGIN = "Trace";                //plugin名字
    public static final String TAG_PLUGIN_FPS = TAG_PLUGIN + "_FPS";//plugin fps功能
    public static final String TAG_PLUGIN_EVIL_METHOD = TAG_PLUGIN + "_EvilMethod"; //plugin 耗时函数功能
    public static final String TAG_PLUGIN_STARTUP = TAG_PLUGIN + "_StartUp";        //plugin 启动耗时功能

    //    public static final String ISSUE_DEVICE = "machine";
    public static final String ISSUE_SCENE = "scene";           //场景，指activity
    public static final String ISSUE_DROP_LEVEL = "dropLevel";  //掉帧程度
    public static final String ISSUE_DROP_SUM = "dropSum";      //掉帧数量
    public static final String ISSUE_FPS = "fps";               //帧率
    public static final String ISSUE_SUM_TASK_FRAME = "dropTaskFrameSum";

    public static final String ISSUE_TRACE_STACK = "stack";
    public static final String ISSUE_THREAD_STACK = "threadStack";
    public static final String ISSUE_PROCESS_PRIORITY = "processPriority";
    //使用nice value（以下成为nice值）来设定一个进程的优先级，系统任务调度器根据nice值合理安排调度。
//    nice的值越大，进程的优先级就越低，获得CPU调用的机会越少，nice值越小，进程的优先级则越高，获得CPU调用的机会越多。
    public static final String ISSUE_PROCESS_NICE = "processNice";//https://blog.csdn.net/caonima0001112/article/details/50379738
    public static final String ISSUE_PROCESS_FOREGROUND = "isProcessForeground";
    public static final String ISSUE_STACK_KEY = "stackKey";
    public static final String ISSUE_MEMORY = "memory";
    public static final String ISSUE_MEMORY_NATIVE = "native_heap";
    public static final String ISSUE_MEMORY_DALVIK = "dalvik_heap";
    public static final String ISSUE_MEMORY_VM_SIZE = "vm_size";
    public static final String ISSUE_COST = "cost";
    public static final String ISSUE_CPU_USAGE = "usage";
    public static final String ISSUE_STACK_TYPE = "detail";
    public static final String ISSUE_IS_WARM_START_UP = "is_warm_start_up";
    public static final String ISSUE_SUB_TYPE = "subType";
    public static final String STAGE_APPLICATION_CREATE = "application_create";
    public static final String STAGE_APPLICATION_CREATE_SCENE = "application_create_scene";
    public static final String STAGE_FIRST_ACTIVITY_CREATE = "first_activity_create";
    public static final String STAGE_STARTUP_DURATION = "startup_duration";
}
