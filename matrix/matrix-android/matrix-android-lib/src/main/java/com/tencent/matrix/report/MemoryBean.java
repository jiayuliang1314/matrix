package com.tencent.matrix.report;

public class MemoryBean {
    private long native_heap;
    private long dalvik_heap;
    private long vm_size;

    public long getNative_heap() {
        return native_heap;
    }

    public void setNative_heap(long native_heap) {
        this.native_heap = native_heap;
    }

    public long getDalvik_heap() {
        return dalvik_heap;
    }

    public void setDalvik_heap(long dalvik_heap) {
        this.dalvik_heap = dalvik_heap;
    }

    public long getVm_size() {
        return vm_size;
    }

    public void setVm_size(long vm_size) {
        this.vm_size = vm_size;
    }
}
