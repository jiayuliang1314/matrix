package com.tencent.matrix.trace.extension;

//Extension
public class MatrixTraceExtension {
    boolean transformInjectionForced;
    String baseMethodMapFile;
    String blackListFile;
    String customDexTransformName;

    boolean enable;                     //开关

//    public void setEnable(boolean enable) {
//        this.enable = enable;
//        onTraceEnabled(enable);
//    }

    public String getBaseMethodMapFile() {
        return baseMethodMapFile;
    }

    public String getBlackListFile() {
        return blackListFile;
    }

    public String getCustomDexTransformName() {
        return customDexTransformName;
    }

    public boolean isTransformInjectionForced() {
        return transformInjectionForced;
    }

    public boolean isEnable() {
        return enable;
    }
}
