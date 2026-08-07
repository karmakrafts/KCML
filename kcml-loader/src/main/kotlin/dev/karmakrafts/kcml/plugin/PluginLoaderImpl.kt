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

package dev.karmakrafts.kcml.plugin

import dev.karmakrafts.kcml.api.InternalKcmlApi
import dev.karmakrafts.kcml.api.extension.ExtensionRegistry
import dev.karmakrafts.kcml.api.ipm.IPM
import dev.karmakrafts.kcml.api.log.Logger
import dev.karmakrafts.kcml.api.plugin.CompilerPlugin
import dev.karmakrafts.kcml.api.plugin.Plugin
import dev.karmakrafts.kcml.api.plugin.PluginLoadContext
import dev.karmakrafts.kcml.api.plugin.PluginLoader
import dev.karmakrafts.kcml.api.plugin.PluginMetadata
import dev.karmakrafts.kcml.api.plugin.nameOrId
import dev.karmakrafts.kcml.extension.DefaultExtensionRegistry
import dev.karmakrafts.kcml.extension.ExtensionDispatcher
import dev.karmakrafts.kcml.ipm.IPMImpl
import dev.karmakrafts.kcml.log.MessageCollectorLoggerFactory
import dev.karmakrafts.kcml.util.connectVertices
import dev.karmakrafts.kcml.util.json
import dev.karmakrafts.kcml.util.kcmlPluginClasspaths
import io.github.alexandrepiveteau.graphs.DirectedGraph
import io.github.alexandrepiveteau.graphs.Vertex
import io.github.alexandrepiveteau.graphs.algorithms.topologicalSort
import io.github.alexandrepiveteau.graphs.builder.buildDirectedGraph
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.messageCollector
import java.net.URL
import java.net.URLClassLoader
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.Path
import kotlin.io.path.absolute

@OptIn(ExperimentalCompilerApi::class, InternalKcmlApi::class)
object PluginLoaderImpl : PluginLoader {
    private lateinit var loggerFactory: MessageCollectorLoggerFactory
    private val loaderLock: Any = Any()
    private val plugins: HashMap<String, CompilerPlugin> = HashMap()
    private val metadata: HashMap<String, SerializablePluginMetadata> = HashMap()
    private val sortedPlugins: LinkedHashMap<String, CompilerPlugin> by lazy { sortPlugins() }
    private var arePluginsLoaded: Boolean = false

    private val extensionRegistries: HashMap<String, DefaultExtensionRegistry> = HashMap()
    internal val extensionDispatcher: ExtensionDispatcher by lazy {
        ExtensionDispatcher(this, extensionRegistries)
    }

    private val ipms: ConcurrentHashMap<String, IPMImpl> = ConcurrentHashMap()

    override lateinit var logger: Logger
    override var loadingPluginId: String? = null

    private fun getOrCreateIpm(pluginId: String): IPM = ipms.getOrPut(pluginId) {
        IPMImpl(pluginId, this@PluginLoaderImpl)
    }

    private fun getOrCreateExtensionRegistry(pluginId: String): DefaultExtensionRegistry {
        return extensionRegistries.getOrPut(pluginId) { DefaultExtensionRegistry(this, pluginId, logger) }
    }

    override fun findIpm(pluginId: String): IPM? = ipms[pluginId]

    override fun getIpm(pluginId: String): IPM = requireNotNull(findIpm(pluginId)) {
        "No IPM for plugin with ID '$pluginId'"
    }

    override fun findPlugin(id: String): CompilerPlugin? = plugins[id]

    override fun getPlugin(id: String): CompilerPlugin = requireNotNull(findPlugin(id)) {
        "No plugin with ID '$id'"
    }

    override fun findMetadata(pluginId: String): PluginMetadata? = metadata[pluginId]

    override fun getMetadata(pluginId: String): PluginMetadata = requireNotNull(findMetadata(pluginId)) {
        "No metadata for plugin ID '$pluginId'"
    }

    override fun findExtensionRegistry(pluginId: String): ExtensionRegistry? = extensionRegistries[pluginId]

    override fun getExtensionRegistry(pluginId: String): ExtensionRegistry =
        requireNotNull(findExtensionRegistry(pluginId)) {
            "No extension registry for plugin ID '$pluginId'"
        }

    override fun allPlugins(): List<String> = plugins.keys.toList()

    override fun allPluginsSorted(): List<String> {
        check(arePluginsLoaded) { "KCML plugins have not been loaded" }
        return sortedPlugins.keys.toList()
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun tryLoadMetadata( // @formatter:off
        pluginClass: Class<out CompilerPlugin>,
        pluginId: String
    ) { // @formatter:on
        try {
            pluginClass.getResourceAsStream("/$pluginId.json")!!.use {
                metadata[pluginId] = json.decodeFromStream<SerializablePluginMetadata>(it)
            }
        } catch (_: Throwable) {
            logger.warn("Plugin with ID '$pluginId' has missing or malformed metadata, this should be fixed")
        }
    }

    private fun setupLogging(config: CompilerConfiguration) {
        loggerFactory = MessageCollectorLoggerFactory(this, config.messageCollector)
        logger = loggerFactory("KCML")
    }

    private fun loadCandidates(urls: List<URL>): List<CompilerPlugin> {
        logger.info("Loading plugins")
        val parentClassLoader = this::class.java.classLoader
        val classLoader = URLClassLoader(urls.toTypedArray(), parentClassLoader)
        return ServiceLoader.load(CompilerPlugin::class.java, classLoader).toList()
    }

    // Used for regular direct compiler invocations
    private fun loadCandidates(config: CompilerConfiguration): List<CompilerPlugin> {
        return loadCandidates(config.kcmlPluginClasspaths.map { path ->
            val absolutePath = path.absolute().normalize()
            logger.debug("Found classpath dependency $absolutePath")
            absolutePath.toUri().toURL()
        })
    }

    // Used in cases where we abuse the regular pluginClasspaths for passing KCML plugins
    private fun loadCandidates(arguments: CommonCompilerArguments): List<CompilerPlugin> {
        return loadCandidates(arguments.pluginClasspaths.map { path ->
            val absolutePath = Path(path).absolute().normalize()
            logger.debug("Found classpath dependency $absolutePath")
            absolutePath.toUri().toURL()
        })
    }

    private fun loadMetadata(candidates: List<CompilerPlugin>) {
        for (plugin in candidates) {
            val pluginClass = plugin::class.java
            if (!pluginClass.isAnnotationPresent(Plugin::class.java)) {
                logger.warn("$pluginClass is missing the @Plugin annotation, skipping load")
                continue
            }
            val pluginId = pluginClass.getAnnotation(Plugin::class.java).id
            if (plugins.containsKey(pluginId)) {
                logger.error("Plugin with ID '$pluginId' was loaded more than once, check your plugin dependencies")
                continue
            }
            tryLoadMetadata(pluginClass, pluginId)
            plugins[pluginId] = plugin
        }
    }

    private fun loadPlugins(config: CompilerConfiguration) {
        for ((pluginId, plugin) in sortedPlugins) {
            try {
                loadingPluginId = pluginId
                val extensionRegistry = getOrCreateExtensionRegistry(pluginId)
                plugin.load(PluginLoadContext( // @formatter:off
                    extensionRegistry = extensionRegistry,
                    config = config,
                    loggerFactory = loggerFactory,
                    logger = loggerFactory.getForPlugin(pluginId),
                    loader = this,
                    ipm = getOrCreateIpm(pluginId)
                )
                ) // @formatter:on
            } catch (error: Throwable) {
                logger.error("Could not load plugin with ID '$pluginId'", error)
            }
        }
        loadingPluginId = null
    }

    private fun processIpms() {
        logger.info("Processing IPMs..")
        for ((pluginId, plugin) in sortedPlugins) {
            logger.info("Processing IPMs for plugin '$pluginId'")
            plugin.processIpms(getIpm(pluginId))
        }
    }

    private fun loadAllPlugins(
        config: CompilerConfiguration, candidates: List<CompilerPlugin> = loadCandidates(config)
    ) {
        if (arePluginsLoaded) return
        logger.info("Found ${candidates.size} plugin candidates")
        // Try to load metadata for all plugins
        loadMetadata(candidates)
        // Log plugin load order for debugging purposes
        val sortedNames = sortedPlugins.keys.map(::getMetadata).joinToString(transform = PluginMetadata::nameOrId)
        logger.info("Sorted plugins into load order: $sortedNames")
        // Load all plugins in the proper order
        loadPlugins(config)
        // Invoke all inter-plugin messaging
        processIpms()
        logger.info("Initialized ${plugins.size} plugins")
        extensionRegistries.values.forEach(DefaultExtensionRegistry::freeze)
        arePluginsLoaded = true
    }

    // Invoked standalone for things like linking tasks
    fun loadAndInvokeStandalone(config: CompilerConfiguration, arguments: CommonCompilerArguments) =
        synchronized(loaderLock) {
            setupLogging(config)
            loadAllPlugins(config, loadCandidates(arguments))
        }

    // Invoked through a delegate IR plugin
    fun CompilerPluginRegistrar.ExtensionStorage.loadAndInvoke(config: CompilerConfiguration) =
        synchronized(loaderLock) {
            setupLogging(config)
            loadAllPlugins(config)
            // Register adapters for extension dispatcher
            extensionDispatcher.registerAdapters(this, config, loggerFactory)
            logger.info("Registered extension adapters")
        }

    private fun buildPluginGraph(): Pair<DirectedGraph, HashMap<String, Vertex>> {
        val vertices = HashMap<String, Vertex>()
        val loadedPlugins = allPlugins()
        val graph = buildDirectedGraph {
            // First create a vertex for each plugin in the graph
            for (pluginId in loadedPlugins) {
                val vertex = addVertex()
                vertices[pluginId] = vertex
            }
            // Then connect vertices according to each plugins dependency metadata
            for (pluginId in loadedPlugins) {
                val pluginVertex = vertices[pluginId]!!
                val pluginMetadata = metadata[pluginId] ?: continue
                for ((dependencyId, required, order, requiredVersion) in pluginMetadata.dependencies) {
                    // Handle required dependencies
                    if (dependencyId !in loadedPlugins) {
                        if (required) {
                            logger.error("Plugin '$pluginId' is missing required dependency '$dependencyId'")
                        }
                        continue
                    }
                    // Check version requirement if present
                    val dependencyMetadata = metadata[dependencyId]
                    val dependencyVersion = dependencyMetadata?.version
                    if (requiredVersion != null) { // No constraints mean any version is accepted
                        if (dependencyVersion == null || !requiredVersion.isSatisfiedBy(dependencyVersion)) {
                            logger.error("Plugin '$pluginId' requested dependency '$dependencyId' version $requiredVersion, but got $dependencyVersion")
                            continue
                        }
                    }
                    // Connect the vertices according to load order
                    val dependencyVertex = vertices[dependencyId]!!
                    order.connectVertices(pluginVertex, dependencyVertex)
                }
            }
        }
        return graph to vertices
    }

    private fun sortPlugins(): LinkedHashMap<String, CompilerPlugin> {
        val (graph, vertices) = buildPluginGraph()
        // Apply topological sort to bring plugins into dependency order
        val sorted = LinkedHashMap<String, CompilerPlugin>()
        try {
            val sortedVertices = graph.topologicalSort()
            for (vertex in sortedVertices) {
                val pluginId = vertices.entries.find { it.value == vertex }?.key
                if (pluginId == null) {
                    logger.error("Could not find plugin with ID '$pluginId' while sorting")
                    continue
                }
                val instance = plugins[pluginId]
                if (instance == null) {
                    logger.error("Could not retrieve plugin instance for id '$pluginId'")
                    continue
                }
                sorted[pluginId] = instance
            }
        } catch (error: IllegalArgumentException) {
            logger.error("Detected dependency cycle while sorting plugins", error)
        }
        return sorted
    }
}