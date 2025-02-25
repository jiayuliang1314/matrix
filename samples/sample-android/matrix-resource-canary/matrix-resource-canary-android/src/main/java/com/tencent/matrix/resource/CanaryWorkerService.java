/*
 * Tencent is pleased to support the open source community by making wechat-matrix available.
 * Copyright (C) 2018 THL A29 Limited, a Tencent company. All rights reserved.
 * Licensed under the BSD 3-Clause License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tencent.matrix.resource;

import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.tencent.matrix.resource.analyzer.model.HeapDump;
import com.tencent.matrix.resource.dumper.DumpStorageManager;
import com.tencent.matrix.resource.hproflib.HprofBufferShrinker;
import com.tencent.matrix.util.MatrixLog;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.tencent.matrix.resource.common.utils.StreamUtil.closeQuietly;
import static com.tencent.matrix.resource.common.utils.StreamUtil.copyFileToStream;

/**
 * Created by tangyinsheng on 2017/7/11.
 */
//裁剪hprof，并上报
public class CanaryWorkerService extends MatrixJobIntentService {
    private static final String TAG = "Matrix.CanaryWorkerService";

    private static final int JOB_ID = 0xFAFBFCFD;
    private static final String ACTION_SHRINK_HPROF = "com.tencent.matrix.resource.worker.action.SHRINK_HPROF";
    private static final String EXTRA_PARAM_HEAPDUMP = "com.tencent.matrix.resource.worker.param.HEAPDUMP";

    //入口
    public static void shrinkHprofAndReport(Context context, HeapDump heapDump) {
        final Intent intent = new Intent(context, CanaryWorkerService.class);
        intent.setAction(ACTION_SHRINK_HPROF);
        intent.putExtra(EXTRA_PARAM_HEAPDUMP, heapDump);
        enqueueWork(context, CanaryWorkerService.class, JOB_ID, intent);
    }

    @Override
    protected void onHandleWork(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_SHRINK_HPROF.equals(action)) {
                try {
                    intent.setExtrasClassLoader(this.getClassLoader());
                    final HeapDump heapDump = (HeapDump) intent.getSerializableExtra(EXTRA_PARAM_HEAPDUMP);
                    if (heapDump != null) {
                        //裁剪并上报
                        doShrinkHprofAndReport(heapDump);
                    } else {
                        MatrixLog.e(TAG, "failed to deserialize heap dump, give up shrinking and reporting.");
                    }
                } catch (Throwable thr) {
                    MatrixLog.printErrStackTrace(TAG, thr, "failed to deserialize heap dump, give up shrinking and reporting.");
                }
            }
        }
    }

    private void doShrinkHprofAndReport(HeapDump heapDump) {
        final File hprofDir = heapDump.getHprofFile().getParentFile();
        //裁剪之后的Hprof文件名
        final File shrinkedHProfFile = new File(hprofDir, getShrinkHprofName(heapDump.getHprofFile()));
        //压缩文件
        final File zipResFile = new File(hprofDir, getResultZipName("dump_result_" + android.os.Process.myPid()));
        final File hprofFile = heapDump.getHprofFile();
        ZipOutputStream zos = null;
        try {
            long startTime = System.currentTimeMillis();
            //执行Hprof裁剪
            new HprofBufferShrinker().shrink(hprofFile, shrinkedHProfFile);
            MatrixLog.i(TAG, "shrink hprof file %s, size: %dk to %s, size: %dk, use time:%d",
                    hprofFile.getPath(), hprofFile.length() / 1024, shrinkedHProfFile.getPath(), shrinkedHProfFile.length() / 1024, (System.currentTimeMillis() - startTime));
            //打成压缩包
            zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipResFile)));
            //记录一些设备信息
            final ZipEntry resultInfoEntry = new ZipEntry("result.info");
            //裁剪后的Hprof文件
            final ZipEntry shrinkedHProfEntry = new ZipEntry(shrinkedHProfFile.getName());

            zos.putNextEntry(resultInfoEntry);
            final PrintWriter pw = new PrintWriter(new OutputStreamWriter(zos, StandardCharsets.UTF_8));
            pw.println("# Resource Canary Result Infomation. THIS FILE IS IMPORTANT FOR THE ANALYZER !!");
            pw.println("sdkVersion=" + Build.VERSION.SDK_INT);//系统版本
            pw.println("manufacturer=" + Build.MANUFACTURER);//厂商信息
            pw.println("hprofEntry=" + shrinkedHProfEntry.getName());//裁剪后Hprof文件名
            pw.println("leakedActivityKey=" + heapDump.getReferenceKey());//泄漏Activity实例的key
            pw.flush();
            zos.closeEntry();

            zos.putNextEntry(shrinkedHProfEntry);
            copyFileToStream(shrinkedHProfFile, zos);
            zos.closeEntry();

            //原始数据删除
            shrinkedHProfFile.delete();
            hprofFile.delete();

            MatrixLog.i(TAG, "process hprof file use total time:%d", (System.currentTimeMillis() - startTime));
            //CanaryResultService执行上报逻辑
            CanaryResultService.reportHprofResult(this, zipResFile.getAbsolutePath(), heapDump.getActivityName());
        } catch (IOException e) {
            MatrixLog.printErrStackTrace(TAG, e, "");
        } finally {
            closeQuietly(zos);
        }
    }

    //返回压缩后的 _shrink.hprof
    private String getShrinkHprofName(File origHprof) {
        final String origHprofName = origHprof.getName();
        final int extPos = origHprofName.indexOf(DumpStorageManager.HPROF_EXT);
        final String namePrefix = origHprofName.substring(0, extPos);
        return namePrefix + "_shrink" + DumpStorageManager.HPROF_EXT;
    }

    //返回zip todo 会不会重复
    private String getResultZipName(String prefix) {
        StringBuilder sb = new StringBuilder();
        sb.append(prefix).append('_')
                .append(new SimpleDateFormat("yyyyMMddHHmmss", Locale.ENGLISH).format(new Date()))
                .append(".zip");
        return sb.toString();
    }
}
