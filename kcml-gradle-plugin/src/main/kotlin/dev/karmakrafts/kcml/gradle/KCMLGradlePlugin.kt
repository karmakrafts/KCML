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
import org.gradle.internal.extensions.stdlib.capitalized
import org.jetbrains.kotlin.gradle.dsl.KotlinJsCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinNativeCompilerOptions
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmAndroidCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import javax.inject.Inject
import kotlin.io.path.absolutePathString

@Suppress("UNUSED")
internal open class KCMLGradlePlugin @Inject constructor(
    private val providerFactory: ProviderFactory
) : KotlinCompilerPluginSupportPlugin {
    companion object {
        private const val CONFIGURATION_NAME: String = "kcml"
        private const val OPT_PLUGIN_CLASSPATH: String = "pluginClasspaths"
        private const val OPT_AGENT_LOGGING: String = "agentLogging"
        private const val OPT_AGENT_COMM_PORT: String = "agentCommPort"
        private const val OPT_MODULE_NAME: String = "moduleName"
        private const val OPT_IS_ANDROID: String = "isAndroid"
    }

    override fun apply(target: Project) {
        val logger = target.logger
        logger.info("KCML ${BuildInfo.version}")
        val extension = target.extensions.create("kcml", KCMLExtension::class.java, target)
        target.gradle.sharedServices.registerIfAbsent(KCMLBuildService.NAME, KCMLBuildService::class.java) { service ->
            val port = extension.agentPort
            logger.info("Using port $port for KCML agent comm server")
            service.parameters.agentCommPort.value(port)
        }
        logger.info("Created KCML project extension")
        target.configurations.create(CONFIGURATION_NAME) // Custom configuration for declaring KCML plugin dependencies
        logger.info("Created KCML plugin configuration")
        registerPreBuildTasks(target)
        registerPostBuildTasks(target)
        logger.info("Registered KCML tasks")
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

    private fun registerPreBuildTasks(project: Project) {
        val pluginManager = project.pluginManager
        if (!pluginManager.hasPlugin(PluginIds.KOTLIN_MP)) return
        pluginManager.withPlugin(PluginIds.KOTLIN_MP) { // For KMP, we wire pre-build as compile dependency tasks
            val kmpExtension = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
            project.afterEvaluate {
                kmpExtension.targets.filterIsInstance<KotlinNativeTarget>().forEach { target ->
                    val taskName = "kcmlPreBuild${target.name.capitalized()}"
                    val preBuildTask = project.tasks.register(taskName, KCMLPreBuildTask::class.java) { task ->
                        task.apply {
                            group = "kcml"
                            description =
                                "Dummy task for retaining a reference to the KCML build service before the relevant build tasks"
                        }
                    }
                    target.compilations.map { compilation -> compilation.compileKotlinTaskName }
                        .toSet()
                        .map(project.tasks::named)
                        .forEach { taskProvider ->
                            taskProvider.configure { task ->
                                task.dependsOn(preBuildTask)
                            }
                        }
                }
            }
        }
    }

    private fun registerPostBuildTasks(project: Project) {
        val pluginManager = project.pluginManager
        if (!pluginManager.hasPlugin(PluginIds.KOTLIN_MP)) return
        pluginManager.withPlugin(PluginIds.KOTLIN_MP) { // For KMP, we wire post-build as link finalization tasks
            val kmpExtension = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
            project.afterEvaluate {
                kmpExtension.targets.filterIsInstance<KotlinNativeTarget>().forEach { target ->
                    val taskName = "kcmlPostBuild${target.name.capitalized()}"
                    val postBuildTask = project.tasks.register(taskName, KCMLPostBuildTask::class.java) { task ->
                        task.apply {
                            group = "kcml"
                            description =
                                "Dummy task for retaining a reference to the KCML build service until after the relevant build tasks"
                        }
                    }
                    target.binaries.map { binary -> binary.linkTaskName }
                        .toSet()
                        .map(project.tasks::named)
                        .forEach { taskProvider ->
                            taskProvider.configure { task ->
                                task.finalizedBy(postBuildTask)
                            }
                        }
                }
            }
        }
    }

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        val supportPlugins = project.plugins.withType(KCMLGradleSupportPlugin::class.java)

        val configuration = project.configurations.getByName(CONFIGURATION_NAME)
        val extension = project.extensions.findByType(KCMLExtension::class.java)!!
        val resolvedArtifacts = configuration.resolvedConfiguration.resolvedArtifacts
        val pluginClasspaths = resolvedArtifacts.joinToString(";") {
            it.file.toPath().absolutePathString()
        }
        val moduleName = getModuleName(kotlinCompilation)
        return providerFactory.provider {
            buildList {
                this += supportPlugins.flatMap { plugin ->
                    plugin.applyToCompilation(kotlinCompilation).get()
                }
                add(SubpluginOption(OPT_PLUGIN_CLASSPATH, pluginClasspaths))
                add(SubpluginOption(OPT_AGENT_COMM_PORT, extension.agentPort.toString()))
                add(SubpluginOption(OPT_AGENT_LOGGING, extension.agentLogging.get().toString()))
                if (moduleName?.isNotEmpty() == true) { // @formatter:off
                    add(SubpluginOption(OPT_MODULE_NAME, moduleName.replace('.', '_')
                        .replace('-', '_')
                        .replace(':', '-')))
                } // @formatter:on
                if (kotlinCompilation is KotlinJvmAndroidCompilation) {
                    add(SubpluginOption(OPT_IS_ANDROID, true.toString()))
                }
            }
        }
    }

    override fun getCompilerPluginId(): String = BuildInfo.PLUGIN_ID
    override fun getPluginArtifact(): SubpluginArtifact = BuildInfo.pluginArtifact
    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true
}