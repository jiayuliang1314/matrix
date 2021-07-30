package sample.tencent.matrix.zp.base.arch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

/**
 * Created by monch on 2016/11/1.
 */

public class ReceiverUtils {

    private static final String TAG = "ReceiverUtils";

    /**
     * 注册广播
     * @param context 上下文
     * @param receiver 接收器
     * @param actions 事件组
     */
    public static void register(Context context, BroadcastReceiver receiver, String... actions) {
        if (context == null || receiver == null || actions == null || actions.length == 0) {
            return;
        }
        IntentFilter filter = new IntentFilter();
        if (actions.length == 1) {
            filter.addAction(actions[0]);
        } else {
            for (String string : actions) {
                filter.addAction(string);
            }
        }
        LocalBroadcastManager.getInstance(context).registerReceiver(receiver, filter);
    }

    /**
     * 解除注册广播
     * @param context 上下文
     * @param receivers 接收器组
     */
    public static void unregister(Context context, BroadcastReceiver... receivers) {
        if (context == null || receivers == null || receivers.length == 0) {
            return;
        }
        for (BroadcastReceiver r : receivers) {
            try {
                LocalBroadcastManager.getInstance(context).unregisterReceiver(r);
            } catch (Exception e) {
                Log.i(TAG, "unregister error", e);
            }
        }
    }

    /**
     * 注册系统广播
     * @param context
     * @param filter
     * @param receiver
     */
    public static void registerSystem(Context context, IntentFilter filter, BroadcastReceiver receiver) {
        if (context == null || filter == null || receiver == null) {
            return;
        }
        context.registerReceiver(receiver, filter);
    }

    /**
     * 解除注册系统广播
     * @param context
     * @param receivers
     */
    public static void unregisterSystem(Context context, BroadcastReceiver... receivers) {
        if (context == null || receivers == null || receivers.length == 0) {
            return;
        }
        for (BroadcastReceiver r : receivers) {
            try {
                context.unregisterReceiver(r);
            } catch (Exception e) {
                Log.i(TAG, "unregister error", e);
            }
        }
    }

    /**
     * 发送系统广播
     * @param context
     * @param intent
     */
    public static void sendBroadcastSystem(Context context, Intent intent) {
        intent.setFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        context.sendBroadcast(intent);
    }

    /**
     * 发送广播
     * @param context 上下文
     * @param intent 事件
     */
    public static void sendBroadcast(Context context, Intent intent) {
        intent.setFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

}
