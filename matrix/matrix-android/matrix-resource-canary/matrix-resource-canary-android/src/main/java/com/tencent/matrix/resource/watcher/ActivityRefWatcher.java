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

/**
 * ActivityRefWatcher继承于FilePublisher，可以将检测出来的泄漏activity保存到SharedPreferences里，设置了一天过期日期
 */
public class ActivityRefWatcher extends FilePublisher implements Watcher {
    private static final String TAG = "Matrix.ActivityRefWatcher";

    private static final int CREATED_ACTIVITY_COUNT_THRESHOLD = 1;
    //文件过期时间一天，FilePublisher
    private static final long FILE_CONFIG_EXPIRED_TIME_MILLIS = TimeUnit.DAYS.toMillis(1);
    //为每个destory activity设置的唯一key的前缀
    private static final String ACTIVITY_REFKEY_PREFIX = "MATRIX_RESCANARY_REFKEY_";
    //ResourcePlugin
    private final ResourcePlugin mResourcePlugin;

    private final RetryableTaskExecutor mDetectExecutor;//定位泄漏的Executor，重复循环检测
    private final int mMaxRedetectTimes;//几次gc之后才确定是否属于泄漏了，10次
    private final long mBgScanTimes;//在后台的时候20分钟扫描一次
    private final long mFgScanTimes;//在前台的时候1分钟扫描一次

    //处理线程
    private final HandlerThread mHandlerThread;
    //延时handler
    private final Handler mHandler;
    //存放destory的activity的链表
    private final ConcurrentLinkedQueue<DestroyedActivityInfo> mDestroyedActivityInfos;

    private final BaseLeakProcessor mLeakProcessor;//检测到泄漏了，处理程序
    //dump模式
    private final ResourceConfig.DumpMode mDumpHprofMode;
    public static class ComponentFactory {//工厂

        //创建循环重复检测的执行器，传入循环时间，后台线程
        protected RetryableTaskExecutor createDetectExecutor(ResourceConfig config, HandlerThread handlerThread) {
            return new RetryableTaskExecutor(config.getScanIntervalMillis(), handlerThread);
        }

        //发生泄漏后，创建处理器
        protected BaseLeakProcessor createCustomLeakProcessor(ResourceConfig.DumpMode dumpMode, ActivityRefWatcher watcher) {
            return null;
            }

        private BaseLeakProcessor createLeakProcess(ResourceConfig.DumpMode dumpMode, ActivityRefWatcher watcher) {
            BaseLeakProcessor leakProcessor = createCustomLeakProcessor(dumpMode, watcher);
            if (leakProcessor != null) {
                return leakProcessor;
            }
            //根据模式，创建处理器
            switch (dumpMode) {
                case AUTO_DUMP:
                    return new AutoDumpProcessor(watcher);
                case MANUAL_DUMP:
                    //manual会让用户跳到一个activity里处理
                    return new ManualDumpProcessor(watcher, watcher.getResourcePlugin().getConfig().getTargetActivity());
                case SILENCE_ANALYSE:
                    return new SilenceAnalyseProcessor(watcher);
                case FORK_DUMP:
                    return new ForkDumpProcessor(watcher);
                case FORK_ANALYSE:
                    return new ForkAnalyseProcessor(watcher);
                case LAZY_FORK_ANALYZE:
                    return new LazyForkAnalyzeProcessor(watcher);
                case NO_DUMP:
                default:
                    return new NoDumpProcessor(watcher);
                }
                }
            }

    public ActivityRefWatcher(Application app,
                              final ResourcePlugin resourcePlugin) {
        this(app, resourcePlugin, new ComponentFactory());
    }

    private ActivityRefWatcher(Application app,
                               ResourcePlugin resourcePlugin,
                               ComponentFactory componentFactory) {
        super(app, FILE_CONFIG_EXPIRED_TIME_MILLIS, resourcePlugin.getTag(), resourcePlugin);
        this.mResourcePlugin = resourcePlugin;
        final ResourceConfig config = resourcePlugin.getConfig();//动态配置
        //todo MatrixHandlerThread使用
        mHandlerThread = MatrixHandlerThread.getNewHandlerThread("matrix_res", Thread.NORM_PRIORITY); // avoid blocking default matrix thread
        mHandler = new Handler(mHandlerThread.getLooper());//创建一个单独线程，调度检测任务
        mDumpHprofMode = config.getDumpHprofMode();//dump模式
        mBgScanTimes = config.getBgScanIntervalMillis();//后台检测时间20分钟
        mFgScanTimes = config.getScanIntervalMillis();//前台检测时间1分钟
        mDetectExecutor = componentFactory.createDetectExecutor(config, mHandlerThread);//工厂创建调度任务器
        mMaxRedetectTimes = config.getMaxRedetectTimes();//发现几次就说明泄漏了
        mLeakProcessor = componentFactory.createLeakProcess(mDumpHprofMode, this);//泄漏之后处理器
        mDestroyedActivityInfos = new ConcurrentLinkedQueue<>();//链表保存泄漏acitvity结构体
    }

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

    @Override
    public void start() {
        stopDetect();
        final Application app = mResourcePlugin.getApplication();
        if (app != null) {
            //注册监听
            app.registerActivityLifecycleCallbacks(mRemovedActivityMonitor);
            //开始检测
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
            //停止注册activity生命周期监听
            app.unregisterActivityLifecycleCallbacks(mRemovedActivityMonitor);
            //关掉检测
            unscheduleDetectProcedure();
        }
    }

    @Override
    public void destroy() {
        //停止检测
        mDetectExecutor.quit();
        //释放线程
        mHandlerThread.quitSafely();
        //释放处理器
        mLeakProcessor.onDestroy();
        MatrixLog.i(TAG, "watcher is destroyed.");
    }

    private void pushDestroyedActivityInfo(Activity activity) {
        final String activityName = activity.getClass().getName();
        //在NO_DUMP，AUTO_DUMP模式下，为什么？这两个模式下才执行以下逻辑，轻量级模式
        if ((mDumpHprofMode == ResourceConfig.DumpMode.NO_DUMP || mDumpHprofMode == ResourceConfig.DumpMode.AUTO_DUMP)
                && !mResourcePlugin.getConfig().getDetectDebugger()
                && isPublished(activityName)) {//该Activity确认存在泄漏，已经上报了就return
            MatrixLog.i(TAG, "activity leak with name %s had published, just ignore", activityName);
            return;
        }
        final UUID uuid = UUID.randomUUID();
        final StringBuilder keyBuilder = new StringBuilder();
        //生成Activity实例的唯一标识
        keyBuilder.append(ACTIVITY_REFKEY_PREFIX).append(activityName)
                .append('_').append(Long.toHexString(uuid.getMostSignificantBits())).append(Long.toHexString(uuid.getLeastSignificantBits()));
        final String key = keyBuilder.toString();
        //构造一个数据结构，表示一个已被destroy的Activity
        final DestroyedActivityInfo destroyedActivityInfo
                = new DestroyedActivityInfo(key, activity, activityName);
        //放入后续待检测的Activity list
        mDestroyedActivityInfos.add(destroyedActivityInfo);
        synchronized (mDestroyedActivityInfos) {
            mDestroyedActivityInfos.notifyAll();
        }
        MatrixLog.d(TAG, "mDestroyedActivityInfos add %s", activityName);
    }

    /**
     * 将mScanDestroyedActivitiesTask放到mDetectExecutor执行
     */
    private void scheduleDetectProcedure() {
        mDetectExecutor.executeInBackground(mScanDestroyedActivitiesTask);
    }

    /**
     * mDetectExecutor是一个可以重复执行的
     */
    private void unscheduleDetectProcedure() {
//        mDetectExecutor是一个可以重复执行的，清空它里边的消息
        mDetectExecutor.clearTasks();
        //清空mDestroyedActivityInfos链表
        mDestroyedActivityInfos.clear();
    }

    /**
     * 可以重复执行的任务，execute返回RETRY，handler会将其重新发送到消息队列里，在一个后台线程里调用
     */
    private final RetryableTask mScanDestroyedActivitiesTask = new RetryableTask() {

        @Override
        public Status execute() {
            // If destroyed activity list is empty, just wait to save power.
            //链表为空，说明destroy的activity为空，等待
            if (mDestroyedActivityInfos.isEmpty()) {
                MatrixLog.i(TAG, "DestroyedActivityInfo is empty! wait...");
                synchronized (mDestroyedActivityInfos) {
                    try {
                        while (mDestroyedActivityInfos.isEmpty()) {
                            mDestroyedActivityInfos.wait();
                        }
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
                //从链表里循环取出已经destroy的DestroyedActivityInfo信息
                final DestroyedActivityInfo destroyedActivityInfo = infoIt.next();
                //如果NO_DUMP AUTO_DUMP为什么这两个模式？才isPublished检测？
                if ((mDumpHprofMode == ResourceConfig.DumpMode.NO_DUMP || mDumpHprofMode == ResourceConfig.DumpMode.AUTO_DUMP)
                        && !mResourcePlugin.getConfig().getDetectDebugger()
                        && isPublished(destroyedActivityInfo.mActivityName)) {//已经检查过了
                    MatrixLog.v(TAG, "activity with key [%s] was already published.", destroyedActivityInfo.mActivityName);
                    infoIt.remove();
                    continue;
                }
                triggerGc();
                //为null的画，说明回收了，将其删除
                if (destroyedActivityInfo.mActivityRef.get() == null) {
                    // The activity was recycled by a gc triggered outside.
                    MatrixLog.v(TAG, "activity with key [%s] was already recycled.", destroyedActivityInfo.mKey);
                    infoIt.remove();
                    continue;
                }
                //如果没回收，将其发现次数增加
                ++destroyedActivityInfo.mDetectedCount;
                //如果发现次数小于10次的画，检查下一个
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
                //发现次数大于10次了，如果处理器为null，报空指针
                if (mLeakProcessor == null) {
                    throw new NullPointerException("LeakProcessor not found!!!");
                }

                triggerGc();
                //处理器处理泄漏的activiy
                if (mLeakProcessor.process(destroyedActivityInfo)) {
                    MatrixLog.i(TAG, "the leaked activity [%s] with key [%s] has been processed. stop polling", destroyedActivityInfo.mActivityName, destroyedActivityInfo.mKey);
                    infoIt.remove();
                }
            }

            triggerGc();
            return Status.RETRY;
        }
    };

    /**
     * 返回根据dump模式，返回不同处理器
     * @return
     */
    public BaseLeakProcessor getLeakProcessor() {
        return mLeakProcessor;
    }

    /**
     * 返回ResourcePlugin
     * @return
     */
    public ResourcePlugin getResourcePlugin() {
        return mResourcePlugin;
    }

    /**
     * 返回DestroyedActivityInfo链表
     *
     * @return
     */
    public Collection<DestroyedActivityInfo> getDestroyedActivityInfos() {
        return mDestroyedActivityInfos;
    }

    /**
     * triggerGc的时候
     */
    public void triggerGc() {
        MatrixLog.v(TAG, "triggering gc...");
        //1.调用gc
        Runtime.getRuntime().gc();
        //2.睡眠100毫秒
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            MatrixLog.printErrStackTrace(TAG, e, "");
        }
        //3.运行Finalization方法
        Runtime.getRuntime().runFinalization();
        MatrixLog.v(TAG, "gc was triggered.");
    }
}
