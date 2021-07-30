package sample.tencent.matrix.zp.base;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import sample.tencent.matrix.zp.base.arch.AppStatusUtil;
import sample.tencent.matrix.zp.base.arch.ReceiverUtils;

//import com.stone.cold.screenrecorder.rain.base.kernel.arch.AppStatusUtil;
//import com.stone.cold.screenrecorder.rain.base.kernel.arch.ReceiverUtils;


public abstract class BaseAppCompatActivity extends AppCompatActivity {
    /**
     * 检测app是否处于后台运行的标识
     */
    private static boolean isBackendRun = true;
    /**
     * 该广播用于接收用户按下HOME键的监听
     */
    private final BroadcastReceiver mHomeKeyEventReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (isBackendRun) {
                return;
            }
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)) {
                String reason = intent.getStringExtra("reason");
                if (TextUtils.equals(reason, "homekey")) {
                    //表示按了home键,程序到了后台
                    isBackendRun = true;
                    AppStatusUtil.onAppStatusIsBackground();
                }
            } else if (action.equals(Intent.ACTION_SCREEN_ON)) {
                if (isBackendRun) {
                    // ... 此处为APP从后台进入前台的处理
                    isBackendRun = false;
                    AppStatusUtil.onAppStatusIsForeground(context);
                }
            } else if (action.equals(Intent.ACTION_SCREEN_OFF)) {
                isBackendRun = true;
                AppStatusUtil.onAppStatusIsBackground();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // ... 注册用户按HOME键的广播接收器
        registerHomeKeyEventReceiver(this);
//        ARouter.getInstance().inject(this);//为啥
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);//为啥
//        ARouter.getInstance().inject(this);//为啥
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 判断当前是否为从后台进入前台的标记
        activityOnResume(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterHomeKeyEventReceiver(this);
    }

    private void activityOnResume(Context context) {
        if (isBackendRun) {
            // ... 此处为APP从后台进入前台的处理
            isBackendRun = false;
            AppStatusUtil.onAppStatusIsForeground(context);
        }
    }

    private void registerHomeKeyEventReceiver(Context context) {
        // 注册用户按HOME键的广播接收器
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);//home键
        ReceiverUtils.registerSystem(context, filter, mHomeKeyEventReceiver);
    }

    @SuppressWarnings("SameReturnValue")
    protected abstract int getContextViewId();

    private void unregisterHomeKeyEventReceiver(Context context) {
        // 解除注册用户按HOME键的广播接收器
        ReceiverUtils.unregisterSystem(context, mHomeKeyEventReceiver);
    }

    public void startFragmentWithNoAnimation(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(getContextViewId(), fragment, fragment.getClass().getSimpleName())
                .addToBackStack(fragment.getClass().getSimpleName())
                .commit();
    }

    public Fragment getCurrentFragment() {
        return (Fragment) getSupportFragmentManager().findFragmentById(getContextViewId());
    }
}
