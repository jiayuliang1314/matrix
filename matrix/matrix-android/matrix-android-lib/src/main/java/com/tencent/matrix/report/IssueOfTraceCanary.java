package com.tencent.matrix.report;

public class IssueOfTraceCanary {

    private String stack;
    //    private String memory;//todo 检查
    private MemoryBean memoryBean;
    private String usage;
    //    private String dropLevel;//todo 检查
    private DropLevelBean dropLevelBean;
    private double cpu_app;
    private String scene;
    private String stackKey;
    private long mem;
    private long first_activity_create;
    private boolean is_warm_start_up;
    private String tag;
    //    private String dropSum;//todo 检查
    private DropLevelBean dropSumBean;
    private String process;
    private long cost;
    private int processNice;
    private long application_create;
    private double fps;
    private int processPriority;
    private int application_create_scene;
    private long processTimerSlack;
    private String threadStack;
    private long startup_duration;
    private String machine;
    private long mem_free;
    private int subType;
    private boolean isProcessForeground;
    private long time;
    private String detail;
//    private long dalvik_heap;
//    private long vm_size;
//    private long native_heap;
    private String key;

    @Override
    public String toString() {
        return "IssueOfTraceCanary{" +
                "stack='" + stack + '\'' +
                ", memoryBean=" + memoryBean +
                ", usage='" + usage + '\'' +
                ", dropLevelBean=" + dropLevelBean +
                ", cpu_app=" + cpu_app +
                ", scene='" + scene + '\'' +
                ", stackKey='" + stackKey + '\'' +
                ", mem=" + mem +
                ", first_activity_create=" + first_activity_create +
                ", is_warm_start_up=" + is_warm_start_up +
                ", tag='" + tag + '\'' +
                ", dropSumBean=" + dropSumBean +
                ", process='" + process + '\'' +
                ", cost=" + cost +
                ", processNice=" + processNice +
                ", application_create=" + application_create +
                ", fps=" + fps +
                ", processPriority=" + processPriority +
                ", application_create_scene=" + application_create_scene +
                ", processTimerSlack=" + processTimerSlack +
                ", threadStack='" + threadStack + '\'' +
                ", startup_duration=" + startup_duration +
                ", machine='" + machine + '\'' +
                ", mem_free=" + mem_free +
                ", subType=" + subType +
                ", isProcessForeground=" + isProcessForeground +
                ", time=" + time +
                ", detail='" + detail + '\'' +
                ", key='" + key + '\'' +
                '}';
    }

    public long getProcessTimerSlack() {
        return processTimerSlack;
    }

    public void setProcessTimerSlack(long processTimerSlack) {
        this.processTimerSlack = processTimerSlack;
    }

    public MemoryBean getMemoryBean() {
        return memoryBean;
    }

    public void setMemoryBean(MemoryBean memoryBean) {
        this.memoryBean = memoryBean;
    }

    public boolean isProcessForeground() {
        return isProcessForeground;
    }

    public void setProcessForeground(boolean processForeground) {
        isProcessForeground = processForeground;
    }

    public DropLevelBean getDropSumBean() {
        return dropSumBean;
    }

    public void setDropSumBean(DropLevelBean dropSumBean) {
        this.dropSumBean = dropSumBean;
    }

    public DropLevelBean getDropLevelBean() {
        return dropLevelBean;
    }

    public void setDropLevelBean(DropLevelBean dropLevelBean) {
        this.dropLevelBean = dropLevelBean;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getStack() {
        return stack;
    }

    public void setStack(String stack) {
        this.stack = stack;
    }

//    public String getMemory() {
//        return memory;
//    }
//
//    public void setMemory(String memory) {
//        this.memory = memory;
//    }

    public String getUsage() {
        return usage;
    }

    public void setUsage(String usage) {
        this.usage = usage;
    }

//    public String getDropLevel() {
//        return dropLevel;
//    }
//
//    public void setDropLevel(String dropLevel) {
//        this.dropLevel = dropLevel;
//    }

    public double getCpu_app() {
        return cpu_app;
    }

    public void setCpu_app(double cpu_app) {
        this.cpu_app = cpu_app;
    }

    public String getScene() {
        return scene;
    }

    public void setScene(String scene) {
        this.scene = scene;
    }

    public String getStackKey() {
        return stackKey;
    }

    public void setStackKey(String stackKey) {
        this.stackKey = stackKey;
    }

    public long getMem() {
        return mem;
    }

    public void setMem(long mem) {
        this.mem = mem;
    }

    public long getFirst_activity_create() {
        return first_activity_create;
    }

    public void setFirst_activity_create(long first_activity_create) {
        this.first_activity_create = first_activity_create;
    }

    public boolean isIs_warm_start_up() {
        return is_warm_start_up;
    }

    public void setIs_warm_start_up(boolean is_warm_start_up) {
        this.is_warm_start_up = is_warm_start_up;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

//    public String getDropSum() {
//        return dropSum;
//    }
//
//    public void setDropSum(String dropSum) {
//        this.dropSum = dropSum;
//    }

    public String getProcess() {
        return process;
    }

    public void setProcess(String process) {
        this.process = process;
    }

    public long getCost() {
        return cost;
    }

    public void setCost(long cost) {
        this.cost = cost;
    }

    public int getProcessNice() {
        return processNice;
    }

    public void setProcessNice(int processNice) {
        this.processNice = processNice;
    }

    public long getApplication_create() {
        return application_create;
    }

    public void setApplication_create(long application_create) {
        this.application_create = application_create;
    }

    public double getFps() {
        return fps;
    }

    public void setFps(double fps) {
        this.fps = fps;
    }

    public int getProcessPriority() {
        return processPriority;
    }

    public void setProcessPriority(int processPriority) {
        this.processPriority = processPriority;
    }

    public int getApplication_create_scene() {
        return application_create_scene;
    }

    public void setApplication_create_scene(int application_create_scene) {
        this.application_create_scene = application_create_scene;
    }

    public String getThreadStack() {
        return threadStack;
    }

    public void setThreadStack(String threadStack) {
        this.threadStack = threadStack;
    }

    public long getStartup_duration() {
        return startup_duration;
    }

    public void setStartup_duration(long startup_duration) {
        this.startup_duration = startup_duration;
    }

    public String getMachine() {
        return machine;
    }

    public void setMachine(String machine) {
        this.machine = machine;
    }

    public long getMem_free() {
        return mem_free;
    }

    public void setMem_free(long mem_free) {
        this.mem_free = mem_free;
    }

    public int getSubType() {
        return subType;
    }

    public void setSubType(int subType) {
        this.subType = subType;
    }

//    public boolean isIsProcessForeground() {
//        return isProcessForeground;
//    }
//
//    public void setIsProcessForeground(boolean isProcessForeground) {
//        this.isProcessForeground = isProcessForeground;
//    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

//    public long getDalvik_heap() {
//        return dalvik_heap;
//    }
//
//    public void setDalvik_heap(long dalvik_heap) {
//        this.dalvik_heap = dalvik_heap;
//    }
//
//    public long getVm_size() {
//        return vm_size;
//    }
//
//    public void setVm_size(long vm_size) {
//        this.vm_size = vm_size;
//    }
//
//    public long getNative_heap() {
//        return native_heap;
//    }
//
//    public void setNative_heap(long native_heap) {
//        this.native_heap = native_heap;
//    }
}
