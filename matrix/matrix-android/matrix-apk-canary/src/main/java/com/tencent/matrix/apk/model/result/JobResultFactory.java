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

package com.tencent.matrix.apk.model.result;

import com.tencent.matrix.apk.model.job.JobConfig;

/**
 * Created by jinqiuchen on 17/6/13.
 * ok
 */

public final class JobResultFactory {

    public static JobResult factory(String format, JobConfig config) {

        JobResult jobResult = null;
        if (config != null) {
            if (TaskResultFactory.isJsonResult(format)) {
                jobResult = new JobJsonResult(format, config.getOutputPath());
            } else if (TaskResultFactory.isHtmlResult(format)) {
                jobResult = new JobHtmlResult(format, config.getOutputPath());
            }
        }
        return jobResult;
    }
}
