package sample.tencent.matrix.zp.base.arch;

import android.content.Context;
import android.util.Log;

//import wavely.base.data.event.AnalyticsAction;
//import wavely.base.data.event.AnalyticsFactory;
//import wavely.kernel.WavelyKernel;
//import wavely.sdk.platformtools.ProcessInfo;
//import wavely.sdk.utils.AdjustEventTool;
//import wavely.sdk.utils.Log;

/**
 * Created by jia on 19/2/13.
 */
public class AppStatusUtil {
    public static final String TAG = "app_status";
    public static long eachStartTime = 0;//每次启动的时间

    /**
     * 应用处于前台处理方法
     */
    public static void onAppStatusIsForeground(Context context) {
        Log.d(TAG, "=====app is Foreground ");
        // 激活埋点
//        WavelyKernel.wid = System.currentTimeMillis();
//        AnalyticsFactory.create().action(AnalyticsAction.ACTION_app_active).build();
//        AdjustEventTool.getInstance().trackEvent("app-active");
//        SharedPreferences sharedPreferences = ProcessInfo.getToolsPreferences();
//        SharedPreferences.Editor editor = sharedPreferences.edit();
//        boolean isFirst = sharedPreferences.getBoolean("isFirst", true);
//        if (isFirst) {
//            editor.putBoolean("isFirst", false);
//            editor.commit();
//            try {
//                AnalyticsFactory.create().action(AnalyticsAction.ACTION_app_first_active)
//                        .param("p4", String.valueOf(System.currentTimeMillis()))
//                        .param("p5", "Organic")
//                        .build();
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        }
        if (eachStartTime == 0) {//eachStartTime 赋值一次
            eachStartTime = System.currentTimeMillis();
        }
    }

    /**
     * 应用处于后台处理方法
     */
    public static void onAppStatusIsBackground() {
        Log.d(TAG, "=====app is Background ");
    }
}
