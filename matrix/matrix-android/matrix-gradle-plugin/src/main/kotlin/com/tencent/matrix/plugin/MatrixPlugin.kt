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

package com.tencent.matrix.plugin

import com.android.build.gradle.AppExtension
import com.tencent.matrix.javalib.util.Log
import com.tencent.matrix.plugin.extension.MatrixExtension
import com.tencent.matrix.plugin.extension.MatrixRemoveUnusedResExtension
import com.tencent.matrix.plugin.task.MatrixTasksManager
import com.tencent.matrix.trace.extension.MatrixTraceExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware

class MatrixPlugin : Plugin<Project> {
    companion object {//伴生对象，里边的值都是static的
        const val TAG = "Matrix.Plugin"
    }

    override fun apply(project: Project) {
//apply plugin: 'com.tencent.matrix-plugin'
//matrix {
//
//    logLevel "D"
//
//    trace {
//        enable = true
//        baseMethodMapFile = "${project.projectDir}/matrixTrace/methodMapping.txt"
//        blackListFile = "${project.projectDir}/matrixTrace/blackMethodList.txt"
//    }
//    removeUnusedResources {
//        variant = "debug"
//
//        v2 = removeUnusedResourcesV2Enable
//
//        if (!v2) {
//            unusedResources = project.ext.unusedResourcesSet
//        }
//
//        enable true
//        needSign true
//        shrinkArsc true
//        shrinkDuplicates true
//        use7zip = true
//        zipAlign = true
//        embedResGuard true
//
//        apkCheckerPath = "${project.configurations.apkCheckerDependency.resolve().find { it.name.startsWith("matrix-apk-canary") }.getAbsolutePath()}"
//        sevenZipPath = "${project.configurations.sevenZipDependency.resolve().getAt(0).getAbsolutePath()}"
//        //Notice: You need to modify the  value of $apksignerPath on different platform. the value below only suitable for Mac Platform,
//        //if on Windows, you may have to  replace apksigner with apksigner.bat.
//        apksignerPath = "${android.getSdkDirectory().getAbsolutePath()}/build-tools/${android.getBuildToolsVersion()}/apksigner"
//        zipAlignPath = "${android.getSdkDirectory().getAbsolutePath()}/build-tools/${android.getBuildToolsVersion()}/zipalign"
//        ignoreResources = ["R.id.*", "R.bool.*", "R.layout.unused_layout"]
//    }
//}
        //创建MatrixExtension对象
        val matrix = project.extensions.create("matrix", MatrixExtension::class.java)
        //创建MatrixTraceExtension对象
        val traceExtension = (matrix as ExtensionAware).extensions.create("trace", MatrixTraceExtension::class.java)
        //创建MatrixRemoveUnusedResExtension对象，todo
        val removeUnusedResourcesExtension = matrix.extensions.create("removeUnusedResources", MatrixRemoveUnusedResExtension::class.java)

        //只支持application
        if (!project.plugins.hasPlugin("com.android.application")) {
            throw GradleException("Matrix Plugin, Android Application plugin required.")
        }

        project.afterEvaluate {
            Log.setLogLevel(matrix.logLevel)//设置level
        }

        //创建Matrix的tasks
        MatrixTasksManager().createMatrixTasks(
                project.extensions.getByName("android") as AppExtension,
                project,
                traceExtension,
                removeUnusedResourcesExtension
        )
    }
}
