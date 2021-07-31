package sample.tencent.matrix.zp.ui.issues;

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
import com.tencent.matrix.report.Issue;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sample.tencent.matrix.R;
import sample.tencent.matrix.issue.IssuesMap;
import sample.tencent.matrix.issue.ParseIssueUtil;
import sample.tencent.matrix.zp.base.BaseFragment;
import sample.tencent.matrix.zp.data.IssuesTagNum;
import sample.tencent.matrix.zp.event.MessageEventChangeMainTabCategory;
import sample.tencent.matrix.zp.event.MessageEventIssueHappen;
import sample.tencent.matrix.zp.ui.MainActivityViewModel;
import sample.tencent.matrix.zp.ui.MainFragmentViewModel;
import sample.tencent.matrix.zp.utils.TimeUtils;
import sample.tencent.matrix.zp.view.rv.FlowLayoutManager;

public class IssuesFragment extends BaseFragment<MainFragmentViewModel> implements
        IssuesFragmentCallback {
    private final static File methodFilePath = new File(Environment.getExternalStorageDirectory(), "Debug.methodmap");
    private static String tag;
    RecyclerView filterRv;
    private Adapter adapter;

    public IssuesFragment() {
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
    BaseRecyclerViewAdapter<IssuesTagNum> tagAdapter;
    @Override
    public void initView() {
        super.initView();
//        tag = "All";
        filterRv = getRootView().findViewById(R.id.filter_rv);
        tagAdapter = new BaseRecyclerViewAdapter<IssuesTagNum>();
        tagAdapter.setBaseRecyclerViewCallback(new BaseRecyclerViewCallbackAdapter<IssuesTagNum>() {
            @Override
            public void onBindView(BaseViewHolder holder, int position, IssuesTagNum item) {
                TextView title = holder.itemView.findViewById(R.id.title);
                title.setText(item.getTag());
                if (
                        (tag == null && item.getTag().equals("All")) ||
                                (tag != null && tag.equals(item.getTag()))
                ) {
                    ((TextView) title).setTextColor(0xffffffff);
                    title.setBackgroundResource(R.drawable.bg_black_desired_child);
                } else {
                    ((TextView) title).setTextColor(0xff484848);
                    title.setBackgroundResource(R.drawable.bg_gray_desired_child);
                }
                holder.itemView.setOnClickListener(view -> {
                    int positionWhenOnClick = holder.getAdapterPosition();
                    if (positionWhenOnClick == RecyclerView.NO_POSITION) {
                        return;
                    }
                    List<IssuesTagNum> issuesTagNums = IssuesMap.getIssuesAllFenlei();
                    for (IssuesTagNum issuesTagNum : issuesTagNums) {
                        issuesTagNum.setSelect(false);
                    }
                    IssuesTagNum issuesTagNum = issuesTagNums.get(positionWhenOnClick);
                    issuesTagNum.setSelect(true);
                    tag = issuesTagNum.getTag();
                    adapter.notifyDataSetChanged();
                    tagAdapter.notifyDataSetChanged();
                });
            }

            @Override
            public int getViewRes(int viewType) {
                return R.layout.matrix_kanzhun_item_filter;
            }
        });
        filterRv.setAdapter(tagAdapter);
        filterRv.setLayoutManager(new FlowLayoutManager(getBaseFragmentActivity(), false));
//        RecyclerViewUtils.setStaggeredGridLayoutManager(getBaseFragmentActivity(), filterRv, 3, StaggeredGridLayoutManager.VERTICAL, false);
        tagAdapter.setItemsDirectly(IssuesMap.getIssuesAllFenlei());

        RecyclerView recyclerVideoView = getRootView().findViewById(R.id.recycler_view_video);
        final SwipeRefreshLayout refreshVideoLayout = getRootView().findViewById(R.id.refresh_video);
        recyclerVideoView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new Adapter(getBaseFragmentActivity());
        recyclerVideoView.setAdapter(adapter);

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
        adapter.notifyDataSetChanged();
        tagAdapter.setItemsDirectly(IssuesMap.getIssuesAllFenlei());
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

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(MessageEventChangeMainTabCategory event) {
        tag = event.category;
        reloadData();
    }

    //region 选择，全选
    //全选或者全不选
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
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
            final Issue issue = getIssues().get(position);
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
            return getIssues().size();
        }

        public List<Issue> getIssues() {
            if (tag == null || tag.equals("All")) {
                return IssuesMap.getIssuesAll();
            } else {
                return IssuesMap.getIssuesFenlei(tag);
            }
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        public int position;
        TextView tvTime, tvTag, tvKey, tvScene, tvContent, tvStackKey;
        private boolean isShow = false;

        public ViewHolder(View itemView) {
            super(itemView);
            tvTime = itemView.findViewById(R.id.item_time);
            tvTag = itemView.findViewById(R.id.item_tag);
            tvStackKey = itemView.findViewById(R.id.stack_key);
            tvKey = itemView.findViewById(R.id.item_key);
            tvScene = itemView.findViewById(R.id.item_scene);
            tvContent = itemView.findViewById(R.id.item_content);
//            tvIndex = itemView.findViewById(R.id.item_index);
        }

        public void bindPosition(int position) {
            this.position = position;
        }

        public void bind(Issue issue) {

            tvTime.setText(TimeUtils.formatTime(Long.parseLong(issue.getContent().optString("time"))));

            if (TextUtils.isEmpty(issue.getTag())) {
                tvTag.setVisibility(View.GONE);
            } else {
                tvTag.setVisibility(View.VISIBLE);
                if ("Trace_EvilMethod".equals(issue.getTag())) {
                    try {
                        String detail = issue.getContent().getString("detail");
                        tvTag.setText(issue.getTag() + " " + detail);
                    } catch (JSONException e) {
                        e.printStackTrace();
                        tvTag.setText(issue.getTag());
                    }
                } else {
                    tvTag.setText(issue.getTag());
                }
            }

            if (TextUtils.isEmpty(issue.getKey())) {
                tvKey.setVisibility(View.GONE);
            } else {
                tvKey.setVisibility(View.VISIBLE);
                tvKey.setText("Key " + issue.getKey() + " (Anr is time)");
            }

            try {
                String stackKey = issue.getContent().getString("stackKey");
                Log.i("IssuesFragment", "stackKey " + stackKey);
                if (TextUtils.isEmpty(stackKey)) {
                    tvStackKey.setVisibility(View.GONE);
                } else {
                    tvStackKey.setVisibility(View.VISIBLE);
                    if (stackKey.endsWith("|")) {
                        stackKey = stackKey.substring(0, stackKey.length() - 1);
                    }
                    tvStackKey.setText(stackKey);
                }
            } catch (JSONException e) {
                e.printStackTrace();
                tvStackKey.setVisibility(View.GONE);
            }
            try {
                String scene = issue.getContent().getString("scene");
                if (TextUtils.isEmpty(scene)) {
                    tvScene.setVisibility(View.GONE);
                } else {
                    tvScene.setVisibility(View.VISIBLE);
                    tvScene.setText(scene.substring(scene.lastIndexOf(".") + 1));
                }
            } catch (JSONException e) {
                e.printStackTrace();
                tvScene.setVisibility(View.GONE);
            }

//            tvIndex.setText((IssuesMap.getIssuesAll().size() - position) + "");
//            tvIndex.setTextColor(getColor(position));
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
