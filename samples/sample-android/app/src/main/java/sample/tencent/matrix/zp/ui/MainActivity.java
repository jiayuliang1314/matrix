package sample.tencent.matrix.zp.ui;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;

import sample.tencent.matrix.R;
import sample.tencent.matrix.zp.base.BaseAppCompatActivity;

/**
 * @author jia
 */
public class MainActivity extends BaseAppCompatActivity {
    //region 参数
    protected MainFragment mainFragment;
    private Handler handler = new Handler();
    private int tab = 0;
    //endregion

    //region start方法
    public static void start(Context context, int tab) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra("tabPosition", tab);
        if (!(context instanceof Activity)) {
            intent.setFlags(268435456);
            startActivityFromNotActivity(context, intent);
            return;
        }
        context.startActivity(intent);
    }

    public static void startActivityFromNotActivity(Context context, Intent intent) {
        try {
            PendingIntent.getActivity(context, (int) (Math.random() * 9999.0d), intent, 134217728).send();
        } catch (PendingIntent.CanceledException e) {
            e.printStackTrace();
            context.startActivity(intent);
        }
    }
    //endregion

    //region common函数
    @Override
    protected int getContextViewId() {
        return R.id.home_container;
    }

    public MainActivityViewModel getMainViewModel() {
        return ViewModelProviders.of(this).get(MainActivityViewModel.class);
    }
    //endregion

    //region 生命周期
    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                getWindow().setStatusBarColor(getResources().getColor(R.color.screenrecorder_au));
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        setContentView(R.layout.matrix_kanzhun_activity_main);
        initUi();
        processExtraData(getIntent());
    }

    @Override
    public void onResume() {
        super.onResume();
        try {
            overridePendingTransition(0, 0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onPause() {
        try {
            overridePendingTransition(0, 0);
        } catch (Exception e) {
            e.printStackTrace();
        }
        super.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        handler = null;
    }
    //endregion

    private void initUi() {
        mainFragment = MainFragment.newInstance(tab);
        startFragmentWithNoAnimation(mainFragment);
    }

    @Override
    public void onBackPressed() {
        if (getSupportFragmentManager().getBackStackEntryCount() == 1 && !isFinishing()) {
            if (mainFragment.popBack()) {
                return;
            }
            finish();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Fragment fragment = getCurrentFragment();
        if (fragment != null) {
            fragment.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Fragment fragment = getCurrentFragment();
        if (fragment != null) {
            fragment.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        processExtraData(intent);
    }

    private void processExtraData(Intent intent) {
        if (intent == null || intent.getExtras() == null) {
            return;
        }
        if (intent.getExtras().getBoolean("exit")) {
            finish();
            return;
        }
        this.tab = intent.getIntExtra("tabPosition", this.mainFragment == null ? 0 : this.mainFragment.getCurrentItemPosition());

        if (mainFragment != null) {
            mainFragment.setTab(this.tab);
        }
    }
}
