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
package com.tencent.matrix.plugin.deobfuscation

import com.tencent.matrix.javalib.util.Log
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.*
import java.io.File

@CacheableTask
open class CopyObfuscationMappingFileTask : DefaultTask() {

    @Input
    var variantName: String = ""

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    var mappingFile: File? = null

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    var mergeAssetsDirectory: File? = null

    @get:OutputFile
    val leakCanaryAssetsOutputFile: File
        get() = File(mergeAssetsDirectory, "tracecanaryObfuscationMapping.txt")

    init {
        description = "Puts obfuscation mapping file in assets directory."
    }

    @TaskAction
    fun copyObfuscationMappingFile() {
        Log.i("CopyObfuscationMappingFileTask", "leakCanaryAssetsOutputFile begin")
        Log.i("CopyObfuscationMappingFileTask", "leakCanaryAssetsOutputFile " + leakCanaryAssetsOutputFile)
        val mapping = validateMappingFile()
        validateMergeAssetsDir()
        mapping.copyTo(leakCanaryAssetsOutputFile, overwrite = true)

        Log.i("CopyObfuscationMappingFileTask", "leakCanaryAssetsOutputFile end")
    }

    private fun validateMappingFile(): File {
        val mapping = mappingFile
        if (mapping == null || !mapping.exists()) {
            throw GradleException(
                    """
        The plugin was configured to be applied to a variant which doesn't define 
        an obfuscation mapping file: make sure that isMinified is true for variant: $variantName.
        """
            )
        }
        return mapping
    }

    private fun validateMergeAssetsDir() {
        mergeAssetsDirectory?.let { mergeAssetsDir ->
            if (!mergeAssetsDir.exists()) {
                val mergeAssetsDirCreated = mergeAssetsDir.mkdirs()
                if (!mergeAssetsDirCreated) {
                    throw GradleException(
                            "Obfuscation mapping destination dir doesn't exist and it's impossible to create it."
                    )
                }
            }
        } ?: throw GradleException("Obfuscation mapping is null.")
    }
}
