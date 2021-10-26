/*
 * Tencent is pleased to support the open source community by making wechat-matrix available.
 * Copyright (C) 2021 THL A29 Limited, a Tencent company. All rights reserved.
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

package com.tencent.matrix.trace.util;

import android.os.Looper;

import com.tencent.matrix.util.DeviceUtil;

public class Utils {

    public static String getStack() {
        StackTraceElement[] trace = new Throwable().getStackTrace();
        return getStack(trace);
    }

    public static String getStack(StackTraceElement[] trace) {
        return getStack(trace, "", -1);
    }

    /**
     *
     * @param trace
     * 0 = {StackTraceElement@11585} "java.lang.Thread.sleep(Native Method)"
     * 1 = {StackTraceElement@11586} "java.lang.Thread.sleep(Thread.java:373)"
     * 2 = {StackTraceElement@11587} "java.lang.Thread.sleep(Thread.java:314)"
     * 3 = {StackTraceElement@11588} "android.os.SystemClock.sleep(SystemClock.java:127)"
     * 4 = {StackTraceElement@11589} "sample.tencent.matrix.trace.TestTraceMainActivity.L(TestTraceMainActivity.java:195)"
     * 5 = {StackTraceElement@11590} "sample.tencent.matrix.trace.TestTraceMainActivity.A(TestTraceMainActivity.java:141)"
     * 6 = {StackTraceElement@11591} "sample.tencent.matrix.trace.TestTraceMainActivity.testANR(TestTraceMainActivity.java:135)"
     * 7 = {StackTraceElement@11592} "java.lang.reflect.Method.invoke(Native Method)"
     * 8 = {StackTraceElement@11593} "android.view.View$DeclaredOnClickListener.onClick(View.java:6079)"
     * 9 = {StackTraceElement@11594} "android.view.View.performClick(View.java:7352)"
     * 10 = {StackTraceElement@11595} "android.widget.TextView.performClick(TextView.java:14230)"
     * 11 = {StackTraceElement@11596} "android.view.View.performClickInternal(View.java:7318)"
     * 12 = {StackTraceElement@11597} "android.view.View.access$3200(View.java:846)"
     * 13 = {StackTraceElement@11598} "android.view.View$PerformClick.run(View.java:27800)"
     * 14 = {StackTraceElement@11599} "android.os.Handler.handleCallback(Handler.java:873)"
     * 15 = {StackTraceElement@11600} "android.os.Handler.dispatchMessage(Handler.java:99)"
     * 16 = {StackTraceElement@11601} "android.os.Looper.loop(Looper.java:214)"
     * 17 = {StackTraceElement@11602} "android.app.ActivityThread.main(ActivityThread.java:7050)"
     * 18 = {StackTraceElement@11603} "java.lang.reflect.Method.invoke(Native Method)"
     * 19 = {StackTraceElement@11604} "com.android.internal.os.RuntimeInit$MethodAndArgsCaller.run(RuntimeInit.java:494)"
     * 20 = {StackTraceElement@11605} "com.android.internal.os.ZygoteInit.main(ZygoteInit.java:964)"
     * @param preFixStr
     * @param limit
     * @return
     *
     * at android.os.SystemClock:sleep(127)
     * at sample.tencent.matrix.trace.TestTraceMainActivity:L(195)
     * at sample.tencent.matrix.trace.TestTraceMainActivity:A(141)
     * at sample.tencent.matrix.trace.TestTraceMainActivity:testANR(135)
     * at java.lang.reflect.Method:invoke(-2)
     * at android.view.View$DeclaredOnClickListener:onClick(6079)
     * at android.view.View:performClick(7352)
     * at android.widget.TextView:performClick(14230)
     * at android.view.View:performClickInternal(7318)
     * at android.view.View:access$3200(846)
     * at android.view.View$PerformClick:run(27800)
     * at android.os.Handler:handleCallback(873)
     * at android.os.Handler:dispatchMessage(99)
     * at android.os.Looper:loop(214)
     * at android.app.ActivityThread:main(7050)
     */
    public static String getStack(StackTraceElement[] trace, String preFixStr, int limit) {
        if ((trace == null) || (trace.length < 3)) {
            return "";
        }
        if (limit < 0) {
            limit = Integer.MAX_VALUE;
        }
        StringBuilder t = new StringBuilder(" \n");
        //放弃前3行和后三行
        for (int i = 3; i < trace.length - 3 && i < limit; i++) {
            t.append(preFixStr);
            t.append("at ");
            t.append(trace[i].getClassName());
            t.append(":");
            t.append(trace[i].getMethodName());
            t.append("(" + trace[i].getLineNumber() + ")");
            t.append("\n");

        }
        return t.toString();
    }

    public static String getWholeStack(StackTraceElement[] trace, String preFixStr) {
        if ((trace == null) || (trace.length < 3)) {
            return "";
        }

        StringBuilder t = new StringBuilder(" \n");
        for (int i = 0; i < trace.length; i++) {
            t.append(preFixStr);
            t.append("at ");
            t.append(trace[i].getClassName());
            t.append(":");
            t.append(trace[i].getMethodName());
            t.append("(" + trace[i].getLineNumber() + ")");
            t.append("\n");

        }
        return t.toString();
    }

    public static String getWholeStack(StackTraceElement[] trace) {
        StringBuilder stackTrace = new StringBuilder();
        for (StackTraceElement stackTraceElement : trace) {
            stackTrace.append(stackTraceElement.toString()).append("\n");
        }
        return stackTrace.toString();
    }

    public static String getMainThreadJavaStackTrace() {
        StringBuilder stackTrace = new StringBuilder();
        for (StackTraceElement stackTraceElement : Looper.getMainLooper().getThread().getStackTrace()) {
            stackTrace.append(stackTraceElement.toString()).append("\n");
        }
        return stackTrace.toString();
    }

    public static String calculateCpuUsage(long threadMs, long ms) {
        if (threadMs <= 0) {//todo？
            return ms > 1000 ? "0%" : "100%";//todo？
        }

        if (threadMs >= ms) {
            return "100%";
        }

        return String.format("%.2f", 1.f * threadMs / ms * 100) + "%";
    }

    public static boolean isEmpty(String str) {
        return null == str || str.equals("");
    }

    //todo？
    public static int[] getProcessPriority(int pid) {
        String name = String.format("/proc/%s/stat", pid);
        int priority = Integer.MIN_VALUE;
        int nice = Integer.MAX_VALUE;
        try {
            String content = DeviceUtil.getStringFromFile(name).trim();
            String[] args = content.split(" ");
            if (args.length >= 19) {
                priority = Integer.parseInt(args[17].trim());
                nice = Integer.parseInt(args[18].trim());
            }
        } catch (Exception e) {
            return new int[]{priority, nice};
        }
        return new int[]{priority, nice};
    }


    public static String formatTime(final long timestamp) {
        return new java.text.SimpleDateFormat("[yy-MM-dd HH:mm:ss]").format(new java.util.Date(timestamp));
    }
}
