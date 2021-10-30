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

import com.tencent.matrix.javalib.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;

/**
 * Created by caichongyang on 2017/6/3.
 *
 * # compiler: R8
 * # compiler_version: 1.6.67
 * # min_api: 21
 * # pg_map_id: f5863a2
 * # common_typos_disable
 * android.support.customtabs.ICustomTabsCallback -> a.a.a.a:
 *     void onNavigationEvent(int,android.os.Bundle) -> a
 *     void onRelationshipValidationResult(int,android.net.Uri,boolean,android.os.Bundle) -> a
 *     void extraCallback(java.lang.String,android.os.Bundle) -> b
 *     void onMessageChannelReady(android.os.Bundle) -> c
 *     void onPostMessage(java.lang.String,android.os.Bundle) -> c
 * android.support.customtabs.ICustomTabsCallback$Stub -> a.a.a.a$a:
 *     1:2:void <init>():18:19 -> <init>
 *     1:3:android.support.customtabs.ICustomTabsCallback asInterface(android.os.IBinder):30:32 -> a
 *     4:4:android.support.customtabs.ICustomTabsCallback asInterface(android.os.IBinder):34:34 -> a
 *     1:1:boolean onTransact(int,android.os.Parcel,android.os.Parcel,int):137:137 -> onTransact
 *     2:2:boolean onTransact(int,android.os.Parcel,android.os.Parcel,int):46:46 -> onTransact
 *     3:3:boolean onTransact(int,android.os.Parcel,android.os.Parcel,int):113:113 -> onTransact
 *     4:4:boolean onTransact(int,android.os.Parcel,android.os.Parcel,int):115:115 -> onTransact
 *
 *     android.support.v4.media.MediaBrowserCompat$ItemReceiver -> android.support.v4.media.MediaBrowserCompat$ItemReceiver:
 *     android.support.v4.media.MediaBrowserCompat$ItemCallback mCallback -> g
 *     java.lang.String mMediaId -> f
 *     1:1:void onReceiveResult(int,android.os.Bundle):2246:2246 -> a
 *     2:2:void onReceiveResult(int,android.os.Bundle):2248:2248 -> a
 *     3:4:void onReceiveResult(int,android.os.Bundle):2252:2253 -> a
 *     5:5:void onReceiveResult(int,android.os.Bundle):2256:2256 -> a
 *     6:6:void onReceiveResult(int,android.os.Bundle):2254:2254 -> a
 *     7:7:void onReceiveResult(int,android.os.Bundle):2249:2249 -> a
 */
public class MappingReader {
    private final static String TAG = "MappingReader";
    private final static String SPLIT = ":";
    private final static String SPACE = " ";
    private final static String ARROW = "->";
    private final static String LEFT_PUNC = "(";
    private final static String RIGHT_PUNC = ")";
    private final static String DOT = ".";
    private final File proguardMappingFile;

    public MappingReader(File proguardMappingFile) {
        this.proguardMappingFile = proguardMappingFile;
    }

    /**
     * Reads the mapping file
     * 按行读取，交给mappingProcessor处理
     */
    public void read(MappingProcessor mappingProcessor) throws IOException {
        LineNumberReader reader = new LineNumberReader(new BufferedReader(new FileReader(proguardMappingFile)));
        try {
            String className = null;
            // Read the class and class member mappings.
            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                line = line.trim();
                if (!line.startsWith("#")) {
                    // a class mapping
                    if (line.endsWith(SPLIT)) {//SPLIT = ":";
                        className = parseClassMapping(line, mappingProcessor);
                    } else if (className != null) { // a class member mapping
                        parseClassMemberMapping(className, line, mappingProcessor);
                    }
                } else {
                    Log.i(TAG, "comment:# %s", line);
                }
            }
        } catch (IOException err) {
            throw new IOException("Can't read mapping file", err);
        } finally {
            try {
                reader.close();
            } catch (IOException ex) {
                // do nothing
            }
        }
    }

    /**
     * @param line read content
     * @param mappingProcessor
     * @return
     *
     * 处理class类mapping
     */
    private String parseClassMapping(String line, MappingProcessor mappingProcessor) {

        int leftIndex = line.indexOf(ARROW);//->
        if (leftIndex < 0) {
            return null;
        }
        int offset = 2;
        int rightIndex = line.indexOf(SPLIT, leftIndex + offset);
        if (rightIndex < 0) {
            return null;
        }

        // trim the elements.
        String className = line.substring(0, leftIndex).trim();
        String newClassName = line.substring(leftIndex + offset, rightIndex).trim();

        // Process this class name mapping.
        boolean ret = mappingProcessor.processClassMapping(className, newClassName);

        return ret ? className : null;
    }

    /**
     * Parses the a class member mapping
     *
     * @param className
     * @param line
     * @param mappingProcessor parse line such as
     *                         ___ ___ -> ___
     *                         ___:___:___ ___(___) -> ___
     *                         ___:___:___ ___(___):___ -> ___
     *                         ___:___:___ ___(___):___:___ -> ___
     *
     *                         android.os.Bundle mExtras -> m
     *
     *                         43:44:boolean isCurrent(android.os.Messenger,java.lang.String):1468:1469 -> a
     *
     * 处理方法mapping
     */
    private void parseClassMemberMapping(String className, String line, MappingProcessor mappingProcessor) {
        int leftIndex1 = line.indexOf(SPLIT);                                           //第一个:
        int leftIndex2 = leftIndex1 < 0 ? -1 : line.indexOf(SPLIT, leftIndex1 + 1);   //第二个:
        int spaceIndex = line.indexOf(SPACE, leftIndex2 + 2);       //第一个空格
        int argIndex1 = line.indexOf(LEFT_PUNC, spaceIndex + 1);    //第一个（
        int argIndex2 = argIndex1 < 0 ? -1 : line.indexOf(RIGHT_PUNC, argIndex1 + 1);   //第一个 ）
        int leftIndex3 = argIndex2 < 0 ? -1 : line.indexOf(SPLIT, argIndex2 + 1);       //第三个：
        int leftIndex4 = leftIndex3 < 0 ? -1 : line.indexOf(SPLIT, leftIndex3 + 1);     //第四个：
        int rightIndex = line.indexOf(ARROW, (leftIndex4 >= 0 ? leftIndex4 : leftIndex3 >= 0
                ? leftIndex3 : argIndex2 >= 0 ? argIndex2 : spaceIndex) + 1);              // -> 位置
        if (spaceIndex < 0 || rightIndex < 0) {
            return;
        }

        // trim the elements.
        String type = line.substring(leftIndex2 + 1, spaceIndex).trim();//返回值类型
        String name = line.substring(spaceIndex + 1, argIndex1 >= 0 ? argIndex1 : rightIndex).trim();//方法名
        String newName = line.substring(rightIndex + 2).trim();//新方法名

        String newClassName = className;
        int dotIndex = name.lastIndexOf(DOT);
        if (dotIndex >= 0) {
            className = name.substring(0, dotIndex);
            name = name.substring(dotIndex + 1);
        }

        // parse class member mapping.
        if (type.length() > 0 && name.length() > 0 && newName.length() > 0 && argIndex2 >= 0) {
            String arguments = line.substring(argIndex1 + 1, argIndex2).trim();//参数
            mappingProcessor.processMethodMapping(className, type, name, arguments, newClassName, newName);
        }
    }
}
