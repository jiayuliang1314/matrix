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

package com.tencent.matrix.trace.tracer;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.MessageQueue;

import androidx.annotation.Nullable;

import com.tencent.matrix.Matrix;
import com.tencent.matrix.report.Issue;
import com.tencent.matrix.report.IssueOfTraceCanary;
import com.tencent.matrix.trace.TracePlugin;
import com.tencent.matrix.trace.config.SharePluginInfo;
import com.tencent.matrix.trace.config.TraceConfig;
import com.tencent.matrix.trace.constants.Constants;
import com.tencent.matrix.trace.core.AppMethodBeat;
import com.tencent.matrix.trace.util.AppForegroundUtil;
import com.tencent.matrix.trace.util.Utils;
import com.tencent.matrix.util.DeviceUtil;
import com.tencent.matrix.util.MatrixLog;

import org.json.JSONObject;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class IdleHandlerLagTracer extends Tracer {

    private static final String TAG = "Matrix.AnrTracer";
    private static HandlerThread idleHandlerLagHandlerThread;
    private static Handler idleHandlerLagHandler;
    private static Runnable idleHanlderLagRunnable;
    private final TraceConfig traceConfig;

    public IdleHandlerLagTracer(TraceConfig traceConfig) {
        this.traceConfig = traceConfig;
    }

    private static void detectIdleHandler() {
        try {
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) {
                return;
            }
            //通过反射获取主线程MessageQueue里的mIdleHandlers
            MessageQueue mainQueue = Looper.getMainLooper().getQueue();
            Field field = MessageQueue.class.getDeclaredField("mIdleHandlers");
            field.setAccessible(true);
            MyArrayList<MessageQueue.IdleHandler> myIdleHandlerArrayList = new MyArrayList<>();
            //将MessageQueue里的mIdleHandlers替换为myIdleHandlerArrayList，代理模式
            field.set(mainQueue, myIdleHandlerArrayList);
            idleHandlerLagHandlerThread.start();//开启检测线程
            idleHandlerLagHandler = new Handler(idleHandlerLagHandlerThread.getLooper());//获取检测线程的handler
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    @Override
    public void onAlive() {
        super.onAlive();
        //如果支持isIdleHandlerEnable
        if (traceConfig.isIdleHandlerEnable()) {
            //新建一个HandlerThread，一个新线程检测是否超时处理
            idleHandlerLagHandlerThread = new HandlerThread("IdleHandlerLagThread");
            //idleHanlderLagRunnable是一个Runnable
            idleHanlderLagRunnable = new IdleHandlerLagRunable();
            //开启检测
            detectIdleHandler();
        }
    }

    @Override
    public void onDead() {
        super.onDead();
        if (traceConfig.isIdleHandlerEnable()) {
            //取消检测
            idleHandlerLagHandler.removeCallbacksAndMessages(null);
        }
    }

    static class IdleHandlerLagRunable implements Runnable {
        @Override
        public void run() {
            try {
//                如果执行了，则说明出问题了，IdleHandler执行超时2s
                TracePlugin plugin = Matrix.with().getPluginByClass(TracePlugin.class);
                if (null == plugin) {
                    return;
                }

                String stackTrace = Utils.getMainThreadJavaStackTrace();
                boolean currentForeground = AppForegroundUtil.isInterestingToUser();
                String scene = AppMethodBeat.getVisibleScene();

                JSONObject jsonObject = new JSONObject();
                jsonObject = DeviceUtil.getDeviceInfo(jsonObject, Matrix.with().getApplication());
                jsonObject.put(SharePluginInfo.ISSUE_STACK_TYPE, Constants.Type.LAG_IDLE_HANDLER);
                jsonObject.put(SharePluginInfo.ISSUE_SCENE, scene);//todo
                jsonObject.put(SharePluginInfo.ISSUE_THREAD_STACK, stackTrace);
                jsonObject.put(SharePluginInfo.ISSUE_PROCESS_FOREGROUND, currentForeground);

                Issue issue = new Issue();
                issue.setTag(SharePluginInfo.TAG_PLUGIN_EVIL_METHOD);//todo
                issue.setContent(jsonObject);

                IssueOfTraceCanary issueOfTraceCanary = new IssueOfTraceCanary();
                DeviceUtil.getDeviceInfo(issueOfTraceCanary, Matrix.with().getApplication());
                issueOfTraceCanary.setDetail(Constants.Type.LAG_IDLE_HANDLER.toString());
                issueOfTraceCanary.setScene(scene);
                issueOfTraceCanary.setThreadStack(stackTrace);
                issueOfTraceCanary.setProcessForeground(currentForeground);
                issueOfTraceCanary.setTag(SharePluginInfo.TAG_PLUGIN_EVIL_METHOD);
                issue.setIssueOfTraceCanary(issueOfTraceCanary);

                plugin.onDetectIssue(issue);
                MatrixLog.e(TAG, "happens idle handler Lag : %s ", jsonObject.toString());


            } catch (Throwable t) {
                MatrixLog.e(TAG, "Matrix error, error = " + t.getMessage());
            }

        }
    }

    static class MyIdleHandler implements MessageQueue.IdleHandler {
        private final MessageQueue.IdleHandler idleHandler;

        MyIdleHandler(MessageQueue.IdleHandler idleHandler) {
            this.idleHandler = idleHandler;
        }

        @Override
        public boolean queueIdle() {
            //代理模式，对原有方法增强
            //在方法开始的时候，放出了一个idleHanlderLagRunnable，2s之后执行，执行了说明超时了
            idleHandlerLagHandler.postDelayed(idleHanlderLagRunnable, Constants.DEFAULT_IDLE_HANDLER_LAG);
            boolean ret = this.idleHandler.queueIdle();
            //idleHandlerLagHandler清除idleHanlderLagRunnable
            idleHandlerLagHandler.removeCallbacks(idleHanlderLagRunnable);
            return ret;
        }
    }

    static class MyArrayList<T> extends ArrayList {
        Map<MessageQueue.IdleHandler, MyIdleHandler> map = new HashMap<>();

        @Override
        public boolean add(Object o) {
            //重写add方法
            if (o instanceof MessageQueue.IdleHandler) {
                //封装为代理的MyIdleHandler，并放入一个map里
                MyIdleHandler myIdleHandler = new MyIdleHandler((MessageQueue.IdleHandler) o);
                map.put((MessageQueue.IdleHandler) o, myIdleHandler);
                return super.add(myIdleHandler);
            }
            return super.add(o);
        }

        @Override
        public boolean remove(@Nullable Object o) {
            if (o instanceof MyIdleHandler) {
                //如果是我们的，则从map里删除
                MessageQueue.IdleHandler idleHandler = ((MyIdleHandler) o).idleHandler;
                map.remove(idleHandler);
                return super.remove(o);
            } else {
                MyIdleHandler myIdleHandler = map.remove(o);
                if (myIdleHandler != null) {
                    return super.remove(myIdleHandler);
                }
                return super.remove(o);
            }
        }
    }

}

