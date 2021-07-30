package sample.tencent.matrix.zp.ui;

import android.content.Context;
import android.graphics.Color;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import sample.tencent.matrix.R;
import sample.tencent.matrix.issue.IssuesMap;
import sample.tencent.matrix.issue.ParseIssueUtil;
import sample.tencent.matrix.zp.base.BaseFragment;
import sample.tencent.matrix.zp.event.MessageEventIssueHappen;

public class FpsFragment extends BaseFragment<MainFragmentViewModel>
        implements InfosFragmentCallback, CompoundButton.OnCheckedChangeListener,
        View.OnClickListener {
    private final static File methodFilePath = new File(Environment.getExternalStorageDirectory(), "Debug.methodmap");
    BaseRecyclerViewAdapter<Issue> fpsAdapter;
    RecyclerView fps_rv;
//    private Adapter adapter;

    public FpsFragment() {
        super();
    }

    @Override
    public int getLayoutId() {
        return R.layout.matrix_kanzhun_issue_fragment;
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

    @Override
    public void initView() {
        super.initView();

        fps_rv = getRootView().findViewById(R.id.recycler_view_video);
        final SwipeRefreshLayout refreshVideoLayout = getRootView().findViewById(R.id.refresh_video);
//        fps_rv.setLayoutManager(new LinearLayoutManager(getContext()));
//        adapter = new Adapter(getBaseFragmentActivity());
//        recyclerVideoView.setAdapter(adapter);

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
        fpsAdapter.setItemsDirectly(IssuesMap.getFpsInfos());
        //endregion


        refreshVideoLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                refreshVideoLayout.setRefreshing(false);
                reloadData();
            }
        });
    }

    /**
     * getData fragment 可见的时候调用，可用于刷新一些容易改动的数据
     **/
    @Override
    public void getData() {
        super.getData();
    }

    public void reloadData() {
        fpsAdapter.setItemsDirectly(IssuesMap.getFpsInfos());
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
        reloadData();
    }

    //region 选择，全选
    //全选或者全不选
    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
    }

    @Override
    public void onClick(View v) {

    }

    public static class Adapter extends RecyclerView.Adapter<ViewHolder> {

        WeakReference<Context> context;

        public Adapter(Context context) {
            this.context = new WeakReference<>(context);
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(context.get()).inflate(R.layout.matrix_kanzhun_item_issue_list, parent, false);

            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(final ViewHolder holder, int position) {

            holder.bindPosition(position);
            final Issue issue = IssuesMap.getFpsInfos().get(position);
            holder.bind(issue);

            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    if (!holder.isShow) {
                        holder.showIssue(issue);
                    } else {
                        holder.hideIssue();
                    }
                }
            });
        }

        @Override
        public int getItemCount() {
            return IssuesMap.getFpsInfos().size();
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        public int position;
        TextView tvTime, tvTag, tvKey, tvType, tvContent, tvIndex;
        private boolean isShow = false;

        public ViewHolder(View itemView) {
            super(itemView);
            tvTime = itemView.findViewById(R.id.item_time);
            tvTag = itemView.findViewById(R.id.item_tag);
            tvKey = itemView.findViewById(R.id.item_key);
            tvType = itemView.findViewById(R.id.item_type);
            tvContent = itemView.findViewById(R.id.item_content);
            tvIndex = itemView.findViewById(R.id.item_index);
        }

        public void bindPosition(int position) {
            this.position = position;
        }

        public void bind(Issue issue) {

            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss:SSS");
            Date date = new Date(Long.parseLong(issue.getContent().optString("time")));
            tvTime.setText("IssueTime -> " + simpleDateFormat.format(date));

            if (TextUtils.isEmpty(issue.getTag())) {
                tvTag.setVisibility(View.GONE);
            } else {
                tvTag.setText("TAG -> " + issue.getTag());
            }

            if (TextUtils.isEmpty(issue.getKey())) {
                tvKey.setVisibility(View.GONE);
            } else {
                tvKey.setText("KEY -> " + issue.getKey());
            }

            tvIndex.setText((IssuesMap.getFpsInfos().size() - position) + "");
            tvIndex.setTextColor(getColor(position));
            if (isShow) {
                showIssue(issue);
            } else {
                hideIssue();
            }
        }

        public void readMappingFile(Map<Integer, String> methoMap) {
            BufferedReader reader = null;
            String tempString = null;
            try {
                reader = new BufferedReader(new FileReader(methodFilePath));
                while ((tempString = reader.readLine()) != null) {
                    String[] contents = tempString.split(",");
                    methoMap.put(Integer.parseInt(contents[0]), contents[2].replace('\n', ' '));
                }
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e1) {
                    }
                }
            }
        }

        public void showIssue(Issue issue) {
            String key = "stack";
            if (issue.getContent().has(key)) {
                try {
                    String stack = issue.getContent().getString(key);
                    Map<Integer, String> map = new HashMap<>();
                    readMappingFile(map);

                    if (map.size() > 0) {
                        StringBuilder stringBuilder = new StringBuilder(" ");

                        String[] lines = stack.split("\n");
                        for (String line : lines) {
                            String[] args = line.split(",");
                            int method = Integer.parseInt(args[1]);
                            boolean isContainKey = map.containsKey(method);
                            if (!isContainKey) {
                                System.out.print("error!!!");
                                continue;
                            }

                            args[1] = map.get(method);
                            stringBuilder.append(args[0]);
                            stringBuilder.append(",");
                            stringBuilder.append(args[1]);
                            stringBuilder.append(",");
                            stringBuilder.append(args[2]);
                            stringBuilder.append(",");
                            stringBuilder.append(args[3] + "\n");
                        }

                        issue.getContent().remove(key);
                        issue.getContent().put(key, stringBuilder.toString());
                    }

                } catch (JSONException ex) {
                    System.out.println(ex.getMessage());
                }
            }

            tvContent.setText(ParseIssueUtil.parseIssue(issue, false));
            tvContent.setVisibility(View.VISIBLE);
            isShow = true;
        }

        public void hideIssue() {
            tvContent.setVisibility(View.GONE);
            isShow = false;
        }

        public int getColor(int index) {
            switch (index) {
                case 0:
                    return Color.RED;
                case 1:
                    return Color.GREEN;
                case 2:
                    return Color.BLUE;
                default:
                    return Color.GRAY;
            }
        }
    }
}
