package com.tencent.matrix.trace.tracer;

import com.tencent.matrix.listeners.IAppForeground;

//ok is ok
public interface ITracer extends IAppForeground {

    boolean isAlive();

    void onStartTrace();

    void onCloseTrace();

}
