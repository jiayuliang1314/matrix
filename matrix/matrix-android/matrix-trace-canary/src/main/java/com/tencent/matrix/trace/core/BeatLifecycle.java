package com.tencent.matrix.trace.core;
//ok
public interface BeatLifecycle {

    void onStart();

    void onStop();

    boolean isAlive();
}
