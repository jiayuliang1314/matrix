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

package com.tencent.matrix.trace.retrace;

/**
 * Created by caichongyang on 2017/6/3.
 */
public interface MappingProcessor {
    /**
     * mapping the class name.
     *
     * @param className    the original class name.
     * @param newClassName the new class name.
     * @return whether the processor is interested in receiving mappings of the class members of
     * this class.
     * 处理器是否有兴趣接收此类的类成员的映射。
     */
    boolean processClassMapping(String className,
                                       String newClassName);

    /**
     * mapping the method name.
     *
     * @param className          the original class name.
     * @param methodReturnType   the original external method return type.
     * @param methodName         the original external method name.
     * @param methodArguments    the original external method arguments.
     * @param newClassName       the new class name.
     * @param newMethodName      the new method name.
     *
     */
    void processMethodMapping(String className,
                                     String methodReturnType,//为什么返回值没有newMethodReturnType todo
                                     String methodName,
                                     String methodArguments,//为什么参数没有newMethodArguments todo
                                     String newClassName,
                                     String newMethodName);
}
