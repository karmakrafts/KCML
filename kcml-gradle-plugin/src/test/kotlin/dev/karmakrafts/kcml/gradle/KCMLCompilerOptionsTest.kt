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
import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.InternalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.konan.target.Architecture
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.KonanTarget
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class OptionsSupportPlugin : KCMLGradleSupportPlugin {
    override val pluginId: String = "options_test"

    override fun apply(target: Project) = Unit

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> =
        kotlinCompilation.target.project.provider { listOf(SubpluginOption("enabled", "true")) }
}

class KCMLCompilerOptionsTest {
    @Test
    @OptIn(InternalKotlinGradlePluginApi::class)
    fun `JVM project custom targets have independent source sets and tasks`() {
        val directory = Files.createTempDirectory(Path.of("build"), "jvm-target-dsl-").toAbsolutePath()
        try {
            val project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build()
            project.pluginManager.apply("org.jetbrains.kotlin.jvm")
            project.pluginManager.apply(KCMLGradlePlugin::class.java)
            val kotlin = project.extensions.getByType(KotlinJvmProjectExtension::class.java)
            val bootstrap = project.plugins.getPlugin(KCMLBootstrapGradlePlugin::class.java)
            val options = kotlin.target.compilations.associate { it.name to bootstrap.applyToCompilation(it) }
            options.values.forEach { assertEquals("jvm", it.get().single { it.key == "compileTarget" }.value) }
            val target = kotlin.customJvmTarget("example_jvm") {
                compilerOptions.moduleName.set("custom_module")
            }
            val another = kotlin.customJvmTarget("another_jvm")
            assertNotSame<Any>(kotlin.target, target)
            assertNotSame(target, another)
            assertEquals(setOf("main", "test"), kotlin.target.compilations.names)
            listOf(target, another).forEach { custom ->
                assertEquals(setOf("main", "test"), custom.compilations.map { it.name }.toSet())
                custom.compilations.forEach { compilation ->
                    assertEquals(
                        "${custom.name}${compilation.name.replaceFirstChar(Char::uppercaseChar)}",
                        compilation.defaultSourceSet.name
                    )
                    assertNotSame(
                        kotlin.target.compilations.getByName(compilation.name).compileTaskProvider.get(),
                        compilation.compileTaskProvider.get()
                    )
                    assertEquals(
                        custom.name,
                        bootstrap.applyToCompilation(compilation).get().single { it.key == "compileTarget" }.value
                    )
                }
            }
            options.values.forEach { assertEquals("jvm", it.get().single { it.key == "compileTarget" }.value) }
            assertTrue("example_jvmTest" in project.tasks.names)
            assertTrue("another_jvmTest" in project.tasks.names)
            assertFailsWith<IllegalArgumentException> { kotlin.customJvmTarget(" ") }
            assertFailsWith<IllegalArgumentException> { kotlin.customJvmTarget("example_jvm") }
            assertFailsWith<IllegalArgumentException> { kotlin.customJvmTarget(kotlin.target.name) }
            target.compilations.create("integrationTest").associateWith(target.compilations.getByName("main"))
            assertEquals(setOf("main", "test"), kotlin.target.compilations.names)
            project.extensions.getByType(JavaPluginExtension::class.java).sourceSets.create("ordinary")
            assertEquals(setOf("main", "test", "ordinary"), kotlin.target.compilations.names)
            (project as ProjectInternal).evaluate()
            listOf(target, another).forEach { custom ->
                custom.compilations.forEach { compilation ->
                    val task = compilation.compileTaskProvider.get() as KotlinCompile
                    val pluginOptions = task.pluginOptions.get()
                        .flatMap { it.allOptions()["${BuildInfo.PLUGIN_ID}.bootstrap"].orEmpty() }
                    assertEquals(custom.name, pluginOptions.single { it.key == "compileTarget" }.value)
                }
            }
        }
        finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `custom target helpers preserve name closures and accept explicit Native identities`() {
        val functions = Class.forName("dev.karmakrafts.kcml.gradle.CustomCompileTargetExtensionsKt").methods.filter {
            it.name in setOf("customJvmTarget", "customJsTarget", "customWasmTarget", "customNativeTarget")
        }
        assertEquals(8, functions.size)
        val nameClosures = functions.filter { it.parameterCount == 3 && it.parameterTypes[1] == String::class.java }
        assertEquals(5, nameClosures.size)
        nameClosures.forEach { function ->
            assertEquals(3, function.parameterCount)
            assertEquals(String::class.java, function.parameterTypes[1])
            assertEquals(Function1::class.java, function.parameterTypes[2])
        }
        assertEquals(
            setOf(
            listOf(KonanTarget::class.java, Function1::class.java),
            listOf(String::class.java, KonanTarget::class.java, Function1::class.java),
            listOf(String::class.java, Family::class.java, Architecture::class.java, Function1::class.java)
        ), functions.filterNot { it in nameClosures }.map { it.parameterTypes.drop(1) }.toSet()
        )
        assertTrue(functions.none { KotlinTarget::class.java in it.parameterTypes })
    }

    @Test
    @OptIn(ExperimentalWasmDsl::class)
    fun `multiplatform custom target DSL creates targets without replacing builtins`() {
        val directory = Files.createTempDirectory(Path.of("build"), "multiplatform-target-dsl-").toAbsolutePath()
        try {
            val project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build()
            project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
            project.pluginManager.apply(KCMLGradlePlugin::class.java)
            val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
            val targets = with(kotlin) {
                mapOf(
                    jvm() to "jvm",
                    js { nodejs() } to "js",
                    wasmJs { nodejs() } to "wasm",
                    wasmWasi { wasmtime() } to "wasm",
                    linuxX64() to "native",
                    customJvmTarget("example_jvm") {
                        compilerOptions.moduleName.set("custom_jvm")
                    } to "example_jvm",
                    customJsTarget("example_js") { nodejs() } to "example_js",
                    customWasmTarget("example_wasm") { nodejs() } to "example_wasm",
                    customNativeTarget("example_host") to "example_host",
                    customJvmTarget("second_jvm") to "second_jvm",
                    customJsTarget("second_js") { nodejs() } to "second_js",
                    customWasmTarget("second_wasm") { nodejs() } to "second_wasm",
                    customNativeTarget("second_native") to "second_native")
            }
            val bootstrap = project.plugins.getPlugin(KCMLBootstrapGradlePlugin::class.java)
            targets.forEach { (target, alias) ->
                assertSame(target, kotlin.targets.getByName(target.name))
                assertTrue(target.compilations.any { it.name == "main" })
                assertTrue(target.compilations.any { it.name == "test" })
                target.compilations.forEach { compilation ->
                    assertEquals(
                        "${target.name}${compilation.name.replaceFirstChar(Char::uppercaseChar)}",
                        compilation.defaultSourceSet.name
                    )
                    assertSame(
                        compilation.compileTaskProvider.get(),
                        project.tasks.getByName(compilation.compileKotlinTaskName)
                    )
                    val options = bootstrap.applyToCompilation(compilation).get()
                    assertEquals(alias, options.single { it.key == "compileTarget" }.value, target.name)
                    if (target is KotlinNativeTarget) {
                        assertEquals(target.konanTarget.name, options.single { it.key == "konanTarget" }.value)
                    }
                }
            }
            val jvm = kotlin.jvm()
            val options = bootstrap.applyToCompilation(jvm.compilations.getByName("main"))
            assertEquals("jvm", options.get().single { it.key == "compileTarget" }.value)
            val compilations = targets.keys.flatMap { it.compilations }
            assertEquals(compilations.size, compilations.map { it.defaultSourceSet }.distinct().size)
            assertEquals(compilations.size, compilations.map { it.compileTaskProvider.name }.distinct().size)
            val extension = project.extensions.getByType(KCMLExtension::class.java)
            extension.compileTargetAliases.put("jvm", "legacy_jvm")
            assertEquals("legacy_jvm", options.get().single { it.key == "compileTarget" }.value)
            assertEquals(
                "example_jvm",
                bootstrap.applyToCompilation(kotlin.targets.getByName("example_jvm").compilations.getByName("main"))
                    .get()
                    .single { it.key == "compileTarget" }.value
            )
            assertFailsWith<IllegalArgumentException> { kotlin.customJvmTarget("jvm") }
            assertFailsWith<IllegalArgumentException> { kotlin.customJsTarget("js") }
            assertFailsWith<IllegalArgumentException> { kotlin.customWasmTarget("wasmJs") }
            assertFailsWith<IllegalArgumentException> { kotlin.customNativeTarget("second_native") }
            assertFailsWith<IllegalArgumentException> { kotlin.customJvmTarget(" ") }
            assertTrue(" " !in kotlin.targets.names)
        }
        finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `bootstrap and contributed options use separate processors`() {
        val directory = Files.createTempDirectory(Path.of("build"), "compiler-options-").toAbsolutePath()
        try {
            val project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build()
            project.pluginManager.apply("org.jetbrains.kotlin.jvm")
            project.pluginManager.apply(KCMLGradlePlugin::class.java)
            project.pluginManager.apply(OptionsSupportPlugin::class.java)
            val compilation =
                project.extensions.getByType(KotlinJvmProjectExtension::class.java).target.compilations.getByName("main")
            val plugin = project.plugins.getPlugin(KCMLGradlePlugin::class.java)
            val bootstrap = project.plugins.getPlugin(KCMLBootstrapGradlePlugin::class.java)
            assertEquals("kcml", plugin.getCompilerPluginId())
            assertEquals("kcml.bootstrap", bootstrap.getCompilerPluginId())
            val options = plugin.applyToCompilation(compilation).get()
            assertEquals(listOf("options_testEnabled"), options.map { it.key })
            assertEquals("true", options.single().value)
            val bootstrapOptions = bootstrap.applyToCompilation(compilation).get()
            assertTrue(bootstrapOptions.any { it.key == "pluginClasspaths" })
            assertTrue(bootstrapOptions.any { it.key == "agentCommPort" })
            assertTrue(bootstrapOptions.none { it.key == "options_testEnabled" })
            assertEquals(plugin.getPluginArtifact().artifactId, bootstrap.getPluginArtifact().artifactId)

            val extension = project.extensions.getByType(KCMLExtension::class.java)
            val targetOptions = bootstrap.applyToCompilation(compilation)
            assertEquals("jvm", targetOptions.get().single { it.key == "compileTarget" }.value)
            extension.compileTargetAliases.put("js", "example_js")
            assertEquals("jvm", targetOptions.get().single { it.key == "compileTarget" }.value)
            extension.compileTargetAliases.put("jvm", "example_jvm")
            assertEquals("example_jvm", targetOptions.get().single { it.key == "compileTarget" }.value)
            extension.compileTargetAliases.set(emptyMap())
            assertEquals("jvm", targetOptions.get().single { it.key == "compileTarget" }.value)
        }
        finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `native target alias override preserves the Konan target`() {
        val directory = Files.createTempDirectory(Path.of("build"), "native-target-options-").toAbsolutePath()
        try {
            val project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build()
            project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
            project.pluginManager.apply(KCMLGradlePlugin::class.java)
            val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
            val target = kotlin.linuxX64()
            val bootstrap = project.plugins.getPlugin(KCMLBootstrapGradlePlugin::class.java)
            val options = bootstrap.applyToCompilation(target.compilations.getByName("main"))
            assertEquals("native", options.get().single { it.key == "compileTarget" }.value)
            val extension = project.extensions.getByType(KCMLExtension::class.java)
            extension.compileTargetAliases.put("native", "example_native")
            assertEquals("example_native", options.get().single { it.key == "compileTarget" }.value)
            assertEquals(target.konanTarget.name, options.get().single { it.key == "konanTarget" }.value)
        }
        finally {
            directory.toFile().deleteRecursively()
        }
    }
}