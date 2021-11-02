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

package com.tencent.matrix.plugin.compat

import com.android.build.gradle.AppExtension
import com.tencent.matrix.javalib.util.Log
import com.tencent.matrix.plugin.extension.MatrixExtension
import com.tencent.matrix.plugin.trace.MatrixTraceInjection
import com.tencent.matrix.plugin.transform.MatrixTraceLegacyTransform
import com.tencent.matrix.trace.extension.ITraceSwitchListener
import com.tencent.matrix.trace.extension.MatrixTraceExtension
import org.gradle.api.Project

class MatrixTraceCompat : ITraceSwitchListener {//Compat兼容性;

    companion object {
        const val TAG = "Matrix.TraceCompat"

        const val LEGACY_FLAG = "matrix_trace_legacy"//legacy遗产; 遗赠财物; 遗留; 后遗症;
    }

    var traceInjection: MatrixTraceInjection? = null

    init {
        if (VersionsCompat.greatThanOrEqual(AGPVersion.AGP_4_0_0)) {
            traceInjection = MatrixTraceInjection()
        }
    }

    override fun onTraceEnabled(enable: Boolean) {
        traceInjection?.onTraceEnabled(enable)
    }

    fun inject(
            matrix: MatrixExtension, appExtension: AppExtension, project: Project, extension: MatrixTraceExtension) {
        when {
            VersionsCompat.lessThan(AGPVersion.AGP_3_6_0) ->
                legacyInject(matrix,appExtension, project, extension)
            VersionsCompat.greatThanOrEqual(AGPVersion.AGP_4_0_0) -> {
                if (project.extensions.extraProperties.has(LEGACY_FLAG) &&
                    (project.extensions.extraProperties.get(LEGACY_FLAG) as? String?) == "true") {
                    legacyInject(matrix,appExtension, project, extension)
                } else {
                    Log.i("TraceCanary", "MatrixTraceCompat type1 traceInjection")
                    traceInjection!!.inject(matrix,appExtension, project, extension)
                }
            }
            else -> Log.e(TAG, "Matrix does not support Android Gradle Plugin " +
                    "${VersionsCompat.androidGradlePluginVersion}!.")
        }
    }

    //legacy遗产; 遗赠财物; 遗留; 后遗症;
    private fun legacyInject(matrix: MatrixExtension,
                             appExtension: AppExtension,
                             project: Project,
                             extension: MatrixTraceExtension) {
        Log.i("TraceCanary", "MatrixTraceCompat type2 legacyInject")
        project.afterEvaluate {

            if (!extension.isEnable) {
                return@afterEvaluate
            }

            appExtension.applicationVariants.all {
                if(matrix.filterObfuscatedVariants(it)) {
                    MatrixTraceLegacyTransform.inject(extension, project, it)
                }
            }
        }
    }
}
