package com.tencent.matrix.trace.extension;

//Extension
public class MatrixTraceExtension {
    boolean transformInjectionForced;   //强制transfrom注入
    String baseMethodMapFile;           //base的mapping文件
    String blackListFile;               //不插桩的路径
    String whiteListFile;               //插桩的路径
    String customDexTransformName;      //自定义transform名字
    boolean skipCheckClass = true; // skip by default

    public String getWhiteListFile() {
        return whiteListFile;
    }

    public void setWhiteListFile(String whiteListFile) {
        this.whiteListFile = whiteListFile;
    }

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

    //Injection 注射; 大量资金的投入; (液体)注入，喷入;
    public boolean isTransformInjectionForced() {
        return transformInjectionForced;
    }

    public boolean isEnable() {
        return enable;
    }

    public boolean isSkipCheckClass() {
        return skipCheckClass;
    }
}
