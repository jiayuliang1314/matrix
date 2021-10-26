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

package com.tencent.matrix.trace.constants;

/**
 * Created by caichongyang on 2017/5/26.
 */

public class Constants {

    public static final int BUFFER_SIZE = 100 * 10000;  // 7.6M
    public static final int TIME_UPDATE_CYCLE_MS = 5;   //ok 通过一个线程，每五毫秒更新变量，每个方法调用前后只需读取该变量获取时间
    //Matrix默认最多上传30个堆栈。如果堆栈调用超过30条，需要裁剪堆栈。裁剪策略如下：
    //从后往前遍历先序遍历结果，如果堆栈大小大于30，则将执行时间小于5*整体遍历次数的节点剔除掉
    //最多整体遍历60次，每次整体遍历，比较时间增加5ms
    //如果遍历了60次，堆栈大小还是大于30，将后面多余的删除掉
    public static final int TARGET_EVIL_METHOD_STACK = 30;          //最多上传30个堆栈
    public static final int FILTER_STACK_MAX_COUNT = 60;            //stack裁剪次数
    public static final float FILTER_STACK_KEY_ALL_PERCENT = .3F;   //statck里key方法，时常超过一个帧的时常的30%的方法
    public static final float FILTER_STACK_KEY_PATENT_PERCENT = .8F;//没有用到
    public static final int DEFAULT_EVIL_METHOD_THRESHOLD_MS = 700;         //邪恶方法，执行时间长的方法阈值
    public static final int DEFAULT_FPS_TIME_SLICE_ALIVE_MS = 10 * 1000;    //掉帧累计10s，就上报
    public static final int TIME_MILLIS_TO_NANO = 1000000;                  //毫秒和纳秒换算单位，中间有个微秒
    public static final int DEFAULT_INPUT_EXPIRED_TIME = 500;   //没有用到
    public static final int DEFAULT_ANR = 5 * 1000;         //anr 5s
    public static final int DEFAULT_NORMAL_LAG = 2 * 1000;  //lag 消息 2s
    public static final int DEFAULT_IDLE_HANDLER_LAG = 2 * 1000;//IDLE_HANDLER_LAG idlehandler超时阈值2s
    public static final int DEFAULT_ANR_INVALID = 6 * 1000; //无效anr，anr倒计时线程，没有在5s唤醒
    public static final long DEFAULT_FRAME_DURATION = 16666667L;    //一帧时间

    public static final int DEFAULT_DROPPED_NORMAL = 3; //ok 掉帧
    public static final int DEFAULT_DROPPED_MIDDLE = 9; //ok 150ms
    public static final int DEFAULT_DROPPED_HIGH = 24;  //ok 384ms
    public static final int DEFAULT_DROPPED_FROZEN = 42;//ok 672ms

    public static final int DEFAULT_STARTUP_THRESHOLD_MS_WARM = 4 * 1000; //热启动4s阈值
    public static final int DEFAULT_STARTUP_THRESHOLD_MS_COLD = 10 * 1000;//冷启动10s阈值

    public static final int DEFAULT_RELEASE_BUFFER_DELAY = 15 * 1000;//15秒

    public static final int MAX_LIMIT_ANALYSE_STACK_KEY_NUM = 10;   //没有用到

    public static final int LIMIT_WARM_THRESHOLD_MS = 5 * 1000;     //没有用到

    //NORMAL EvilMethodTracer 700ms todo 修改名字
    //ANR LooperAnrTracer
    //STARTUP 冷热启动
    //LAG 消息发送超过2s
    //SIGNAL_ANR 真正的anr
    //LAG_IDLE_HANDLER IDLE_HANDLER超过2s
    //PRIORITY_MODIFIED todo
    //TIMERSLACK_MODIFIED todo
    public enum Type {
        NORMAL, ANR, STARTUP, LAG, SIGNAL_ANR, LAG_IDLE_HANDLER, PRIORITY_MODIFIED, TIMERSLACK_MODIFIED
    }
}
