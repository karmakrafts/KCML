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
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.ClasspathNormalizer
import org.gradle.api.tasks.TaskProvider
import org.gradle.internal.extensions.stdlib.capitalized
import org.jetbrains.kotlin.gradle.dsl.KotlinJsCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinNativeCompilerOptions
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmAndroidCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrLink
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink
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
        private const val OPT_AGENT_DUMP_CLASSES: String = "agentDumpClasses"
        private const val OPT_AGENT_DUMP_PATH: String = "agentDumpPath"
        private const val OPT_MODULE_NAME: String = "moduleName"
        private const val OPT_IS_ANDROID: String = "isAndroid"
    }

    override fun apply(target: Project) {
        val logger = target.logger
        logger.info("KCML ${BuildInfo.version}")
        val extension = target.extensions.create("kcml", KCMLExtension::class.java, target)
        val pluginConfiguration = target.configurations.create(CONFIGURATION_NAME)
        val runtimeConfiguration = target.configurations.create("kcmlRuntime") {
            it.isCanBeConsumed = false
            it.isTransitive = false
        }
        target.dependencies.add(
            runtimeConfiguration.name,
            "${BuildInfo.pluginArtifact.groupId}:${BuildInfo.pluginArtifact.artifactId}:${BuildInfo.version}"
        )
        val prepareRuntime = target.tasks.register("kcmlPrepareRuntime", KCMLPrepareRuntimeTask::class.java) { task ->
            task.compilerClasspath.from(runtimeConfiguration)
            task.outputDirectory.set(target.layout.buildDirectory.dir("kcml_runtime"))
        }
        val buildService = target.gradle.sharedServices.registerIfAbsent(
            "${KCMLBuildService.NAME}${target.path}", KCMLBuildService::class.java
        ) { service ->
            val port = extension.agentPort
            logger.info("Using port $port for KCML agent comm server")
            service.parameters.agentCommPort.value(port)
            service.parameters.loaderFile.set(prepareRuntime.flatMap { it.outputDirectory.file("kcml-loader.jar") })
            service.parameters.bridgeFile.set(prepareRuntime.flatMap { it.outputDirectory.file("kcml-mixin-bridge.jar") })
            service.parameters.pluginPaths.set(pluginConfiguration.elements.map { files -> files.map { it.asFile.absolutePath } })
            service.parameters.dumpClasses.set(extension.agentDumpClasses)
            service.parameters.dumpPath.set(extension.agentDumpPath.map { it.asFile.absolutePath })
        }
        val startServer = target.tasks.register("kcmlStartAgentServer", KCMLPreBuildTask::class.java) { task ->
            task.dependsOn(prepareRuntime, pluginConfiguration)
            task.buildService.set(buildService)
        }
        target.tasks.configureEach { task ->
            if (task is KotlinCompilationTask<*> || task is KotlinNativeLink) {
                task.dependsOn(startServer)
                task.usesService(buildService)
            }
        }
        logger.info("Created KCML project extension")
        logger.info("Created KCML plugin configuration")
        registerPreBuildTasks(target)
        registerPostBuildTasks(target)
        logger.info("Registered KCML tasks")
        configureTaskOptions(target)
        logger.lifecycle("KCML attaches an agent to patch the compiler at runtime, this may cause warnings to appear")
    }

    private fun configureTaskOptions(project: Project) {
        project.pluginManager.withPlugin(PluginIds.KOTLIN_MP) {
            project.afterEvaluate {
                // We need to load plugins during JS/WASM IR linking ourselves to get a bootstrap entrypoint
                project.tasks.withType(KotlinJsIrLink::class.java) { task ->
                    project.logger.lifecycle("Configuring KCML task options for '${task.name}'")
                    // @formatter:off
                    task.pluginClasspath.from(*getPluginClasspaths(project)
                        .map(ResolvedArtifact::getFile)
                        .toTypedArray())
                    // @formatter:on
                }
                val kmpExtension = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
                val nativeCacheTasks = HashMap<String, TaskProvider<KCMLPrepareNativeCacheTask>>()
                kmpExtension.targets.filterIsInstance<KotlinNativeTarget>().forEach { target ->
                    target.binaries.forEach { binary ->
                        val plugins = project.configurations.getByName(CONFIGURATION_NAME)
                        val runtime = project.configurations.getByName("kcmlRuntime")
                        // Kotlin shares this cache root across targets with the same binary name.
                        val prepareCache = nativeCacheTasks.getOrPut(binary.name) {
                            project.tasks.register(
                                "kcmlPrepareNativeCache${binary.name.capitalized()}",
                                KCMLPrepareNativeCacheTask::class.java
                            ) { task ->
                                task.pluginClasspath.from(plugins, runtime)
                                task.cacheDirectory.set(project.layout.buildDirectory.dir("kotlin-native-ic-cache/${binary.name}"))
                                task.markerFile.set(project.layout.buildDirectory.file("kcml_native_cache/${binary.name}.marker"))
                            }
                        }
                        binary.linkTaskProvider.configure { task ->
                            task.dependsOn(prepareCache)
                            task.inputs.files(plugins, runtime)
                                .withPropertyName("kcmlPluginClasspath")
                                .withNormalizer(ClasspathNormalizer::class.java)
                        }
                    }
                }
            }
        }
    }

    private fun getModuleName(compilation: KotlinCompilation<*>): String? {
        return when (val options = compilation.compileTaskProvider.get().compilerOptions) {
            is KotlinJvmCompilerOptions -> options.moduleName.orNull
            is KotlinNativeCompilerOptions -> options.moduleName.orNull
            is KotlinJsCompilerOptions -> options.moduleName.orNull
            else -> null
        }
    }

    private fun createPreBuildTask(project: Project, target: KotlinTarget): TaskProvider<KCMLPreBuildTask> {
        val taskName = "kcmlPreBuild${target.name.capitalized()}"
        return project.tasks.register(taskName, KCMLPreBuildTask::class.java) { task ->
            task.dependsOn("kcmlStartAgentServer")
            task.buildService.set(project.gradle.sharedServices.registrations.getByName("${KCMLBuildService.NAME}${project.path}").service.map { it as KCMLBuildService })
            task.apply {
                group = "kcml"
                description =
                    "Dummy task for retaining a reference to the KCML build service before the relevant build tasks"
            }
        }
    }

    private fun registerPreBuildTasks(project: Project) {
        val pluginManager = project.pluginManager
        if (!pluginManager.hasPlugin(PluginIds.KOTLIN_MP)) return
        pluginManager.withPlugin(PluginIds.KOTLIN_MP) { // For KMP, we wire pre-build as compile dependency tasks
            val kmpExtension = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
            project.afterEvaluate {
                kmpExtension.targets.filter { target ->
                    val platformType = target.platformType
                    platformType == KotlinPlatformType.native || platformType == KotlinPlatformType.wasm
                }.forEach { target ->
                    val preBuildTask = createPreBuildTask(project, target)
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

    private fun createPostBuildTask(project: Project, target: KotlinTarget): TaskProvider<KCMLPostBuildTask> {
        val taskName = "kcmlPostBuild${target.name.capitalized()}"
        return project.tasks.register(taskName, KCMLPostBuildTask::class.java) { task ->
            task.buildService.set(project.gradle.sharedServices.registrations.getByName("${KCMLBuildService.NAME}${project.path}").service.map { it as KCMLBuildService })
            task.apply {
                group = "kcml"
                description =
                    "Dummy task for retaining a reference to the KCML build service until after the relevant build tasks"
            }
        }
    }

    private fun registerPostBuildTasks(project: Project) {
        val pluginManager = project.pluginManager
        if (!pluginManager.hasPlugin(PluginIds.KOTLIN_MP)) return
        pluginManager.withPlugin(PluginIds.KOTLIN_MP) { // For KMP, we wire post-build as link finalization tasks
            val kmpExtension = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
            project.afterEvaluate {
                // Kotlin/Native has an extra link task we need to extend the build service lifetime to
                kmpExtension.targets.filterIsInstance<KotlinNativeTarget>().forEach { target ->
                    val postBuildTask = createPostBuildTask(project, target)
                    target.binaries.map { binary -> binary.linkTaskName }
                        .toSet()
                        .map(project.tasks::named)
                        .forEach { taskProvider ->
                            taskProvider.configure { task ->
                                task.finalizedBy(postBuildTask)
                            }
                        }
                }
                // Kotlin/WASM
                val targets = kmpExtension.targets.filterIsInstance<KotlinJsIrTarget>()
                    .filter { target -> target.platformType == KotlinPlatformType.wasm }
                for (target in targets) {
                    val postBuildTask = createPostBuildTask(project, target)
                    project.tasks.named(target.artifactsTaskName).configure { task ->
                        task.finalizedBy(postBuildTask)
                    }
                }
            }
        }
    }

    private fun getPluginClasspaths(project: Project): List<ResolvedArtifact> {
        val configuration = project.configurations.getByName(CONFIGURATION_NAME)
        return configuration.resolvedConfiguration.resolvedArtifacts.toList()
    }

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        val supportPlugins = project.plugins.withType(KCMLGradleSupportPlugin::class.java)
        val pluginClasspaths = getPluginClasspaths(project).joinToString(";") {
            it.file.toPath().absolutePathString()
        }
        val extension = project.extensions.findByType(KCMLExtension::class.java)!!
        val moduleName = getModuleName(kotlinCompilation)
        return providerFactory.provider {
            buildList {
                this += supportPlugins.flatMap { plugin ->
                    plugin.applyToCompilation(kotlinCompilation).get()
                }
                add(SubpluginOption(OPT_PLUGIN_CLASSPATH, pluginClasspaths))
                add(SubpluginOption(OPT_AGENT_COMM_PORT, extension.agentPort.toString()))
                add(SubpluginOption(OPT_AGENT_LOGGING, extension.agentLogging.get().toString()))
                add(SubpluginOption(OPT_AGENT_DUMP_CLASSES, extension.agentDumpClasses.get().toString()))
                add(SubpluginOption(OPT_AGENT_DUMP_PATH, extension.agentDumpPath.get().asFile.absolutePath))
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