package com.tencent.matrix.trace.core;

import android.os.Build;
import android.os.Looper;
import android.os.MessageQueue;
import android.os.SystemClock;
import android.util.Log;
import android.util.Printer;

import androidx.annotation.CallSuper;

import com.tencent.matrix.util.MatrixLog;
import com.tencent.matrix.util.ReflectUtils;

import java.util.HashSet;
import java.util.Objects;

//Looper 的监控是由类 LooperMonitor 实现的，原理很简单，为主线程 Looper 设置一个 Printer 即可，
// 但值得一提的是，LooperMonitor 不会直接设置 Printer，而是先获取旧对象，并创建代理对象，避免影响到其它用户设置的 Printer

//消息队列空闲时会执行IdelHandler的queueIdle()方法，该方法返回一个boolean值，
//        如果为false则执行完毕之后移除这条消息，
//        如果为true则保留，等到下次空闲时会再次执行，
public class LooperMonitor implements MessageQueue.IdleHandler {
    //region 参数
    private static final String TAG = "Matrix.LooperMonitor";
    private static final long CHECK_TIME = 60 * 1000L;                      //1分钟重新设置一次
    private static final LooperMonitor mainMonitor = new LooperMonitor();   //单例模式
    private static boolean isReflectLoggingError = false;
    private final HashSet<LooperDispatchListener> listeners = new HashSet<>();  //所有的监听器
    private LooperPrinter printer;                                              //代理printer对象
    private Looper looper;
    private long lastCheckPrinterTime = 0;                                      //1分钟重新设置一次
    //endregion

    public LooperMonitor(Looper looper) {
        Objects.requireNonNull(looper);
        this.looper = looper;
        //重新创建一个代理printer
        resetPrinter();
        //
        addIdleHandler(looper);
    }

    private LooperMonitor() {
        this(Looper.getMainLooper());
    }

    //region get set
    //没有pulic修饰符，只能在此package下使用
    static void register(LooperDispatchListener listener) {
        mainMonitor.addListener(listener);
    }

    //没有pulic修饰符，只能在此package下使用
    static void unregister(LooperDispatchListener listener) {
        mainMonitor.removeListener(listener);
    }

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
    //endregion

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

    private synchronized void resetPrinter() {
        Printer originPrinter = null;
        try {
            if (!isReflectLoggingError) {
                //拿到originPrinter
                originPrinter = ReflectUtils.get(looper.getClass(), "mLogging", looper);
                if (originPrinter == printer && null != printer) {//不是很清楚这个？？？ todo 干哈的
                    return;
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


    private void dispatch(boolean isBegin, String log) {

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

    //region ok
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
    //endregion

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
                origin.println(x); // 保证原对象正常执行
                if (origin == this) {//代理对象和原来的不能是一个，如果是，则报错
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
    //endregion

}
