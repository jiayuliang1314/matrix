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

/**
 * Created by jinqiuchen on 17/6/14.
 *
 *    ok
 *    包含13个功能
 *    不明白的
 *    1.检查是否有多个动态库静态链接了STL
 *      如果有多个动态库都依赖了STL，应该采用动态链接的方式而非多个动态库都去静态链接STL
 *    2.搜索apk中未经裁剪的动态库文件
 *      动态库经过裁剪之后，文件大小通常会减小很多
 *
 *
 *
 *   --apk   输入apk文件路径（默认文件名以apk结尾即可）
 *   --mappingTxt   代码混淆mapping文件路径 （默认文件名是mapping.txt）
 *   --resMappingTxt   资源混淆mapping文件路径（默认文件名是resguard-mapping.txt）
 *   --input   包含了上述输入文件的目录（给定--input之后，则可以省略上述输入文件参数，但上述输入文件必须使用默认文件名）
 *   --unzip   解压apk的输出目录
 *   --output   输出结果文件路径（不含后缀，会根据format决定输出文件的后缀）
 *   --format   结果文件的输出格式（例如 html、json等）
 *   --formatJar   实现了自定义结果文件输出格式的jar包
 *   --formatConfig   对结果文件输出格式的一些配置项（json数组格式）
 *
 *   global参数之后紧跟若干个Option，这些Option是可选的，一个Option表示针对apk的一个检测选项。
 *
 * option参数
 * manifest 从AndroidManifest.xml文件中读取apk的全局信息，如packageName、versionCode等。
 *
 * fileSize 列出超过一定大小的文件，可按文件后缀过滤，并且按文件大小排序
 * --min   文件大小最小阈值，单位是KB
 * --order   按照文件大小升序（asc）或者降序（desc）排列
 * --suffix   按照文件后缀过滤，使用","作为多个文件后缀的分隔符
 *
 * countMethod 统计方法数
 *      group   输出结果按照类名(class)或者包名(package)来分组
 *
 * checkResProguard 检查是否经过了资源混淆(AndResGuard)
 *
 * findNonAlphaPng 发现不含alpha通道的png文件
 * min   png文件大小最小阈值，单位是KB
 *
 * checkMultiLibrary 检查是否包含多个ABI版本的动态库
 *
 * uncompressedFile 发现未经压缩的文件类型（即该类型的所有文件都未经压缩）
 *      suffix   按照文件后缀过滤，使用","作为多个文件后缀的分隔符
 *
 * countR 统计apk中包含的R类以及R类中的field count
 *
 * duplicatedFile 发现冗余的文件，按照文件大小降序排序
 *
 * checkMultiSTL 检查是否有多个动态库静态链接了STL
 *      toolnm   nm工具的路径
 *
 * unusedResources 发现apk中包含的无用资源
 *       rTxt   R.txt文件的路径（如果在全局参数中给定了--input，则可以省略）
 *      ignoreResources   需要忽略的资源，使用","作为多个资源名称的分隔符
 *
 * unusedAssets 发现apk中包含的无用assets文件
 *      ignoreAssets   需要忽略的assets文件，使用","作为多个文件的分隔符
 *
 * unstrippedSo 发现apk中未经裁剪的动态库文件
 *      toolnm   nm工具的路径
 *
 * 除了直接在命令行中带上详细参数外，也可以将参数配置以json的格式写到一个配置文件中，然后在命令行中使用
 * config CONFIG-FILE_PATH
 */

public final class JobConstants {


    public static final String PARAM_CONFIG = "--config";
    public static final String PARAM_INPUT = "--input";
    public static final String PARAM_APK = "--apk";
    public static final String PARAM_UNZIP = "--unzip";
    public static final String PARAM_OUTPUT = "--output";
    //mm.html 和 mm.json 是微信使用的自定义输出格式，Matrix-ApkChecker默认提供 html 、json、mm.html 以及 mm.json 四种输出格式。
    public static final String PARAM_FORMAT = "--format";
    public static final String PARAM_FORMAT_JAR = "--formatJar";
    public static final String PARAM_FORMAT_CONFIG = "--formatConfig";
    public static final String PARAM_TOOL_NM = "--toolnm";
    public static final String PARAM_MIN_SIZE_IN_KB = "--min";
    public static final String PARAM_ORDER = "--order";
    public static final String PARAM_GROUP = "--group";
    public static final String PARAM_SUFFIX = "--suffix";
    public static final String PARAM_R_TXT = "--rTxt";
    public static final String PARAM_IGNORE_RESOURCES_LIST = "--ignoreResources";
    public static final String PARAM_MAPPING_TXT = "--mappingTxt";
    public static final String PARAM_RES_MAPPING_TXT = "--resMappingTxt";
    public static final String PARAM_IGNORE_ASSETS_LIST = "--ignoreAssets";
    public static final String PARAM_LOG_LEVEL = "--log";

    public static final String OPTION_MANIFEST = "-manifest";
    public static final String OPTION_FILE_SIZE = "-fileSize";
    public static final String OPTION_COUNT_METHOD = "-countMethod";
    public static final String OPTION_CHECK_RES_PROGUARD = "-checkResProguard";
    public static final String OPTION_FIND_NON_ALPHA_PNG = "-findNonAlphaPng";
    public static final String OPTION_CHECK_MULTILIB = "-checkMultiLibrary";
    public static final String OPTION_UNCOMPRESSED_FILE = "-uncompressedFile";
    public static final String OPTION_COUNT_R_CLASS = "-countR";
    public static final String OPTION_DUPLICATE_RESOURCES = "-duplicatedFile";
    public static final String OPTION_CHECK_MULTISTL = "-checkMultiSTL";
    public static final String OPTION_UNUSED_RESOURCES = "-unusedResources";
    public static final String OPTION_UNUSED_ASSETS = "-unusedAssets";
    public static final String OPTION_UNSTRIPPED_SO = "-unstrippedSo";
    public static final String OPTION_COUNT_CLASS = "-countClass";

    public static final String ORDER_ASC = "asc";
    public static final String ORDER_DESC = "desc";
    public static final String GROUP_PACKAGE = "package";
    public static final String GROUP_CLASS = "class";


    public static final String TASK_RESULT_REGISTRY = "TaskResult-Registry";
    public static final String TASK_RESULT_REGISTERY_CLASS = "TaskResult-Registry-Class";

}
