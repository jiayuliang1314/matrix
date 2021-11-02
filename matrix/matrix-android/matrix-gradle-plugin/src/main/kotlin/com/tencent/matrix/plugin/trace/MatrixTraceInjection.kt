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

package com.tencent.matrix.plugin.trace

import com.android.build.gradle.AppExtension
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.internal.tasks.DexArchiveBuilderTask
import com.android.build.gradle.internal.tasks.factory.dependsOn
import com.android.builder.model.CodeShrinker
import com.tencent.matrix.javalib.util.Log
import com.tencent.matrix.plugin.compat.CreationConfig
import com.tencent.matrix.plugin.compat.CreationConfig.Companion.getCodeShrinker
import com.tencent.matrix.plugin.extension.MatrixExtension
import com.tencent.matrix.plugin.task.BaseCreationAction
import com.tencent.matrix.plugin.task.MatrixTraceTask
import com.tencent.matrix.plugin.transform.MatrixTraceTransform
import com.tencent.matrix.trace.extension.ITraceSwitchListener
import com.tencent.matrix.trace.extension.MatrixTraceExtension
import org.gradle.api.Project
import org.gradle.api.Task

class MatrixTraceInjection : ITraceSwitchListener {

    companion object {
        const val TAG = "Matrix.TraceInjection"
    }

    private var traceEnable = false

    override fun onTraceEnabled(enable: Boolean) {
        traceEnable = enable
    }

    //step 1
    fun inject(matrix: MatrixExtension, appExtension: AppExtension,
               project: Project,
               extension: MatrixTraceExtension) {
        injectTransparentTransform(appExtension, project, extension)
        project.afterEvaluate {
            if (extension.isEnable) {
                doInjection(matrix, appExtension, project, extension)
            }
        }
    }

    private var transparentTransform: MatrixTraceTransform? = null

    //step 2
    private fun injectTransparentTransform(appExtension: AppExtension,
                                           project: Project,
                                           extension: MatrixTraceExtension) {
        //创建一个MatrixTraceTransform
        transparentTransform = MatrixTraceTransform(project, extension)
        //注册MatrixTraceTransform
        appExtension.registerTransform(transparentTransform!!)
    }

    //step 3
    private fun doInjection(matrix: MatrixExtension, appExtension: AppExtension,
                            project: Project,
                            extension: MatrixTraceExtension) {
        appExtension.applicationVariants.all { variant ->

//            var methodNewMapMergeAssetsFilePath = "/Users/admin/StudioProjects/matrix/samples/sample-android/app/build/intermediates/merged_assets/debug/out"+ "/tracecanaryObfuscationMapping.txt"//variant.mergeAssetsProvider.get().outputDir.get().asFile.absolutePath + "/tracecanaryObfuscationMapping.txt"
//            Log.i("TraceCanary", "doInjection methodNewMapMergeAssetsFilePath " + methodNewMapMergeAssetsFilePath)

            if (matrix.filterObfuscatedVariants(variant)) {
                //判断哪种 todo
                if (injectTaskOrTransform(project, extension, variant) == InjectionMode.TransformInjection) {
                    Log.i("TraceCanary", "InjectionMode TransformInjection")
                    // Inject transform
                    transformInjection()
                } else {
                    Log.i("TraceCanary", "InjectionMode TaskInjection")
                    // Inject task
                    taskInjection(project, extension, variant)
                }
            }
        }
    }

    //step 4.2
    private fun taskInjection(project: Project,
                              extension: MatrixTraceExtension,
                              variant: BaseVariant) {

        Log.i(TAG, "Using trace task mode.")

        project.afterEvaluate {

            val creationConfig = CreationConfig(variant, project)
            val action = MatrixTraceTask.CreationAction(creationConfig, extension)
            val traceTaskProvider = project.tasks.register(action.name, action.type, action)

            val variantName = variant.name

            val minifyTasks = arrayOf(
                    BaseCreationAction.computeTaskName("minify", variantName, "WithProguard")
            )

            var minify = false
            for (taskName in minifyTasks) {
                val taskProvider = BaseCreationAction.findNamedTask(project.tasks, taskName)
                if (taskProvider != null) {
                    minify = true
                    //找到位置Proguard
                    traceTaskProvider.dependsOn(taskProvider)//todo
                }
            }

            if (minify) {
                val dexBuilderTaskName = BaseCreationAction.computeTaskName("dexBuilder", variantName, "")
                val taskProvider = BaseCreationAction.findNamedTask(project.tasks, dexBuilderTaskName)

                taskProvider?.configure { task: Task ->
                    //找到位置dexBuilder
                    traceTaskProvider.get().wired(task as DexArchiveBuilderTask)//todo
                }

                if (taskProvider == null) {
                    Log.e(TAG, "Do not find '$dexBuilderTaskName' task. Inject matrix trace task failed.")
                }
            }
        }
    }

    //step 4.1
    private fun transformInjection() {

        Log.i(TAG, "Using trace transform mode.")
//        transparentTransform!!.methodNewMapMergeAssetsFilePath=methodNewMapMergeAssetsFilePath
        transparentTransform!!.enable()
    }

    enum class InjectionMode {
        TaskInjection,
        TransformInjection,
    }

    //step 3.1
    private fun injectTaskOrTransform(project: Project,
                                      extension: MatrixTraceExtension,
                                      variant: BaseVariant): InjectionMode {

        if (!variant.buildType.isMinifyEnabled
                || extension.isTransformInjectionForced
                || getCodeShrinker(project) == CodeShrinker.R8) {
            return InjectionMode.TransformInjection
        }

        return InjectionMode.TaskInjection
    }
}
