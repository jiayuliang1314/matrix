package sample.tencent.matrix.zp.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
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
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;

import sample.tencent.matrix.R;
import sample.tencent.matrix.issue.IssuesMap;
import sample.tencent.matrix.zp.base.BaseFragment;
import sample.tencent.matrix.zp.data.IssuesTagNum;
import sample.tencent.matrix.zp.event.MessageEventChangeMainTab;
import sample.tencent.matrix.zp.event.MessageEventChangeMainTabCategory;
import sample.tencent.matrix.zp.event.MessageEventIssueHappen;
import sample.tencent.matrix.zp.utils.TimeUtils;

public class InfosFragment extends BaseFragment<MainFragmentViewModel>
        implements InfosFragmentCallback, CompoundButton.OnCheckedChangeListener,
        View.OnClickListener {
    public static final String TAG = "SettingsFragment";
    RecyclerView startup_rv;
    RecyclerView issues_rv;
    RecyclerView fps_rv;
    TextView startup_more;
    TextView issues_more;
    TextView sub_fps_more;
    BaseRecyclerViewAdapter<Issue> startAdapter;
    BaseRecyclerViewAdapter<IssuesTagNum> issuesAdapter;
    BaseRecyclerViewAdapter<Issue> fpsAdapter;

    public static InfosFragment newInstance() {
        Bundle args = new Bundle();
        InfosFragment fragment = new InfosFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public int getLayoutId() {
        return R.layout.matrix_kanzhun_infos;
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
        issues_rv = getRootView().findViewById(R.id.issues_rv);
        fps_rv = getRootView().findViewById(R.id.fps_rv);
        startup_more = getRootView().findViewById(R.id.startup_more);
        issues_more = getRootView().findViewById(R.id.issues_more);
        sub_fps_more = getRootView().findViewById(R.id.sub_fps_more);
        startup_more.setOnClickListener(view -> {
            startFragment(StartupInfosFragment.newInstance());
        });
        issues_more.setOnClickListener(view -> {
            EventBus.getDefault().post(new MessageEventChangeMainTabCategory("All"));
            EventBus.getDefault().post(new MessageEventChangeMainTab(1));
        });
        sub_fps_more.setOnClickListener(view -> {
            EventBus.getDefault().post(new MessageEventChangeMainTab(2));
        });
        //region startup
        startAdapter = new BaseRecyclerViewAdapter<>();
        startAdapter.setBaseRecyclerViewCallback(new BaseRecyclerViewCallbackAdapter<Issue>() {
            @Override
            public void onBindView(BaseViewHolder holder, int position, Issue item) {
                TextView item_time = holder.itemView.findViewById(R.id.item_time);
                TextView application_create = holder.itemView.findViewById(R.id.application_create);
                TextView first_activity_create = holder.itemView.findViewById(R.id.first_activity_create);
                TextView startup_duration = holder.itemView.findViewById(R.id.startup_duration);
                TextView cold_hot = holder.itemView.findViewById(R.id.cold_hot);
                TextView scence = holder.itemView.findViewById(R.id.scence);
                long time = Long.parseLong(item.getContent().optString("time"));
                item_time.setText(TimeUtils.formatTime(time));

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
                    scence.setText(item.getContent().getInt("application_create_scene") + "");
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
        startAdapter.setItemsDirectly(IssuesMap.getStartupInfosLimit5());
        //endregion

        //region issue
        issuesAdapter = new BaseRecyclerViewAdapter<>();
        issuesAdapter.setBaseRecyclerViewCallback(new BaseRecyclerViewCallbackAdapter<IssuesTagNum>() {
            @Override
            public void onBindView(BaseViewHolder holder, int position, IssuesTagNum item) {
                Log.i("InfosFragment", "onBindView " + item);
                TextView item_tag = holder.itemView.findViewById(R.id.item_tag);
                TextView item_num = holder.itemView.findViewById(R.id.item_num);
                TextView item_more = holder.itemView.findViewById(R.id.item_more);
                item_tag.setText(item.getTag());
                item_num.setText(item.getNum() + "");
                item_more.setOnClickListener(view -> {
                    //todo筛选用

                    EventBus.getDefault().post(new MessageEventChangeMainTabCategory(item.getTag()));
                    EventBus.getDefault().post(new MessageEventChangeMainTab(1));
                });
            }

            @Override
            public int getViewRes(int viewType) {
                return R.layout.matrix_kanzhun_item_issue_fenlei;
            }
        });
        issues_rv.setAdapter(issuesAdapter);
        RecyclerViewUtils.setLinearLayoutManager(getBaseFragmentActivity(), issues_rv, LinearLayoutManager.VERTICAL, false);
        issuesAdapter.setItemsDirectly(IssuesMap.getIssuesAllFenlei());
        //endregion

        //region fps
        fpsAdapter = new BaseRecyclerViewAdapter<>();
        fpsAdapter.setBaseRecyclerViewCallback(new BaseRecyclerViewCallbackAdapter<Issue>() {
            @Override
            public void onBindView(BaseViewHolder holder, int position, Issue item) {
                Log.i("InfosFragment", "onBindView " + item);
                TextView item_tag = holder.itemView.findViewById(R.id.item_tag);
                TextView item_num = holder.itemView.findViewById(R.id.item_num);

                TextView level_normal = holder.itemView.findViewById(R.id.level_normal);
                TextView level_middle = holder.itemView.findViewById(R.id.level_middle);
                TextView level_high = holder.itemView.findViewById(R.id.level_high);
                TextView level_frozen = holder.itemView.findViewById(R.id.level_frozen);
                TextView sum_level_normal = holder.itemView.findViewById(R.id.sum_level_normal);
                TextView sum_level_middle = holder.itemView.findViewById(R.id.sum_level_middle);
                TextView sum_level_high = holder.itemView.findViewById(R.id.sum_level_high);
                TextView sum_level_frozen = holder.itemView.findViewById(R.id.sum_level_frozen);
//                TextView drop_frame = holder.itemView.findViewById(R.id.drop_frame);
//                TextView item_more = holder.itemView.findViewById(R.id.item_more);
//                item_tag.setText(item.getTag());
//                item_num.setText(item.getNum() + "");

                try {
                    String scene = item.getContent().getString("scene");
                    item_tag.setText(scene.substring(scene.lastIndexOf(".") + 1));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                try {
                    item_num.setText((int) (item.getContent().getDouble("fps")) + "");
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                try {
                    JSONObject dropLevelObject = item.getContent().getJSONObject("dropLevel");
                    JSONObject dropSum = item.getContent().getJSONObject("dropSum");
                    level_normal.setText(dropLevelObject.getInt("DROPPED_NORMAL") + "");
                    level_middle.setText(dropLevelObject.getInt("DROPPED_MIDDLE") + "");
                    level_high.setText(dropLevelObject.getInt("DROPPED_HIGH") + "");
                    level_frozen.setText(dropLevelObject.getInt("DROPPED_FROZEN") + "");

                    sum_level_normal.setText(dropSum.getInt("DROPPED_NORMAL") + "");
                    sum_level_middle.setText(dropSum.getInt("DROPPED_MIDDLE") + "");
                    sum_level_high.setText(dropSum.getInt("DROPPED_HIGH") + "");
                    sum_level_frozen.setText(dropSum.getInt("DROPPED_FROZEN") + "");
                } catch (JSONException e) {
                    e.printStackTrace();
                }


//                item_more.setOnClickListener(view -> {
//                    EventBus.getDefault().post(new MessageEventChangeMainTab(2));
//                });
            }

            @Override
            public int getViewRes(int viewType) {
                return R.layout.matrix_kanzhun_item_fps_info;
            }
        });
        fps_rv.setAdapter(fpsAdapter);
        RecyclerViewUtils.setLinearLayoutManager(getBaseFragmentActivity(), fps_rv, LinearLayoutManager.VERTICAL, false);
        fpsAdapter.setItemsDirectly(IssuesMap.getFpsInfosLimit5());
        //endregion
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
        startAdapter.setItemsDirectly(IssuesMap.getStartupInfosLimit5());
        issuesAdapter.setItemsDirectly(IssuesMap.getIssuesAllFenlei());
        fpsAdapter.setItemsDirectly(IssuesMap.getFpsInfosLimit5());
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
