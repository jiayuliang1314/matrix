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
    //region 参数
    private static final String TAG = "Matrix.AppMethodBeat";
    private static final int STATUS_DEFAULT = Integer.MAX_VALUE;//状态
    private static final int STATUS_STARTED = 2;
    private static final int STATUS_READY = 1;
    private static final int STATUS_STOPPED = -1;
    private static final int STATUS_EXPIRED_START = -2; //15秒之后，查看是否没有启动，如果没有启动则设置状态为STATUS_EXPIRED_START
    private static final int STATUS_OUT_RELEASE = -3;   //release 状态，15s之后调用，检查是否STATUS_DEFAULT,如果是则释放
    private final static Object statusLock = new Object();//锁
    private static final int METHOD_ID_MAX = 0xFFFFF;
    public static final int METHOD_ID_DISPATCH = METHOD_ID_MAX - 1;//1048574
    private static final HashSet<IAppMethodBeatListener> listeners = new HashSet<>();//onActivityFocused的监听
    private static final Object updateTimeLock = new Object();//锁
    private static final AppMethodBeat sInstance = new AppMethodBeat();//单例模式
    private static final long sMainThreadId = Looper.getMainLooper().getThread().getId();//主线程id
    private static final HandlerThread sTimerUpdateThread = MatrixHandlerThread.getNewHandlerThread("matrix_time_update_thread", Thread.MIN_PRIORITY + 2);
    private static final Handler sHandler = new Handler(sTimerUpdateThread.getLooper());//时间是维护在单独一个线程里，每5ms刷新
    private static final Set<String> sFocusActivitySet = new HashSet<>();//处于可见状态的activity
    public static boolean isDev = false;
    public static MethodEnterListener sMethodEnterListener;
    private static volatile int status = STATUS_DEFAULT;
    private static long[] sBuffer = new long[Constants.BUFFER_SIZE];//sMethodEnterListener
    private static int sIndex = 0;//要存入的位置
    private static int sLastIndex = -1;//上个节点
    private static boolean assertIn = false;
    private volatile static long sCurrentDiffTime = SystemClock.uptimeMillis();// 从开机到现在的毫秒数（手机睡眠的时间不包括在内）
    private static final long sDiffTime = sCurrentDiffTime;
    private static volatile boolean isPauseUpdateTime = false;//暂停更新时间
    //looper监控
    private static final LooperMonitor.LooperDispatchListener looperMonitorListener = new LooperMonitor.LooperDispatchListener() {
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
    /**
     * update time runnable
     */
    private static final Runnable sUpdateDiffTimeRunnable = new Runnable() {
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
    //realExecute 之后15秒还没掉用onStart，设置状态为STATUS_EXPIRED_START
    private static Runnable checkStartExpiredRunnable = null;
    private static IndexRecord sIndexRecordHead = null;
    //endregion

    //region 静态方法

    //region 辅助
    // 15s之后调用，检查是否STATUS_DEFAULT,如果是则释放，ok
    static {
        sHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                //15s之后调用，检查是否STATUS_DEFAULT,如果是则释放
                realRelease();
            }
        }, Constants.DEFAULT_RELEASE_BUFFER_DELAY);
    }

    public static AppMethodBeat getInstance() {//ok
        return sInstance;
    }

    public static boolean isRealTrace() {//ok
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

    //从开机到现在的毫秒数（手机睡眠的时间不包括在内）；
    public static long getDiffTime() {
        return sDiffTime;
    }

    //获取可见的activity
    public static String getVisibleScene() {
        return AppActiveMatrixDelegate.INSTANCE.getVisibleScene();
    }
    //endregion

    //region 第一步，i方法，realExecute
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

    private static void realExecute() {
        MatrixLog.i(TAG, "[realExecute] timestamp:%s", System.currentTimeMillis());

        sCurrentDiffTime = SystemClock.uptimeMillis() - sDiffTime;

        sHandler.removeCallbacksAndMessages(null);
        //1.每5毫秒调用一次sUpdateDiffTimeRunnable
        sHandler.postDelayed(sUpdateDiffTimeRunnable, Constants.TIME_UPDATE_CYCLE_MS);
        //15秒之后，查看是否没有启动，如果没有启动则设置状态为STATUS_EXPIRED_START
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
        //2.记录时间戳，作为应用启用的开始时间
        ActivityThreadHacker.hackSysHandlerCallback();
        //开始监控主线程 Looper，仅仅在dispatchbegin的时候更新了时间
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
    //endregion

    //region 第二步 o方法
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
    //endregion

    //region 第三步，at方法
    /**
     * when the special method calls,it's will be called.
     * <p>
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
    //endregion

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

    //IndexRecord是一个链表
    //校验用，指的是100万个缓存用了一遍了，头尾相撞了，让链表头部往前挪
    private static void checkPileup(int index) {//pileup 相撞
        //a.1 刚满了 index 为Constants.BUFFER_SIZE - 1，sIndexRecordHead最初index为-1，赋值给了indexRecord
        //b.1 中间满了，例如index为100，这个时候sIndexRecordHead的index也为100，则需要往前移动
        IndexRecord indexRecord = sIndexRecordHead;
        while (indexRecord != null) {
            //a.2 indexRecord第index为-1，sLastIndex为Constants.BUFFER_SIZE - 1，这个时候，进入
            //a.5 indexRecord这时为0位置的元素，不满足条件，退出
            //b.2 满足第一个条件indexRecord.index == index 都是100
            //b.5 indexRecord这时为101位置的元素，不满足条件，退出
            if (indexRecord.index == index || (indexRecord.index == -1 && sLastIndex == Constants.BUFFER_SIZE - 1)) {
                //a.3 indexRecord isValid设置为false
                //b.3 indexRecord isValid设置为false
                indexRecord.isValid = false;
                MatrixLog.w(TAG, "[checkPileup] %s", indexRecord.toString());
                //a.4 让链表头部往前挪,sIndexRecordHead为0位置的元素
                //b.4 让链表头部往前挪,sIndexRecordHead为101位置的元素
                sIndexRecordHead = indexRecord = indexRecord.next;
            } else {
                break;
            }
        }
    }
    //endregion

    //region onStart onStop forceStop isAlive addListener removeListener
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
    //endregion

    //region 链表相关操作
    //链表相关操作
    public IndexRecord maskIndex(String source) {
        if (sIndexRecordHead == null) {
            sIndexRecordHead = new IndexRecord(sIndex - 1);//为什么是-1？因为sIndex表示要存入的位置，这里还没有数据，最初sIndex为0，sIndex-1为-1，copyData的时候和0比较了下
            sIndexRecordHead.source = source;
            return sIndexRecordHead;
        } else {
            IndexRecord newRecord = new IndexRecord(sIndex - 1);//为什么是-1？因为sIndex表示要存入的位置，这里还没有数据，最初sIndex为0，sIndex-1为-1，copyData的时候和0比较了下
            newRecord.source = source;
            //a.1 最初还没满
            //b.1 假如sIndexRecordHead在中间100位置，sIndex-1在1000吧
            //c.1 假如sIndexRecordHead在中间100位置，sIndex-1在50吧
            //d.1 假如sIndexRecordHead在中间100位置，sIndex-1在1000吧，last在2000
            IndexRecord trivalrecord = sIndexRecordHead;
            IndexRecord last = null;
            while (trivalrecord != null) {
                //下面这个判断干哈的？用于在头部插入，或者链表中间插入的，根据index
                //a.2 这里不满足
                //b.2 1000<=100 不满足
                //c.2 50<=100满足
                //d2 1000<=100 不满足
                //d4 1000<=1000 满足
                if (newRecord.index <= trivalrecord.index) {
                    if (null == last) {
                        //头部插入
                        //c.3 sIndexRecordHead在100位置,tmp在100位置
                        IndexRecord tmp = sIndexRecordHead;
                        //c.4 修改头部为newRecord，sIndexRecordHead在50位置
                        sIndexRecordHead = newRecord;
                        //c.5 修改newRecord的next为100位置，中间的元素干掉了，为啥
                        newRecord.next = tmp;
                    } else {
                        //中间插入
                        //d5 last index是1000，last next赋值给tmp
                        IndexRecord tmp = last.next;
                        //d6 last.next设置为新的
                        last.next = newRecord;
                        //d7 新的next设置为原本的next的后边
                        newRecord.next = tmp;
                    }
                    return newRecord;
                }
                //a.3 走这里
                //b.3 走这里
                //d.3 走这里
                last = trivalrecord;
                trivalrecord = trivalrecord.next;
            }
            //a.4 last是最后一个节点，他的next为null，将其next设置为newRecord
            //b.4 last是最后一个节点，他的next为null，将其next设置为newRecord
            last.next = newRecord;

            return newRecord;
        }
    }

    //为什么是-1，因为sIndex表示要存入的位置，这里还没有数据，最初sIndex为0，sIndex-1为-1，copyData的时候和0比较了下
    public long[] copyData(IndexRecord startRecord) {
        return copyData(startRecord, new IndexRecord(sIndex - 1));
    }

    private long[] copyData(IndexRecord startRecord, IndexRecord endRecord) {
        long current = System.currentTimeMillis();
        long[] data = new long[0];
        try {
            if (startRecord.isValid && endRecord.isValid) {
                int length;
                int start = Math.max(0, startRecord.index);//这里和0比较了一下，最初的
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
    //endregion

    //region 工具方法
    public void printIndexRecord() {
        StringBuilder ss = new StringBuilder(" \n");
        IndexRecord record = sIndexRecordHead;
        while (null != record) {
            ss.append(record).append("\n");
            record = record.next;
        }
        MatrixLog.i(TAG, "[printIndexRecord] %s", ss.toString());
    }
    //endregion

    public interface MethodEnterListener {
        void enter(int method, long threadId);
    }

    public static final class IndexRecord {
        public int index;
        public boolean isValid = true;
        public String source;
        private IndexRecord next;

        public IndexRecord(int index) {
            this.index = index;
        }

        public IndexRecord() {
            this.isValid = false;
        }

        //释放
        public void release() {
            isValid = false;
            IndexRecord record = sIndexRecordHead;
            IndexRecord last = null;
            //a.1 还没满的时候，sIndexRecordHead在-1，record在-1
            //b.1 sIndexRecordHead，就是这个的时候，例如app创建的时候ApplicationCreateBeginMethodIndex
            while (null != record) {
                //a.2 record在-1，不满足
                //b.2 满足
                if (record == this) {
                    if (null != last) {
                        //a.4设置last的next为这个record的next
                        last.next = record.next;
                    } else {
                        //b.3 sIndexRecordHead设置为下一个
                        sIndexRecordHead = record.next;
                    }
                    //a.5这个record的next设置为null
                    //b.4 这个对象的next设置为null
                    record.next = null;
                    break;
                }
                //a.3 遍历直到找到
                last = record;
                record = record.next;
            }
        }

        @Override
        public String toString() {
            return "index:" + index + ",\tisValid:" + isValid + " source:" + source;
        }
    }

}
