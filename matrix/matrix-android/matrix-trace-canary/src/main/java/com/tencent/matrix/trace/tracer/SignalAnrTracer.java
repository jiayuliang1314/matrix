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

import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Looper;
import android.os.Message;
import android.os.MessageQueue;
import android.os.SystemClock;

import androidx.annotation.Keep;
import androidx.annotation.RequiresApi;

import com.tencent.matrix.Matrix;
import com.tencent.matrix.report.Issue;
import com.tencent.matrix.trace.TracePlugin;
import com.tencent.matrix.trace.config.SharePluginInfo;
import com.tencent.matrix.trace.config.TraceConfig;
import com.tencent.matrix.trace.constants.Constants;
import com.tencent.matrix.trace.core.AppMethodBeat;
import com.tencent.matrix.trace.util.AppForegroundUtil;
import com.tencent.matrix.trace.util.Utils;
import com.tencent.matrix.util.DeviceUtil;
import com.tencent.matrix.util.MatrixLog;
import com.tencent.matrix.util.MatrixUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.util.List;

public class SignalAnrTracer extends Tracer {
    //region 参数
    private static final String TAG = "SignalAnrTracer";
    //检测anr线程名字
    //监控到SIGQUIT后，我们在20秒内（20秒是ANR dump的timeout时间）不断轮询自己是否有NOT_RESPONDING flag
    //一旦发现有这个flag，那么马上就可以认定发生了一次ANR。
    private static final String CHECK_ANR_STATE_THREAD_NAME = "Check-ANR-State-Thread";
    //检测NOT_RESPONDING flag间隔时间
    private static final int CHECK_ERROR_STATE_INTERVAL = 500;
    //dump最长时间20s
    private static final int ANR_DUMP_MAX_TIME = 20000;
    //检测error次数
    private static final int CHECK_ERROR_STATE_COUNT =
            ANR_DUMP_MAX_TIME / CHECK_ERROR_STATE_INTERVAL;
    //前台消息，超时2s的时候，说明卡住了
    private static final long FOREGROUND_MSG_THRESHOLD = -2000;
    //后台消息，超时2s的时候，说明卡住了
    private static final long BACKGROUND_MSG_THRESHOLD = -10000;
    //是否hasInstance
    public static boolean hasInstance = false;
    //是否是前台状态
    private static boolean currentForeground = false;
    //anr trace 文件路径
    private static String sAnrTraceFilePath = "";
    //    这个Hook Trace的方案，不仅仅可以用来查ANR问题，任何时候我们都可以手动向自己发送一个SIGQUIT信号，
//    从而hook到当时的Trace。Trace的内容对于我们排查线程死锁，线程异常，耗电等问题都非常有帮助。
    //打印trace 文件路径 ，自己触发的
    private static String sPrintTraceFilePath = "";
    //监听
    private static SignalAnrDetectedListener sSignalAnrDetectedListener;
    //sApplication
    private static Application sApplication;
    //是否初始化了
    private static boolean hasInit = false;
    //anr发生时间，负值
    private static long anrMessageWhen = 0L;
    //anr发生时主线程处理的消息
    private static String anrMessageString = "";
    //endregion

    static {
        //加载trace-canary lib
        System.loadLibrary("trace-canary");
    }

    //region 构造函数
    public SignalAnrTracer(TraceConfig traceConfig) {
        hasInstance = true;
        sAnrTraceFilePath = traceConfig.anrTraceFilePath;
        sPrintTraceFilePath = traceConfig.printTraceFilePath;
    }

    public SignalAnrTracer(Application application) {
        hasInstance = true;
        sApplication = application;
    }

    public SignalAnrTracer(Application application, String anrTraceFilePath, String printTraceFilePath) {
        hasInstance = true;
        sAnrTraceFilePath = anrTraceFilePath;
        sPrintTraceFilePath = printTraceFilePath;
        sApplication = application;
    }
    //endregion

    /**
     * AnrDumper.cc里 handleSignal
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    @Keep
    private static void onANRDumped() {
        //是否是前台
        currentForeground = AppForegroundUtil.isInterestingToUser();
        //是否是主线程堵塞了，需要report
        boolean needReport = isMainThreadBlocked();

        //有两种情况，主线程消息已经堵住了，或者开启一个线程检测状态 NOT_RESPONDING
        //需要report
        if (needReport) {
            report(false);
        } else {
//            监控到SIGQUIT后，我们在20秒内（20秒是ANR dump的timeout时间）不断轮询自己是否有NOT_RESPONDING flag
//            ，一旦发现有这个flag，那么马上就可以认定发生了一次ANR。
            new Thread(new Runnable() {
                @Override
                public void run() {
                    //开启了一个线程检查
                    checkErrorStateCycle();
                }
            }, CHECK_ANR_STATE_THREAD_NAME).start();
        }
    }

    @Keep
    private static void onANRDumpTrace() {
        try {
            MatrixUtil.printFileByLine(TAG, sAnrTraceFilePath);
        } catch (Throwable t) {
            MatrixLog.e(TAG, "onANRDumpTrace error: %s", t.getMessage());
        }
    }
    //endregion

    @Keep
    private static void onPrintTrace() {
        try {
            MatrixUtil.printFileByLine(TAG, sPrintTraceFilePath);
        } catch (Throwable t) {
            MatrixLog.e(TAG, "onPrintTrace error: %s", t.getMessage());
        }
    }

    /**
     * @param fromProcessErrorState false代表主线程阻塞了
     */
    private static void report(boolean fromProcessErrorState) {
        try {
            String stackTrace = Utils.getMainThreadJavaStackTrace();
            if (sSignalAnrDetectedListener != null) {
                sSignalAnrDetectedListener.onAnrDetected(stackTrace, anrMessageString, anrMessageWhen, fromProcessErrorState);
                return;
            }

            TracePlugin plugin = Matrix.with().getPluginByClass(TracePlugin.class);
            if (null == plugin) {
                return;
            }

            String scene = AppMethodBeat.getVisibleScene();

            JSONObject jsonObject = new JSONObject();
            jsonObject = DeviceUtil.getDeviceInfo(jsonObject, Matrix.with().getApplication());
            jsonObject.put(SharePluginInfo.ISSUE_STACK_TYPE, Constants.Type.SIGNAL_ANR);
            jsonObject.put(SharePluginInfo.ISSUE_SCENE, scene);
            jsonObject.put(SharePluginInfo.ISSUE_THREAD_STACK, stackTrace);
            jsonObject.put(SharePluginInfo.ISSUE_PROCESS_FOREGROUND, currentForeground);

            Issue issue = new Issue();
            issue.setTag(SharePluginInfo.TAG_PLUGIN_EVIL_METHOD);
            issue.setContent(jsonObject);
            plugin.onDetectIssue(issue);
            MatrixLog.e(TAG, "happens real ANR : %s ", jsonObject.toString());

        } catch (JSONException e) {
            MatrixLog.e(TAG, "[JSONException error: %s", e);
        }
    }

    //通过消息时间，来判断是否到超出阈值
    @RequiresApi(api = Build.VERSION_CODES.M)
    private static boolean isMainThreadBlocked() {
        try {
            MessageQueue mainQueue = Looper.getMainLooper().getQueue();
            Field field = mainQueue.getClass().getDeclaredField("mMessages");
            field.setAccessible(true);
            final Message mMessage = (Message) field.get(mainQueue);
            if (mMessage != null) {
                anrMessageString = mMessage.toString();
                long when = mMessage.getWhen();
                if (when == 0) {
                    return false;
                }
                long time = when - SystemClock.uptimeMillis();
                anrMessageWhen = time;
                long timeThreshold = BACKGROUND_MSG_THRESHOLD;
                if (currentForeground) {
                    timeThreshold = FOREGROUND_MSG_THRESHOLD;
                }
                return time < timeThreshold;
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    private static void checkErrorStateCycle() {
        int checkErrorStateCount = 0;
        //开启一个循环检测
        while (checkErrorStateCount < CHECK_ERROR_STATE_COUNT) {
            try {
                checkErrorStateCount++;
                boolean myAnr = checkErrorState();
                if (myAnr) {
                    report(true);
                    break;
                }

                Thread.sleep(CHECK_ERROR_STATE_INTERVAL);
            } catch (Throwable t) {
                MatrixLog.e(TAG, "checkErrorStateCycle error, e : " + t.getMessage());
                break;
            }
        }
    }

    //用来判断anr发生了
//    在ANR弹窗前，会执行到makeAppNotRespondingLocked方法中，在这里会给发生ANR进程标记一个NOT_RESPONDING的flag。
//    而这个flag我们可以通过ActivityManager来获取：
    private static boolean checkErrorState() {
        try {
            Application application =
                    sApplication == null ? Matrix.with().getApplication() : sApplication;
            ActivityManager am = (ActivityManager) application
                    .getSystemService(Context.ACTIVITY_SERVICE);
            //从ActivityManager 获取ProcessErrorStateInfo
            List<ActivityManager.ProcessErrorStateInfo> procs = am.getProcessesInErrorState();
            if (procs == null) {
                return false;
            }

            for (ActivityManager.ProcessErrorStateInfo proc : procs) {
                MatrixLog.i(TAG, "[checkErrorState] found Error State proccessName = %s, proc.condition = %d", proc.processName, proc.condition);

                if (proc.uid != android.os.Process.myUid()
                        && proc.condition == ActivityManager.ProcessErrorStateInfo.NOT_RESPONDING) {
                    MatrixLog.i(TAG, "maybe received other apps ANR signal");
                }

                if (proc.pid != android.os.Process.myPid()) continue;

                if (proc.condition != ActivityManager.ProcessErrorStateInfo.NOT_RESPONDING) {
                    continue;
                }
                //只有是自己进程，并且是NOT_RESPONDING的时候，才返回true
                return true;
            }
            return false;
        } catch (Throwable t) {
            MatrixLog.e(TAG, "[checkErrorState] error : %s", t.getMessage());
        }
        return false;
    }

    //ok
    public static void printTrace() {
        if (!hasInstance) {
            MatrixLog.e(TAG, "SignalAnrTracer has not been initialize");
            return;
        }
        if (sPrintTraceFilePath.equals("")) {
            MatrixLog.e(TAG, "PrintTraceFilePath has not been set");
            return;
        }
        nativePrintTrace();
    }

    private static native void nativeInitSignalAnrDetective(String anrPrintTraceFilePath, String printTraceFilePath);

    private static native void nativeFreeSignalAnrDetective();

    private static native void nativePrintTrace();

    @Override
    protected void onAlive() {
        super.onAlive();
        if (!hasInit) {
            //调用native方法启动监听
            nativeInitSignalAnrDetective(sAnrTraceFilePath, sPrintTraceFilePath);
            //主要用来判断是否是前台
            AppForegroundUtil.INSTANCE.init();
            hasInit = true;
        }
    }

    @Override
    protected void onDead() {
        super.onDead();
        //free anr检测
        nativeFreeSignalAnrDetective();
    }

    public void setSignalAnrDetectedListener(SignalAnrDetectedListener listener) {
        sSignalAnrDetectedListener = listener;
    }

    public interface SignalAnrDetectedListener {
        void onAnrDetected(String stackTrace, String mMessageString, long mMessageWhen, boolean fromProcessErrorState);
    }
}
