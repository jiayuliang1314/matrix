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

package com.tencent.matrix.trace.core;

import android.app.Activity;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;

import com.tencent.matrix.AppActiveMatrixDelegate;
import com.tencent.matrix.trace.constants.Constants;
import com.tencent.matrix.trace.hacker.ActivityThreadHacker;
import com.tencent.matrix.trace.listeners.IAppMethodBeatListener;
import com.tencent.matrix.trace.util.Utils;
import com.tencent.matrix.util.MatrixHandlerThread;
import com.tencent.matrix.util.MatrixLog;

import java.util.HashSet;
import java.util.Set;

public class AppMethodBeat implements BeatLifecycle {

    public interface MethodEnterListener {
        void enter(int method, long threadId);
    }

    private static final String TAG = "Matrix.AppMethodBeat";
    public static boolean isDev = false;
    private static AppMethodBeat sInstance = new AppMethodBeat();//单例模式
    private static final int STATUS_DEFAULT = Integer.MAX_VALUE;//状态
    private static final int STATUS_STARTED = 2;
    private static final int STATUS_READY = 1;
    private static final int STATUS_STOPPED = -1;
    private static final int STATUS_EXPIRED_START = -2; //15秒之后，查看是否没有启动，如果没有启动则设置状态为STATUS_EXPIRED_START
    private static final int STATUS_OUT_RELEASE = -3;   //release 状态，15s之后调用，检查是否STATUS_DEFAULT,如果是则释放

    private static volatile int status = STATUS_DEFAULT;
    private final static Object statusLock = new Object();//锁
    public static MethodEnterListener sMethodEnterListener;
    private static long[] sBuffer = new long[Constants.BUFFER_SIZE];//sMethodEnterListener
    private static int sIndex = 0;//要存入的位置
    private static int sLastIndex = -1;//上个节点
    private static boolean assertIn = false;
    private volatile static long sCurrentDiffTime = SystemClock.uptimeMillis();// 从开机到现在的毫秒数（手机睡眠的时间不包括在内）
    private volatile static long sDiffTime = sCurrentDiffTime;
    private static long sMainThreadId = Looper.getMainLooper().getThread().getId();//主线程id
    private static HandlerThread sTimerUpdateThread = MatrixHandlerThread.getNewHandlerThread("matrix_time_update_thread", Thread.MIN_PRIORITY + 2);
    private static Handler sHandler = new Handler(sTimerUpdateThread.getLooper());//时间是维护在单独一个线程里，每5ms刷新
    private static final int METHOD_ID_MAX = 0xFFFFF;
    public static final int METHOD_ID_DISPATCH = METHOD_ID_MAX - 1;
    private static Set<String> sFocusActivitySet = new HashSet<>();//处于可见状态的activity
    private static final HashSet<IAppMethodBeatListener> listeners = new HashSet<>();//onActivityFocused的监听
    private static final Object updateTimeLock = new Object();//锁
    private static volatile boolean isPauseUpdateTime = false;//暂停更新时间
    //realExecute 之后15秒还没掉用onStart，设置状态为STATUS_EXPIRED_START
    private static Runnable checkStartExpiredRunnable = null;
    //looper监控
    private static LooperMonitor.LooperDispatchListener looperMonitorListener = new LooperMonitor.LooperDispatchListener() {
        @Override
        public boolean isValid() {
            return status >= STATUS_READY;
        }

        @Override
        public void dispatchStart() {
            super.dispatchStart();
            //消息分发开始
            AppMethodBeat.dispatchBegin();
        }

        @Override
        public void dispatchEnd() {
            super.dispatchEnd();
            //消息分发结束
            AppMethodBeat.dispatchEnd();
        }
    };

    // 15s之后调用，检查是否STATUS_DEFAULT,如果是则释放
    static {
        sHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                //15s之后调用，检查是否STATUS_DEFAULT,如果是则释放
                realRelease();
            }
        }, Constants.DEFAULT_RELEASE_BUFFER_DELAY);
    }

    /**
     * update time runnable
     */
    private static Runnable sUpdateDiffTimeRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                while (true) {
                    while (!isPauseUpdateTime && status > STATUS_STOPPED) {
                        sCurrentDiffTime = SystemClock.uptimeMillis() - sDiffTime;
                        SystemClock.sleep(Constants.TIME_UPDATE_CYCLE_MS);
                    }
                    synchronized (updateTimeLock) {
                        updateTimeLock.wait();
                    }
                }
            } catch (Exception e) {
                MatrixLog.e(TAG, "" + e.toString());
            }
        }
    };

    public static AppMethodBeat getInstance() {
        return sInstance;
    }

    @Override
    public void onStart() {
        //        这个时候status应该是STATUS_READY 1
        synchronized (statusLock) {
            if (status < STATUS_STARTED && status >= STATUS_EXPIRED_START) {
                sHandler.removeCallbacks(checkStartExpiredRunnable);//检查是否过期
                if (sBuffer == null) {
                    throw new RuntimeException(TAG + " sBuffer == null");
                }
                MatrixLog.i(TAG, "[onStart] preStatus:%s", status, Utils.getStack());
                status = STATUS_STARTED;
            } else {
                MatrixLog.w(TAG, "[onStart] current status:%s", status);
            }
        }
    }

    @Override
    public void onStop() {
        synchronized (statusLock) {
            if (status == STATUS_STARTED) {
                MatrixLog.i(TAG, "[onStop] %s", Utils.getStack());
                status = STATUS_STOPPED;
            } else {
                MatrixLog.w(TAG, "[onStop] current status:%s", status);
            }
        }
    }

    public void forceStop() {
        synchronized (statusLock) {
            status = STATUS_STOPPED;
        }
    }

    @Override
    public boolean isAlive() {
        return status >= STATUS_STARTED;
    }


    public static boolean isRealTrace() {
        return status >= STATUS_READY;
    }

    //15s之后调用，检查是否STATUS_DEFAULT,如果是则释放
    private static void realRelease() {
        synchronized (statusLock) {
            if (status == STATUS_DEFAULT) {
                MatrixLog.i(TAG, "[realRelease] timestamp:%s", System.currentTimeMillis());
                sHandler.removeCallbacksAndMessages(null);
                LooperMonitor.unregister(looperMonitorListener);
                sTimerUpdateThread.quit();
                sBuffer = null;
                status = STATUS_OUT_RELEASE;
            }
        }
    }

    private static void realExecute() {
        MatrixLog.i(TAG, "[realExecute] timestamp:%s", System.currentTimeMillis());

        sCurrentDiffTime = SystemClock.uptimeMillis() - sDiffTime;

        sHandler.removeCallbacksAndMessages(null);
        //1.每5毫秒调用一次sUpdateDiffTimeRunnable
        sHandler.postDelayed(sUpdateDiffTimeRunnable, Constants.TIME_UPDATE_CYCLE_MS);
        //2.15秒之后，查看是否没有启动，如果没有启动则设置状态为STATUS_EXPIRED_START
        sHandler.postDelayed(checkStartExpiredRunnable = new Runnable() {
            @Override
            public void run() {
                synchronized (statusLock) {
                    MatrixLog.i(TAG, "[startExpired] timestamp:%s status:%s", System.currentTimeMillis(), status);
                    if (status == STATUS_DEFAULT || status == STATUS_READY) {
                        status = STATUS_EXPIRED_START;
                    }
                }
            }
        }, Constants.DEFAULT_RELEASE_BUFFER_DELAY);
        //3.记录时间戳，作为应用启用的开始时间
        ActivityThreadHacker.hackSysHandlerCallback();
        //4.开始监控主线程 Looper
        LooperMonitor.register(looperMonitorListener);
    }

    //监听消息发送结束，更新时间
    private static void dispatchBegin() {
        sCurrentDiffTime = SystemClock.uptimeMillis() - sDiffTime;
        isPauseUpdateTime = false;

        synchronized (updateTimeLock) {
            updateTimeLock.notify();
        }
    }

    private static void dispatchEnd() {
        isPauseUpdateTime = true;
    }

    /**
     * hook method when it's called in.
     *
     * @param methodId
     */
    public static void i(int methodId) {

        if (status <= STATUS_STOPPED) {
            return;
        }
        if (methodId >= METHOD_ID_MAX) {
            return;
        }
        //第一个方法i的时候，启动realExecute
        if (status == STATUS_DEFAULT) {
            synchronized (statusLock) {
                if (status == STATUS_DEFAULT) {
                    realExecute();
                    status = STATUS_READY;
                }
            }
        }

        long threadId = Thread.currentThread().getId();
        if (sMethodEnterListener != null) {
            sMethodEnterListener.enter(methodId, threadId);
        }
        //主线程的方法才记录
        if (threadId == sMainThreadId) {
            if (assertIn) {
                android.util.Log.e(TAG, "ERROR!!! AppMethodBeat.i Recursive calls!!!");
                return;
            }
            assertIn = true;
            //将方法 methodId，时间，是i，组成一个long型整数放到sIndex里
            if (sIndex < Constants.BUFFER_SIZE) {
                mergeData(methodId, sIndex, true);
            } else {
                sIndex = 0;
                mergeData(methodId, sIndex, true);
            }
            ++sIndex;
            assertIn = false;
        }
    }

    /**
     * hook method when it's called out.
     *
     * @param methodId
     */
    public static void o(int methodId) {
        if (status <= STATUS_STOPPED) {
            return;
        }
        if (methodId >= METHOD_ID_MAX) {
            return;
        }
        //主线程的方法才记录
        if (Thread.currentThread().getId() == sMainThreadId) {
            //将方法 methodId，时间，是o，组成一个long型整数放到sIndex里
            if (sIndex < Constants.BUFFER_SIZE) {
                mergeData(methodId, sIndex, false);
            } else {
                sIndex = 0;
                mergeData(methodId, sIndex, false);
            }
            ++sIndex;
        }
    }

    /**
     * when the special method calls,it's will be called.
     *
     * called after {@link #i(int)}
     *
     * @param activity now at which activity
     * @param isFocus  this window if has focus
     */
    public static void at(Activity activity, boolean isFocus) {
        String activityName = activity.getClass().getName();
        if (isFocus) {
            if (sFocusActivitySet.add(activityName)) {
                synchronized (listeners) {
                    for (IAppMethodBeatListener listener : listeners) {
                        listener.onActivityFocused(activity);
                    }
                }
                MatrixLog.i(TAG, "[at] visibleScene[%s] has %s focus!", getVisibleScene(), "attach");
            }
        } else {
            if (sFocusActivitySet.remove(activityName)) {
                MatrixLog.i(TAG, "[at] visibleScene[%s] has %s focus!", getVisibleScene(), "detach");
            }
        }
    }

    //获取可见的activity，todo
    public static String getVisibleScene() {
        return AppActiveMatrixDelegate.INSTANCE.getVisibleScene();
    }

    /**
     * merge trace info as a long data
     *
     * @param methodId
     * @param index
     * @param isIn
     */
    private static void mergeData(int methodId, int index, boolean isIn) {
        //如果是消息分发methodId，则获取一次准确时间
        if (methodId == AppMethodBeat.METHOD_ID_DISPATCH) {
            sCurrentDiffTime = SystemClock.uptimeMillis() - sDiffTime;
        }
        long trueId = 0L;
        if (isIn) {
            trueId |= 1L << 63;
        }
        trueId |= (long) methodId << 43;
        trueId |= sCurrentDiffTime & 0x7FFFFFFFFFFL;
        sBuffer[index] = trueId;
        checkPileup(index);
        sLastIndex = index;
    }

    public void addListener(IAppMethodBeatListener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    public void removeListener(IAppMethodBeatListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    private static IndexRecord sIndexRecordHead = null;

    //链表相关操作
    public IndexRecord maskIndex(String source) {
        if (sIndexRecordHead == null) {
            sIndexRecordHead = new IndexRecord(sIndex - 1);//为什么是-1？
            sIndexRecordHead.source = source;
            return sIndexRecordHead;
        } else {
            IndexRecord indexRecord = new IndexRecord(sIndex - 1);//为什么是-1？
            indexRecord.source = source;
            IndexRecord record = sIndexRecordHead;
            IndexRecord last = null;
            while (record != null) {
                //下面这个判断干哈的？用于在头部插入，或者链表中间插入的，根据index
                if (indexRecord.index <= record.index) {
                    if (null == last) {
                        //头部插入
                        IndexRecord tmp = sIndexRecordHead;
                        sIndexRecordHead = indexRecord;
                        indexRecord.next = tmp;
                    } else {
                        //中间插入
                        IndexRecord tmp = last.next;
                        last.next = indexRecord;
                        indexRecord.next = tmp;
                    }
                    return indexRecord;
                }
                last = record;
                record = record.next;
            }
            last.next = indexRecord;

            return indexRecord;
        }
    }

    //IndexRecord是一个链表
    //校验用，指的是100万个缓存用了一遍了，头尾相撞了，让链表头部往前挪
    private static void checkPileup(int index) {//pileup 相撞
        IndexRecord indexRecord = sIndexRecordHead;
        while (indexRecord != null) {
            if (indexRecord.index == index || (indexRecord.index == -1 && sLastIndex == Constants.BUFFER_SIZE - 1)) {
                indexRecord.isValid = false;
                MatrixLog.w(TAG, "[checkPileup] %s", indexRecord.toString());
                //让链表头部往前挪
                sIndexRecordHead = indexRecord = indexRecord.next;
            } else {
                break;
            }
        }
    }

    public static final class IndexRecord {
        public IndexRecord(int index) {
            this.index = index;
        }

        public IndexRecord() {
            this.isValid = false;
        }

        public int index;
        private IndexRecord next;
        public boolean isValid = true;
        public String source;

        public void release() {
            isValid = false;
            IndexRecord record = sIndexRecordHead;
            IndexRecord last = null;
            while (null != record) {
                if (record == this) {
                    if (null != last) {
                        last.next = record.next;
                    } else {
                        sIndexRecordHead = record.next;
                    }
                    record.next = null;
                    break;
                }
                last = record;
                record = record.next;
            }
        }

        @Override
        public String toString() {
            return "index:" + index + ",\tisValid:" + isValid + " source:" + source;
        }
    }

    //为什么是-1
    public long[] copyData(IndexRecord startRecord) {
        return copyData(startRecord, new IndexRecord(sIndex - 1));
    }

    private long[] copyData(IndexRecord startRecord, IndexRecord endRecord) {
        long current = System.currentTimeMillis();
        long[] data = new long[0];
        try {
            if (startRecord.isValid && endRecord.isValid) {
                int length;
                int start = Math.max(0, startRecord.index);//这里和0比较了一下
                int end = Math.max(0, endRecord.index);//这里和0比较了一下

                if (end > start) {
                    length = end - start + 1;
                    data = new long[length];
                    System.arraycopy(sBuffer, start, data, 0, length);//把start 到end之间的copy出来
                } else if (end < start) {
                    length = 1 + end + (sBuffer.length - start);
                    data = new long[length];
                    //....end...start...
                    //把start之后的放到前面
                    //把end之前的放到前面
                    System.arraycopy(sBuffer, start, data, 0, sBuffer.length - start);
                    System.arraycopy(sBuffer, 0, data, sBuffer.length - start, end + 1);
                }
                return data;
            }
            return data;
        } catch (OutOfMemoryError e) {
            MatrixLog.e(TAG, e.toString());
            return data;
        } finally {
            MatrixLog.i(TAG, "[copyData] [%s:%s] length:%s cost:%sms", Math.max(0, startRecord.index), endRecord.index, data.length, System.currentTimeMillis() - current);
        }
    }

    //从开机到现在的毫秒数（手机睡眠的时间不包括在内）；
    public static long getDiffTime() {
        return sDiffTime;
    }

    public void printIndexRecord() {
        StringBuilder ss = new StringBuilder(" \n");
        IndexRecord record = sIndexRecordHead;
        while (null != record) {
            ss.append(record).append("\n");
            record = record.next;
        }
        MatrixLog.i(TAG, "[printIndexRecord] %s", ss.toString());
    }

}
