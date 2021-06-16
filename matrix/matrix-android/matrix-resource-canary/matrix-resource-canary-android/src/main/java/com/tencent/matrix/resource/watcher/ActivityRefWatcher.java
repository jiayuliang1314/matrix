/*
 * Copyright (C) 2015 Square, Inc.
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
package com.tencent.matrix.resource.watcher;

import android.app.Activity;
import android.app.Application;
import android.os.Debug;
import android.os.Handler;
import android.os.HandlerThread;

import com.tencent.matrix.report.FilePublisher;
import com.tencent.matrix.resource.ResourcePlugin;
import com.tencent.matrix.resource.analyzer.model.DestroyedActivityInfo;
import com.tencent.matrix.resource.config.ResourceConfig;
import com.tencent.matrix.resource.processor.AutoDumpProcessor;
import com.tencent.matrix.resource.processor.BaseLeakProcessor;
import com.tencent.matrix.resource.processor.ManualDumpProcessor;
import com.tencent.matrix.resource.processor.NoDumpProcessor;
import com.tencent.matrix.resource.processor.SilenceAnalyseProcessor;
import com.tencent.matrix.resource.watcher.RetryableTaskExecutor.RetryableTask;
import com.tencent.matrix.util.MatrixHandlerThread;
import com.tencent.matrix.util.MatrixLog;

import java.util.Collection;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

/**
 * Created by tangyinsheng on 2017/6/2.
 * <p>
 * This class is ported from LeakCanary.
 */

public class ActivityRefWatcher extends FilePublisher implements Watcher {
    //region 参数
    private static final String TAG = "Matrix.ActivityRefWatcher";

    private static final int CREATED_ACTIVITY_COUNT_THRESHOLD = 1;
    private static final long FILE_CONFIG_EXPIRED_TIME_MILLIS = TimeUnit.DAYS.toMillis(1);//文件过期时间

    private static final String ACTIVITY_REFKEY_PREFIX = "MATRIX_RESCANARY_REFKEY_";

    private final ResourcePlugin mResourcePlugin;

    private final RetryableTaskExecutor mDetectExecutor;//定位泄漏的地方Executor
    private final int mMaxRedetectTimes;//几次gc之后才确定是否属于泄漏了  GC Root
    private final long mBgScanTimes;//在后台的时候20分钟扫描一次
    private final long mFgScanTimes;//在前台的时候1分钟扫描一次

    private final HandlerThread mHandlerThread;
    private final Handler mHandler;

    private final ConcurrentLinkedQueue<DestroyedActivityInfo> mDestroyedActivityInfos;

    private final BaseLeakProcessor mLeakProcessor;//检测到泄漏了，处理程序

    private final ResourceConfig.DumpMode mDumpHprofMode;
    private final Application.ActivityLifecycleCallbacks mRemovedActivityMonitor = new ActivityLifeCycleCallbacksAdapter() {

        @Override
        public void onActivityDestroyed(Activity activity) {
            //记录已被destory的Activity
            pushDestroyedActivityInfo(activity);
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    triggerGc();
                }
            }, 2000);
        }
    };
    private final RetryableTask mScanDestroyedActivitiesTask = new RetryableTask() {

        @Override
        public Status execute() {
            // If destroyed activity list is empty, just wait to save power.
            if (mDestroyedActivityInfos.isEmpty()) {
                MatrixLog.i(TAG, "DestroyedActivityInfo is empty! wait...");
                synchronized (mDestroyedActivityInfos) {
                    try {
                        mDestroyedActivityInfos.wait();
                    } catch (Throwable ignored) {
                        // Ignored.
                    }
                }
                MatrixLog.i(TAG, "DestroyedActivityInfo is NOT empty! resume check");
                return Status.RETRY;
            }

            // Fake leaks will be generated when debugger is attached.
            //Debug调试模式，检测可能失效，直接return
            if (Debug.isDebuggerConnected() && !mResourcePlugin.getConfig().getDetectDebugger()) {
                MatrixLog.w(TAG, "debugger is connected, to avoid fake result, detection was delayed.");
                return Status.RETRY;
            }

//            final WeakReference<Object[]> sentinelRef = new WeakReference<>(new Object[1024 * 1024]); // alloc big object
            triggerGc();
            triggerGc();
            triggerGc();
//            if (sentinelRef.get() != null) {
//                // System ignored our gc request, we will retry later.
//                MatrixLog.d(TAG, "system ignore our gc request, wait for next detection.");
//                return Status.RETRY;
//            }

            final Iterator<DestroyedActivityInfo> infoIt = mDestroyedActivityInfos.iterator();

            while (infoIt.hasNext()) {
                final DestroyedActivityInfo destroyedActivityInfo = infoIt.next();
                if ((mDumpHprofMode == ResourceConfig.DumpMode.NO_DUMP || mDumpHprofMode == ResourceConfig.DumpMode.AUTO_DUMP)
                        && !mResourcePlugin.getConfig().getDetectDebugger()
                        && isPublished(destroyedActivityInfo.mActivityName)) {
                    MatrixLog.v(TAG, "activity with key [%s] was already published.", destroyedActivityInfo.mActivityName);
                    infoIt.remove();
                    continue;
                }
                triggerGc();
                if (destroyedActivityInfo.mActivityRef.get() == null) {
                    // The activity was recycled by a gc triggered outside.
                    MatrixLog.v(TAG, "activity with key [%s] was already recycled.", destroyedActivityInfo.mKey);
                    infoIt.remove();
                    continue;
                }

                ++destroyedActivityInfo.mDetectedCount;

                if (destroyedActivityInfo.mDetectedCount < mMaxRedetectTimes
                        && !mResourcePlugin.getConfig().getDetectDebugger()) {
                    // Although the sentinel tell us the activity should have been recycled,
                    // system may still ignore it, so try again until we reach max retry times.
                    MatrixLog.i(TAG, "activity with key [%s] should be recycled but actually still exists in %s times, wait for next detection to confirm.",
                            destroyedActivityInfo.mKey, destroyedActivityInfo.mDetectedCount);

                    triggerGc();
                    continue;
                }

                MatrixLog.i(TAG, "activity with key [%s] was suspected to be a leaked instance. mode[%s]", destroyedActivityInfo.mKey, mDumpHprofMode);

                if (mLeakProcessor == null) {
                    throw new NullPointerException("LeakProcessor not found!!!");
                }

                triggerGc();
                if (mLeakProcessor.process(destroyedActivityInfo)) {
                    MatrixLog.i(TAG, "the leaked activity [%s] with key [%s] has been processed. stop polling", destroyedActivityInfo.mActivityName, destroyedActivityInfo.mKey);
                    infoIt.remove();
                }
            }

            triggerGc();
            return Status.RETRY;
        }
    };
    //endregion

    //region ok构造函数
    public ActivityRefWatcher(Application app,
                              final ResourcePlugin resourcePlugin) {
        this(app, resourcePlugin, new ComponentFactory());
    }

    private ActivityRefWatcher(Application app,
                               ResourcePlugin resourcePlugin,
                               ComponentFactory componentFactory) {
        super(app, FILE_CONFIG_EXPIRED_TIME_MILLIS, resourcePlugin.getTag(), resourcePlugin);
        this.mResourcePlugin = resourcePlugin;
        final ResourceConfig config = resourcePlugin.getConfig();
        //todo MatrixHandlerThread使用
        mHandlerThread = MatrixHandlerThread.getNewHandlerThread("matrix_res"); // avoid blocking default matrix thread
        mHandler = new Handler(mHandlerThread.getLooper());
        mDumpHprofMode = config.getDumpHprofMode();
        mBgScanTimes = config.getBgScanIntervalMillis();
        mFgScanTimes = config.getScanIntervalMillis();
        mDetectExecutor = componentFactory.createDetectExecutor(config, mHandlerThread);
        mMaxRedetectTimes = config.getMaxRedetectTimes();
        mLeakProcessor = componentFactory.createLeakProcess(mDumpHprofMode, this);
        mDestroyedActivityInfos = new ConcurrentLinkedQueue<>();
    }
    //endregion

    //region 周期函数
    public void onForeground(boolean isForeground) {
        if (isForeground) {
            MatrixLog.i(TAG, "we are in foreground, modify scan time[%sms].", mFgScanTimes);
            mDetectExecutor.clearTasks();
            mDetectExecutor.setDelayMillis(mFgScanTimes);
            mDetectExecutor.executeInBackground(mScanDestroyedActivitiesTask);
        } else {
            MatrixLog.i(TAG, "we are in background, modify scan time[%sms].", mBgScanTimes);
            mDetectExecutor.setDelayMillis(mBgScanTimes);
        }
    }
    //endregion

    @Override
    public void start() {
        stopDetect();
        final Application app = mResourcePlugin.getApplication();
        if (app != null) {
            app.registerActivityLifecycleCallbacks(mRemovedActivityMonitor);
            scheduleDetectProcedure();
            MatrixLog.i(TAG, "watcher is started.");
        }
    }

    @Override
    public void stop() {
        stopDetect();
        MatrixLog.i(TAG, "watcher is stopped.");
    }

    private void stopDetect() {
        final Application app = mResourcePlugin.getApplication();
        if (app != null) {
            app.unregisterActivityLifecycleCallbacks(mRemovedActivityMonitor);
            unscheduleDetectProcedure();
        }
    }

    //ok
    @Override
    public void destroy() {
        mDetectExecutor.quit();
        mHandlerThread.quitSafely();
        mLeakProcessor.onDestroy();
        MatrixLog.i(TAG, "watcher is destroyed.");
    }

    //ok
    private void pushDestroyedActivityInfo(Activity activity) {
        final String activityName = activity.getClass().getName();
        //该Activity确认存在泄漏，且已经上报
        if ((mDumpHprofMode == ResourceConfig.DumpMode.NO_DUMP || mDumpHprofMode == ResourceConfig.DumpMode.AUTO_DUMP)
                && !mResourcePlugin.getConfig().getDetectDebugger() //不是debug模式
                && isPublished(activityName)) {                     //已经上报了
            MatrixLog.i(TAG, "activity leak with name %s had published, just ignore", activityName);
            return;
        }
        final UUID uuid = UUID.randomUUID();
        final StringBuilder keyBuilder = new StringBuilder();
        //生成Activity实例的唯一标识
        keyBuilder
                .append(ACTIVITY_REFKEY_PREFIX)
                .append(activityName)
                .append('_')
                .append(Long.toHexString(uuid.getMostSignificantBits()))
                .append(Long.toHexString(uuid.getLeastSignificantBits()));
        final String key = keyBuilder.toString();
        //构造一个数据结构，表示一个已被destroy的Activity
        final DestroyedActivityInfo destroyedActivityInfo = new DestroyedActivityInfo(key, activity, activityName);
        //放入后续待检测的Activity list
        mDestroyedActivityInfos.add(destroyedActivityInfo);
        synchronized (mDestroyedActivityInfos) {
            mDestroyedActivityInfos.notifyAll();
        }
        MatrixLog.d(TAG, "mDestroyedActivityInfos add %s", activityName);
    }

    //region ok
    private void scheduleDetectProcedure() {
        mDetectExecutor.executeInBackground(mScanDestroyedActivitiesTask);
    }

    private void unscheduleDetectProcedure() {
        mDetectExecutor.clearTasks();
        mDestroyedActivityInfos.clear();
    }

    public BaseLeakProcessor getLeakProcessor() {
        return mLeakProcessor;
    }

    public ResourcePlugin getResourcePlugin() {
        return mResourcePlugin;
    }

    public Collection<DestroyedActivityInfo> getDestroyedActivityInfos() {
        return mDestroyedActivityInfos;
    }

    public void triggerGc() {
        MatrixLog.v(TAG, "triggering gc...");
        Runtime.getRuntime().gc();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Runtime.getRuntime().runFinalization();
        MatrixLog.v(TAG, "gc was triggered.");
    }

    public static class ComponentFactory {

        protected RetryableTaskExecutor createDetectExecutor(ResourceConfig config, HandlerThread handlerThread) {
            return new RetryableTaskExecutor(config.getScanIntervalMillis(), handlerThread);
        }

        protected BaseLeakProcessor createCustomLeakProcessor(ResourceConfig.DumpMode dumpMode, ActivityRefWatcher watcher) {
            return null;
        }

        private BaseLeakProcessor createLeakProcess(ResourceConfig.DumpMode dumpMode, ActivityRefWatcher watcher) {
            BaseLeakProcessor leakProcessor = createCustomLeakProcessor(dumpMode, watcher);
            if (leakProcessor != null) {
                return leakProcessor;
            }

            switch (dumpMode) {
                case AUTO_DUMP:
                    return new AutoDumpProcessor(watcher);
                case MANUAL_DUMP:
                    return new ManualDumpProcessor(watcher, watcher.getResourcePlugin().getConfig().getTargetActivity());
                case SILENCE_ANALYSE:
                    return new SilenceAnalyseProcessor(watcher);
                case NO_DUMP:
                default:
                    return new NoDumpProcessor(watcher);
            }
        }
    }
    //endregion
}
