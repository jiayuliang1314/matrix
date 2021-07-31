package sample.tencent.matrix.zp.ui.fps;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.CompoundButton;

import androidx.lifecycle.ViewModelProviders;

import sample.tencent.matrix.R;
import sample.tencent.matrix.zp.base.BaseFragment;
import sample.tencent.matrix.zp.ui.InfosFragmentCallback;
import sample.tencent.matrix.zp.ui.MainFragmentViewModel;

public class FpsDetailFragment extends BaseFragment<MainFragmentViewModel>
        implements InfosFragmentCallback, CompoundButton.OnCheckedChangeListener,
        View.OnClickListener {
    public static final String TAG = "SettingsFragment";

    public static FpsDetailFragment newInstance() {
        Bundle args = new Bundle();
        FpsDetailFragment fragment = new FpsDetailFragment();
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
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean z2) {
    }

    @Override
    public void onRequestPermissionsResult(int i2, String[] strArr, int[] iArr) {
        super.onRequestPermissionsResult(i2, strArr, iArr);
    }

    @Override
    public void onClick(View view) {
    }

    @Override
    public void onActivityResult(int i2, int i3, Intent intent) {
        super.onActivityResult(i2, i3, intent);
    }
}
