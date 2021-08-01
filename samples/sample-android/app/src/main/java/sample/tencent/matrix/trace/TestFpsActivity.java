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

package sample.tencent.matrix.trace;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tencent.matrix.Matrix;
import com.tencent.matrix.trace.TracePlugin;
import com.tencent.matrix.trace.constants.Constants;
import com.tencent.matrix.trace.listeners.IDoFrameListener;
import com.tencent.matrix.util.MatrixLog;

import java.util.Random;
import java.util.concurrent.Executor;

import sample.tencent.matrix.R;
import sample.tencent.matrix.issue.IssueFilter;

/**
 * Created by caichongyang on 2017/11/14.
 */
//耗时10s，才上报问题

public class TestFpsActivity extends Activity {
    private static final String TAG = "Matrix.TestFpsActivity";
    //只是用于埋了个点，没啥实际意义
    private static final HandlerThread sHandlerThread = new HandlerThread("test");
    //只是用于埋了个点，没啥实际意义
    static {
        sHandlerThread.start();
    }

    private ListView mListView;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private int count;
    private long time = System.currentTimeMillis();
    //只是埋了个点，没啥实际意义
    private final IDoFrameListener mDoFrameListener = new IDoFrameListener(new Executor() {
        final Handler handler = new Handler(sHandlerThread.getLooper());

        @Override
        public void execute(Runnable command) {
            handler.post(command);
        }
    }) {

        @Override
        public void doFrameAsync(String focusedActivity, long startNs, long endNs, int dropFrame, boolean isVsyncFrame, long intendedFrameTimeNs, long inputCostNs, long animationCostNs, long traversalCostNs) {
            super.doFrameAsync(focusedActivity, startNs, endNs, dropFrame, isVsyncFrame, intendedFrameTimeNs, inputCostNs, animationCostNs, traversalCostNs);
            //costMs 花费时间
            //dropFrame 掉帧
            //isVsyncFrame 是否是vsync帧
            //offsetVsync 开始时间和帧开始时间的offset，偏移时间
            //input/anim/渲染时间
            MatrixLog.i(TAG, "[doFrameAsync]" + " costMs=" + (endNs - intendedFrameTimeNs) / Constants.TIME_MILLIS_TO_NANO
                    + " dropFrame=" + dropFrame + " isVsyncFrame=" + isVsyncFrame + " offsetVsync=" + ((startNs - intendedFrameTimeNs) / Constants.TIME_MILLIS_TO_NANO) + " [%s:%s:%s]", inputCostNs, animationCostNs, traversalCostNs);
        }

    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        MatrixLog.i(TAG, "onCreate");

        //一个列表
        setContentView(R.layout.test_fps_layout);
        //过滤问题
        IssueFilter.setCurrentFilter(IssueFilter.ISSUE_TRACE);

        Matrix.with().getPluginByClass(TracePlugin.class).getFrameTracer().onStartTrace();
        Matrix.with().getPluginByClass(TracePlugin.class).getFrameTracer().addListener(mDoFrameListener);

        time = System.currentTimeMillis();
        mListView = (ListView) findViewById(R.id.list_view);
        String[] data = new String[200];
        for (int i = 0; i < 200; i++) {
            data[i] = "MatrixTrace:" + i;
        }
        mListView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                MatrixLog.i(TAG, "onTouch=" + motionEvent);
                SystemClock.sleep(80);
                return false;
            }
        });
        mListView.setAdapter(new ArrayAdapter(this, android.R.layout.simple_list_item_1, data) {
            final Random random = new Random();

            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
//                mainHandler.post(new Runnable() {
//                    @Override
//                    public void run() {
//                        int rand = random.nextInt(10);
//                        SystemClock.sleep(rand * 4);
//                    }
//                });
                return super.getView(position, convertView, parent);
            }
        });
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        MatrixLog.i(TAG, "[onDestroy] count:" + count + " time:" + (System.currentTimeMillis() - time) + "");
        Matrix.with().getPluginByClass(TracePlugin.class).getFrameTracer().removeListener(mDoFrameListener);
        //这里调用了getFrameTracer的onCloseTrace，注意此处
        Matrix.with().getPluginByClass(TracePlugin.class).getFrameTracer().onCloseTrace();
    }
}
