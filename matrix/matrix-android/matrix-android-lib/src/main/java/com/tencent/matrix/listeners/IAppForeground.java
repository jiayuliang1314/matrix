package com.tencent.matrix.listeners;

public interface IAppForeground {
    //是否是前台
    void onForeground(boolean isForeground);
}
