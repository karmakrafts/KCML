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

import dev.karmakrafts.kcml.gradle.api.KCMLGradleSupportPlugin
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import org.jetbrains.kotlin.gradle.targets.js.ir.JsIrBinary
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrLink
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrTarget
import org.jetbrains.kotlin.gradle.tasks.CompilerPluginOptions
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KCMLJsLinkTest {
    class LinkSupportPlugin : KCMLGradleSupportPlugin {
        override val pluginId = "linkTest"

        override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>) =
            kotlinCompilation.target.project.providers.provider {
                listOf(
                    SubpluginOption(
                        "context",
                        "${kotlinCompilation.target.name}:${kotlinCompilation.name}:" + kotlinCompilation.target.project.extensions.extraProperties["linkTestValue"]
                    )
                )
            }
    }

    @Test
    fun `JS and Wasm links preserve custom target aliases and contributed options`() {
        checkLinkOptions(mapOf("js" to "example_js", "wasm" to "example_wasm"))
    }

    @Test
    fun `JS and Wasm links fall back to builtin target aliases`() {
        checkLinkOptions(emptyMap())
    }

    @Test
    fun `JS and Wasm links preserve Kotlin DSL target selections`() {
        checkLinkOptions(mapOf("js" to "example_js", "wasm" to "example_wasm"), useTargetDsl = true)
    }

    @OptIn(ExperimentalWasmDsl::class)
    private fun checkLinkOptions(aliases: Map<String, String>, useTargetDsl: Boolean = false) {
        val directory = Files.createTempDirectory(Path.of("build"), "js-link-options-test-").toAbsolutePath()
        try {
            val project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build()
            project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
            project.pluginManager.apply(KCMLGradlePlugin::class.java)
            project.pluginManager.apply(LinkSupportPlugin::class.java)
            project.extensions.extraProperties["linkTestValue"] = "initial"
            project.configurations.getByName("kcmlRuntime").dependencies.clear()
            val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
            val targets = (if (useTargetDsl) listOf(
                kotlin.customJsTarget(aliases.getValue("js")) { nodejs(); binaries.executable() },
                kotlin.customWasmTarget(aliases.getValue("wasm")) { nodejs(); binaries.executable() })
            else listOf(
                kotlin.js { nodejs(); binaries.executable() },
                kotlin.wasmJs { nodejs(); binaries.executable() },
                kotlin.wasmWasi {
                    wasmtime()
                    binaries.executable()
                })).map { it as KotlinJsIrTarget }
            val libraries = targets.flatMap { it.binaries.library() }
            (project as ProjectInternal).evaluate()
            val binaries = libraries + targets.flatMap { target ->
                target.compilations.flatMap { it.binaries.withType(JsIrBinary::class.java).toList() }
            }
            assertTrue(binaries.any { it.compilation.name == "main" })
            assertTrue(binaries.any { it.compilation.name == "test" })
            binaries.forEach { it.linkTask.get() }
            val extension = project.extensions.getByType(KCMLExtension::class.java)
            if (!useTargetDsl) extension.compileTargetAliases.set(aliases)
            project.extensions.extraProperties["linkTestValue"] = "late"
            targets.forEach { target ->
                target.compilations.forEach { compilation ->
                    compilation.compileTaskProvider.get().compilerOptions.moduleName.set("${target.name}.${compilation.name}-late")
                }
            }
            binaries.forEach { binary ->
                val link = binary.linkTask.get()
                val compilation = binary.compilation
                val target = compilation.target
                val alias = if (target.wasmTargetType == null) "js" else "wasm"
                val arguments = link.pluginOptions.get().flatMap { CompilerPluginOptions(it).arguments }
                assertEquals(arguments.distinct(), arguments, link.name)
                val bootstrap = "plugin:${BuildInfo.PLUGIN_ID}.bootstrap:"
                assertEquals(
                    listOf("${bootstrap}compileTarget=${aliases[alias] ?: alias}"),
                    arguments.filter { it.startsWith("${bootstrap}compileTarget=") },
                    link.name
                )
                assertEquals(
                    listOf("${bootstrap}moduleName=${target.name}_${compilation.name}_late"),
                    arguments.filter { it.startsWith("${bootstrap}moduleName=") },
                    link.name
                )
                assertEquals(
                    listOf("plugin:${BuildInfo.PLUGIN_ID}:linkTestContext=${target.name}:${compilation.name}:late"),
                    arguments.filter { it.startsWith("plugin:${BuildInfo.PLUGIN_ID}:linkTestContext=") },
                    link.name
                )
                assertTrue(arguments.none { it.startsWith("${bootstrap}konanTarget=") }, link.name)
                val prepareCache =
                    link.taskDependencies.getDependencies(link).filterIsInstance<KCMLPrepareJsCacheTask>().single()
                assertEquals(
                    if (useTargetDsl) emptyMap() else aliases,
                    prepareCache.inputs.properties["kcmlCompileTargetAliases"],
                    link.name
                )
                assertEquals(if (useTargetDsl) targets.associate { it.name to it.name }
                else emptyMap(), prepareCache.inputs.properties["kcmlCustomCompileTargets"], link.name)
                assertEquals(
                    listOf("linkTestContext=${target.name}:${compilation.name}:late"),
                    prepareCache.inputs.properties["kcmlPluginOptions"],
                    link.name
                )
            }
        }
        finally {
            directory.toFile().deleteRecursively()
        }
    }

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
                wasmWasi {
                    wasmtime()
                }
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