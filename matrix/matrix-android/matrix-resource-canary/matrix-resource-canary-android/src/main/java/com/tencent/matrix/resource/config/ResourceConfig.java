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

import com.tencent.mrs.plugin.IDynamicConfig;

import java.util.concurrent.TimeUnit;

/**
 * Created by tangyinsheng on 2017/6/2.
 */

public final class ResourceConfig {
    public static final String TAG = "Matrix.ResourceConfig";

    public static final int FORK_DUMP_SUPPORTED_API_GUARD = 31; // Now is Android 12 (S).

    public enum DumpMode {
        NO_DUMP, // report only
        AUTO_DUMP, // auto dump hprof
        MANUAL_DUMP, // notify only
        SILENCE_ANALYSE, // dump and analyse hprof when screen off
        FORK_DUMP, // fork dump hprof immediately
        FORK_ANALYSE, // fork dump and analyse hprof immediately
        LAZY_FORK_ANALYZE, // fork dump immediately but analyze hprof until the screen is off
    }

    private static final long DEFAULT_DETECT_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(1);
    //应用在后台时，每20分钟检测是否有泄漏
    private static final long DEFAULT_DETECT_INTERVAL_MILLIS_BG = TimeUnit.MINUTES.toMillis(20);
    //检测一个activity是否泄漏，是检测了10次它还没回收，就说明它泄漏了
    private static final int DEFAULT_MAX_REDETECT_TIMES = 10;
    //默认的模式是MANUAL_DUMP，手动dump
    private static final DumpMode DEFAULT_DUMP_HPROF_MODE = DumpMode.MANUAL_DUMP;
    private final IDynamicConfig mDynamicConfig;//todo，一个动态配置类
    //dump模式
    private final DumpMode mDumpHprofMode;
    private final boolean mDetectDebugger;
    private final String mTargetActivity;
    private final String mManufacture;

    private ResourceConfig(IDynamicConfig dynamicConfig, DumpMode dumpHprofMode, boolean detectDebuger, String targetActivity, String manufacture) {
        this.mDynamicConfig = dynamicConfig;
        this.mDumpHprofMode = dumpHprofMode;
        this.mDetectDebugger = detectDebuger;
        this.mTargetActivity = targetActivity;
        this.mManufacture = manufacture;
    }

    //应用在前台时，每分钟去检测是否有泄漏
    public long getScanIntervalMillis() {
        return mDynamicConfig.get(IDynamicConfig.ExptEnum.clicfg_matrix_resource_detect_interval_millis.name(), DEFAULT_DETECT_INTERVAL_MILLIS);
    }

    //应用在后台时，每20分钟检测是否有泄漏
    public long getBgScanIntervalMillis() {
        return mDynamicConfig.get(IDynamicConfig.ExptEnum.clicfg_matrix_resource_detect_interval_millis_bg.name(), DEFAULT_DETECT_INTERVAL_MILLIS_BG);
    }

    //检测10次
    public int getMaxRedetectTimes() {
        return mDynamicConfig.get(IDynamicConfig.ExptEnum.clicfg_matrix_resource_max_detect_times.name(), DEFAULT_MAX_REDETECT_TIMES);
    }

    //检测模式
    public DumpMode getDumpHprofMode() {
        return mDumpHprofMode;
    }

    //手动dump模式的时候，跳转activity
    public String getTargetActivity() {
        return mTargetActivity;
    }

    //debug模式是否可以检测
    public boolean getDetectDebugger() {
        return mDetectDebugger;
    }

    public String getManufacture() {
        return mManufacture;
    }

    public static final class Builder {

        private DumpMode mDefaultDumpHprofMode = DEFAULT_DUMP_HPROF_MODE;
        private IDynamicConfig dynamicConfig;
        private String mTargetActivity;
        private boolean mDetectDebugger = false;
        private String mManufacture;

        public Builder dynamicConfig(IDynamicConfig dynamicConfig) {
            this.dynamicConfig = dynamicConfig;
            return this;
        }

        public Builder setAutoDumpHprofMode(DumpMode mode) {
            mDefaultDumpHprofMode = mode;
            return this;
        }

        public Builder setDetectDebuger(boolean enabled) {
            mDetectDebugger = true;
            return this;
        }

        public Builder setManualDumpTargetActivity(String targetActivity) {
            mTargetActivity = targetActivity;
            return this;
        }

        public Builder setManufacture(String manufacture) {
            mManufacture = manufacture;
            return this;
        }

        public ResourceConfig build() {
            return new ResourceConfig(dynamicConfig, mDefaultDumpHprofMode, mDetectDebugger, mTargetActivity, mManufacture);
        }
    }
}
