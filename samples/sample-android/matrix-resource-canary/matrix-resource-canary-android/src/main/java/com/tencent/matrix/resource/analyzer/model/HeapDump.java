/*
 * Copyright (C) 2015 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tencent.matrix.resource.analyzer.model;

import com.tencent.matrix.resource.common.utils.Preconditions;

import java.io.File;
import java.io.Serializable;

/**
 * Created by tangyinsheng on 2017/6/4.
 * <p>
 * This class is ported from LeakCanary.
 * <p>
 * Some unused fields were removed.
 */
//ok - HeapDump代表
public class HeapDump implements Serializable {
    //Hprof文件
    private final File mHprofFile;
    //DestroyedActivityInfo类中为每个已经ondestroy的activity设置的key
    private final String mRefKey;
    //activity名字
    private final String mActivityName;


    public HeapDump(File hprofFile, String refKey, String activityName) {
        mHprofFile = Preconditions.checkNotNull(hprofFile, "hprofFile");
        mRefKey = Preconditions.checkNotNull(refKey, "refKey");
        mActivityName = Preconditions.checkNotNull(activityName, "activityName");
    }

    public File getHprofFile() {
        return mHprofFile;
    }

    public String getReferenceKey() {
        return mRefKey;
    }

    public String getActivityName() {
        return mActivityName;
    }

}
