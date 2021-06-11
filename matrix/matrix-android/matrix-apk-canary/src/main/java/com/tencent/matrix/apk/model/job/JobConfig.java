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

package com.tencent.matrix.apk.model.job;

import com.android.utils.Pair;
import com.google.gson.JsonArray;

import java.util.List;
import java.util.Map;

/**
 * Created by jinqiuchen on 17/6/15.
 * apkPath = "/Users/admin/StudioProjects/strongedge/app/build/outputs/apk/strong_edge/release/AndResGuard_app-strong_edge-release/app-strong_edge-release_aligned_signed.apk"
 * unzipPath = "/Users/admin/StudioProjects/strongedge/app/build/outputs/apk/strong_edge/release/AndResGuard_app-strong_edge-release/app-strong_edge-release_aligned_signed_unzip"
 * outputPath = "/Users/admin/StudioProjects/strongedge/app/build/outputs/apk/strong_edge/release/AndResGuard_app-strong_edge-release/apk-checker-result"
 * mappingFilePath = "/Users/admin/StudioProjects/strongedge/app/build/outputs/mapping/strong_edgeRelease/mapping.txt"
 * resMappingFilePath = "/Users/admin/StudioProjects/strongedge/app/build/outputs/apk/strong_edge/release/AndResGuard_app-strong_edge-release/resource_mapping_app-strong_edge-release.txt"
 * outputConfig [{"name":"-countMethod","group":[{"name":"Android System","package":"android"},{"name":"java system","package":"java"},{"name":"com.tencent.test.$","package":"com.tencent.test.$"}]}]
 * outputFormatList = {ArrayList@904}  size = 2
 * 0 = "mm.html"
 * 1 = "mm.json"
 * <p>
 * proguardClassMap
 * com.newbilling.view.activity.VipActivity -> com.newbilling.view.activity.VipActivity
 * <p>
 * resguardMap
 * R.animator.e -> R.animator.mtrl_chip_state_list_anim
 * <p>
 * entrySizeMap
 * //            "res/color/abc_primary_text_material_light.xml" -> {Pair@1161} "Pair [first=464, second=229]"
 * //            key = "res/color/abc_primary_text_material_light.xml"
 * //            value = {Pair@1161} "Pair [first=464, second=229]"
 * <p>
 * entryNameMap
 * r/n/btn_normal.webp -> res/drawable-xhdpi-v4/btn_normal.webp
 */

public final class JobConfig {

    private String inputDir;
    private String apkPath;//ok
    private String unzipPath;//ok
    private String outputPath;//ok
    private String mappingFilePath;//ok
    private String resMappingFilePath;//ok
    private JsonArray outputConfig;//ok countMethod

    private List<String> outputFormatList;//ok
    private Map<String, String> proguardClassMap;//ok
    private Map<String, String> resguardMap;//ok
    private Map<String, Pair<Long, Long>> entrySizeMap;//ok
    private Map<String, String> entryNameMap;//ok

    public String getInputDir() {
        return inputDir;
    }

    public void setInputDir(String inputDir) {
        this.inputDir = inputDir;
    }

    public String getApkPath() {
        return apkPath;
    }

    public void setApkPath(String apkPath) {
        this.apkPath = apkPath;
    }

    public List<String> getOutputFormatList() {
        return outputFormatList;
    }

    public void setOutputFormatList(List<String> outputFormatList) {
        this.outputFormatList = outputFormatList;
    }

    public String getUnzipPath() {
        return unzipPath;
    }

    public void setUnzipPath(String unzipPath) {
        this.unzipPath = unzipPath;
    }

    public String getOutputPath() {
        return outputPath;
    }

    public void setOutputPath(String outputPath) {
        this.outputPath = outputPath;
    }

    public String getMappingFilePath() {
        return mappingFilePath;
    }

    public void setMappingFilePath(String mappingFilePath) {
        this.mappingFilePath = mappingFilePath;
    }

    public String getResMappingFilePath() {
        return resMappingFilePath;
    }

    public void setResMappingFilePath(String resMappingFilePath) {
        this.resMappingFilePath = resMappingFilePath;
    }

    public Map<String, String> getProguardClassMap() {
        return proguardClassMap;
    }


    public void setProguardClassMap(Map<String, String> proguardClassMap) {
        this.proguardClassMap = proguardClassMap;
    }

    public Map<String, String> getResguardMap() {
        return resguardMap;
    }

    public void setResguardMap(Map<String, String> resguardMap) {
        this.resguardMap = resguardMap;
    }

    public Map<String, Pair<Long, Long>> getEntrySizeMap() {
        return entrySizeMap;
    }

    public void setEntrySizeMap(Map<String, Pair<Long, Long>> entrySizeMap) {
        this.entrySizeMap = entrySizeMap;
    }

    public Map<String, String> getEntryNameMap() {
        return entryNameMap;
    }

    public void setEntryNameMap(Map<String, String> entryNameMap) {
        this.entryNameMap = entryNameMap;
    }

    public JsonArray getOutputConfig() {
        return outputConfig;
    }

    public void setOutputConfig(JsonArray outputConfig) {
        this.outputConfig = outputConfig;
    }

}
