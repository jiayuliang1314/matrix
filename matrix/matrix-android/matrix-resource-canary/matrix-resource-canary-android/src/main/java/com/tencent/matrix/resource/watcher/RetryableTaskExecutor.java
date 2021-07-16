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

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

/**
 * Created by tangyinsheng on 2017/6/2.
 * <p>
 * This class is ported from LeakCanary.
 * <p>
 * Some modification was done in order to executeInBackground task with fixed delay and support
 * custom HeapDumpHandler and HandlerThread support.
 * e.g. Some framework needs to wrap default handler class for monitoring.
 */
//再次执行某个任务的执行器
public class RetryableTaskExecutor {
    //后台handler，将task发送到后台线程执行
    private final Handler mBackgroundHandler;
    //主线程handler，将task发送到主线程执行
    private final Handler mMainHandler;
    //执行时间delay时间
    private long mDelayMillis;

    public RetryableTaskExecutor(long delayMillis, HandlerThread handleThread) {
        mBackgroundHandler = new Handler(handleThread.getLooper());
        mMainHandler = new Handler(Looper.getMainLooper());
        mDelayMillis = delayMillis;
    }

    //设置delay时间
    public void setDelayMillis(long delayed) {
        this.mDelayMillis = delayed;
    }

    //没有用
    public void executeInMainThread(final RetryableTask task) {
        postToMainThreadWithDelay(task, 0);
    }

    //发送到后台线程执行任务
    public void executeInBackground(final RetryableTask task) {
        postToBackgroundWithDelay(task, 0);
    }

    //清空任务
    public void clearTasks() {
        mBackgroundHandler.removeCallbacksAndMessages(null);
        mMainHandler.removeCallbacksAndMessages(null);
    }

    //丢弃
    public void quit() {
        clearTasks();
    }

    //没有用
    private void postToMainThreadWithDelay(final RetryableTask task, final int failedAttempts) {
        mMainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                RetryableTask.Status status = task.execute();
                if (status == RetryableTask.Status.RETRY) {
                    postToMainThreadWithDelay(task, failedAttempts + 1);
                }
            }
        }, mDelayMillis);
    }

    /**
     * 发送任务到后台线程
     * @param task 代表任务，它的execute返回RETRY的时候，还需将其再次发送任务到后台线程，再次执行
     * @param failedAttempts 传入失败次数
     */
    private void postToBackgroundWithDelay(final RetryableTask task, final int failedAttempts) {
        mBackgroundHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                RetryableTask.Status status = task.execute();
                if (status == RetryableTask.Status.RETRY) {
                    postToBackgroundWithDelay(task, failedAttempts + 1);
                }
            }
        }, mDelayMillis);
    }

    /**
     * 代表可以执行再次执行的任务
     */
    public interface RetryableTask {
//        如果返回了RETRY，将其再次发送到线程执行
        Status execute();

        enum Status {
            DONE, RETRY
        }
    }
}
