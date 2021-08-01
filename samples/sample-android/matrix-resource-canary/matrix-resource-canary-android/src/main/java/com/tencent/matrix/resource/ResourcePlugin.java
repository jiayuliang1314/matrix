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

package com.tencent.matrix.resource;

import android.app.Activity;
import android.app.Application;
import android.os.Build;

import com.tencent.matrix.plugin.Plugin;
import com.tencent.matrix.plugin.PluginListener;
import com.tencent.matrix.resource.config.ResourceConfig;
import com.tencent.matrix.resource.config.SharePluginInfo;
import com.tencent.matrix.resource.watcher.ActivityLifeCycleCallbacksAdapter;
import com.tencent.matrix.resource.watcher.ActivityRefWatcher;
import com.tencent.matrix.util.MatrixLog;

/**
 * Created by tangyinsheng on 2017/6/2.
 * ok
 */

public class ResourcePlugin extends Plugin {
    private static final String TAG = "Matrix.ResourcePlugin";
    //配置类
    private final ResourceConfig mConfig;
    //执行activity泄漏检测的类
    private ActivityRefWatcher mWatcher = null;

    public ResourcePlugin(ResourceConfig config) {
        mConfig = config;
    }

    //todo 没有用到
    public static void activityLeakFixer(Application application) {
        // Auto break the path from Views in their holder to gc root when activity is destroyed.
        application.registerActivityLifecycleCallbacks(new ActivityLifeCycleCallbacksAdapter() {
            @Override
            public void onActivityDestroyed(Activity activity) {
                ActivityLeakFixer.fixInputMethodManagerLeak(activity);
                ActivityLeakFixer.unbindDrawables(activity);
                ActivityLeakFixer.fixViewLocationHolderLeakApi28(activity);
            }
        });
    }

    public ActivityRefWatcher getWatcher() {
        return mWatcher;
    }

    @Override
    public void init(Application app, PluginListener listener) {
        super.init(app, listener);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            MatrixLog.e(TAG, "API is low Build.VERSION_CODES.ICE_CREAM_SANDWICH(14), ResourcePlugin is not supported");
            unSupportPlugin();
            return;
        }
        //执行activity泄漏检测的类
        mWatcher = new ActivityRefWatcher(app, this);
    }

    @Override
    public void start() {
        super.start();
        if (!isSupported()) {
            MatrixLog.e(TAG, "ResourcePlugin start, ResourcePlugin is not supported, just return");
            return;
        }
        //开启检测
        mWatcher.start();
    }

    @Override
    public void stop() {
        super.stop();
        if (!isSupported()) {
            MatrixLog.e(TAG, "ResourcePlugin stop, ResourcePlugin is not supported, just return");
            return;
        }
        //关掉检测
        mWatcher.stop();
    }

    @Override
    public void destroy() {
        super.destroy();
        if (!isSupported()) {
            MatrixLog.e(TAG, "ResourcePlugin destroy, ResourcePlugin is not supported, just return");
            return;
        }
        //destroy检测
        mWatcher.destroy();
    }

    @Override
    public String getTag() {
        return SharePluginInfo.TAG_PLUGIN;
    }

    @Override
    public void onForeground(boolean isForeground) {
        MatrixLog.d(TAG, "onForeground: %s", isForeground);
        if (isPluginStarted() && mWatcher != null) {
            mWatcher.onForeground(isForeground);
        }
    }

    public ResourceConfig getConfig() {
        return mConfig;
    }

}
