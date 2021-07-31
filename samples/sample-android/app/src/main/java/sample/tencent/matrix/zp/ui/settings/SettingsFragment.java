package sample.tencent.matrix.zp.ui.settings;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatCheckBox;
import androidx.lifecycle.ViewModelProviders;

import sample.tencent.matrix.R;
import sample.tencent.matrix.zp.base.BaseFragment;
import sample.tencent.matrix.zp.ui.MainFragmentViewModel;

import static sample.tencent.matrix.zp.ui.settings.CustomDialog.ResourceCanaryBackTime;
import static sample.tencent.matrix.zp.ui.settings.CustomDialog.ResourceCanaryCheckTime;
import static sample.tencent.matrix.zp.ui.settings.CustomDialog.ResourceCanaryForegroundTime;
import static sample.tencent.matrix.zp.ui.settings.CustomDialog.ResourceCanaryModeType;

public class SettingsFragment extends BaseFragment<MainFragmentViewModel>
        implements SettingsFragmentCallback, CompoundButton.OnCheckedChangeListener,
        View.OnClickListener {
    public static final String TAG = "SettingsFragment";

    //resource canary
    public AppCompatCheckBox resource_canary_open_value;
    public AppCompatCheckBox resource_canary_function_beat_io_value;

    public RelativeLayout resource_canary_model_rl;
    public TextView resource_canary_model_value;
    public RelativeLayout resource_canary_foregroundtime_rl;
    public TextView resource_canary_foregroundtime_value;
    public RelativeLayout resource_canary_background_time_rl;
    public TextView resource_canary_background_time_value;
    public RelativeLayout resource_canary_num_rl;
    public TextView resource_canary_num_value;
    //trace canary
    public AppCompatCheckBox trace_canary_anr_open_value;
    public AppCompatCheckBox trace_canary_fps_open_value;
    public AppCompatCheckBox trace_canary_startup_open_value;
    public AppCompatCheckBox trace_canary_evilmethod_open_value;
    public AppCompatCheckBox trace_canary_float_open_value;
    //notification
    public AppCompatCheckBox matrix_notification_open_value;
    CustomDialog customDialogReCanaryMode;
    CustomDialog customDialogReCanaryFor;
    CustomDialog customDialogReCanaryBack;
    CustomDialog customDialogReCanaryNum;

    public static SettingsFragment newInstance() {
        Bundle args = new Bundle();
        SettingsFragment fragment = new SettingsFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public int getLayoutId() {
        return R.layout.matrix_kanzhun_settings;
    }

    /**
     * 如果多个Fragment共享数据，此处this，需要改成getBaseFragmentActivity()
     **/
    @Override
    public MainFragmentViewModel getViewModel() {
        return ViewModelProviders.of(getBaseFragmentActivity()).get(MainFragmentViewModel.class);
    }

    @Override
    public Object getCallback() {
        return this;
    }

    /**
     * getData fragment 可见的时候调用，可用于刷新一些容易改动的数据
     **/
    @Override
    public void getData() {
        super.getData();
    }

    /**
     * initView fragment生命周期就调用一次
     **/
    @Override
    public void initView() {
        super.initView();
        resource_canary_open_value = getRootView().findViewById(R.id.resource_canary_open_value);
        resource_canary_function_beat_io_value = getRootView().findViewById(R.id.resource_canary_function_beat_io_value);
        resource_canary_model_rl = getRootView().findViewById(R.id.resource_canary_model_rl);
        resource_canary_model_value = getRootView().findViewById(R.id.resource_canary_model_value);
        resource_canary_foregroundtime_rl = getRootView().findViewById(R.id.resource_canary_foregroundtime_rl);
        resource_canary_foregroundtime_value = getRootView().findViewById(R.id.resource_canary_foregroundtime_value);
        resource_canary_background_time_rl = getRootView().findViewById(R.id.resource_canary_background_time_rl);
        resource_canary_background_time_value = getRootView().findViewById(R.id.resource_canary_background_time_value);
        resource_canary_num_rl = getRootView().findViewById(R.id.resource_canary_num_rl);
        resource_canary_num_value = getRootView().findViewById(R.id.resource_canary_num_value);
        trace_canary_anr_open_value = getRootView().findViewById(R.id.trace_canary_anr_open_value);
        trace_canary_fps_open_value = getRootView().findViewById(R.id.trace_canary_fps_open_value);
        trace_canary_startup_open_value = getRootView().findViewById(R.id.trace_canary_startup_open_value);
        trace_canary_evilmethod_open_value = getRootView().findViewById(R.id.trace_canary_evilmethod_open_value);
        trace_canary_float_open_value = getRootView().findViewById(R.id.trace_canary_float_open_value);
        matrix_notification_open_value = getRootView().findViewById(R.id.matrix_notification_open_value);

        resource_canary_open_value.setOnCheckedChangeListener(this);
        resource_canary_function_beat_io_value.setOnCheckedChangeListener(this);
        trace_canary_anr_open_value.setOnCheckedChangeListener(this);
        trace_canary_fps_open_value.setOnCheckedChangeListener(this);
        trace_canary_startup_open_value.setOnCheckedChangeListener(this);
        trace_canary_evilmethod_open_value.setOnCheckedChangeListener(this);
        trace_canary_float_open_value.setOnCheckedChangeListener(this);
        matrix_notification_open_value.setOnCheckedChangeListener(this);

        resource_canary_model_rl.setOnClickListener(this);
        resource_canary_foregroundtime_rl.setOnClickListener(this);
        resource_canary_background_time_rl.setOnClickListener(this);
        resource_canary_num_rl.setOnClickListener(this);

    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean z2) {
        switch (buttonView.getId()) {
            case R.id.resource_canary_open_value:
                break;
            case R.id.resource_canary_function_beat_io_value:
                break;
            case R.id.trace_canary_anr_open_value:
                break;
            case R.id.trace_canary_fps_open_value:
                break;
            case R.id.trace_canary_startup_open_value:
                break;
            case R.id.trace_canary_evilmethod_open_value:
                break;
            case R.id.trace_canary_float_open_value:
                break;
            case R.id.matrix_notification_open_value:
                break;
        }
    }

    @Override
    public void onRequestPermissionsResult(int i2, String[] strArr, int[] iArr) {
        super.onRequestPermissionsResult(i2, strArr, iArr);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.resource_canary_model_rl:
                if (this.customDialogReCanaryMode == null) {
                    this.customDialogReCanaryMode = new CustomDialog(this.getBaseFragmentActivity(), "检测模式", ResourceCanaryModeType, resource_canary_model_value);
                }
                if (!this.customDialogReCanaryMode.isShowing()) {
                    this.customDialogReCanaryMode.show();
                    return;
                }
                break;
            case R.id.resource_canary_foregroundtime_rl:
                if (this.customDialogReCanaryFor == null) {
                    this.customDialogReCanaryFor = new CustomDialog(this.getBaseFragmentActivity(), "前台检测时间", ResourceCanaryForegroundTime, resource_canary_foregroundtime_value);
                }
                if (!this.customDialogReCanaryFor.isShowing()) {
                    this.customDialogReCanaryFor.show();
                    return;
                }
                break;
            case R.id.resource_canary_background_time_rl:
                if (this.customDialogReCanaryBack == null) {
                    this.customDialogReCanaryBack = new CustomDialog(this.getBaseFragmentActivity(), "后台检测时间", ResourceCanaryBackTime, resource_canary_background_time_value);
                }
                if (!this.customDialogReCanaryBack.isShowing()) {
                    this.customDialogReCanaryBack.show();
                    return;
                }
                break;
            case R.id.resource_canary_num_rl:
                if (this.customDialogReCanaryNum == null) {
                    this.customDialogReCanaryNum = new CustomDialog(this.getBaseFragmentActivity(), "检测多少次才算泄漏", ResourceCanaryCheckTime, resource_canary_num_value);
                }
                if (!this.customDialogReCanaryNum.isShowing()) {
                    this.customDialogReCanaryNum.show();
                    return;
                }
                break;
        }
    }

    @Override
    public void onActivityResult(int i2, int i3, Intent intent) {
        super.onActivityResult(i2, i3, intent);
    }

    public void restoreData() {

    }

    public void storeData() {

    }

}
