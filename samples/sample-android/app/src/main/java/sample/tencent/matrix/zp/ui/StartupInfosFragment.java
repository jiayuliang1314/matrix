package sample.tencent.matrix.zp.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.strong.tools.recyclerview.BaseRecyclerViewAdapter;
import com.strong.tools.recyclerview.BaseRecyclerViewCallbackAdapter;
import com.strong.tools.recyclerview.BaseViewHolder;
import com.strong.tools.recyclerview.RecyclerViewUtils;
import com.tencent.matrix.report.Issue;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONException;

import java.text.SimpleDateFormat;
import java.util.Date;

import sample.tencent.matrix.R;
import sample.tencent.matrix.issue.IssuesMap;
import sample.tencent.matrix.zp.base.BaseFragment;
import sample.tencent.matrix.zp.event.MessageEventIssueHappen;

public class StartupInfosFragment extends BaseFragment<MainFragmentViewModel>
        implements InfosFragmentCallback, CompoundButton.OnCheckedChangeListener,
        View.OnClickListener {
    public static final String TAG = "SettingsFragment";
    RecyclerView startup_rv;
    BaseRecyclerViewAdapter<Issue> startAdapter;

    public static StartupInfosFragment newInstance() {
        Bundle args = new Bundle();
        StartupInfosFragment fragment = new StartupInfosFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public int getLayoutId() {
        return R.layout.matrix_kanzhun_startup_infos;
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
        startup_rv = getRootView().findViewById(R.id.startup_rv);

        startAdapter = new BaseRecyclerViewAdapter<>();
        startAdapter.setBaseRecyclerViewCallback(new BaseRecyclerViewCallbackAdapter<Issue>() {
            @Override
            public void onBindView(BaseViewHolder holder, int position, Issue item) {
                TextView item_time = holder.itemView.findViewById(R.id.item_time);
                TextView application_create = holder.itemView.findViewById(R.id.application_create);
                TextView first_activity_create = holder.itemView.findViewById(R.id.first_activity_create);
                TextView startup_duration = holder.itemView.findViewById(R.id.startup_duration);
                TextView cold_hot = holder.itemView.findViewById(R.id.cold_hot);

                SimpleDateFormat simpleDateFormat = new SimpleDateFormat("MM/dd HH:mm:ss");
                Date date = new Date(Long.parseLong(item.getContent().optString("time")));
                item_time.setText(simpleDateFormat.format(date));

                try {
                    application_create.setText(item.getContent().getString("application_create"));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                try {
                    first_activity_create.setText(item.getContent().getString("first_activity_create"));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                try {
                    startup_duration.setText(item.getContent().getString("startup_duration"));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                try {
                    cold_hot.setText(item.getContent().getBoolean("is_warm_start_up") ? "热启动" : "冷启动");
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public int getViewRes(int viewType) {
                return R.layout.matrix_kanzhun_item_startup;
            }
        });
        startup_rv.setAdapter(startAdapter);
        RecyclerViewUtils.setLinearLayoutManager(getBaseFragmentActivity(), startup_rv, LinearLayoutManager.VERTICAL, false);
        startAdapter.setItemsDirectly(IssuesMap.getStartupInfos());
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
    public void onMessageEvent(MessageEventIssueHappen event) {
        startAdapter.setItemsDirectly(IssuesMap.getStartupInfos());
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
