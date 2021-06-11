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

package com.tencent.matrix.apk.model.task;


import com.google.common.collect.Ordering;
import com.google.gson.JsonArray;
import com.tencent.matrix.apk.model.task.util.ApkConstants;
import com.tencent.matrix.apk.model.exception.TaskExecuteException;
import com.tencent.matrix.apk.model.exception.TaskInitException;
import com.tencent.matrix.apk.model.job.JobConfig;
import com.tencent.matrix.apk.model.job.JobConstants;
import com.tencent.matrix.apk.model.result.TaskJsonResult;
import com.tencent.matrix.apk.model.result.TaskResult;
import com.tencent.matrix.apk.model.result.TaskResultFactory;
import com.tencent.matrix.apk.model.task.util.ApkResourceDecoder;
import com.tencent.matrix.apk.model.task.util.ApkUtil;
import com.tencent.matrix.javalib.util.FileUtil;
import com.tencent.matrix.javalib.util.Log;
import com.tencent.matrix.javalib.util.Util;
import org.jf.baksmali.BaksmaliOptions;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.iface.ClassDef;
import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import brut.androlib.AndrolibException;


/**
 * Created by jinqiuchen on 17/7/11.
 * UnusedResourceTask 可以检测出apk中未使用的资源，对于getIdentifier获取的资源可以加入白名单
 * 实现方法：
 * （1）过读取R.txt获取apk中声明的所有资源得到declareResourceSet；
 * （2）通过读取smali文件中引用资源的指令（包括通过reference和直接通过资源id引用资源）得出class中引用的资源classRefResourceSet；
 * （3）通过ApkTool解析res目录下的xml文件、AndroidManifest.xml 以及 resource.arsc 得出资源之间的引用关系；
 * （4）根据上述几步得到的中间数据即可确定出apk中未使用到的资源。
 *
 *     {
 *       "name":"-unusedResources",
 *       "--rTxt":"/Users/admin/StudioProjects/strongedge/app/build/intermediates/runtime_symbol_list/strong_edgeRelease/R.txt",
 *       "--ignoreResources"
 *       :["R.raw.*",
 *         "R.style.*",
 *         "R.styleable.*",
 *         "R.attr.*",
 *         "R.id.*",
 *         "R.string.ignore_*"
 *       ]
 *     },
 */

public class UnusedResourcesTask extends ApkTask {

    private static final String TAG = "Matrix.UnusedResourcesTask";

    private File inputFile;
    private File resourceTxt;
    private File mappingTxt;
    private File resMappingTxt;
    private final List<String> dexFileNameList;
    private final Map<String, String> rclassProguardMap;//todo 干哈的？
    //this.resourceDefMap = {HashMap@1789}  size = 5782
    // "0x7f09014f" -> "R.id.dar_container"
    // "0x7f10003f" -> "R.string.advert_facebook_full"
    // "0x7f09014e" -> "R.id.dar_close"
    // "0x7f10003e" -> "R.string.advert_a"
    //资源id 资源名字
    private final Map<String, String> resourceDefMap;
    //result = {HashMap@1790}  size = 108
    // "R.styleable.AlertDialog" -> {HashSet@2461}  size = 9
    //  key = "R.styleable.AlertDialog"
    //  value = {HashSet@2461}  size = 9
    //   0 = "0x7f0401c8"
    //   1 = "0x010100f2"
    //   2 = "0x7f040071"
    //   3 = "0x7f04019b"
    //   4 = "0x7f04019a"
    //   5 = "0x7f040072"
    //   6 = "}"
    //   7 = "0x7f040213"
    //   8 = "0x7f040212"
    // "R.styleable.DrawerArrowToggle" -> {HashSet@2463}  size = 9
    //  key = "R.styleable.DrawerArrowToggle"
    //  value = {HashSet@2463}  size = 9
    //   0 = "0x7f0400a6"
    //   1 = "0x7f040219"
    //   2 = "0x7f0400e3"
    //   3 = "0x7f04003c"
    //   4 = "0x7f04026d"
    //   5 = "0x7f04004c"
    //   6 = "0x7f04003d"
    //   7 = "}"
    //   8 = "0x7f04011c"
    //style 及其子属性
    private final Map<String, Set<String>> styleableMap;
    //1 = "R.id.tv_music_2"
    //2 = "R.id.tv_music_3"
    //3 = "R.id.tv_music_1"
    //4 = "R.id.image_view_nav_backstyleableMapground2"
    //5 = "R.styleable.ConstraintLayout_Layout_layout_constraintGuide_end"
    //6 = "R.styleable.DrawerArrowToggle_barLength"
    //7 = "R.attr.twIndexViewFluidEnabled"
    //8 = "R.id.arrow_down"
    //9 = "R.color.cardview_shadow_start_color"
    //10 = "R.id.back"
    //11 = "R.id.id_pre_title"
    //12 = "R.id.clean_after_rl"
    //13 = "R.layout.item_weather_forecasts_list"
    //14 = "R.id.people_edge_effect"
    //15 = "R.id.key_screenrecorder"
    //16 = "R.layout.activity_search_music"
    //17 = "R.string.key_edge_screen"
    //18 = "R.drawable.weather_pageindicator_default"
    //19 = "R.string.direction"
    //20 = "R.string.recent"
    //21 = "R.id.cartoon_button"
    //22 = "R.styleable.AppCompatTextView_drawableStartCompat"
    //23 = "R.attr.cardUseCompatPadding"
    //24 = "R.id.progressBar2"
    //25 = "R.id.info"
    //26 = "R.id.progressBar3"
    //27 = "R.attr.passwordToggleTint"
    //28 = "R.id.more_btn_viber"
    //29 = "R.id.skip_prev"
    //30 = "R.id.indicator_2_right"
    //31 = "R.string.key_turn_screen_on"
    //32 = "R.drawable.edge_picture"
    //33 = "R.id.edge_panel_weather_life_right_text"
    //34 = "R.attr.layout_constraintEnd_toEndOf"
    //35 = "R.drawable.weather_pageindicator_current"
    //36 = "R.styleable.GradientColor_android_startColor"
    //37 = "R.id.play_pause_icon"
    //38 = "R.id.color_3"
    //39 = "R.id.color_4"
    //40 = "R.id.color_5"
    //41 = "R.id.color_6"
    //42 = "R.id.color_7"
    //43 = "R.attr.tickMarkTintMode"
    //44 = "R.attr.riv_border_width"
    //45 = "R.id.end_padder"
    //46 = "R.styleable.lib_smartui_ColorSeekBar_isVertical"
    //47 = "R.attr.boxStrokeColor"
    //48 = "R.id.music_player"
    //49 = "R.styleable.MaterialButton_android_insetBottom"
    //50 = "R.styleable.ConstraintSet_layout_constraintRight_toRightOf"
    //51 = "R.drawable.ic_star_border"
    //52 = "R.attr.tintMode"
    //53 = "R.attr.border_color"
    //54 = "R.anim.animation_rocket_arrow2"
    //55 = "R.styleable.ConstraintLayout_Layout_layout_constraintTop_toTopOf"
    //56 = "R.layout.people_detail_activity"
    //57 = "R.attr.direction"
    //58 = "R.anim.animation_rocket_arrow1"
    //59 = "R.dimen.item_touch_helper_swipe_escape_velocity"
    //60 = "R.color.ruler_scale_text"
    //61 = "R.id.color_0"
    //62 = "R.layout.activity_online_music_list_header"
    //63 = "R.id.color_1"
    //64 = "R.id.color_2"
    //65 = "R.attr.panelMenuListWidth"
    //66 = "R.id.edge_panel_shortcut"
    //67 = "R.id.rocket_prepare"
    //68 = "R.array.lib_screenrecorder_audio_channels"
    //69 = "R.attr.textAppearanceCaption"
    //70 = "R.string.done"
    //71 = "R.id.image_category_none"
    //72 = "R.styleable.FloatingActionButton_borderWidth"
    //73 = "R.id.image_slider"
    //74 = "R.id.id_rounded_corner_color"
    //75 = "R.id.fab"
    //76 = "R.drawable.iphonex"
    //77 = "R.styleable.SwitchCompat_track"
    //78 = "R.styleable.FontFamilyFont_android_fontVariationSettings"
    //79 = "R.id.prev_btn"
    //80 = "R.id.more_btn_email"
    //81 = "R.attr.itemHorizontalPadding"
    //82 = "R.id.click_open_edge_button"
    //83 = "R.string.favorite"
    //84 = "R.attr.drawableEndCompat"
    //85 = "R.attr.actionModeStyle"
    //86 = "R.styleable.DrawerArrowToggle_arrowShaftLength"
    //87 = "R.styleable.TabItem_android_layout"
    //88 = "R.id.whatsapp_layout"
    //89 = "R.layout.notification_template_big_media_narrow_custom"
    //90 = "R.styleable.FontFamily_fontProviderAuthority"
    //91 = "R.attr.divider"
    //92 = "R.id.text"
    //93 = "R.layout.ruler_right"
    //94 = "R.styleable.SnackbarLayout_maxActionInlineWidth"
    //95 = "R.styleable.ConstraintSet_layout_goneMarginTop"
    //96 = "R.attr.lottie_enableMergePathsForKitKatAndAbove"
    //97 = "R.styleable.ConstraintSet_layout_constraintStart_toStartOf"
    private final Set<String> resourceRefSet;
    private final Set<String> unusedResSet;
    private final Set<String> ignoreSet;
    //result = {HashMap@1835}  size = 837
    // "R.layout.brightness_expanded_layout" -> {HashSet@2938}  size = 11
    //  key = "R.layout.brightness_expanded_layout"
    //  value = {HashSet@2938}  size = 11
    //   0 = "R.id.seekbar_brightness_expanded"
    //   1 = "R.id.icon_brightness_expanded"
    //   2 = "R.drawable.action_brightness_auto"
    //   3 = "R.id.guideline8"
    //   4 = "R.id.guideline7"
    //   5 = "R.drawable.progress_drawable_large"
    //   6 = "R.dimen.margin_text"
    //   7 = "R.id.guideline2"
    //   8 = "R.drawable.ic_brightness"
    //   9 = "R.id.auto_brightness_expanded"
    //   10 = "R.id.guideline1"
    // "R.layout.edge_panel_queue" -> {HashSet@2940}  size = 23
    private final Map<String, Set<String>> nonValueReferences;
    private Stack<String> visitPath;

    public UnusedResourcesTask(JobConfig config, Map<String, String> params) {
        super(config, params);
        type = TaskFactory.TASK_TYPE_UNUSED_RESOURCES;
        dexFileNameList = new ArrayList<>();
        ignoreSet = new HashSet<>();
        rclassProguardMap = new HashMap<>();
        resourceDefMap = new HashMap<>();
        styleableMap = new HashMap<>();
        resourceRefSet = new HashSet<>();
        unusedResSet = new HashSet<>();
        nonValueReferences = new HashMap<>();
        visitPath = new Stack<String>();
    }

    @Override
    public void init() throws TaskInitException {
        super.init();

        String inputPath = config.getUnzipPath();
        if (Util.isNullOrNil(inputPath)) {
            throw new TaskInitException(TAG + "---APK-UNZIP-PATH can not be null!");
        }
        inputFile = new File(inputPath);
        if (!inputFile.exists()) {
            throw new TaskInitException(TAG + "---APK-UNZIP-PATH '" + inputPath + "' is not exist!");
        } else if (!inputFile.isDirectory()) {
            throw new TaskInitException(TAG + "---APK-UNZIP-PATH '" + inputPath + "' is not directory!");
        }

        if (!params.containsKey(JobConstants.PARAM_R_TXT) || Util.isNullOrNil(params.get(JobConstants.PARAM_R_TXT))) {
            throw new TaskInitException(TAG + "---The File 'R.txt' can not be null!");
        }
        resourceTxt = new File(params.get(JobConstants.PARAM_R_TXT));
        if (!FileUtil.isLegalFile(resourceTxt)) {
            throw new TaskInitException(TAG + "---The Resource declarations file 'R.txt' is not legal!");
        }

        if (!Util.isNullOrNil(config.getMappingFilePath())) {
            mappingTxt = new File(config.getMappingFilePath());
            if (!FileUtil.isLegalFile(mappingTxt)) {
                throw new TaskInitException(TAG + "---The Proguard mapping file 'mapping.txt' is not legal!");
            }
        }
        if (params.containsKey(JobConstants.PARAM_IGNORE_RESOURCES_LIST) && !Util.isNullOrNil(params.get(JobConstants.PARAM_IGNORE_RESOURCES_LIST))) {
            String[] ignoreRes = params.get(JobConstants.PARAM_IGNORE_RESOURCES_LIST).split(",");
            for (String ignore : ignoreRes) {
                //this.ignoreSet = {HashSet@1146}  size = 6
                // 0 = "^\QR.style.\E.*?$"
                // 1 = "^\QR.styleable.\E.*?$"
                // 2 = "^\QR.string.ignore_\E.*?$"
                // 3 = "^\QR.attr.\E.*?$"
                // 4 = "^\QR.raw.\E.*?$"
                // 5 = "^\QR.id.\E.*?$"
                ignoreSet.add(Util.globToRegexp(ignore));
            }
        }
        if (!Util.isNullOrNil(config.getResMappingFilePath())) {
            resMappingTxt = new File(config.getResMappingFilePath());
            if (!FileUtil.isLegalFile(resMappingTxt)) {
                throw new TaskInitException(TAG + "---The Resguard mapping file 'resguard-mapping.txt' is not legal!");
            }
        }

        File[] files = inputFile.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().endsWith(ApkConstants.DEX_FILE_SUFFIX)) {
                    dexFileNameList.add(file.getName());
                }
            }
        }
    }

    private String parseResourceId(String resId) {
        if (!Util.isNullOrNil(resId) && resId.startsWith("0x")) {
            if (resId.length() == 10) {
                return resId;
            } else if (resId.length() < 10) {
                StringBuilder strBuilder = new StringBuilder(resId);
                for (int i = 0; i < 10 - resId.length(); i++) {
                    strBuilder.append('0');
                }
                return strBuilder.toString();
            }
        }
        return "";
    }

    private String parseResourceNameFromProguard(String entry) {
        if (!Util.isNullOrNil(entry)) {
            String[] columns = entry.split("->");
            if (columns.length == 2) {
                int index = columns[1].indexOf(':');
                if (index >= 0) {
                    final String className = ApkUtil.getNormalClassName(columns[0]);
                    final String fieldName = columns[1].substring(0, index);
                    if (!rclassProguardMap.isEmpty()) {
                        String resource = className.replace('$', '.') + "." + fieldName;
                        if (rclassProguardMap.containsKey(resource)) {
                            return rclassProguardMap.get(resource);
                        } else {
                            return "";
                        }
                    } else {
                        if (ApkUtil.isRClassName(ApkUtil.getPureClassName(className))) {
                            return (ApkUtil.getPureClassName(className) + "." + fieldName).replace('$', '.');
                        }
                    }
                }
            }
        }
        return "";
    }

    /**
     * 2.
     * int anim abc_fade_in 0x7f010000
     * int anim abc_fade_out 0x7f010001
     * int anim abc_grow_fade_in_from_bottom 0x7f010002
     * int anim abc_popup_enter 0x7f010003
     * int anim abc_popup_exit 0x7f010004
     * @throws IOException
     */
    private void readResourceTxtFile() throws IOException {
        BufferedReader bufferedReader = new BufferedReader(new FileReader(resourceTxt));
        String line = bufferedReader.readLine();
        try {
            while (line != null) {
                String[] columns = line.split(" ");
                if (columns.length >= 4) {
                    final String resourceName = "R." + columns[1] + "." + columns[2];
                    if (!columns[0].endsWith("[]") && columns[3].startsWith("0x")) {
                        if (columns[3].startsWith("0x01")) {
                            Log.d(TAG, "ignore system resource %s", resourceName);
                        } else {
                            final String resId = parseResourceId(columns[3]);
                            if (!Util.isNullOrNil(resId)) {
                                resourceDefMap.put(resId, resourceName);
                            }
                        }
                    } else {
                        Log.d(TAG, "ignore resource %s", resourceName);
                        if (columns[0].endsWith("[]") && columns.length > 5) {
                            Set<String> attrReferences = new HashSet<String>();
                            for (int i = 4; i < columns.length; i++) {
                                if (columns[i].endsWith(",")) {
                                    attrReferences.add(columns[i].substring(0, columns[i].length() - 1));
                                } else {
                                    attrReferences.add(columns[i]);
                                }
                            }
                            .put(resourceName, attrReferences);
                        }
                    }
                }
                line = bufferedReader.readLine();
            }
        } finally {
            bufferedReader.close();
        }
    }

    /**
     * 1.todo 干哈的
     * @throws IOException
     */
    private void readMappingTxtFile() throws IOException {
        // com.tencent.mm.R$string -> com.tencent.mm.R$l:
        //      int fade_in_property_anim -> aRW

        if (mappingTxt != null) {
            BufferedReader bufferedReader = new BufferedReader(new FileReader(mappingTxt));
            String line = bufferedReader.readLine();
            boolean readRField = false;
            String beforeClass = "", afterClass = "";
            try {
                while (line != null) {
                    if (!line.startsWith(" ")) {
                        String[] pair = line.split("->");
                        if (pair.length == 2) {
                            beforeClass = pair[0].trim();
                            afterClass = pair[1].trim();
                            afterClass = afterClass.substring(0, afterClass.length() - 1);
                            if (!Util.isNullOrNil(beforeClass) && !Util.isNullOrNil(afterClass) && ApkUtil.isRClassName(ApkUtil.getPureClassName(beforeClass))) {
                                Log.d(TAG, "before:%s,after:%s", beforeClass, afterClass);
                                readRField = true;
                            } else {
                                readRField = false;
                            }
                        } else {
                            readRField = false;
                        }
                    } else {
                        if (readRField) {
                            String[] entry = line.split("->");
                            if (entry.length == 2) {
                                String key = entry[0].trim();
                                String value = entry[1].trim();
                                if (!Util.isNullOrNil(key) && !Util.isNullOrNil(value)) {
                                    String[] field = key.split(" ");
                                    if (field.length == 2) {
                                        Log.d(TAG, "%s -> %s", afterClass.replace('$', '.') + "." + value, ApkUtil.getPureClassName(beforeClass).replace('$', '.') + "." + field[1]);
                                        rclassProguardMap.put(afterClass.replace('$', '.') + "." + value, ApkUtil.getPureClassName(beforeClass).replace('$', '.') + "." + field[1]);
                                    }
                                }
                            }
                        }
                    }
                    line = bufferedReader.readLine();
                }
            } finally {
                bufferedReader.close();
            }
        }
    }

    //3.读取smail
    private void decodeCode() throws IOException {
        for (String dexFileName : dexFileNameList) {
            DexBackedDexFile dexFile = DexFileFactory.loadDexFile(new File(inputFile, dexFileName), Opcodes.forApi(15));

            BaksmaliOptions options = new BaksmaliOptions();
            List<? extends ClassDef> classDefs = Ordering.natural().sortedCopy(dexFile.getClasses());

            for (ClassDef classDef : classDefs) {
                String[] lines = ApkUtil.disassembleClass(classDef, options);
                if (lines != null) {
                    readSmaliLines(lines);
                }
            }

        }
    }

    /*

        1. const

        const v6, 0x7f0c0061

        2. sget

        sget v6, Lcom/tencent/mm/R$string;->chatting_long_click_menu_revoke_msg:I
        sget v1, Lcom/tencent/mm/libmmui/R$id;->property_anim:I

        3. sput

        sput-object v0, Lcom/tencent/mm/plugin_welab_api/R$styleable;->ActionBar:[I   //define resource in R.java

        4. array-data

        :array_0
        .array-data 4
            0x7f0a0022
            0x7f0a0023
        .end array-data

    */

    /**
     * 3.1.读取smail
     * @param lines
     */
    private void readSmaliLines(String[] lines) {
        if (lines == null) {
            return;
        }
        boolean arrayData = false;
        for (String line : lines) {
            line = line.trim();
            if (!Util.isNullOrNil(line)) {
                if (line.startsWith("const")) {
                    String[] columns = line.split(" ");
                    if (columns.length >= 3) {
                        final String resId = parseResourceId(columns[2].trim());
                        if (!Util.isNullOrNil(resId) && resourceDefMap.containsKey(resId)) {
                            resourceRefSet.add(resourceDefMap.get(resId));
                        }
                    }
                } else if (line.startsWith("sget")) {
                    String[] columns = line.split(" ");
                    if (columns.length >= 3) {
                        final String resourceRef = parseResourceNameFromProguard(columns[2].trim());
                        if (!Util.isNullOrNil(resourceRef)) {
                            Log.d(TAG, "find resource reference %s", resourceRef);
                            if (styleableMap.containsKey(resourceRef)) {
                                //reference of R.styleable.XXX
                                for (String attr : styleableMap.get(resourceRef)) {
                                    resourceRefSet.add(resourceDefMap.get(attr));
                                }
                            } else {
                                resourceRefSet.add(resourceRef);
                            }
                        }
                    }
                } else if (line.startsWith(".array-data 4")) {
                    arrayData = true;
                } else if (line.startsWith(".end array-data")) {
                    arrayData = false;
                } else  {
                    if (arrayData) {
                        String[] columns = line.split(" ");
                        if (columns.length > 0) {
                            final String resId = parseResourceId(columns[0].trim());
                            if (!Util.isNullOrNil(resId) && resourceDefMap.containsKey(resId)) {
                                Log.d(TAG, "array field resource, %s", resId);
                                resourceRefSet.add(resourceDefMap.get(resId));
                            }
                        }
                    }
                }
            }
        }
    }

    //4.读取res
    private void decodeResources() throws IOException, InterruptedException, AndrolibException, XmlPullParserException {
        File manifestFile = new File(inputFile, ApkConstants.MANIFEST_FILE_NAME);
        File arscFile = new File(inputFile, ApkConstants.ARSC_FILE_NAME);
        File resDir = new File(inputFile, ApkConstants.RESOURCE_DIR_NAME);
        if (!resDir.exists()) {
            resDir = new File(inputFile, ApkConstants.RESOURCE_DIR_PROGUARD_NAME);
        }

        Map<String, Set<String>> fileResMap = new HashMap<>();
        Set<String> valuesReferences = new HashSet<>();

        ApkResourceDecoder.decodeResourcesRef(manifestFile, arscFile, resDir, fileResMap, valuesReferences);

        Map<String, String> resguardMap = config.getResguardMap();

        for (String resource : fileResMap.keySet()) {
            Set<String> result = new HashSet<>();
            for (String resName : fileResMap.get(resource)) {
               if (resguardMap.containsKey(resName)) {
                   result.add(resguardMap.get(resName));
               } else {
                   result.add(resName);
               }
            }
            if (resguardMap.containsKey(resource)) {
                nonValueReferences.put(resguardMap.get(resource), result);
            } else {
                nonValueReferences.put(resource, result);
            }
        }

        for (String resource : valuesReferences) {
            if (resguardMap.containsKey(resource)) {
                resourceRefSet.add(resguardMap.get(resource));
            } else {
                resourceRefSet.add(resource);
            }
        }

        for (String resource : unusedResSet) {
            if (ignoreResource(resource)) {
                resourceRefSet.add(resource);
            }
        }

        for (String resource : resourceRefSet) {
            readChildReference(resource);
        }
    }

    private boolean ignoreResource(String name) {
        for (String pattern : ignoreSet) {
            if (name.matches(pattern)) {
                return true;
            }
        }
        return false;
    }

    private void readChildReference(String resource) throws IllegalStateException {
        if (nonValueReferences.containsKey(resource)) {
            visitPath.push(resource);
            Set<String> childReference = nonValueReferences.get(resource);
            unusedResSet.removeAll(childReference);
            for (String reference : childReference) {
                if (!visitPath.contains(reference)) {
                    readChildReference(reference);
                } else {
                    visitPath.push(reference);
                    throw new IllegalStateException("Found resource cycle! " + visitPath.toString());
                }
            }
            visitPath.pop();
        }
    }


    @Override
    public TaskResult call() throws TaskExecuteException {
        try {
            TaskResult taskResult = TaskResultFactory.factory(type, TaskResultFactory.TASK_RESULT_TYPE_JSON, config);
            long startTime = System.currentTimeMillis();
            readMappingTxtFile();
            readResourceTxtFile();
            //this.resourceDefMap = {HashMap@1789}  size = 5782
            // "0x7f09014f" -> "R.id.dar_container"
            // "0x7f10003f" -> "R.string.advert_facebook_full"
            // "0x7f09014e" -> "R.id.dar_close"
            // "0x7f10003e" -> "R.string.advert_a"
            unusedResSet.addAll(resourceDefMap.values());
            Log.i(TAG, "find resource declarations %d items.", unusedResSet.size());
            decodeCode();
            Log.i(TAG, "find resource references in classes: %d items.", resourceRefSet.size());
            decodeResources();
            Log.i(TAG, "find resource references %d items.", resourceRefSet.size());
            unusedResSet.removeAll(resourceRefSet);
            Log.i(TAG, "find unused references %d items", unusedResSet.size());
            Log.d(TAG, "find unused references %s", unusedResSet.toString());
            JsonArray jsonArray = new JsonArray();
            for (String name : unusedResSet) {
                jsonArray.add(name);
            }
            ((TaskJsonResult) taskResult).add("unused-resources", jsonArray);
            taskResult.setStartTime(startTime);
            taskResult.setEndTime(System.currentTimeMillis());
            return taskResult;
        } catch (Exception e) {
            throw new TaskExecuteException(e.getMessage(), e);
        }
    }
}
