/*
 * Tencent is pleased to support the open source community by making wechat-matrix available.
 * Copyright (C) 2018 THL A29 Limited, a Tencent company. All rights reserved.
 * Licensed under the BSD 3-Clause License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package sample.tencent.matrix.issue;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.tencent.matrix.report.Issue;

import org.json.JSONException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import sample.tencent.matrix.R;

public class IssuesListActivity extends AppCompatActivity {

    private final static int toastCount = 3;
    private static int currToastCount = 0;
    private RecyclerView recyclerView;
    private final static File methodFilePath = new File(Environment.getExternalStorageDirectory(), "Debug.methodmap");

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_issue_list);

        setTitle("ISSUE List");
        initRecyclerView();

        if (currToastCount < toastCount) {
            currToastCount++;
            Toast.makeText(this, "click view to hide or show issue detail", Toast.LENGTH_LONG).show();
        }


    }

    public void initRecyclerView() {
        recyclerView = (RecyclerView) findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(new Adapter(this));
    }

    public static class Adapter extends RecyclerView.Adapter<ViewHolder> {

        WeakReference<Context> context;

        public Adapter(Context context) {
            this.context = new WeakReference<>(context);
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(context.get()).inflate(R.layout.item_issue_list, parent, false);

            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(final ViewHolder holder, int position) {

            holder.bindPosition(position);
            final Issue issue = IssuesMap.getIssueReverse(position);
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
            return IssuesMap.amountOfIssues();
        }
    }


    public static class ViewHolder extends RecyclerView.ViewHolder {

        TextView tvTime, tvTag, tvKey, tvType, tvContent, tvIndex;

        public int position;

        private boolean isShow = true;
        private boolean isObfused=false;

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

        @SuppressLint("SetTextI18n")
        public void bind(Issue issue) {

            @SuppressLint("SimpleDateFormat")
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy 年 MM 月 dd 日 HH:mm:ss:SSS");
            Date date = new Date(Long.parseLong(issue.getContent().optString("time")));
            tvTime.setText("ISSUE Time Stamp: " + simpleDateFormat.format(date));

            if (TextUtils.isEmpty(issue.getTag())) {
                tvTag.setVisibility(View.GONE);
            } else {
                tvTag.setText("ISSUE TAG: " + issue.getTag());
            }

            if (TextUtils.isEmpty(issue.getKey())) {
                tvKey.setVisibility(View.GONE);
            } else {
                tvKey.setText("ISSUE KEY: " + issue.getKey());
            }

            tvIndex.setText((IssuesMap.getCount() - position) + "");
            tvIndex.setTextColor(getColor(position));
            if (isShow) {
                showIssue(issue);
            } else {
                hideIssue();
            }
        }

        public void readMappingFile(Map<Integer, String> methoMap) {
            Log.i("IssuesListActivity","readMappingFile");
            BufferedReader reader = null;
            String tempString = null;
            try {
                InputStream in = tvTime.getContext().getResources().getAssets().open("tracecanaryObfuscationMapping.txt");
                InputStreamReader isr = new InputStreamReader(in,"UTF-8");
                reader = new BufferedReader(isr);//new FileReader(methodFilePath));
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
            if (issue.getContent().has(key) && isObfused==false) {
                try {
                    String stack = issue.getContent().getString(key);
                    Map<Integer, String> map = new HashMap<>();
                    readMappingFile(map);

                    if (map.size() > 0) {
                        StringBuilder stringBuilder = new StringBuilder(" ");

                        String[] lines = stack.split("\n");
                        for (String line : lines) {
                            String[] args = line.split(",");
                            int method = 0;
                            try {
                                method = Integer.parseInt(args[1]);
                            } catch (NumberFormatException e) {
                                e.printStackTrace();
                                return;
                            }
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
                        isObfused=true;
                    }

                } catch (JSONException ex) {
                    System.out.println(ex.getMessage());
                }
            }

            tvContent.setText(ParseIssueUtil.parseIssue(issue, true));
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
