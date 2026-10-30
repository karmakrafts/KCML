/*
 * Copyright 2026 Karma Krafts
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.karmakrafts.kcml.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

@DisableCachingByDefault(because = "Invalidates local Kotlin/Native incremental caches")
internal abstract class KCMLPrepareNativeCacheTask : DefaultTask() {
    @get:Classpath
    abstract val pluginClasspath: ConfigurableFileCollection

    @get:LocalState
    abstract val cacheDirectory: DirectoryProperty

    @get:OutputFile
    abstract val markerFile: RegularFileProperty

    @get:Inject
    protected abstract val fileSystemOperations: FileSystemOperations

    @TaskAction
    fun performAction() {
        // Native incremental caches do not account for late compiler plugin changes.
        fileSystemOperations.delete { it.delete(cacheDirectory) }
        val marker = markerFile.get().asFile
        marker.parentFile.mkdirs()
        marker.writeText("KCML native cache prepared\n")
    }
}