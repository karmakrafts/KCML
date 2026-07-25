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

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.jetbrains.kotlin.gradle.dsl.KotlinJsCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinNativeCompilerOptions
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
        private val moduleNameRegex: Regex = Regex("""[.:-]""")
    }

    override fun apply(target: Project) {
        val logger = target.logger
        logger.info("KCML ${BuildInfo.version}")
        target.extensions.create("kcml", KCMLExtension::class.java, target)
        logger.info("Created KCML project extension")
        target.configurations.create(CONFIGURATION_NAME) // Custom configuration for declaring KCML plugin dependencies
        logger.info("Created KCML plugin configuration")
        logger.lifecycle("KCML attaches an agent to patch the compiler at runtime, this may cause warnings to appear")
    }

    private fun getModuleName(compilation: KotlinCompilation<*>): String? {
        return when (val options = compilation.compileTaskProvider.get().compilerOptions) {
            is KotlinJvmCompilerOptions -> options.moduleName.orNull
            is KotlinNativeCompilerOptions -> options.moduleName.orNull
            is KotlinJsCompilerOptions -> options.moduleName.orNull
            else -> null
        }
    }

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        val configuration = project.configurations.getByName(CONFIGURATION_NAME)
        val extension = project.extensions.findByType(KCMLExtension::class.java)!!
        val resolvedArtifacts = configuration.resolvedConfiguration.resolvedArtifacts
        val pluginClasspaths = resolvedArtifacts.joinToString(";") {
            it.file.toPath().absolutePathString()
        }
        val moduleName = getModuleName(kotlinCompilation)
        val loggingMode = extension.agentLoggingMode.get()
        return providerFactory.provider {
            buildList {
                add(SubpluginOption("pluginClasspaths", pluginClasspaths))
                add(SubpluginOption("agentLoggingMode", loggingMode.name))
                // Only pass relevant agent flags when needed
                when (loggingMode) {
                    AgentLoggingMode.FILE -> {
                        add(SubpluginOption("agentLogFilePath", extension.agentLogFilePath.get().asFile.absolutePath))
                    }

                    AgentLoggingMode.REMOTE -> {
                        add(SubpluginOption("agentLogServerPort", extension.agentLogServerPort.get().toString()))
                    }

                    else -> {}
                }
                if (moduleName?.isNotEmpty() == true) {
                    add(SubpluginOption("moduleName", moduleName.replace(moduleNameRegex, "_")))
                }
            }
        }
    }

    override fun getCompilerPluginId(): String = BuildInfo.PLUGIN_ID
    override fun getPluginArtifact(): SubpluginArtifact = BuildInfo.pluginArtifact
    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true
}