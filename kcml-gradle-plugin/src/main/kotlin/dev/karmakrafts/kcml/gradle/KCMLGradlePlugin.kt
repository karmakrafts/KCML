/*
 * Copyright 2025 Karma Krafts & associates
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

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import javax.inject.Inject
import kotlin.io.path.absolutePathString

@Suppress("UNUSED")
open class KCMLGradlePlugin @Inject constructor(
    private val providerFactory: ProviderFactory
) : KotlinCompilerPluginSupportPlugin {
    companion object {
        private const val CONFIGURATION_NAME: String = "kcml"
    }

    override fun apply(target: Project) {
        target.logger.info("KCML ${BuildInfo.version}")
        target.configurations.create(CONFIGURATION_NAME) // Custom configuration for declaring KCML plugin dependencies
        target.logger.info("Created KCML plugin configuration")
    }

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        val configuration = project.configurations.getByName(CONFIGURATION_NAME)
        val pluginClasspaths = configuration.resolvedConfiguration.resolvedArtifacts.joinToString(";") {
            it.file.toPath().absolutePathString()
        }
        return providerFactory.provider {
            listOf(SubpluginOption("pluginClasspaths", pluginClasspaths))
        }
    }

    override fun getCompilerPluginId(): String = BuildInfo.PLUGIN_ID
    override fun getPluginArtifact(): SubpluginArtifact = BuildInfo.pluginArtifact
    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true
}