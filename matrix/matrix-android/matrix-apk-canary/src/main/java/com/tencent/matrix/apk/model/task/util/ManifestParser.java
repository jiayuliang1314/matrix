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

package com.tencent.matrix.apk.model.task.util;

import brut.androlib.AndrolibException;
import brut.androlib.res.decoder.AXmlResourceParser;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tencent.matrix.javalib.util.Util;
import org.xmlpull.v1.XmlPullParser;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Stack;

/**
 * Created by jinqiuchen on 17/11/13.
 *
 * ok
 *
 * members = {LinkedTreeMap@1585}  size = 11
 *  "android:versionCode" -> {JsonPrimitive@1600} ""228""
 *  "android:versionName" -> {JsonPrimitive@1602} ""2.2.8""
 *  "android:compileSdkVersion" -> {JsonPrimitive@1604} ""29""
 *  "android:compileSdkVersionCodename" -> {JsonPrimitive@1606} ""10""
 *  "package" -> {JsonPrimitive@1608} ""com.strong.love_edge8""
 *  "platformBuildVersionCode" -> {JsonPrimitive@1610} ""29""
 *  "platformBuildVersionName" -> {JsonPrimitive@1612} ""10""
 *  "uses-sdk" -> {JsonArray@1614} "[{"android:minSdkVersion":"18","android:targetSdkVersion":"29"}]"
 *  "uses-permission" -> {JsonArray@1616} "[{"android:name":"com.android.alarm.permission.SET_ALARM"},{"android:name":"android.permission.WAKE_LOCK"},{"android:name":"com.android.vending.BILLING"},{"android:name":"android.permission.CAMERA"},{"android:name":"android.permission.READ_CONTACTS"},{"android:name":"android.permission.WRITE_CONTACTS"},{"android:name":"android.permission.CALL_PHONE"},{"android:name":"android.permission.RECEIVE_BOOT_COMPLETED"},{"android:name":"android.permission.SYSTEM_ALERT_WINDOW"},{"android:name":"android.permission.SYSTEM_OVERLAY_WINDOW"},{"android:name":"android.permission.GET_TASKS"},{"android:name":"android.permission.SET_WALLPAPER"},{"android:name":"android.permission.READ_CALENDAR"},{"android:name":"android.permission.WRITE_CALENDAR"},{"android:name":"android.permission.WRITE_SETTINGS"},{"android:name":"android.permission.SET_WALLPAPER_HINTS"},{"android:name":"android.permission.VIBRATE"},{"android:name":"android.permission.INTERNET"},{"android:name":"android.permission.ACCESS_COARSE_LOCATION""
 *  "uses-feature" -> {JsonArray@1618} "[{"android:name":"android.hardware.camera"}]"
 *  "application" -> {JsonArray@1620} "[{"android:theme":"@style/od","android:label":"@string/b_","android:icon":"@drawable/ic_launcher1","android:name":"com.common.data.app.EasyController","android:allowBackup":"true","android:hardwareAccelerated":"true","android:supportsRtl":"true","android:networkSecurityConfig":"@xml/b","android:appComponentFactory":"androidx.core.app.CoreComponentFactory","android:requestLegacyExternalStorage":"true","uses-library":[{"android:name":"org.apache.http.legacy","android:required":"false"}],"meta-data":[{"android:name":"com.google.android.gms.ads.APPLICATION_ID","android:value":"@string/ax"},{"android:name":"android.max_aspect","android:value":"2.1"},{"android:name":"com.facebook.sdk.ApplicationId","android:value":"@string/em"},{"android:name":"com.google.android.gms.version","android:value":"@integer/t"},{"android:name":"com.google.firebase.messaging.default_notification_icon","android:resource":"@drawable/ic_launcher1"},{"android:name":"com.google.firebase.messaging.default_notification_co"
 */

public class ManifestParser {
    private static final String ROOTTAG = "manifest";
    private final AXmlResourceParser resourceParser;//todo
    private File manifestFile;
    private boolean isParseStarted;
    private final Stack<JsonObject> jsonStack = new Stack<>();
    private JsonObject result;

    public ManifestParser(String path) {
        manifestFile = new File(path);
        resourceParser = ApkResourceDecoder.createAXmlParser();
    }

    public ManifestParser(File manifestFile) {
        if (manifestFile != null) {
            this.manifestFile = manifestFile;
        }
        resourceParser = ApkResourceDecoder.createAXmlParser();
    }

    public ManifestParser(File manifestFile, File arscFile) throws IOException, AndrolibException {
        if (manifestFile != null) {
            this.manifestFile = manifestFile;
        }
        resourceParser = ApkResourceDecoder.createAXmlParser(arscFile);
    }

    public JsonObject parse() throws Exception {

        FileInputStream inputStream = null;
        try {
            inputStream = new FileInputStream(manifestFile);
            try {
                resourceParser.open(inputStream);
                int token = resourceParser.nextToken();

                while (token != XmlPullParser.END_DOCUMENT) {
                    token = resourceParser.next();
                    if (token == XmlPullParser.START_TAG) {
                        handleStartElement();
                    } else if (token == XmlPullParser.TEXT) {
                        handleElementContent();
                    } else if (token == XmlPullParser.END_TAG) {
                        handleEndElement();
                    }
                }
            } finally {
                resourceParser.close();
                if (inputStream != null) {
                    inputStream.close();
                }
            }
        } catch (Exception e) {
            throw e;
        }


        return result;
    }


    private void handleStartElement() {
        final String name = resourceParser.getName();

        if (name.equals(ROOTTAG)) {
            isParseStarted = true;
        }
        if (isParseStarted) {
            JsonObject jsonObject = new JsonObject();
            for (int i = 0; i < resourceParser.getAttributeCount(); i++) {
                if (!Util.isNullOrNil(resourceParser.getAttributePrefix(i))) {
                    jsonObject.addProperty(resourceParser.getAttributePrefix(i) + ":" + resourceParser.getAttributeName(i), resourceParser.getAttributeValue(i));
                } else {
                    jsonObject.addProperty(resourceParser.getAttributeName(i), resourceParser.getAttributeValue(i));
                }
            }
            jsonStack.push(jsonObject);
        }
    }

    private void handleElementContent() {
        //do nothing
    }

    private void handleEndElement() {
        final String name = resourceParser.getName();
        JsonObject jsonObject = jsonStack.pop();

        if (jsonStack.isEmpty()) {                                      //root element
            result = jsonObject;
        } else {
            JsonObject preObject = jsonStack.peek();
            JsonArray jsonArray = null;
            if (preObject.has(name)) {
                jsonArray = preObject.getAsJsonArray(name);
                jsonArray.add(jsonObject);
            } else {
                jsonArray = new JsonArray();
                jsonArray.add(jsonObject);
                preObject.add(name, jsonArray);
            }
        }
    }
}
