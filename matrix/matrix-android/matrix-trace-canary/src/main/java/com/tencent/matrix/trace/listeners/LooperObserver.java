package com.tencent.matrix.trace.listeners;

import androidx.annotation.CallSuper;

//ok
//它是 Looper 的观察者，在 Looper 分发消息、刷新 UI 、vsync时回调，这几个回调方法也是 ANR、慢方法等模块的判断依据
public abstract class LooperObserver {

    private boolean isDispatchBegin = false;

    /**
     * 消息分发前回调
     *
     * @param beginNs    开始纳秒单位的时间
     * @param cpuBeginNs 开始毫秒单位的时间
     * @param token      开始纳秒单位的时间，也用做token
     */
    @CallSuper
    public void dispatchBegin(long beginNs, long cpuBeginNs, long token) {
        isDispatchBegin = true;
    }

    /**
     * 帧信息
     *
     * @param focusedActivity     activity
     * @param startNs             开始纳秒
     * @param endNs               结束纳秒
     * @param isVsyncFrame        是否是帧时间
     * @param intendedFrameTimeNs vsync开始的时间,校对时间
     * @param inputCostNs         输入事件耗时
     * @param animationCostNs     动画耗时
     * @param traversalCostNs     绘制耗时
     */
    public void doFrame(String focusedActivity, long startNs, long endNs, boolean isVsyncFrame, long intendedFrameTimeNs, long inputCostNs, long animationCostNs, long traversalCostNs) {

    }

    /**
     * 消息分发后回调
     *
     * @param beginNs      开始前纳秒
     * @param cpuBeginMs   开始前毫秒
     * @param endNs        结束时纳秒
     * @param cpuEndMs     结束时毫秒
     * @param token        开始纳秒单位的时间，也用做token
     * @param isVsyncFrame 是否是vsync消息
     */
    @CallSuper
    public void dispatchEnd(long beginNs, long cpuBeginMs, long endNs, long cpuEndMs, long token, boolean isVsyncFrame) {
        isDispatchBegin = false;
    }

    public boolean isDispatchBegin() {
        return isDispatchBegin;
    }
}
