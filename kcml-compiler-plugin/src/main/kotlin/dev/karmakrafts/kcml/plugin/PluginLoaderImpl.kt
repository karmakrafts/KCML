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

import dev.karmakrafts.kcml.api.plugin.CompilerPlugin
import dev.karmakrafts.kcml.api.plugin.Plugin
import dev.karmakrafts.kcml.api.plugin.PluginLoader
import dev.karmakrafts.kcml.api.plugin.PluginMetadata
import dev.karmakrafts.kcml.api.plugin.nameOrId
import dev.karmakrafts.kcml.api.util.error
import dev.karmakrafts.kcml.api.util.info
import dev.karmakrafts.kcml.api.util.verbose
import dev.karmakrafts.kcml.api.util.warn
import dev.karmakrafts.kcml.extension.DefaultExtensionRegistry
import dev.karmakrafts.kcml.extension.ExtensionDispatcher
import dev.karmakrafts.kcml.util.connectVertices
import dev.karmakrafts.kcml.util.json
import dev.karmakrafts.kcml.util.kcmlPluginClasspaths
import io.github.alexandrepiveteau.graphs.DirectedGraph
import io.github.alexandrepiveteau.graphs.Vertex
import io.github.alexandrepiveteau.graphs.algorithms.topologicalSort
import io.github.alexandrepiveteau.graphs.builder.buildDirectedGraph
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.messageCollector
import java.net.URLClassLoader
import java.util.*
import kotlin.io.path.absolute

@OptIn(ExperimentalCompilerApi::class)
internal object PluginLoaderImpl : PluginLoader {
    lateinit var messageCollector: MessageCollector
        private set

    private val plugins: HashMap<String, CompilerPlugin> = HashMap()
    private val metadata: HashMap<String, SerializablePluginMetadata> = HashMap()
    private val extensionRegistries: HashMap<String, DefaultExtensionRegistry> = HashMap()
    private val extensionDispatcher: ExtensionDispatcher by lazy { ExtensionDispatcher(extensionRegistries) }
    private val sortedPlugins: LinkedHashMap<String, CompilerPlugin> by lazy { sortPlugins() }
    private var isLoadComplete: Boolean = false
    override var loadingPluginId: String? = null

    private fun getOrCreateExtensionRegistry(id: String): DefaultExtensionRegistry {
        return extensionRegistries.getOrPut(id) { DefaultExtensionRegistry(messageCollector) }
    }

    override fun findPlugin(id: String): CompilerPlugin? = plugins[id]

    override fun getPlugin(id: String): CompilerPlugin = requireNotNull(findPlugin(id)) {
        "No plugin with ID '$id'"
    }

    override fun findMetadata(id: String): PluginMetadata? = metadata[id]

    override fun getMetadata(id: String): PluginMetadata = requireNotNull(findMetadata(id)) {
        "No metadata for plugin ID '$id'"
    }

    override fun allPlugins(): List<String> = plugins.keys.toList()

    override fun allPluginsSorted(): List<String> {
        check(isLoadComplete) { "KCML plugins have not been loaded" }
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
            messageCollector.warn("KCML plugin with ID '$pluginId' has missing or malformed metadata, this should be fixed")
        }
    }

    internal fun CompilerPluginRegistrar.ExtensionStorage.loadAndInvoke(config: CompilerConfiguration) {
        check(!isLoadComplete) { "KCML plugins already have been loaded" }
        // Load all plugins and their associated metadata by plugin ID
        messageCollector = config.messageCollector
        messageCollector.info("Loading KCML plugins")
        val parentClassLoader = PluginLoaderImpl::class.java.classLoader
        val classLoader = URLClassLoader(
            config.kcmlPluginClasspaths.map { path ->
                val absolutePath = path.absolute().normalize()
                messageCollector.verbose("Loading classpath dependency $absolutePath")
                absolutePath.toUri().toURL()
            }.toTypedArray(), parentClassLoader
        )
        val candidates = ServiceLoader.load(CompilerPlugin::class.java, classLoader).toList()
        messageCollector.info("Found ${candidates.size} KCML plugin candidates")
        for (plugin in candidates) {
            val pluginClass = plugin::class.java
            if (!pluginClass.isAnnotationPresent(Plugin::class.java)) {
                messageCollector.warn("$pluginClass is missing the @Plugin annotation, skipping load")
                continue
            }
            val pluginId = pluginClass.getAnnotation(Plugin::class.java).id
            if (pluginId in plugins) {
                messageCollector.error("KCML plugin with ID '$pluginId' was loaded more than once, check your plugin dependencies")
                continue
            }
            tryLoadMetadata(pluginClass, pluginId)
            plugins[pluginId] = plugin
        }
        messageCollector.info("Loaded ${plugins.size} KCML plugins")
        // Load all plugins
        val sortedNames = sortedPlugins.keys.map(::getMetadata).joinToString(transform = PluginMetadata::nameOrId)
        messageCollector.info("Sorted plugins into load order: $sortedNames")
        for ((pluginId, plugin) in sortedPlugins) {
            try {
                loadingPluginId = pluginId
                val extensionRegistry = getOrCreateExtensionRegistry(pluginId)
                plugin.registerExtensions(extensionRegistry, config)
            } catch (error: Throwable) {
                messageCollector.error("Could not load KCML plugin with ID '$pluginId'", error)
            }
        }
        loadingPluginId = null // Only used during the load phase
        // Register adapters for extension dispatcher
        extensionRegistries.values.forEach(DefaultExtensionRegistry::freeze)
        extensionDispatcher.registerAdapters(this, config)
        // TODO: wire up NativeIntrinsicsExtension
        messageCollector.info("Registered extension adapters")
        // Complete load
        isLoadComplete = true
        messageCollector.info("Initialized ${plugins.size} KCML plugins")
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
                            messageCollector.error("KCML plugin '$pluginId' is missing required dependency '$dependencyId'")
                        }
                        continue
                    }
                    // Check version requirement if present
                    val dependencyMetadata = metadata[dependencyId]
                    val dependencyVersion = dependencyMetadata?.version
                    if (requiredVersion != null) { // No constraints mean any version is accepted
                        if (dependencyVersion == null || !requiredVersion.isSatisfiedBy(dependencyVersion)) {
                            messageCollector.error("KCML plugin '$pluginId' requested dependency '$dependencyId' version $requiredVersion, but got $dependencyVersion")
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
                    messageCollector.error("Could not find KCML plugin with ID '$pluginId' while sorting")
                    continue
                }
                val instance = plugins[pluginId]
                if (instance == null) {
                    messageCollector.error("Could not retrieve KCML plugin instance for id '$pluginId'")
                    continue
                }
                sorted[pluginId] = instance
            }
        } catch (error: IllegalArgumentException) {
            messageCollector.error("Detected dependency cycle while sorting KCML plugins", error)
        }
        return sorted
    }
}