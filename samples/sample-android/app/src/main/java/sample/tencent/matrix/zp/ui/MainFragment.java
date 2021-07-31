package sample.tencent.matrix.zp.ui;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatCheckBox;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;

import sample.tencent.matrix.R;
import sample.tencent.matrix.zp.base.BaseFragment;
import sample.tencent.matrix.zp.event.MessageEventChangeMainTab;
import sample.tencent.matrix.zp.ui.fps.FpsFragment;
import sample.tencent.matrix.zp.ui.issues.IssuesFragment;
import sample.tencent.matrix.zp.ui.settings.SettingsFragment;
import sample.tencent.matrix.zp.view.vp.BaseFragmentAdapter;

public class MainFragment extends BaseFragment<MainFragmentViewModel> implements MainFragmentCallback/*, DrawerAdapter.OnItemSelectedListener */ {
    public static int tab;
    public static int[] Colors = {
            0xffff7e2e,
            0xff70ae0e,
            0xff70ae0e,
            0xff70ae0e
    };
    private final String[] tabNameRes = {"主页", "问题", "FPS", "设置"};
    //    private final int[] tabImages = {R.drawable.r7, R.drawable.r5, R.drawable.r4, R.drawable.r6};
    List<Fragment> fragments = new ArrayList<>();
    Fragment info, issue, fps, setting;

    public static MainFragment newInstance(int tab) {
        Bundle args = new Bundle();
        args.putInt("tab", tab);
        MainFragment fragment = new MainFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public int getLayoutId() {
        return R.layout.matrix_kanzhun_main_fragment;
    }

    /**
     * 如果多个Fragment共享数据，此处this，需要改成getBaseFragmentActivity()
     **/
    @Override
    public MainFragmentViewModel getViewModel() {
        return ViewModelProviders.of(this.getBaseFragmentActivity()).get(MainFragmentViewModel.class);
    }

    public MainActivityViewModel getMainViewModel() {
        return ViewModelProviders.of(this.getBaseFragmentActivity()).get(MainActivityViewModel.class);
    }

    @Override
    public Object getCallback() {
        return this;
    }

    public void initTopbar() {
        Toolbar toolbar = getRootView().findViewById(R.id.toolbar);
        toolbar.setTitle("Matrix性能监控");
    }

    /**
     * initView fragment生命周期就调用一次
     **/
    @Override
    public void initView() {
        super.initView();

        initTopbar();
        initTab();
        initViewPager();
        ((TabLayout) getRootView().findViewById(R.id.main_tab_layout)).getTabAt(0).select();
        getRootView().setFocusableInTouchMode(true);
        getRootView().requestFocus();
        getRootView().setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View view, int i, KeyEvent keyEvent) {
                if (keyEvent.getAction() == KeyEvent.ACTION_DOWN && i == KeyEvent.KEYCODE_BACK) {
                }
                return false;
            }
        });
        ((AppCompatCheckBox) getRootView().findViewById(R.id.select_all_checkbox)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (tab == 0) {
                    ((IssuesFragment) issue).onCheckedChanged(buttonView, isChecked);
                } else if (tab == 1) {
                }
            }
        });
    }

    public boolean popBack() {
        if (tab != 0) {
            ((ViewPager) getRootView().findViewById(R.id.main_view_pager)).setCurrentItem(0);
            return true;
        }
        return false;
    }

    private void initViewPager() {
        ViewPager viewPager = getRootView().findViewById(R.id.main_view_pager);
        info = new InfosFragment();
        issue = new IssuesFragment();
        fps = new FpsFragment();
        setting = new SettingsFragment();
        fragments.add(info);
        fragments.add(issue);
        fragments.add(fps);
        fragments.add(setting);
        viewPager.setAdapter(new BaseFragmentAdapter(getChildFragmentManager(), fragments));
        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            @Override
            public void onPageSelected(int position) {
                tab = position;
            }

            @Override
            public void onPageScrollStateChanged(int state) {
            }
        });
        viewPager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(getRootView().findViewById(R.id.main_tab_layout)) {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
            }
        });
        viewPager.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                tab = getArguments().getInt("tab", 0);
                ((ViewPager) getRootView().findViewById(R.id.main_view_pager)).setCurrentItem(tab);
                viewPager.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            }
        });
    }

    private void initTab() {
        for (int i = 0; i < 4; i++) {
            View tab_main = LayoutInflater.from(this.getBaseFragmentActivity()).inflate
                    (R.layout.matrix_kanzhun_tab_main,
                            getRootView().findViewById(R.id.main_tab_layout), false);
            tab_main.setTag(i);
            ImageView lottieAnimationView = tab_main.findViewById(R.id.animation_view);
            TextView text = tab_main.findViewById(R.id.text);
            text.setText(tabNameRes[i]);
//            lottieAnimationView.setImageResource(tabImages[i]);
            TabLayout.Tab tab = ((TabLayout) getRootView().findViewById(R.id.main_tab_layout)).newTab().setCustomView(tab_main);
            ((TabLayout) getRootView().findViewById(R.id.main_tab_layout)).addTab(tab, i, false);
        }
        ((TabLayout) getRootView().findViewById(R.id.main_tab_layout)).addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab != null && tab.getCustomView() != null) {
                    tabSelected(tab);
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                if (tab != null && tab.getCustomView() != null) {
                    tabUnSelected(tab);
                }
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
    }

    private void tabSelected(TabLayout.Tab tab) {
        int position = (Integer) tab.getCustomView().getTag();
        ImageView lottieAnimationView = tab.getCustomView().findViewById(R.id.animation_view);
        TextView text = tab.getCustomView().findViewById(R.id.text);
        text.setSelected(true);
        text.setTextColor(Colors[position]);
        lottieAnimationView.setColorFilter(getResources().getColor(R.color.screenrecorder_au));
        ((ViewPager) getRootView().findViewById(R.id.main_view_pager)).setCurrentItem(position);
    }

    private void tabUnSelected(TabLayout.Tab tab) {
        ImageView lottieAnimationView = tab.getCustomView().findViewById(R.id.animation_view);
        TextView text = tab.getCustomView().findViewById(R.id.text);
        text.setSelected(false);
        text.setTextColor(ContextCompat.getColor(getBaseFragmentActivity(), R.color.screenrecorder_black_87));
        lottieAnimationView.setColorFilter(getResources().getColor(R.color.screenrecorder_at));
    }

    /**
     * getData fragment 可见的时候调用，可用于刷新一些容易改动的数据
     **/
    @Override
    public void getData() {
        super.getData();
    }

    public int getCurrentItemPosition() {
        return tab;
    }

    public void setTab(int tab) {
        if (MainFragment.tab != tab) {
            MainFragment.tab = tab;
            if (getRootView() != null
                    && getRootView().findViewById(R.id.main_view_pager) != null
                    && ((ViewPager) getRootView().findViewById(R.id.main_view_pager)).getAdapter() != null
                    && ((ViewPager) getRootView().findViewById(R.id.main_view_pager)).getAdapter().getCount() != 0
            ) {
                ((ViewPager) getRootView().findViewById(R.id.main_view_pager)).setCurrentItem(tab);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }


    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        EventBus.getDefault().unregister(this);
        super.onStop();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(MessageEventChangeMainTab event) {
        ((ViewPager) getRootView().findViewById(R.id.main_view_pager)).setCurrentItem(event.tab);
    }

}
