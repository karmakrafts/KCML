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
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OptionsSupportPlugin : KCMLGradleSupportPlugin {
    override val pluginId: String = "options_test"

    override fun apply(target: Project) = Unit

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> =
        kotlinCompilation.target.project.provider { listOf(SubpluginOption("enabled", "true")) }
}

class KCMLCompilerOptionsTest {
    @Test
    fun `bootstrap and contributed options use separate processors`() {
        val directory = Files.createTempDirectory(Path.of("build"), "compiler-options-").toAbsolutePath()
        try {
            val project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build()
            project.pluginManager.apply("org.jetbrains.kotlin.jvm")
            project.pluginManager.apply(KCMLGradlePlugin::class.java)
            project.pluginManager.apply(OptionsSupportPlugin::class.java)
            val compilation = project.extensions.getByType(KotlinJvmProjectExtension::class.java)
                .target.compilations.getByName("main")
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
        }
        finally {
            directory.toFile().deleteRecursively()
        }
    }
}