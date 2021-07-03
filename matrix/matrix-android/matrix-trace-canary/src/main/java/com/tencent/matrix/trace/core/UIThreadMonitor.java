package com.tencent.matrix.trace.core;

import android.os.Build;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Choreographer;

import com.tencent.matrix.trace.config.TraceConfig;
import com.tencent.matrix.trace.constants.Constants;
import com.tencent.matrix.trace.listeners.LooperObserver;
import com.tencent.matrix.trace.util.Utils;
import com.tencent.matrix.util.MatrixLog;
import com.tencent.matrix.util.ReflectUtils;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;

public class UIThreadMonitor implements BeatLifecycle, Runnable {
    //region 参数
    /**
     * Callback type: Input callback.  Runs first.
     *
     * @hide
     */
    public static final int CALLBACK_INPUT = 0;
    /**
     * Callback type: Animation callback.  Runs before traversals.
     *
     * @hide
     */
    public static final int CALLBACK_ANIMATION = 1;
    /**
     * Callback type: Commit callback.  Handles post-draw operations for the frame.
     * Runs after traversal completes.
     *
     * @hide
     */
    public static final int CALLBACK_TRAVERSAL = 2;
    /**
     * never do queue end code
     */
    public static final int DO_QUEUE_END_ERROR = -100;
    private static final String TAG = "Matrix.UIThreadMonitor";
    // Choreographer 中一个内部类的方法，用于添加回调
    private static final String ADD_CALLBACK = "addCallbackLocked";
    // The time of the oldest input event，没有用到
    private static final int OLDEST_INPUT_EVENT = 3;
    // The time of the newest input event，没用到
    private static final int NEWEST_INPUT_EVENT = 4;
    private static final int CALLBACK_LAST = CALLBACK_TRAVERSAL;
    // 回调类型，分别为输入事件、动画、View 绘制三种
    private final static UIThreadMonitor sInstance = new UIThreadMonitor();
    private static final int DO_QUEUE_DEFAULT = 0;
    private static final int DO_QUEUE_BEGIN = 1;
    private static final int DO_QUEUE_END = 2;
    private final HashSet<LooperObserver> observers = new HashSet<>();  //用于通知帧状态
    private final long[] dispatchTimeMs = new long[4];//dispatchTimeMs 0,2位置为开始时间，0为纳秒，2为毫秒，1，3为结束时间
    private volatile boolean isAlive = false;
    private volatile long token = 0L;       //消息执行开始时间，也用做token
    private boolean isVsyncFrame = false;   //frame开始标记
    private TraceConfig config;
    private Object callbackQueueLock;   //用于同步
    private Object[] callbackQueues;    //队列数组，保存
    private Method addTraversalQueue;   //往绘制队列添加callback的方法
    private Method addInputQueue;
    private Method addAnimationQueue;
    private Choreographer choreographer;//Choreographer机制，用于同vsync机制配合，统一动画，输入，绘制时机。
    private Object vsyncReceiver;       //？干哈的，获取了一个getIntendedFrameTimeNs，不知道啥用
    private long frameIntervalNanos = 16666666;
    private int[] queueStatus = new int[CALLBACK_LAST + 1];                 //队列状态
    private boolean[] callbackExist = new boolean[CALLBACK_LAST + 1];       //用于标记callback是否添加
    private long[] queueCost = new long[CALLBACK_LAST + 1];                 //每个队列花费时间
    private boolean isInit = false;                                         //是否init
    private long[] frameInfo = null;//没用到
    //endregion

    //region ok
    public static UIThreadMonitor getMonitor() {
        return sInstance;
    }

    public boolean isInit() {
        return isInit;
    }
    //endregion

    //region step 1 init
    public void init(TraceConfig config) {
        if (Thread.currentThread() != Looper.getMainLooper().getThread()) {
            throw new AssertionError("must be init in main thread!");
        }
        this.config = config;
        choreographer = Choreographer.getInstance();
        //private final Object mLock = new Object(); 这是个同步锁，addFrameCallback会用到
        callbackQueueLock = ReflectUtils.reflectObject(choreographer, "mLock", new Object());
        // 回调队列 private final CallbackQueue[] mCallbackQueues;
        callbackQueues = ReflectUtils.reflectObject(choreographer, "mCallbackQueues", null);
        if (null != callbackQueues) {
            // 反射，找到在 Choreographer内部类 CallbackQueue 上添加回调的addCallbackLocked方法
            // 反射获取Choreographer中CALLBACK_INPUT、CALLBACK_ANIMATION、CALLBACK_TRAVERSAL三种类型的CallbackQueue的addCallbackLocked方法的句柄。
            addInputQueue = ReflectUtils.reflectMethod(callbackQueues[CALLBACK_INPUT], ADD_CALLBACK, long.class, Object.class, Object.class);
            addAnimationQueue = ReflectUtils.reflectMethod(callbackQueues[CALLBACK_ANIMATION], ADD_CALLBACK, long.class, Object.class, Object.class);
            addTraversalQueue = ReflectUtils.reflectMethod(callbackQueues[CALLBACK_TRAVERSAL], ADD_CALLBACK, long.class, Object.class, Object.class);
        }
        //？干哈的
        vsyncReceiver = ReflectUtils.reflectObject(choreographer, "mDisplayEventReceiver", null);
        //每一帧的时间16ms的纳秒值
        frameIntervalNanos = ReflectUtils.reflectObject(choreographer, "mFrameIntervalNanos", Constants.DEFAULT_FRAME_DURATION);
        //向LooperMonitor注册Message执行开始的回调、执行结束的回调。这里的Message是指主线程中发生的所有Message，
        //包括App自己的以及Framework中的，Choreographer中的自然也可以捕获到。
        LooperMonitor.register(new LooperMonitor.LooperDispatchListener() {
            @Override
            public boolean isValid() {
                return isAlive;
            }

            @Override
            public void dispatchStart() {
                super.dispatchStart();
                UIThreadMonitor.this.dispatchBegin();
            }

            @Override
            public void dispatchEnd() {
                super.dispatchEnd();
                UIThreadMonitor.this.dispatchEnd();
            }

        });
        this.isInit = true;
        MatrixLog.i(TAG, "[UIThreadMonitor] %s %s %s %s %s %s frameIntervalNanos:%s", callbackQueueLock == null, callbackQueues == null,
                addInputQueue == null, addTraversalQueue == null, addAnimationQueue == null, vsyncReceiver == null, frameIntervalNanos);


        if (config.isDevEnv()) {
            addObserver(new LooperObserver() {
                @Override
                public void doFrame(String focusedActivity, long startNs, long endNs, boolean isVsyncFrame, long intendedFrameTimeNs, long inputCostNs, long animationCostNs, long traversalCostNs) {
                    MatrixLog.i(TAG, "focusedActivity[%s] frame cost:%sms isVsyncFrame=%s intendedFrameTimeNs=%s [%s|%s|%s]ns",
                            focusedActivity, (endNs - startNs) / Constants.TIME_MILLIS_TO_NANO, isVsyncFrame, intendedFrameTimeNs, inputCostNs, animationCostNs, traversalCostNs);
                }
            });
        }
    }
    //endregion

    //region onStop isAlive getFrameIntervalNanos addObserver removeObserver getQueueCost
    @Override
    public synchronized void onStop() {
        if (!isInit) {
            MatrixLog.e(TAG, "[onStart] is never init.");
            return;
        }
        if (isAlive) {
            this.isAlive = false;
            MatrixLog.i(TAG, "[onStop] callbackExist:%s %s", Arrays.toString(callbackExist), Utils.getStack());
        }
    }

    @Override
    public boolean isAlive() {
        return isAlive;
    }

    public long getFrameIntervalNanos() {
        return frameIntervalNanos;
    }

    public void addObserver(LooperObserver observer) {
        if (!isAlive) {
            onStart();
        }
        synchronized (observers) {
            observers.add(observer);
        }
    }

    public void removeObserver(LooperObserver observer) {
        synchronized (observers) {
            observers.remove(observer);
            if (observers.isEmpty()) {
                onStop();
            }
        }
    }

    public long getQueueCost(int type, long token) {
        if (token != this.token) {
            return -1;
        }
        return queueStatus[type] == DO_QUEUE_END ? queueCost[type] : 0;
    }

    private void doQueueBegin(int type) {
        queueStatus[type] = DO_QUEUE_BEGIN;//设置queueStatus[type]状态为begin
        queueCost[type] = System.nanoTime();//记录初始值时间
    }

    private void doQueueEnd(int type) {
        queueStatus[type] = DO_QUEUE_END;//设置queueStatus[type]状态为end
        queueCost[type] = System.nanoTime() - queueCost[type];//记录消耗的时间
        synchronized (this) {
            callbackExist[type] = false;//重置状态
        }
    }
    //endregion

    //region step 4 dispatchBegin
    private void dispatchBegin() {
        token = dispatchTimeMs[0] = System.nanoTime();              //记录时间，dispatchTimeMs 0,2位置为开始时间
        dispatchTimeMs[2] = SystemClock.currentThreadTimeMillis();  //记录时间
        AppMethodBeat.i(AppMethodBeat.METHOD_ID_DISPATCH);          //打个点

        synchronized (observers) {
            for (LooperObserver observer : observers) {
                if (!observer.isDispatchBegin()) {
                    observer.dispatchBegin(dispatchTimeMs[0], dispatchTimeMs[2], token);
                }
            }
        }
        if (config.isDevEnv()) {
            MatrixLog.d(TAG, "[dispatchBegin#run] inner cost:%sns", System.nanoTime() - token);
        }
    }
    //endregion

    private void doFrameBegin(long token) {
        this.isVsyncFrame = true;
    }

    private void doFrameEnd(long token) {
        doQueueEnd(CALLBACK_TRAVERSAL);

        for (int i : queueStatus) {
            if (i != DO_QUEUE_END) {
                queueCost[i] = DO_QUEUE_END_ERROR;
                if (config.isDevEnv) {
                    throw new RuntimeException(String.format("UIThreadMonitor happens type[%s] != DO_QUEUE_END", i));
                }
            }
        }
        queueStatus = new int[CALLBACK_LAST + 1];

        addFrameCallback(CALLBACK_INPUT, this, true);
    }

    //
    private void dispatchEnd() {
        long traceBegin = 0;//用于devenv记录时间打log用
        if (config.isDevEnv()) {//用于devenv记录时间打log用
            traceBegin = System.nanoTime();//用于devenv记录时间打log用
        }
        long startNs = token;
        long intendedFrameTimeNs = startNs;
        if (isVsyncFrame) {
            doFrameEnd(token);
            intendedFrameTimeNs = getIntendedFrameTimeNs(startNs);
        }

        long endNs = System.nanoTime();

        synchronized (observers) {
            for (LooperObserver observer : observers) {
                if (observer.isDispatchBegin()) {
                    observer.doFrame(AppMethodBeat.getVisibleScene(), startNs, endNs, isVsyncFrame, intendedFrameTimeNs, queueCost[CALLBACK_INPUT], queueCost[CALLBACK_ANIMATION], queueCost[CALLBACK_TRAVERSAL]);
                }
            }
        }

        dispatchTimeMs[3] = SystemClock.currentThreadTimeMillis();
        dispatchTimeMs[1] = System.nanoTime();

        AppMethodBeat.o(AppMethodBeat.METHOD_ID_DISPATCH);

        synchronized (observers) {
            for (LooperObserver observer : observers) {
                if (observer.isDispatchBegin()) {
                    observer.dispatchEnd(dispatchTimeMs[0], dispatchTimeMs[2], dispatchTimeMs[1], dispatchTimeMs[3], token, isVsyncFrame);
                }
            }
        }
        this.isVsyncFrame = false;

        if (config.isDevEnv()) {//用于devenv记录时间打log用
            MatrixLog.d(TAG, "[dispatchEnd#run] inner cost:%sns", System.nanoTime() - traceBegin);//用于devenv记录时间打log用
        }
    }


    //region step 2 onStart,初始化callbackExist，queueStatus，queueCost三个值，然后调用addFrameCallback CALLBACK_INPUT
//    上面就是onStart方法干的事情，归根结底就是向Choreograpger注册了一个回调（即UIThreadMonitor自身），这样下次Vsync信号来到时，
//    就会触发这个callback（UIThreadMonitor#run方法）。
    @Override
    public synchronized void onStart() {
        if (!isInit) {
            MatrixLog.e(TAG, "[onStart] is never init.");
            return;
        }
        if (!isAlive) {
            this.isAlive = true;
            synchronized (this) {
                MatrixLog.i(TAG, "[onStart] callbackExist:%s %s", Arrays.toString(callbackExist), Utils.getStack());
                callbackExist = new boolean[CALLBACK_LAST + 1];//记录是否已经向该类型的CallbackQueue添加了Runnable，避免重复添加
            }
            //记录CallbackQueue中添加的Runnable的运行状态，分为默认状态（DO_QUEUE_DEFAULT）、已经添加的状态
            // （DO_QUEUE_BEGIN）、运行结束的状态（DO_QUEUE_END）
            queueStatus = new int[CALLBACK_LAST + 1];
            //记录上面某种类型的Runnable的执行起始的耗时，这可以反映出当前这一次执行CallbackQueue里面的任务耗时有多久。
            queueCost = new long[CALLBACK_LAST + 1];
//            这里首先会判断某种type类型的callback是否已经添加，UIThreadMonitor是否已经启动等等检查，
//            然后根据type取得需要invoke的方法句柄，然后调用该方法并设置callbackExist标志位。
            addFrameCallback(CALLBACK_INPUT, this, true);
        }
    }
    //endregion

    //region step 3 addFrameCallback run
    //    addFrameCallback方法将一个Runnable（自己）插到了INPUT类型的CallbackQueue的头部。
    //    CallbackQueue是一个单链表组织起来的队列，里面按照时间从小到大进行组织。
    private synchronized void addFrameCallback(int type, Runnable callback, boolean isAddHeader) {
        if (callbackExist[type]) {
            MatrixLog.w(TAG, "[addFrameCallback] this type %s callback has exist! isAddHeader:%s", type, isAddHeader);
            return;
        }

        if (!isAlive && type == CALLBACK_INPUT) {
            MatrixLog.w(TAG, "[addFrameCallback] UIThreadMonitor is not alive!");
            return;
        }
        try {
            synchronized (callbackQueueLock) {
                Method method = null;
                switch (type) {
                    case CALLBACK_INPUT:
                        method = addInputQueue;
                        break;
                    case CALLBACK_ANIMATION:
                        method = addAnimationQueue;
                        break;
                    case CALLBACK_TRAVERSAL:
                        method = addTraversalQueue;
                        break;
                }
                if (null != method) {
                    method.invoke(callbackQueues[type], !isAddHeader ? SystemClock.uptimeMillis() : -1, callback, null);
                    callbackExist[type] = true;
                }
            }
        } catch (Exception e) {
            MatrixLog.e(TAG, e.toString());
        }
    }

    @Override
    public void run() {
        final long start = System.nanoTime();
        try {
            doFrameBegin(token);
            doQueueBegin(CALLBACK_INPUT);

            addFrameCallback(CALLBACK_ANIMATION, new Runnable() {

                @Override
                public void run() {
                    doQueueEnd(CALLBACK_INPUT);
                    doQueueBegin(CALLBACK_ANIMATION);
                }
            }, true);

            addFrameCallback(CALLBACK_TRAVERSAL, new Runnable() {

                @Override
                public void run() {
                    doQueueEnd(CALLBACK_ANIMATION);
                    doQueueBegin(CALLBACK_TRAVERSAL);
                }
            }, true);

        } finally {
            if (config.isDevEnv()) {
                MatrixLog.d(TAG, "[UIThreadMonitor#run] inner cost:%sns", System.nanoTime() - start);
            }
        }
    }
    //endregion

    //todo ?
    private long getIntendedFrameTimeNs(long defaultValue) {
        try {
            return ReflectUtils.reflectObject(vsyncReceiver, "mTimestampNanos", defaultValue);
        } catch (Exception e) {
            e.printStackTrace();
            MatrixLog.e(TAG, e.toString());
        }
        return defaultValue;
    }


    public long getInputEventCost() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Object obj = ReflectUtils.reflectObject(choreographer, "mFrameInfo", null);
            if (null == frameInfo) {
                frameInfo = ReflectUtils.reflectObject(obj, "frameInfo", null);
                if (null == frameInfo) {
                    frameInfo = ReflectUtils.reflectObject(obj, "mFrameInfo", new long[9]);
                }
            }
            long start = frameInfo[OLDEST_INPUT_EVENT];
            long end = frameInfo[NEWEST_INPUT_EVENT];
            return end - start;
        }
        return 0;
    }
}
