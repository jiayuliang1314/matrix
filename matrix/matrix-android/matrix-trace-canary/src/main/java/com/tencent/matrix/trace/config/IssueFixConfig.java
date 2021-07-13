package com.tencent.matrix.trace.config;

//ok 没有引用
public class IssueFixConfig {
    private final static IssueFixConfig sInstance = new IssueFixConfig();
    private boolean enableFixSpApply;
    public static IssueFixConfig getsInstance() {
        return sInstance;
    }

    public boolean isEnableFixSpApply() {
        return enableFixSpApply;
    }

    public void setEnableFixSpApply(boolean enableFixSpApply) {
        this.enableFixSpApply = enableFixSpApply;
    }
}
