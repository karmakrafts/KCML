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

import org.gradle.api.internal.project.ProjectInternal
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrLink
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KCMLJsLinkTest {
    @Test
    @OptIn(ExperimentalWasmDsl::class)
    fun `JS and Wasm links include KCML runtime and contributed plugins`() {
        val directory = Files.createTempDirectory(Path.of("build"), "js-link-test-").toAbsolutePath()
        try {
            val contributedPlugin = Files.createFile(directory.resolve("contributed-plugin.jar"))
            val runtimePlugin = Files.createFile(directory.resolve("runtime-plugin.jar"))
            val project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build()
            project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
            project.pluginManager.apply(KCMLGradlePlugin::class.java)
            val runtime = project.configurations.getByName("kcmlRuntime")
            runtime.dependencies.clear()
            project.dependencies.add("kcml", project.files(contributedPlugin.toFile()))
            project.dependencies.add("kcmlRuntime", project.files(runtimePlugin.toFile()))
            project.extensions.getByType(KotlinMultiplatformExtension::class.java).apply {
                js { nodejs() }
                wasmJs { nodejs() }
                wasmWasi { nodejs() }
            }
            val links = project.tasks.withType(KotlinJsIrLink::class.java).toList()
            assertTrue(links.isNotEmpty())
            assertTrue(links.any { "WasmJs" in it.name })
            assertTrue(links.any { "WasmWasi" in it.name })
            links.forEach { it.incrementalJsIr = true }
            links.first().incrementalJsIr = false
            (project as ProjectInternal).evaluate()

            links.forEachIndexed { index, link ->
                assertEquals(index != 0, link.incrementalJsIr, link.name)
                assertTrue(runtimePlugin.toFile() in link.pluginClasspath.files, link.name)
                assertTrue(contributedPlugin.toFile() in link.pluginClasspath.files, link.name)
                val prepareCache =
                    link.taskDependencies.getDependencies(link).filterIsInstance<KCMLPrepareJsCacheTask>().single()
                assertEquals(link.rootCacheDirectory.get(), prepareCache.cacheDirectory.get())
                assertTrue(prepareCache.taskDependencies.getDependencies(prepareCache).none { it is KotlinJsIrLink })
            }
        }
        finally {
            directory.toFile().deleteRecursively()
        }
    }
}