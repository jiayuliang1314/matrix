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

import android.util.Log;

import com.tencent.matrix.report.Issue;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import sample.tencent.matrix.zp.data.IssuesTagNum;

public class IssuesMap {

    private static final ConcurrentHashMap<String, List<Issue>> issues = new ConcurrentHashMap<>();
    private static final ArrayList<Issue> allIssues = new ArrayList<>();
    private static final ConcurrentHashMap<String, List<Issue>> issuesFenlei = new ConcurrentHashMap<>();
    private static final CopyOnWriteArrayList<Issue> issuesList = new CopyOnWriteArrayList<>();

    public static void put(@IssueFilter.FILTER String filter, Issue issue) {
        List<Issue> list = issues.get(filter);
        if (list == null) {
            list = new ArrayList<>();
        }
        list.add(0, issue);
        issues.put(filter, list);

        synchronized (allIssues) {
            allIssues.add(issue);
        }
        Log.i("IssuesMap", "put " + issue.getTag() + " issue " + issue);


        if ("Trace_EvilMethod".equals(issue.getTag())) {
            try {
                String detail = issue.getContent().getString("detail");
                List<Issue> list1 = issuesFenlei.get(issue.getTag() + " " + detail);
                if (list1 == null) {
                    list1 = new ArrayList<>();
                }
                list1.add(0, issue);
                issuesFenlei.put(issue.getTag() + " " + detail, list1);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else {
            List<Issue> list2 = issuesFenlei.get(issue.getTag());
            if (list2 == null) {
                list2 = new ArrayList<>();
            }
            list2.add(0, issue);
            issuesFenlei.put(issue.getTag(), list2);
        }

        issuesList.add(0, issue);
    }

    public static List<Issue> get(@IssueFilter.FILTER String filter) {
        return issues.get(filter);
    }

    /**
     * 返回FPS相关的信息 ok
     *
     * @return
     */
    public static List<Issue> getFpsInfos() {
        return getInfos(true, "Trace_FPS");
    }

    public static List<Issue> getFpsInfosLimit5() {
        List<Issue> issues = getInfos(true, "Trace_FPS");
        if (issues.size() <= 3) {
            return issues;
        }
        return issues.subList(0, 3);
    }

    /**
     * 返回Startup相关的信息，启动耗时 ok
     *
     * @return
     */
    public static List<Issue> getStartupInfos() {
        return getInfos(true, "Trace_StartUp");
    }

    public static List<Issue> getStartupInfosLimit5() {
        List<Issue> issues = getInfos(true, "Trace_StartUp");
        if (issues.size() <= 3) {
            return issues;
        }
        return issues.subList(0, 3);
    }

    /**
     * 返回其他异常信息，包括ANR，超时方法，超时消息，启动超时，io，sqlite，battery
     * ANR,NORMAL,LAG,STARTUP
     *
     * @return
     */
    public static List<Issue> getIssuesAll() {
        List<Issue> issues = getInfos(false, "Trace_FPS", "Trace_StartUp");
//        Log.i("IssuesMap", "getIssuesAll " + issues.size());
        return issues;
    }

    public static List<Issue> getIssuesFenlei(String category) {
        List<Issue> issues = issuesFenlei.get(category);
//        Log.i("IssuesMap", "getIssuesAll " + issues.size());
        return issues;
    }


    public static List<IssuesTagNum> getIssuesAllFenlei() {
//        List<Issue> issues = getInfos(false, "Trace_FPS", "Trace_StartUp");
//        Log.i("IssuesMap", "getIssuesAll " + issues.size());
        List<IssuesTagNum> list = new ArrayList<>();
        int sizeAll = 0;
        for (Map.Entry<String, List<Issue>> entry : issuesFenlei.entrySet()) {
            if (entry.getKey().equals("Trace_FPS") || entry.getKey().equals("Trace_StartUp")) {
                continue;
            }
            System.out.println("Key = " + entry.getKey() + ", Value = " + entry.getValue().size());
            list.add(new IssuesTagNum(entry.getKey(), entry.getValue().size()));
            sizeAll += entry.getValue().size();
        }
        list.add(0, new IssuesTagNum("All", sizeAll));
        return list;
    }


    public static List<Issue> getInfos(boolean include, String... tags) {
        List<Issue> fpsList = new ArrayList<>();
        if (tags == null || tags.length == 0) {
            return fpsList;
        }
        if (issuesList != null) {
            for (Issue issue : issuesList) {
//                Log.i("IssuesMap", "getInfos issue " + issue);
                boolean findSame = false;
                for (String tag : tags) {
                    if (issue != null &&
                            issue.getTag() != null &&
                            issue.getTag().equals(tag)) {
                        if (include) {
                            fpsList.add(issue);
                        }
                        findSame = true;
                    }
                }
                if (!include && !findSame) {
//                    Log.i("IssuesMap", "getInfos !include !findSame " + issue);
                    fpsList.add(issue);
                }
            }
        }
        return fpsList;
    }

    public static int getCount() {
        List list = issues.get(IssueFilter.getCurrentFilter());
        return null == list ? 0 : list.size();
    }

    public static void clear() {
        issues.clear();
    }

    public static Issue getIssueReverse(int index) {
        synchronized (allIssues) {
            if (allIssues.size() <= index) {
                return null;
            }

            return allIssues.get(allIssues.size() - index - 1);
        }
    }

    public static int amountOfIssues() {
        synchronized (allIssues) {
            return allIssues.size();
        }
    }

}
