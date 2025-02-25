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

import android.os.Build;
import android.os.Looper;
import android.os.MessageQueue;
import android.os.SystemClock;
import android.util.Log;
import android.util.Printer;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import com.tencent.matrix.util.MatrixLog;
import com.tencent.matrix.util.ReflectUtils;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

//Looper 的监控是由类 LooperMonitor 实现的，原理很简单，为主线程 Looper 设置一个 Printer 即可，
//但值得一提的是，LooperMonitor 不会直接设置 Printer，而是先获取旧对象，并创建代理对象，避免影响到其它用户设置的 Printer
//将looper的mLogging对象，设置为我们的代理对象，LooperPrinter

//消息队列空闲时会执行IdelHandler的queueIdle()方法，该方法返回一个boolean值，
//        如果为false则执行完毕之后移除这条消息，
//        如果为true则保留，等到下次空闲时会再次执行，
//这里每隔60s重设置一次LooperPrinter
public class LooperMonitor implements MessageQueue.IdleHandler {
    private static final String TAG = "Matrix.LooperMonitor";
    private static final Map<Looper, LooperMonitor> sLooperMonitorMap = new ConcurrentHashMap<>();
    private static final LooperMonitor sMainMonitor = LooperMonitor.of(Looper.getMainLooper());//单例模式
    private static final long CHECK_TIME = 60 * 1000L;//1分钟重新设置一次
    private static boolean isReflectLoggingError = false;
    private final HashSet<LooperDispatchListener> listeners = new HashSet<>();//所有的监听器
    private LooperPrinter printer;//代理printer对象
    private Looper looper;
    private long lastCheckPrinterTime = 0;

    private LooperMonitor(Looper looper) {
        Objects.requireNonNull(looper);
        this.looper = looper;
        resetPrinter();
        addIdleHandler(looper);
    }

    public static LooperMonitor of(@NonNull Looper looper) {
        LooperMonitor looperMonitor = sLooperMonitorMap.get(looper);
        if (looperMonitor == null) {
            //重新创建一个代理printer
            looperMonitor = new LooperMonitor(looper);
            sLooperMonitorMap.put(looper, looperMonitor);
        }
        return looperMonitor;
    }

    //没有pulic修饰符，只能在此package下使用
    static void register(LooperDispatchListener listener) {
        sMainMonitor.addListener(listener);
    }

    //没有pulic修饰符，只能在此package下使用
    static void unregister(LooperDispatchListener listener) {
        sMainMonitor.removeListener(listener);
    }

    /**
     * It will be thread-unsafe if you get the listeners and literate.
     */
    @Deprecated
    public HashSet<LooperDispatchListener> getListeners() {
        return listeners;
    }

    public void addListener(LooperDispatchListener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    public void removeListener(LooperDispatchListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    public Looper getLooper() {
        return looper;
    }

    //当空闲的时候，60s重置一次
    @Override
    public boolean queueIdle() {
        if (SystemClock.uptimeMillis() - lastCheckPrinterTime >= CHECK_TIME) {
            resetPrinter();
            lastCheckPrinterTime = SystemClock.uptimeMillis();
        }
        return true;
    }

    public synchronized void onRelease() {
        if (printer != null) {
            //清空listeners
            synchronized (listeners) {
                listeners.clear();
            }
            MatrixLog.v(TAG, "[onRelease] %s, origin printer:%s", looper.getThread().getName(), printer.origin);
            //重新设置为原来的Printer
            looper.setMessageLogging(printer.origin);
            removeIdleHandler(looper);
            looper = null;
            printer = null;
        }
    }

    //将looper的mLogging对象，设置为我们的代理对象，LooperPrinter
    private synchronized void resetPrinter() {
        Printer originPrinter = null;
        try {
            if (!isReflectLoggingError) {
                //拿到originPrinter
                originPrinter = ReflectUtils.get(looper.getClass(), "mLogging", looper);
                if (originPrinter == printer && null != printer) {//已经代理了，不用再设置了
                    return;
                }
                // Fix issues that printer loaded by different classloader
                if (originPrinter != null && printer != null) {
                    if (originPrinter.getClass().getName().equals(printer.getClass().getName())) {
                        MatrixLog.w(TAG, "LooperPrinter might be loaded by different classloader"
                                + ", my = " + printer.getClass().getClassLoader()
                                + ", other = " + originPrinter.getClass().getClassLoader());
                        return;
                    }
                }
            }
        } catch (Exception e) {
            isReflectLoggingError = true;
            Log.e(TAG, "[resetPrinter] %s", e);
        }

        if (null != printer) {
            MatrixLog.w(TAG, "maybe thread:%s printer[%s] was replace other[%s]!",
                    looper.getThread().getName(), printer, originPrinter);
        }
        looper.setMessageLogging(printer = new LooperPrinter(originPrinter));
        if (null != originPrinter) {
            MatrixLog.i(TAG, "reset printer, originPrinter[%s] in %s", originPrinter, looper.getThread().getName());
        }
    }

    private synchronized void removeIdleHandler(Looper looper) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            looper.getQueue().removeIdleHandler(this);
        } else {
            try {
                MessageQueue queue = ReflectUtils.get(looper.getClass(), "mQueue", looper);
                queue.removeIdleHandler(this);
            } catch (Exception e) {
                Log.e(TAG, "[removeIdleHandler] %s", e);
            }

        }
    }

    //给looper的MessageQueue添加一个IdleHandler
    private synchronized void addIdleHandler(Looper looper) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            looper.getQueue().addIdleHandler(this);
        } else {
            try {
                MessageQueue queue = ReflectUtils.get(looper.getClass(), "mQueue", looper);
                queue.addIdleHandler(this);
            } catch (Exception e) {
                Log.e(TAG, "[removeIdleHandler] %s", e);
            }
        }
    }

    private void dispatch(boolean isBegin, String log) {
        synchronized (listeners) {
            for (LooperDispatchListener listener : listeners) {
                if (listener.isValid()) {
                    if (isBegin) {
                        if (!listener.isHasDispatchStart) {
                            listener.onDispatchStart(log);
                        }
                    } else {
                        if (listener.isHasDispatchStart) {
                            listener.onDispatchEnd(log);
                        }
                    }
                } else if (!isBegin && listener.isHasDispatchStart) {
                    listener.dispatchEnd();
                }
            }
        }
    }

    /**
     * 监听Looper 消息分发开始，结束
     */
    public abstract static class LooperDispatchListener {

        boolean isHasDispatchStart = false;

        public boolean isValid() {
            return false;
        }


        public void dispatchStart() {

        }

        @CallSuper
        public void onDispatchStart(String x) {
            this.isHasDispatchStart = true;
            dispatchStart();
        }

        @CallSuper
        public void onDispatchEnd(String x) {
            this.isHasDispatchStart = false;
            dispatchEnd();
        }


        public void dispatchEnd() {
        }
    }

    //android.util.Printer 一个系统类
    class LooperPrinter implements Printer {
        public Printer origin;
        boolean isHasChecked = false;
        boolean isValid = false;

        LooperPrinter(Printer printer) {
            this.origin = printer;
        }

        @Override
        public void println(String x) {
            if (null != origin) {
                origin.println(x);// 保证原对象正常执行
                if (origin == this) {
                    //代理对象和原来的不能是一个，如果是，则报错
                    throw new RuntimeException(TAG + " origin == this");
                }
            }
            //校验一下，消息是 > 或者 < 开头的才有效
            if (!isHasChecked) {
                isValid = x.charAt(0) == '>' || x.charAt(0) == '<';
                isHasChecked = true;
                if (!isValid) {
                    MatrixLog.e(TAG, "[println] Printer is inValid! x:%s", x);
                }
            }

            if (isValid) {
                dispatch(x.charAt(0) == '>', x);// 分发，通过第一个字符判断是开始分发，还是结束分发
            }
        }
    }
}
