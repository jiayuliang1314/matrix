package com.tencent.matrix.resource.processor;

import com.tencent.matrix.resource.analyzer.model.DestroyedActivityInfo;
import com.tencent.matrix.resource.analyzer.model.HeapDump;
import com.tencent.matrix.resource.watcher.ActivityRefWatcher;
import com.tencent.matrix.util.MatrixLog;

import java.io.File;

/**
 * Created by Yves on 2021/3/4
 * ok ok
 */
public class AutoDumpProcessor extends BaseLeakProcessor {

    private static final String TAG = "Matrix.LeakProcessor.AutoDump";

    public AutoDumpProcessor(ActivityRefWatcher watcher) {
        super(watcher);
    }

    @Override
    public boolean process(DestroyedActivityInfo destroyedActivityInfo) {
//        dump
        final File hprofFile = getHeapDumper().dumpHeap(true);
        if (hprofFile != null) {
//标记
            getWatcher().markPublished(destroyedActivityInfo.mActivityName);
            getWatcher().triggerGc();
            //
            final HeapDump heapDump = new HeapDump(hprofFile, destroyedActivityInfo.mKey, destroyedActivityInfo.mActivityName);
            getHeapDumpHandler().process(heapDump);
        } else {
            MatrixLog.i(TAG, "heap dump for further analyzing activity with key [%s] was failed, just ignore.",
                    destroyedActivityInfo.mKey);
        }
        return true;
    }
}
