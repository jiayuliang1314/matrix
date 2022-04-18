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
    public static final String ISSUE_SUM_TASK_FRAME = "dropTaskFrameSum";//没有用到
    public static final String ISSUE_TRACE_STACK = "stack";         //耗时方法堆栈
    public static final String ISSUE_THREAD_STACK = "threadStack";  //主线程堆栈
    public static final String ISSUE_PROCESS_PRIORITY = "processPriority";//进程优先级
    public static final String ISSUE_PROCESS_TIMER_SLACK = "processTimerSlack";//todo
    //使用nice value（以下成为nice值）来设定一个进程的优先级，系统任务调度器根据nice值合理安排调度。
//    nice的值越大，进程的优先级就越低，获得CPU调用的机会越少，nice值越小，进程的优先级则越高，获得CPU调用的机会越多。
    public static final String ISSUE_PROCESS_NICE = "processNice";//https://blog.csdn.net/caonima0001112/article/details/50379738
    public static final String ISSUE_PROCESS_FOREGROUND = "isProcessForeground";//是否是前台
    public static final String ISSUE_STACK_KEY = "stackKey";                    //主要耗时方法的id
    public static final String ISSUE_MEMORY = "memory";             //LooperAnrTracer anr发生的时候内存状况
    public static final String ISSUE_MEMORY_NATIVE = "native_heap"; //todo https://blog.csdn.net/gemmem/article/details/8920039?utm_source=tuicoolhttps://blog.csdn.net/gemmem/article/details/8920039?utm_source=tuicool
    public static final String ISSUE_MEMORY_DALVIK = "dalvik_heap"; //todo
    public static final String ISSUE_MEMORY_VM_SIZE = "vm_size";    //todo
    public static final String ISSUE_COST = "cost";         //耗时
    public static final String ISSUE_CPU_USAGE = "usage";   //cpu占比时间 todo EvilMethodTracer
    //                jsonObject.put(SharePluginInfo.ISSUE_STACK_TYPE, Constants.Type.NORMAL);
    //                jsonObject.put(SharePluginInfo.ISSUE_STACK_TYPE, Constants.Type.LAG_IDLE_HANDLER);
    //                jsonObject.put(SharePluginInfo.ISSUE_STACK_TYPE, Constants.Type.ANR);
    //                jsonObject.put(SharePluginInfo.ISSUE_STACK_TYPE, Constants.Type.LAG);
    //            jsonObject.put(SharePluginInfo.ISSUE_STACK_TYPE, Constants.Type.SIGNAL_ANR);
    //            jsonObject.put(SharePluginInfo.ISSUE_STACK_TYPE, Constants.Type.STARTUP);
    //            jsonObject.put(SharePluginInfo.ISSUE_STACK_TYPE, Constants.Type.PRIORITY_MODIFIED);
    //            jsonObject.put(SharePluginInfo.ISSUE_STACK_TYPE, Constants.Type.TIMERSLACK_MODIFIED);
    public static final String ISSUE_STACK_TYPE = "detail"; //问题类型 todo 需要修改
    public static final String ISSUE_IS_WARM_START_UP = "is_warm_start_up";     //热启动还是冷启动
    public static final String ISSUE_SUB_TYPE = "subType";                      //冷启动还是热启动
    public static final String STAGE_APPLICATION_CREATE = "application_create";             //应用创建
    public static final String STAGE_APPLICATION_CREATE_SCENE = "application_create_scene"; //应用创建的第一个组件
    public static final String STAGE_FIRST_ACTIVITY_CREATE = "first_activity_create";       //第一个activity启动时间
    public static final String STAGE_STARTUP_DURATION = "startup_duration";                 //启动时间，冷或者热
    public static final String ANR_FILE_NAME = "anr_file_name";                 //启动时间，冷或者热

}
