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
import org.gradle.api.provider.Provider
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
    fun `JVM project custom target DSL selects existing target lazily`() {
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
            assertSame(kotlin.target, target)
            options.forEach { (compilation, provider) ->
                assertEquals("example_jvm", provider.get().single { it.key == "compileTarget" }.value)
                val moduleName = if (compilation == "main") "custom_module" else "custom_module_$compilation"
                assertEquals(moduleName, provider.get().single { it.key == "moduleName" }.value)
            }
            kotlin.customJvmTarget("another_jvm")
            options.values.forEach { assertEquals("another_jvm", it.get().single { it.key == "compileTarget" }.value) }
            assertFailsWith<IllegalArgumentException> { kotlin.customJvmTarget(" ") }
        }
        finally {
            directory.toFile().deleteRecursively()
        }
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
                    customWasmTarget("example_wasi", wasmWasi("customWasi")) {
                        wasmtime()
                    } to "example_wasi",
                    customNativeTarget("example_host") to "example_host",
                    customNativeTarget("example_native", mingwX64("customMingw")) to "example_native"
                )
            }
            val bootstrap = project.plugins.getPlugin(KCMLBootstrapGradlePlugin::class.java)
            targets.forEach { (target, alias) ->
                assertSame(target, kotlin.targets.getByName(target.name))
                assertTrue(target.compilations.any { it.name == "main" })
                assertTrue(target.compilations.any { it.name == "test" })
                target.compilations.forEach { compilation ->
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
            assertSame(jvm, kotlin.customJvmTarget("late_jvm", jvm))
            assertEquals("late_jvm", options.get().single { it.key == "compileTarget" }.value)
            val extension = project.extensions.getByType(KCMLExtension::class.java)
            extension.compileTargetAliases.put("jvm", "legacy_jvm")
            assertEquals("late_jvm", options.get().single { it.key == "compileTarget" }.value)
            assertFailsWith<IllegalArgumentException> { kotlin.customJvmTarget("wrong_family", kotlin.linuxX64()) }
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