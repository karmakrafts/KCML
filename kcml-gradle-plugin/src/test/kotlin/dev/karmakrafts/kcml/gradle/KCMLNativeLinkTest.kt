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
import org.jetbrains.kotlin.gradle.InternalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import org.jetbrains.kotlin.gradle.tasks.CompilerPluginOptions
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilerPluginData
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class KCMLNativeLinkTest {
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
    fun `native links preserve custom target aliases and contributed options`() {
        checkLinkOptions(mapOf("native" to "example_native"))
    }

    @Test
    fun `native links fall back to builtin target aliases`() {
        checkLinkOptions(emptyMap())
    }

    @OptIn(InternalKotlinGradlePluginApi::class)
    private fun checkLinkOptions(aliases: Map<String, String>) {
        val directory = Files.createTempDirectory(Path.of("build"), "native-link-options-test-").toAbsolutePath()
        try {
            val runtimePlugin = Files.createFile(directory.resolve("runtime-plugin.jar"))
            val contributedPlugin = Files.createFile(directory.resolve("contributed-plugin.jar"))
            val otherPlugin = Files.createFile(directory.resolve("other-plugin.jar"))
            val project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build()
            project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
            project.pluginManager.apply(KCMLGradlePlugin::class.java)
            project.pluginManager.apply(LinkSupportPlugin::class.java)
            project.extensions.extraProperties["linkTestValue"] = "initial"
            project.configurations.getByName("kcmlRuntime").dependencies.clear()
            project.dependencies.add("kcmlRuntime", project.files(runtimePlugin.toFile()))
            project.dependencies.add("kcml", project.files(contributedPlugin.toFile()))
            val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
            val targets = listOf(kotlin.linuxX64(), kotlin.mingwX64())
            targets.forEach { it.binaries.executable() }
            targets.flatMap { it.binaries }.forEach { binary ->
                binary.linkTaskProvider.configure { task ->
                    task.kotlinPluginData = project.providers.provider {
                        KotlinCompilerPluginData(
                            project.files(otherPlugin.toFile()), CompilerPluginOptions().apply {
                                addPluginArgument("other.plugin", SubpluginOption("keep", "value"))
                            }, KotlinCompilerPluginData.InputsOutputsState(
                                mapOf("other" to "preserved"), emptySet(), emptySet()
                            )
                        )
                    }
                }
            }
            (project as ProjectInternal).evaluate()
            targets.flatMap { it.binaries }.forEach { it.linkTaskProvider.get() }
            val extension = project.extensions.getByType(KCMLExtension::class.java)
            extension.compileTargetAliases.set(aliases)
            project.extensions.extraProperties["linkTestValue"] = "late"
            targets.forEach { target ->
                target.compilations.forEach { compilation ->
                    compilation.compileTaskProvider.get().compilerOptions.moduleName.set("${target.name}.${compilation.name}-late")
                }
                target.binaries.forEach { binary ->
                    val link = binary.linkTaskProvider.get()
                    val data = link.kotlinPluginData?.get()
                    val arguments = link.compilerPluginOptions.arguments + data?.options?.arguments.orEmpty()
                    assertEquals(arguments.distinct(), arguments, link.name)
                    assertTrue("plugin:other.plugin:keep=value" in arguments, link.name)
                    assertEquals("preserved", data?.inputsOutputsState?.inputs?.get("other"), link.name)
                    val bootstrap = "plugin:${BuildInfo.PLUGIN_ID}.bootstrap:"
                    assertEquals(
                        listOf("${bootstrap}compileTarget=${aliases["native"] ?: "native"}"),
                        arguments.filter { it.startsWith("${bootstrap}compileTarget=") },
                        link.name
                    )
                    assertEquals(
                        listOf("${bootstrap}konanTarget=${target.konanTarget.name}"),
                        arguments.filter { it.startsWith("${bootstrap}konanTarget=") },
                        link.name
                    )
                    assertEquals(
                        listOf("${bootstrap}moduleName=${target.name}_${binary.compilation.name}_late"),
                        arguments.filter { it.startsWith("${bootstrap}moduleName=") },
                        link.name
                    )
                    assertEquals(
                        listOf("plugin:${BuildInfo.PLUGIN_ID}:linkTestContext=${target.name}:${binary.compilation.name}:late"),
                        arguments.filter { it.startsWith("plugin:${BuildInfo.PLUGIN_ID}:linkTestContext=") },
                        link.name
                    )
                    val classpath = link.compilerPluginClasspath?.files.orEmpty() + data?.classpath?.files.orEmpty()
                    assertTrue(runtimePlugin.toFile() in classpath, link.name)
                    assertTrue(contributedPlugin.toFile() in classpath, link.name)
                    assertTrue(otherPlugin.toFile() in classpath, link.name)
                    val prepareCache = link.taskDependencies.getDependencies(link)
                        .filterIsInstance<KCMLPrepareNativeCacheTask>()
                        .single()
                    assertEquals(aliases, prepareCache.inputs.properties["kcmlCompileTargetAliases"], link.name)
                    targets.forEach { cacheTarget ->
                        assertEquals(
                            listOf("linkTestContext=${cacheTarget.name}:${binary.compilation.name}:late"),
                            prepareCache.inputs.properties["kcmlPluginOptions${cacheTarget.name}"],
                            link.name
                        )
                    }
                }
            }
        }
        finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `native links invalidate caches when KCML plugins change`() {
        val directory = Files.createTempDirectory(Path.of("build"), "native-link-test-").toAbsolutePath()
        try {
            val project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build()
            project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
            project.pluginManager.apply(KCMLGradlePlugin::class.java)
            val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
            val target = kotlin.linuxX64()
            val otherTarget = kotlin.mingwX64()
            (project as ProjectInternal).evaluate()
            val binary = target.binaries.getTest("DEBUG")
            val link = binary.linkTaskProvider.get()

            val dependencies = link.taskDependencies.getDependencies(link)

            assertTrue(dependencies.any { it.name == "kcmlPrepareNativeCache${binary.name.replaceFirstChar(Char::uppercaseChar)}" })
            val prepareCache = dependencies.filterIsInstance<KCMLPrepareNativeCacheTask>().single()
            assertEquals(
                project.layout.buildDirectory.dir("kotlin-native-ic-cache/${binary.name}").get(),
                prepareCache.cacheDirectory.get()
            )
            val otherLink = otherTarget.binaries.getTest("DEBUG").linkTaskProvider.get()
            assertSame(
                prepareCache,
                otherLink.taskDependencies.getDependencies(otherLink)
                    .filterIsInstance<KCMLPrepareNativeCacheTask>()
                    .single()
            )
        }
        finally {
            directory.toFile().deleteRecursively()
        }
    }
}