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

import com.tencent.matrix.AppActiveMatrixDelegate;
import com.tencent.matrix.Matrix;
import com.tencent.matrix.report.Issue;
import com.tencent.matrix.report.IssueOfTraceCanary;
import com.tencent.matrix.trace.TracePlugin;
import com.tencent.matrix.trace.config.SharePluginInfo;
import com.tencent.matrix.trace.config.TraceConfig;
import com.tencent.matrix.trace.constants.Constants;
import com.tencent.matrix.trace.util.AnrTraceDirectoryProvider;
import com.tencent.matrix.trace.util.AppForegroundUtil;
import com.tencent.matrix.trace.util.Utils;
import com.tencent.matrix.util.DeviceUtil;
import com.tencent.matrix.util.MatrixLog;
import com.tencent.matrix.util.MatrixUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.util.List;

import kotlin.jvm.functions.Function0;

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
    //前台的时候，消息超时2s的时候，说明卡住了
    private static final long FOREGROUND_MSG_THRESHOLD = -2000;
    //后台的时候，消息超时10s的时候，说明卡住了
    private static final long BACKGROUND_MSG_THRESHOLD = -10000;
    //是否是前台状态
    private static boolean currentForeground = false;
    //anr trace 文件路径
    private static String sAnrTraceFilePath = "";//todo
    //    这个Hook Trace的方案，不仅仅可以用来查ANR问题，任何时候我们都可以手动向自己发送一个SIGQUIT信号，
//    从而hook到当时的Trace。Trace的内容对于我们排查线程死锁，线程异常，耗电等问题都非常有帮助。
    //打印trace 文件路径 ，自己触发的
    private static String sPrintTraceFilePath = "";//todo
    //监听，没有设置
    private static SignalAnrDetectedListener sSignalAnrDetectedListener;
    //sApplication
    private static Application sApplication;
    //是否初始化了
    private static boolean hasInit = false;
    //是否hasInstance
    public static boolean hasInstance = false;
    //anr发生时间，负值，代表过去的哪个时间
    private static long anrMessageWhen = 0L;
    //anr发生时，主线程处理的消息
    private static String anrMessageString = "";
    private static String cgroup = "";
    private static String stackTrace = "";
    private static String nativeBacktraceStackTrace = "";
    private static long lastReportedTimeStamp = 0;
    private static long onAnrDumpedTimeStamp = 0;
    //endregion

    static {
        //加载trace-canary lib
        System.loadLibrary("trace-canary");
    }

    @Override
    protected void onAlive() {
        super.onAlive();
        if (!hasInit) {
            nativeInitSignalAnrDetective(sAnrTraceFilePath, sPrintTraceFilePath);
            AppForegroundUtil.INSTANCE.init();
            hasInit = true;
        }

    }

    @Override
    protected void onDead() {
        super.onDead();
        nativeFreeSignalAnrDetective();
    }

    private static AnrTraceDirectoryProvider anrTraceDirectoryProvider;

    public SignalAnrTracer(TraceConfig traceConfig) {
        hasInstance = true;
        sAnrTraceFilePath = getAnrTraceDirectoryProvider().newHeapDumpFile("anr").getAbsolutePath();//traceConfig.anrTraceFilePath;
        sPrintTraceFilePath = getAnrTraceDirectoryProvider().newHeapDumpFile("user").getAbsolutePath();
        MatrixLog.i(TAG, "SignalAnrTracer sAnrTraceFilePath " + sAnrTraceFilePath);
        MatrixLog.i(TAG, "SignalAnrTracer sPrintTraceFilePath " + sPrintTraceFilePath);
    }

    public static void prepareFilePath(){
        sAnrTraceFilePath = getAnrTraceDirectoryProvider().newHeapDumpFile("anr").getAbsolutePath();//traceConfig.anrTraceFilePath;
        sPrintTraceFilePath = getAnrTraceDirectoryProvider().newHeapDumpFile("user").getAbsolutePath();
        MatrixLog.i(TAG, "prepareFilePath sAnrTraceFilePath " + sAnrTraceFilePath);
        MatrixLog.i(TAG, "prepareFilePath sPrintTraceFilePath " + sPrintTraceFilePath);
        nativeChangeAnrPath(sAnrTraceFilePath,sPrintTraceFilePath);
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

    public void setSignalAnrDetectedListener(SignalAnrDetectedListener listener) {
        sSignalAnrDetectedListener = listener;
    }

    public static String readCgroup() {
        StringBuilder ret = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream("/proc/self/cgroup")))) {
            String line;
            while ((line = reader.readLine()) != null) {
                ret.append(line).append("\n");
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return ret.toString();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private static void confirmRealAnr(final boolean isSigQuit) {
        MatrixLog.i(TAG, "confirmRealAnr, isSigQuit = " + isSigQuit);
        boolean needReport = isMainThreadBlocked();
        if (needReport) {
            report(false, isSigQuit);
        } else {
//            监控到SIGQUIT后，我们在20秒内（20秒是ANR dump的timeout时间）不断轮询自己是否有NOT_RESPONDING flag
//            ，一旦发现有这个flag，那么马上就可以认定发生了一次ANR。
            new Thread(new Runnable() {
                @Override
                public void run() {
                    checkErrorStateCycle(isSigQuit);
                }
            }, CHECK_ANR_STATE_THREAD_NAME).start();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Keep
    private synchronized static void onANRDumped() {
        prepareFilePath();
        onAnrDumpedTimeStamp = System.currentTimeMillis();
        MatrixLog.i(TAG, "onANRDumped");
        stackTrace = Utils.getMainThreadJavaStackTrace();
        MatrixLog.i(TAG, "onANRDumped, stackTrace = %s, duration = %d", stackTrace, (System.currentTimeMillis() - onAnrDumpedTimeStamp));
        cgroup = readCgroup();
        MatrixLog.i(TAG, "onANRDumped, read cgroup duration = %d", (System.currentTimeMillis() - onAnrDumpedTimeStamp));
        currentForeground = AppForegroundUtil.isInterestingToUser();
        MatrixLog.i(TAG, "onANRDumped, isInterestingToUser duration = %d", (System.currentTimeMillis() - onAnrDumpedTimeStamp));
        confirmRealAnr(true);
    }

    @Keep
    private static void onANRDumpTrace() {
        MatrixLog.e(TAG, "onANRDumpTrace begin " + sAnrTraceFilePath);
        try {
            MatrixUtil.printFileByLine(TAG, sAnrTraceFilePath);
            //todo 上传
        } catch (Throwable t) {
            MatrixLog.e(TAG, "onANRDumpTrace error: %s", t.getMessage());
        }
    }
    //endregion

    //    step 6
    @Keep
    private static void onPrintTrace() {
        MatrixLog.e(TAG, "onPrintTrace begin " + sPrintTraceFilePath);
        try {
            MatrixUtil.printFileByLine(TAG, sPrintTraceFilePath);
            //这里不用上传，用户自己打的
        } catch (Throwable t) {
            MatrixLog.e(TAG, "onPrintTrace error: %s", t.getMessage());
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Keep
    private static void onNativeBacktraceDumped() {
        prepareFilePath();
        MatrixLog.i(TAG, "happens onNativeBacktraceDumped");
        if (System.currentTimeMillis() - lastReportedTimeStamp < ANR_DUMP_MAX_TIME) {
            MatrixLog.i(TAG, "report SIGQUIT recently, just return");
            return;
        }
        nativeBacktraceStackTrace = Utils.getMainThreadJavaStackTrace();
        MatrixLog.i(TAG, "happens onNativeBacktraceDumped, mainThreadStackTrace = " + stackTrace);

        confirmRealAnr(false);
    }

    private static void report(boolean fromProcessErrorState, boolean isSigQuit) {
        try {
            if (sSignalAnrDetectedListener != null) {
                if (isSigQuit) {
                    sSignalAnrDetectedListener.onAnrDetected(stackTrace, anrMessageString, anrMessageWhen, fromProcessErrorState, cgroup);
                } else {
                    sSignalAnrDetectedListener.onNativeBacktraceDetected(nativeBacktraceStackTrace, anrMessageString, anrMessageWhen, fromProcessErrorState);
                }
                return;
            }

            TracePlugin plugin = Matrix.with().getPluginByClass(TracePlugin.class);
            if (null == plugin) {
                return;
            }

            String scene = AppActiveMatrixDelegate.INSTANCE.getVisibleScene();

            JSONObject jsonObject = new JSONObject();
            jsonObject = DeviceUtil.getDeviceInfo(jsonObject, Matrix.with().getApplication());
            if (isSigQuit) {
                jsonObject.put(SharePluginInfo.ISSUE_STACK_TYPE, Constants.Type.SIGNAL_ANR);
                jsonObject.put(SharePluginInfo.ISSUE_THREAD_STACK, stackTrace);
            } else {
                jsonObject.put(SharePluginInfo.ISSUE_STACK_TYPE, Constants.Type.SIGNAL_ANR_NATIVE_BACKTRACE);
                jsonObject.put(SharePluginInfo.ISSUE_THREAD_STACK, nativeBacktraceStackTrace);
            }
            jsonObject.put(SharePluginInfo.ISSUE_SCENE, scene);
            jsonObject.put(SharePluginInfo.ISSUE_PROCESS_FOREGROUND, currentForeground);
            jsonObject.put(SharePluginInfo.ANR_FILE_NAME, sAnrTraceFilePath);

            Issue issue = new Issue();
            issue.setTag(SharePluginInfo.TAG_PLUGIN_EVIL_METHOD);
            issue.setContent(jsonObject);

            IssueOfTraceCanary issueOfTraceCanary = new IssueOfTraceCanary();
            DeviceUtil.getDeviceInfo(issueOfTraceCanary, Matrix.with().getApplication());
            if (isSigQuit) {
                issueOfTraceCanary.setDetail(Constants.Type.SIGNAL_ANR.toString());
                issueOfTraceCanary.setThreadStack(stackTrace);
            } else {
                issueOfTraceCanary.setDetail(Constants.Type.SIGNAL_ANR_NATIVE_BACKTRACE.toString());
                issueOfTraceCanary.setThreadStack(nativeBacktraceStackTrace);
            }
            issueOfTraceCanary.setScene(scene);
            issueOfTraceCanary.setProcessForeground(currentForeground);
            issueOfTraceCanary.setTag(SharePluginInfo.TAG_PLUGIN_EVIL_METHOD);
            issue.setIssueOfTraceCanary(issueOfTraceCanary);

            plugin.onDetectIssue(issue);
            MatrixLog.e(TAG, "happens real ANR : %s ", jsonObject.toString());

        } catch (JSONException e) {
            MatrixLog.e(TAG, "[JSONException error: %s", e);
        } finally {
            lastReportedTimeStamp = System.currentTimeMillis();
        }
    }

    //step 2.1
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
                MatrixLog.i(TAG, "anrMessageString = " + anrMessageString);
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
            } else {
                MatrixLog.i(TAG, "mMessage is null");
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }


    private static void checkErrorStateCycle(boolean isSigQuit) {
        int checkErrorStateCount = 0;
        //开启一个循环检测
        while (checkErrorStateCount < CHECK_ERROR_STATE_COUNT) {
            try {
                checkErrorStateCount++;
                boolean myAnr = checkErrorState();
                if (myAnr) {
                    report(true, isSigQuit);
                    break;
                }

                Thread.sleep(CHECK_ERROR_STATE_INTERVAL);
            } catch (Throwable t) {
                MatrixLog.e(TAG, "checkErrorStateCycle error, e : " + t.getMessage());
                break;
            }
        }
    }

    //step 2.3
    //用来判断anr发生了
//    在ANR弹窗前，会执行到makeAppNotRespondingLocked方法中，在这里会给发生ANR进程标记一个NOT_RESPONDING的flag。
//    而这个flag我们可以通过ActivityManager来获取：
    private static boolean checkErrorState() {
        try {
            MatrixLog.i(TAG, "[checkErrorState] start");
            Application application =
                    sApplication == null ? Matrix.with().getApplication() : sApplication;
            ActivityManager am = (ActivityManager) application
                    .getSystemService(Context.ACTIVITY_SERVICE);
            //从ActivityManager 获取ProcessErrorStateInfo
            List<ActivityManager.ProcessErrorStateInfo> procs = am.getProcessesInErrorState();
            if (procs == null) {
                MatrixLog.i(TAG, "[checkErrorState] procs == null");
                return false;
            }

            for (ActivityManager.ProcessErrorStateInfo proc : procs) {
                MatrixLog.i(TAG, "[checkErrorState] found Error State proccessName = %s, proc.condition = %d", proc.processName, proc.condition);

                if (proc.uid != android.os.Process.myUid()
                        && proc.condition == ActivityManager.ProcessErrorStateInfo.NOT_RESPONDING) {
                    MatrixLog.i(TAG, "maybe received other apps ANR signal");
                    return false;
                }

                if (proc.pid != android.os.Process.myPid()) continue;

                if (proc.condition != ActivityManager.ProcessErrorStateInfo.NOT_RESPONDING) {
                    continue;
                }

                MatrixLog.i(TAG, "error sate longMsg = %s", proc.longMsg);

                return true;
            }
            return false;
        } catch (Throwable t) {
            MatrixLog.e(TAG, "[checkErrorState] error : %s", t.getMessage());
        }
        return false;
    }

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

    private static native void nativeChangeAnrPath(String anrPrintTraceFilePath, String printTraceFilePath);

    private static native void nativeFreeSignalAnrDetective();

    private static native void nativePrintTrace();

    public static AnrTraceDirectoryProvider getAnrTraceDirectoryProvider() {
        if (anrTraceDirectoryProvider == null) {
            anrTraceDirectoryProvider = new AnrTraceDirectoryProvider(Matrix.with().getApplication(), new Function0<Integer>() {
                @Override
                public Integer invoke() {
                    return 5;
                }
            }, new Function0<Boolean>() {
                @Override
                public Boolean invoke() {
                    return false;
                }
            });
        }
        return anrTraceDirectoryProvider;
    }

    public interface SignalAnrDetectedListener {
        void onAnrDetected(String stackTrace, String mMessageString, long mMessageWhen, boolean fromProcessErrorState, String cpuset);
        void onNativeBacktraceDetected(String backtrace, String mMessageString, long mMessageWhen, boolean fromProcessErrorState);
    }
}
