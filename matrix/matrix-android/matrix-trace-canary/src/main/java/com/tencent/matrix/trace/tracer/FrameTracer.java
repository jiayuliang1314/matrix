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

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.view.FrameMetrics;
import android.view.Window;

import androidx.annotation.RequiresApi;

import com.tencent.matrix.AppActiveMatrixDelegate;
import com.tencent.matrix.Matrix;
import com.tencent.matrix.report.DropLevelBean;
import com.tencent.matrix.report.Issue;
import com.tencent.matrix.report.IssueOfTraceCanary;
import com.tencent.matrix.trace.TracePlugin;
import com.tencent.matrix.trace.config.SharePluginInfo;
import com.tencent.matrix.trace.config.TraceConfig;
import com.tencent.matrix.trace.constants.Constants;
import com.tencent.matrix.trace.core.UIThreadMonitor;
import com.tencent.matrix.trace.listeners.IDoFrameListener;
import com.tencent.matrix.trace.util.Utils;
import com.tencent.matrix.util.DeviceUtil;
import com.tencent.matrix.util.MatrixHandlerThread;
import com.tencent.matrix.util.MatrixLog;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

public class FrameTracer extends Tracer implements Application.ActivityLifecycleCallbacks {
    //region 参数
    private static final String TAG = "Matrix.FrameTracer";
    private static boolean useFrameMetrics;
    private final HashSet<IDoFrameListener> listeners = new HashSet<>();
    private DropFrameListener dropFrameListener;
    private int dropFrameListenerThreshold = 0;
    private long frameIntervalNs;
    private int refreshRate;
    private final TraceConfig config;
    private final long timeSliceMs;
    private boolean isFPSEnable;
    private long frozenThreshold;
    private long highThreshold;
    private long middleThreshold;
    private long normalThreshold;
    private int droppedSum = 0;
    private long durationSum = 0;
    private Map<String, Long> lastResumeTimeMap = new HashMap<>();
    private Map<Integer, Window.OnFrameMetricsAvailableListener> frameListenerMap = new HashMap<>();

    public FrameTracer(TraceConfig config, boolean supportFrameMetrics) {
        useFrameMetrics = supportFrameMetrics;
        this.config = config;
        this.frameIntervalNs = UIThreadMonitor.getMonitor().getFrameIntervalNanos();
        this.timeSliceMs = config.getTimeSliceMs();
        this.isFPSEnable = config.isFPSEnable();
        this.frozenThreshold = config.getFrozenThreshold();
        this.highThreshold = config.getHighThreshold();
        this.normalThreshold = config.getNormalThreshold();
        this.middleThreshold = config.getMiddleThreshold();

        MatrixLog.i(TAG, "[init] frameIntervalMs:%s isFPSEnable:%s", frameIntervalNs, isFPSEnable);
        if (isFPSEnable) {
            addListener(new FPSCollector());
        }
    }

    //region addListener removeListener
    public void addListener(IDoFrameListener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    public void removeListener(IDoFrameListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    public int getDroppedSum() {
        return droppedSum;
    }

    public long getDurationSum() {
        return durationSum;
    }

//    public void addDropFrameListener(int dropFrameListenerThreshold, DropFrameListener dropFrameListener) {
//        this.dropFrameListener = dropFrameListener;
//        this.dropFrameListenerThreshold = dropFrameListenerThreshold;
//    }
//
//    public void removeDropFrameListener() {
//        this.dropFrameListener = null;
//    }
    //endregion

    //region onAlive onDead
    @Override
    public void onAlive() {
        super.onAlive();
        if (isFPSEnable) {
            if (!useFrameMetrics) {
                UIThreadMonitor.getMonitor().addObserver(this);
            }
            Matrix.with().getApplication().registerActivityLifecycleCallbacks(this);
        }
    }

    @Override
    public void onDead() {
        super.onDead();
        removeDropFrameListener();
        if (isFPSEnable) {
            UIThreadMonitor.getMonitor().removeObserver(this);
            Matrix.with().getApplication().unregisterActivityLifecycleCallbacks(this);
        }
    }
    //endregion

    //region doFrame

    /**
     * @param focusedActivity     正在显示的Activity名称
     * @param startNs             Message执行前的时间
     * @param endNs               Message执行完毕，调用LooperObserver#doFrame时的时间
     * @param isVsyncFrame        是否是vsync帧
     * @param intendedFrameTimeNs vsync帧开始时间，校对时间
     * @param inputCostNs         执行三种CallbackQueue的耗时
     * @param animationCostNs     执行三种CallbackQueue的耗时
     * @param traversalCostNs     执行三种CallbackQueue的耗时
     */
    @Override
    public void doFrame(String focusedActivity, long startNs, long endNs, boolean isVsyncFrame, long intendedFrameTimeNs, long inputCostNs, long animationCostNs, long traversalCostNs) {
        if (isForeground()) {
            notifyListener(focusedActivity, startNs, endNs, isVsyncFrame, intendedFrameTimeNs, inputCostNs, animationCostNs, traversalCostNs);
        }
    }

    private void notifyListener(final String focusedActivity, final long startNs, final long endNs, final boolean isVsyncFrame,
                                final long intendedFrameTimeNs, final long inputCostNs, final long animationCostNs, final long traversalCostNs) {
        long traceBegin = System.currentTimeMillis();
        try {
            final long jitter = endNs - intendedFrameTimeNs;
            final int dropFrame = (int) (jitter / frameIntervalNs);
            if (dropFrameListener != null) {
                if (dropFrame > dropFrameListenerThreshold) {
                    try {
                        if (AppActiveMatrixDelegate.getTopActivityName() != null) {
                            long lastResumeTime = lastResumeTimeMap.get(AppActiveMatrixDelegate.getTopActivityName());
                            dropFrameListener.dropFrame(dropFrame, jitter, AppActiveMatrixDelegate.getTopActivityName(), lastResumeTime);
                        }
                    } catch (Exception e) {
                        MatrixLog.e(TAG, "dropFrameListener error e:" + e.getMessage());
                    }
                }
            }
            //掉帧数量
            droppedSum += dropFrame;
            durationSum += Math.max(jitter, frameIntervalNs);

            synchronized (listeners) {
                for (final IDoFrameListener listener : listeners) {
                    if (config.isDevEnv()) {
                        listener.time = SystemClock.uptimeMillis();
                    }
                    if (null != listener.getExecutor()) {
//                        300
                        if (listener.getIntervalFrameReplay() > 0) {
                            //会进入这个收集
                            listener.collect(focusedActivity, startNs, endNs, dropFrame, isVsyncFrame,
                                    intendedFrameTimeNs, inputCostNs, animationCostNs, traversalCostNs);
                        } else {
                            listener.getExecutor().execute(new Runnable() {
                                @Override
                                public void run() {
                                    //Deprecated了
                                    listener.doFrameAsync(focusedActivity, startNs, endNs, dropFrame, isVsyncFrame,
                                            intendedFrameTimeNs, inputCostNs, animationCostNs, traversalCostNs);
                                }
                            });
                        }
                    } else {
                        //Deprecated了
                        listener.doFrameSync(focusedActivity, startNs, endNs, dropFrame, isVsyncFrame,
                                intendedFrameTimeNs, inputCostNs, animationCostNs, traversalCostNs);
                    }

                    if (config.isDevEnv()) {
                        listener.time = SystemClock.uptimeMillis() - listener.time;
                        MatrixLog.d(TAG, "[notifyListener] cost:%sms listener:%s", listener.time, listener);
                    }
                }
            }
        } finally {
            long cost = System.currentTimeMillis() - traceBegin;
            if (config.isDebug() && cost > frameIntervalNs) {
                MatrixLog.w(TAG, "[notifyListener] warm! maybe do heavy work in doFrameSync! size:%s cost:%sms", listeners.size(), cost);
            }
        }
    }
    //endregion


    //region ActivityLifecycleCallbacks ok
//    @Override
//    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
//
//    }
//
//    @Override
//    public void onActivityStarted(Activity activity) {
//
//    }
//
//    @Override
//    public void onActivityResumed(Activity activity) {
//        lastResumeTimeMap.put(activity.getClass().getName(), System.currentTimeMillis());
//    }
//
//    @Override
//    public void onActivityPaused(Activity activity) {
//
//    }
//
//    @Override
//    public void onActivityStopped(Activity activity) {
//
//    }
//
//    @Override
//    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
//
//    }
//
//    @Override
//    public void onActivityDestroyed(Activity activity) {
//
//    }
    //endregion

    //region ok
    public enum DropStatus {
        DROPPED_FROZEN(4), DROPPED_HIGH(3), DROPPED_MIDDLE(2), DROPPED_NORMAL(1), DROPPED_BEST(0);
        public int index;

        DropStatus(int index) {
            this.index = index;
        }
    }


//    public interface DropFrameListener {
//        void dropFrame(int dropedFrame, String scene, long lastResume);
//    }
    //endregion

    //FrameTracer模块主要FPSCollector， 主要原理是通过Choreographer获取VSync垂直同步相关回调。
    private class FPSCollector extends IDoFrameListener {

        private final Handler frameHandler = new Handler(MatrixHandlerThread.getDefaultHandlerThread().getLooper());
        private final HashMap<String, FrameCollectItem> map = new HashMap<>();
        Executor executor = new Executor() {
            @Override
            public void execute(Runnable command) {
                frameHandler.post(command);
            }
        };

        @Override
        public Executor getExecutor() {
            return executor;
        }

        @Override
        public int getIntervalFrameReplay() {
            return 300;
        }

        @Override
        public void doReplay(List<FrameReplay> list) {
            super.doReplay(list);
            for (FrameReplay replay : list) {
                doReplayInner(replay.focusedActivity, replay.startNs, replay.endNs, replay.dropFrame, replay.isVsyncFrame,
                        replay.intendedFrameTimeNs, replay.inputCostNs, replay.animationCostNs, replay.traversalCostNs);
            }
        }

        /**
         * @param visibleScene        可以设置为fragment todo
         * @param startNs
         * @param endNs
         * @param droppedFrames
         * @param isVsyncFrame
         * @param intendedFrameTimeNs
         * @param inputCostNs
         * @param animationCostNs
         * @param traversalCostNs
         */
        public void doReplayInner(String visibleScene, long startNs, long endNs, int droppedFrames,
                                  boolean isVsyncFrame, long intendedFrameTimeNs, long inputCostNs,
                                  long animationCostNs, long traversalCostNs) {

            if (Utils.isEmpty(visibleScene)) return;
            if (!isVsyncFrame) return;//只处理vsync帧

            FrameCollectItem item = map.get(visibleScene);//得到这个visibleScene的FrameCollectItem
            if (null == item) {
                item = new FrameCollectItem(visibleScene);
                map.put(visibleScene, item);
            }

            item.collect(droppedFrames);
            if (item.sumFrameCost >= timeSliceMs) { // report
                map.remove(visibleScene);
                item.report();
            }
        }
    }

    private class FrameCollectItem {
        String visibleScene;    //activity
        long sumFrameCost;      //时间累计，算上本来的16ms，总共帧所占时间
        int sumFrame = 0;       //帧数
        int sumDroppedFrames;   //超时帧累计
        // record the level of frames dropped each time
        //每种情况掉帧次数
        int[] dropLevel = new int[DropStatus.values().length];
        //每种情况总掉帧计数
        int[] dropSum = new int[DropStatus.values().length];

        FrameCollectItem(String visibleScene) {
            this.visibleScene = visibleScene;
        }

        void collect(int droppedFrames) {
            float frameIntervalCost = 1f * FrameTracer.this.frameIntervalNs
                    / Constants.TIME_MILLIS_TO_NANO;
            sumFrameCost += (droppedFrames + 1) * frameIntervalCost;
            sumDroppedFrames += droppedFrames;
            sumFrame++;
            if (droppedFrames >= frozenThreshold) {
                dropLevel[DropStatus.DROPPED_FROZEN.index]++;
                dropSum[DropStatus.DROPPED_FROZEN.index] += droppedFrames;
            } else if (droppedFrames >= highThreshold) {
                dropLevel[DropStatus.DROPPED_HIGH.index]++;
                dropSum[DropStatus.DROPPED_HIGH.index] += droppedFrames;
            } else if (droppedFrames >= middleThreshold) {
                dropLevel[DropStatus.DROPPED_MIDDLE.index]++;
                dropSum[DropStatus.DROPPED_MIDDLE.index] += droppedFrames;
            } else if (droppedFrames >= normalThreshold) {
                dropLevel[DropStatus.DROPPED_NORMAL.index]++;
                dropSum[DropStatus.DROPPED_NORMAL.index] += droppedFrames;
            } else {
                dropLevel[DropStatus.DROPPED_BEST.index]++;
                dropSum[DropStatus.DROPPED_BEST.index] += Math.max(droppedFrames, 0);
            }
        }

        void report() {
            float fps = Math.min(refreshRate, 1000.f * sumFrame / sumFrameCost);
            MatrixLog.i(TAG, "[report] FPS:%s %s", fps, toString());
            try {
                TracePlugin plugin = Matrix.with().getPluginByClass(TracePlugin.class);
                if (null == plugin) {
                    return;
                }
                JSONObject dropLevelObject = new JSONObject();
                dropLevelObject.put(DropStatus.DROPPED_FROZEN.name(), dropLevel[DropStatus.DROPPED_FROZEN.index]);
                dropLevelObject.put(DropStatus.DROPPED_HIGH.name(), dropLevel[DropStatus.DROPPED_HIGH.index]);
                dropLevelObject.put(DropStatus.DROPPED_MIDDLE.name(), dropLevel[DropStatus.DROPPED_MIDDLE.index]);
                dropLevelObject.put(DropStatus.DROPPED_NORMAL.name(), dropLevel[DropStatus.DROPPED_NORMAL.index]);
                dropLevelObject.put(DropStatus.DROPPED_BEST.name(), dropLevel[DropStatus.DROPPED_BEST.index]);

                JSONObject dropSumObject = new JSONObject();
                dropSumObject.put(DropStatus.DROPPED_FROZEN.name(), dropSum[DropStatus.DROPPED_FROZEN.index]);
                dropSumObject.put(DropStatus.DROPPED_HIGH.name(), dropSum[DropStatus.DROPPED_HIGH.index]);
                dropSumObject.put(DropStatus.DROPPED_MIDDLE.name(), dropSum[DropStatus.DROPPED_MIDDLE.index]);
                dropSumObject.put(DropStatus.DROPPED_NORMAL.name(), dropSum[DropStatus.DROPPED_NORMAL.index]);
                dropSumObject.put(DropStatus.DROPPED_BEST.name(), dropSum[DropStatus.DROPPED_BEST.index]);

                JSONObject resultObject = new JSONObject();
                resultObject = DeviceUtil.getDeviceInfo(resultObject, plugin.getApplication());

                resultObject.put(SharePluginInfo.ISSUE_SCENE, visibleScene);//todo
                resultObject.put(SharePluginInfo.ISSUE_DROP_LEVEL, dropLevelObject);
                resultObject.put(SharePluginInfo.ISSUE_DROP_SUM, dropSumObject);
                resultObject.put(SharePluginInfo.ISSUE_FPS, fps);

                Issue issue = new Issue();
                issue.setTag(SharePluginInfo.TAG_PLUGIN_FPS);
                issue.setContent(resultObject);

                IssueOfTraceCanary issueOfTraceCanary = new IssueOfTraceCanary();
                DeviceUtil.getDeviceInfo(issueOfTraceCanary, Matrix.with().getApplication());
                issueOfTraceCanary.setScene(visibleScene);
//                issueOfTraceCanary.setDropLevel(dropLevelObject.toString());
                DropLevelBean dropLevelBean = new DropLevelBean();
                dropLevelBean.setDroppedFrozen(dropLevel[DropStatus.DROPPED_FROZEN.index]);
                dropLevelBean.setDroppedHigh(dropLevel[DropStatus.DROPPED_HIGH.index]);
                dropLevelBean.setDroppedMiddle(dropLevel[DropStatus.DROPPED_MIDDLE.index]);
                dropLevelBean.setDroppedNormal(dropLevel[DropStatus.DROPPED_NORMAL.index]);
                dropLevelBean.setDroppedBest(dropLevel[DropStatus.DROPPED_BEST.index]);
                issueOfTraceCanary.setDropLevelBean(dropLevelBean);
//                issueOfTraceCanary.setDropSum(dropSumObject.toString());
                DropLevelBean dropSumBean = new DropLevelBean();
                dropSumBean.setDroppedFrozen(dropSum[DropStatus.DROPPED_FROZEN.index]);
                dropSumBean.setDroppedHigh(dropSum[DropStatus.DROPPED_HIGH.index]);
                dropSumBean.setDroppedMiddle(dropSum[DropStatus.DROPPED_MIDDLE.index]);
                dropSumBean.setDroppedNormal(dropSum[DropStatus.DROPPED_NORMAL.index]);
                dropSumBean.setDroppedBest(dropSum[DropStatus.DROPPED_BEST.index]);
                issueOfTraceCanary.setDropSumBean(dropLevelBean);
                issueOfTraceCanary.setFps(fps);
                issueOfTraceCanary.setTag(SharePluginInfo.TAG_PLUGIN_FPS);
                issue.setIssueOfTraceCanary(issueOfTraceCanary);

                plugin.onDetectIssue(issue);

            } catch (JSONException e) {
                MatrixLog.e(TAG, "json error", e);
            } finally {
                sumFrame = 0;
                sumDroppedFrames = 0;
                sumFrameCost = 0;
            }
        }

        @Override
        public String toString() {
            return "visibleScene=" + visibleScene
                    + ", sumFrame=" + sumFrame
                    + ", sumDroppedFrames=" + sumDroppedFrames
                    + ", sumFrameCost=" + sumFrameCost
                    + ", dropLevel=" + Arrays.toString(dropLevel);
        }
    }

//    public enum DropStatus {
//        DROPPED_FROZEN(4), DROPPED_HIGH(3), DROPPED_MIDDLE(2), DROPPED_NORMAL(1), DROPPED_BEST(0);
//        public int index;
//
//        DropStatus(int index) {
//            this.index = index;
//        }
//
//    }

    public void addDropFrameListener(int dropFrameListenerThreshold, DropFrameListener dropFrameListener) {
        this.dropFrameListener = dropFrameListener;
        this.dropFrameListenerThreshold = dropFrameListenerThreshold;
    }

    public void removeDropFrameListener() {
        this.dropFrameListener = null;
    }

    public interface DropFrameListener {
        void dropFrame(int droppedFrame, long jitter, String scene, long lastResumeTime);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void onActivityCreated(final Activity activity, Bundle savedInstanceState) {
        if (useFrameMetrics) {
            this.refreshRate = (int) activity.getWindowManager().getDefaultDisplay().getRefreshRate();
            this.frameIntervalNs = Constants.TIME_SECOND_TO_NANO / (long) refreshRate;
            Window.OnFrameMetricsAvailableListener onFrameMetricsAvailableListener = new Window.OnFrameMetricsAvailableListener() {
                @RequiresApi(api = Build.VERSION_CODES.O)
                @Override
                public void onFrameMetricsAvailable(Window window, FrameMetrics frameMetrics, int dropCountSinceLastInvocation) {
                    FrameMetrics frameMetricsCopy = new FrameMetrics(frameMetrics);
                    long vsynTime = frameMetricsCopy.getMetric(FrameMetrics.VSYNC_TIMESTAMP);
                    long intendedVsyncTime = frameMetricsCopy.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP);
                    frameMetricsCopy.getMetric(FrameMetrics.DRAW_DURATION);
                    notifyListener(AppActiveMatrixDelegate.INSTANCE.getVisibleScene(), intendedVsyncTime, vsynTime, true, intendedVsyncTime, 0, 0, 0);
                }
            };
            this.frameListenerMap.put(activity.hashCode(), onFrameMetricsAvailableListener);
            activity.getWindow().addOnFrameMetricsAvailableListener(onFrameMetricsAvailableListener, new Handler());
            MatrixLog.i(TAG, "onActivityCreated addOnFrameMetricsAvailableListener");
        }
    }

    @Override
    public void onActivityStarted(Activity activity) {

    }

    @Override
    public void onActivityResumed(Activity activity) {
        lastResumeTimeMap.put(activity.getClass().getName(), System.currentTimeMillis());
    }

    @Override
    public void onActivityPaused(Activity activity) {

    }

    @Override
    public void onActivityStopped(Activity activity) {

    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {

    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void onActivityDestroyed(Activity activity) {
        if (useFrameMetrics) {
            try {
                activity.getWindow().removeOnFrameMetricsAvailableListener(frameListenerMap.remove(activity.hashCode()));
            } catch (Throwable t) {
                MatrixLog.e(TAG, "removeOnFrameMetricsAvailableListener error : " + t.getMessage());
            }
        }
    }
}
