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
package com.tencent.matrix.resource.leakcanary.internal;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ok
 *
 * todo AtomicReference
 * todo CountDownLatch
 * @param <T>
 */

public final class FutureResult<T> {
//    AtomicReference类提供了一个可以原子读写的对象引用变量。
//    原子意味着尝试更改相同AtomicReference的多个线程（例如，使用比较和交换操作）不会使AtomicReference最终达到不一致的状态。
    private final AtomicReference<T> resultHolder;
//    countDownLatch这个类使一个线程等待其他线程各自执行完毕后再执行。
//    是通过一个计数器来实现的，计数器的初始值是线程的数量。每当一个线程执行完毕后，
//    计数器的值就-1，当计数器的值为0时，表示所有线程都执行完毕，然后在闭锁上等待的线程就可以恢复工作了。
    private final CountDownLatch latch;

    public FutureResult() {
        resultHolder = new AtomicReference<>();
        latch = new CountDownLatch(1);
    }

    public boolean wait(long timeout, TimeUnit unit) {
        try {
            //调用await()方法的线程会被挂起，它会等待直到count值为0才继续执行
            //await(timeout，...)和await()类似，只不过等待一定的时间后count值还没变为0的话就会继续执行
            return latch.await(timeout, unit);
        } catch (InterruptedException e) {
            throw new RuntimeException("Did not expect thread to be interrupted", e);
        }
    }

    public T get() {
        if (latch.getCount() > 0) {
            throw new IllegalStateException("Call wait() and check its result");
        }
        return resultHolder.get();
    }

    public void set(T result) {
        resultHolder.set(result);
        //将count值减1
        latch.countDown();
    }
}
