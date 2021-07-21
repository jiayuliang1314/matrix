package com.tencent.matrix.resource.processor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;

import com.tencent.matrix.resource.R;
import com.tencent.matrix.resource.analyzer.model.ActivityLeakResult;
import com.tencent.matrix.resource.analyzer.model.DestroyedActivityInfo;
import com.tencent.matrix.resource.config.ResourceConfig;
import com.tencent.matrix.resource.config.SharePluginInfo;
import com.tencent.matrix.resource.watcher.ActivityRefWatcher;
import com.tencent.matrix.util.MatrixHandlerThread;
import com.tencent.matrix.util.MatrixLog;
import com.tencent.matrix.util.MatrixUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static android.os.Build.VERSION.SDK_INT;

/**
 * X process leaked -> send notification -> main process activity
 * -> dump and analyse in X process -> show result in main process activity
 * Created by Yves on 2021/3/4
 */
public class ManualDumpProcessor extends BaseLeakProcessor {

    private static final String TAG = "Matrix.LeakProcessor.ManualDump";

    private static final int NOTIFICATION_ID = 0x110;

    private final String mTargetActivity;

    private final NotificationManager mNotificationManager;

    private final List<DestroyedActivityInfo> mLeakedActivities = new ArrayList<>();

    private boolean isMuted;

    public ManualDumpProcessor(ActivityRefWatcher watcher, String targetActivity) {
        super(watcher);
        //处理activity
        mTargetActivity = targetActivity;
        //notification
        mNotificationManager = (NotificationManager) watcher.getContext().getSystemService(Context.NOTIFICATION_SERVICE);
        //注册BroadcastReceiver
        ManualDumpProcessorHelper.install(watcher.getContext(), this);
    }

    @Override
    public boolean process(DestroyedActivityInfo destroyedActivityInfo) {
        final Context context = getWatcher().getContext();
        //gc了以下
        getWatcher().triggerGc();
        //如果回收了返回true
        if (destroyedActivityInfo.mActivityRef.get() == null) {
            MatrixLog.v(TAG, "activity with key [%s] was already recycled.", destroyedActivityInfo.mKey);
            return true;
        }
        //保存了下泄漏的信息
        mLeakedActivities.add(destroyedActivityInfo);

        MatrixLog.i(TAG, "show notification for activity leak. %s", destroyedActivityInfo.mActivityName);
        //？todo 不知道干哈的
        if (isMuted) {
            MatrixLog.i(TAG, "is muted, won't show notification util process reboot");
            return true;
        }
        //发送通知
        //通知点击之后跳转的activity
        Intent targetIntent = new Intent();
        targetIntent.setClassName(getWatcher().getContext(), mTargetActivity);
        targetIntent.putExtra(SharePluginInfo.ISSUE_ACTIVITY_NAME, destroyedActivityInfo.mActivityName);
        targetIntent.putExtra(SharePluginInfo.ISSUE_REF_KEY, destroyedActivityInfo.mKey);
        targetIntent.putExtra(SharePluginInfo.ISSUE_LEAK_PROCESS, MatrixUtil.getProcessName(context));

        //点击之后的响应pendingintent
        PendingIntent pIntent = PendingIntent.getActivity(context, 0, targetIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        //title
        String dumpingHeapTitle = context.getString(R.string.resource_canary_leak_tip);
        ResourceConfig config = getWatcher().getResourcePlugin().getConfig();
        //content
        String dumpingHeapContent =
                String.format(Locale.getDefault(), "[%s] has leaked for [%s]min!!!",
                        destroyedActivityInfo.mActivityName,
                        TimeUnit.MILLISECONDS.toMinutes(
                                config.getScanIntervalMillis() * config.getMaxRedetectTimes()));

        Notification.Builder builder = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            builder = new Notification.Builder(context, getNotificationChannelIdCompat(context));
        } else {
            builder = new Notification.Builder(context);
        }
        builder.setContentTitle(dumpingHeapTitle)
                .setPriority(Notification.PRIORITY_DEFAULT)
                .setStyle(new Notification.BigTextStyle().bigText(dumpingHeapContent))
                .setContentIntent(pIntent)
                .setAutoCancel(true)
                .setSmallIcon(R.drawable.ic_launcher)
                .setWhen(System.currentTimeMillis());

        Notification notification = builder.build();
        //发送通知
        mNotificationManager.notify(
                NOTIFICATION_ID + destroyedActivityInfo.mKey.hashCode(), notification);
        //上报了问题
        publishIssue(destroyedActivityInfo.mActivityName, destroyedActivityInfo.mKey);
        MatrixLog.i(TAG, "shown notification!!!3");
        return true;
    }

    //创建通知channel
    private String getNotificationChannelIdCompat(Context context) {
        if (SDK_INT >= Build.VERSION_CODES.O) {
            String channelName = "com.tencent.matrix.resource.processor.ManualDumpProcessor";
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel notificationChannel = notificationManager.getNotificationChannel(channelName);
            if (notificationChannel == null) {
                MatrixLog.v(TAG, "create channel");
                notificationChannel = new NotificationChannel(channelName, channelName, NotificationManager.IMPORTANCE_HIGH);
                notificationManager.createNotificationChannel(notificationChannel);
            }
            return channelName;
        }
        return null;
    }

    public void setMuted(boolean mute) {
        isMuted = mute;
    }

    /**
     * run in leaked process
     *
     * @param activity
     * @param refString
     * @return ManualDumpData 包含dump文件+引用链
     */
    private ManualDumpData dumpAndAnalyse(String activity, String refString) {
        long dumpBegin = System.currentTimeMillis();

        getWatcher().triggerGc();

        File file = getHeapDumper().dumpHeap(false);
        if (file == null || file.length() <= 0) {
//            publishIssue(SharePluginInfo.IssueType.ERR_FILE_NOT_FOUND, activity, refString, "file is null", "0");
            MatrixLog.e(TAG, "file is null!");
            return null;
        }

        MatrixLog.i(TAG, String.format("dump cost=%sms refString=%s path=%s",
                System.currentTimeMillis() - dumpBegin, refString, file.getAbsolutePath()));

        long analyseBegin = System.currentTimeMillis();
        try {
            final ActivityLeakResult result = analyze(file, refString);
            MatrixLog.i(TAG, String.format("analyze cost=%sms refString=%s",
                    System.currentTimeMillis() - analyseBegin, refString));

            String refChain = result.toString();
            if (result.mLeakFound) {
//                publishIssue(SharePluginInfo.IssueType.LEAK_FOUND, activity, refString, refChain, String.valueOf(System.currentTimeMillis() - dumpBegin));
                MatrixLog.i(TAG, "leakFound,refcChain = %s", refChain);
                return new ManualDumpData(file.getAbsolutePath(), refChain);
            } else {
                MatrixLog.i(TAG, "leak not found");
                return new ManualDumpData(file.getAbsolutePath(), null);
            }
        } catch (OutOfMemoryError error) {
//            publishIssue(SharePluginInfo.IssueType.ERR_ANALYSE_OOM, activity, refString, "OutOfMemoryError", "0");
            MatrixLog.printErrStackTrace(TAG, error.getCause(), "");
        }
        return null;
    }

    //单纯上报问题
    private void publishIssue(String activity, String refKey) {
        publishIssue(SharePluginInfo.IssueType.LEAK_FOUND, ResourceConfig.DumpMode.MANUAL_DUMP, activity, refKey, "manual_dump", "0");
    }

    /**
     * multi process dump helper.
     * 多进程dump帮组类
     */
    public static class ManualDumpProcessorHelper extends BroadcastReceiver {
        //permission
        private static final String DUMP_PERMISSION_SUFFIX = ".manual.dump";

        private static final String ACTION_DUMP = "com.tencent.matrix.manual.dump";
        private static final String ACTION_RESULT = "com.tencent.matrix.manual.result";

        private static final String KEY_RESULT_PROCESS = "result_process";
        private static final String KEY_LEAK_ACTIVITY = "leak_activity";
        private static final String KEY_LEAK_PROCESS = "leak_process";
        private static final String KEY_LEAK_REFKEY = "leak_refkey";
        private static final String KEY_HPROF_PATH = "hprof_path";
        private static final String KEY_REF_CHAIN = "ref_chain";
        //是否注册了BroadcastReceiver
        private static boolean hasInstalled;

        private static ManualDumpProcessor sProcessor;
        private static IResultListener sListener; // only not null in process who called dumpAndAnalyse

        @Override
        public void onReceive(final Context context, final Intent intent) {
            if (intent == null) {
                MatrixLog.e(TAG, "intent is null");
                return;
            }

            MatrixHandlerThread.getDefaultHandler().postAtFrontOfQueue(new Runnable() {
                @Override
                public void run() {
                    if (ACTION_DUMP.equals(intent.getAction())) {
                        //进程
                        String leakProcess = intent.getStringExtra(KEY_LEAK_PROCESS);
                        String currentProcess = MatrixUtil.getProcessName(context);
                        //如果是这个进程泄漏了
                        if (!currentProcess.equals(leakProcess)) {
                            MatrixLog.v(TAG, "ACTION_DUMP: current process [%s] is NOT leaked process [%s]", currentProcess, leakProcess);
                            return;
                        }

                        // leaked process
                        MatrixLog.v(TAG, "ACTION_DUMP: current process [%s] is leaked process [%s]", currentProcess, leakProcess);

                        String leakActivity = intent.getStringExtra(KEY_LEAK_ACTIVITY);
                        String refKey = intent.getStringExtra(KEY_LEAK_REFKEY);
                        //dump信息并分析
                        ManualDumpData data = sProcessor.dumpAndAnalyse(leakActivity, refKey);
                        Intent resultIntent = new Intent(ACTION_RESULT);
                        if (data != null) {
                            resultIntent.putExtra(KEY_HPROF_PATH, data.hprofPath);
                            resultIntent.putExtra(KEY_REF_CHAIN, data.refChain);
                        }
                        String resultProcess = intent.getStringExtra(KEY_RESULT_PROCESS);
                        resultIntent.putExtra(KEY_RESULT_PROCESS, resultProcess);
                        context.sendBroadcast(resultIntent,
                                context.getPackageName() + DUMP_PERMISSION_SUFFIX);
                    } else if (ACTION_RESULT.equals(intent.getAction())) {
                        // result process
                        final String resultProcess = intent.getStringExtra(KEY_RESULT_PROCESS);
                        final String currentProcess = MatrixUtil.getProcessName(context);
                        if (!currentProcess.equals(resultProcess)) {
                            MatrixLog.v(TAG, "ACTION_RESULT: current process [%s] is NOT result process [%s]", currentProcess, resultProcess);
                            return;
                        }

                        MatrixLog.v(TAG, "ACTION_RESULT: current process [%s] is result process [%s]", currentProcess, resultProcess);

                        // generally, sListener must be NOT null
                        if (sListener == null) {
                            throw new NullPointerException("result listener is null!!!");
                        }

                        final String hprofPath = intent.getStringExtra(KEY_HPROF_PATH);
                        if (hprofPath == null) {
                            sListener.onFailed();
                            return;
                        }
                        final String refChain = intent.getStringExtra(KEY_REF_CHAIN);
                        sListener.onSuccess(hprofPath, refChain);
                        sListener = null;
                    }
                }
            });
        }

        //注册BroadcastReceiver
        private static void install(Context context, ManualDumpProcessor processor) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(ACTION_DUMP);
            filter.addAction(ACTION_RESULT);
            final String dumpPermission = context.getPackageName() + DUMP_PERMISSION_SUFFIX;
            //注册BroadcastReceiver
            context.registerReceiver(new ManualDumpProcessorHelper(), filter, dumpPermission, null);
            MatrixLog.d(TAG, "[%s] DUMP_PERMISSION is %s", MatrixUtil.getProcessName(context), dumpPermission);
            hasInstalled = true;
            sProcessor = processor;
        }

        //nouse
        public static void dumpAndAnalyse(Context context, String leakProcess, String activity, String refKey, IResultListener resultListener) {
            if (!hasInstalled) {
                throw new IllegalStateException("ManualDumpProcessorHelper was not installed yet!!! maybe your target activity is not running in right process.");
            }
            final String currentProcess = MatrixUtil.getProcessName(context);
            if (currentProcess.equalsIgnoreCase(leakProcess)) {
                // dump and analyze for current process
                ManualDumpData data = sProcessor.dumpAndAnalyse(activity, refKey);
                if (data == null) {
                    resultListener.onFailed();
                } else {
                    resultListener.onSuccess(data.hprofPath, data.refChain);
                }
            } else {
                sListener = resultListener;
                MatrixLog.v(TAG, "[%s] send broadcast with permission: %s", currentProcess,
                        context.getPackageName() + DUMP_PERMISSION_SUFFIX);
                Intent intent = new Intent(ACTION_DUMP);
                intent.putExtra(KEY_LEAK_PROCESS, leakProcess);
                intent.putExtra(KEY_LEAK_ACTIVITY, activity);
                intent.putExtra(KEY_LEAK_REFKEY, refKey);
                intent.putExtra(KEY_RESULT_PROCESS, currentProcess);
                context.sendBroadcast(intent, context.getPackageName() + DUMP_PERMISSION_SUFFIX);
            }
        }
    }

    public interface IResultListener {
        void onSuccess(String hprof, String leakReference);

        void onFailed();
    }

    /**
     * hprof文件路径
     * 链
     */
    public static class ManualDumpData {
        public final String hprofPath;
        public final String refChain;

        public ManualDumpData(String hprofPath, String refChain) {
            this.hprofPath = hprofPath;
            this.refChain = refChain;
        }
    }
}
