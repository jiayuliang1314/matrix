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

package com.tencent.matrix.resource.config;

/**
 * Created by zhangshaowen on 17/7/13.
 */
//ok
public class SharePluginInfo {

    //plugin名字
    public static final String TAG_PLUGIN = "memory";
    //Hprof压缩文件位置？
    public static final String ISSUE_RESULT_PATH = "resultZipPath";
    //dump的模式
    public static final String ISSUE_DUMP_MODE     = "dump_mode";
    //泄漏的activity的名字
    public static final String ISSUE_ACTIVITY_NAME = "activity";
    //泄漏的acitivity的唯一key
    public static final String ISSUE_REF_KEY       = "ref_key";
    //refChain gc链
    public static final String ISSUE_LEAK_DETAIL   = "leak_detail";
    //花了多长时间，dump+分析的消耗时间
    public static final String ISSUE_COST_MILLIS   = "cost_millis";
    //进程名字
    public static final String ISSUE_LEAK_PROCESS  = "leak_process";
    //没用到
    public static final String ISSUE_NOTIFICATION_ID     = "notification_id";

    public static final class IssueType {
        public static final int LEAK_FOUND         = 0;
        public static final int ERR_FILE_NOT_FOUND = 2;
        public static final int ERR_ANALYSE_OOM    = 3;
    }
}
