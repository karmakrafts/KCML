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

package dev.karmakrafts.kcml.plugin

import dev.karmakrafts.kcml.util.error
import dev.karmakrafts.kcml.util.json
import dev.karmakrafts.kcml.util.kcmlPluginClasspaths
import dev.karmakrafts.kcml.util.log
import dev.karmakrafts.kcml.util.warn
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

@OptIn(ExperimentalCompilerApi::class)
object PluginLoader {
    lateinit var messageCollector: MessageCollector
        private set

    private val plugins: HashMap<String, CompilerPlugin> = HashMap()
    private val metadata: HashMap<String, PluginMetadata> = HashMap()
    private val sortedPlugins: LinkedHashMap<String, CompilerPlugin> = LinkedHashMap()

    fun getLoadedPlugins(): Set<String> = plugins.keys
    fun getLoadedSortedPlugins(): Set<String> = sortedPlugins.keys
    fun getMetadata(id: String): PluginMetadata? = metadata[id]
    operator fun get(id: String): CompilerPlugin? = plugins[id]

    @OptIn(ExperimentalSerializationApi::class)
    private fun tryLoadMetadata( // @formatter:off
        pluginClass: Class<out CompilerPlugin>,
        pluginId: String
    ) { // @formatter:on
        try {
            pluginClass.getResourceAsStream("/$pluginId.json")!!.use {
                metadata[pluginId] = json.decodeFromStream<PluginMetadata>(it)
            }
        } catch (_: Throwable) {
            messageCollector.warn("KCML plugin with ID '$pluginId' is missing metadata, this should be fixed")
        }
    }

    internal fun CompilerPluginRegistrar.ExtensionStorage.loadAndInvoke(config: CompilerConfiguration) {
        // Load all plugins and their associated metadata by plugin ID
        messageCollector = config.messageCollector
        messageCollector.log("Loading KCML plugins")
        val parentClassLoader = PluginLoader::class.java.classLoader
        val classLoader = URLClassLoader(
            config.kcmlPluginClasspaths.map { it.toUri().toURL() }.toTypedArray(), parentClassLoader
        )
        val candidates = ServiceLoader.load(CompilerPlugin::class.java, classLoader).toList()
        messageCollector.log("Found ${candidates.size} KCML plugin candidates")
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
        messageCollector.log("Loaded ${plugins.size} KCML plugins")
        // Sort all plugins into their load order
        sortedPlugins += sortPlugins()
        for ((pluginId, plugin) in sortedPlugins) {
            try {
                with(plugin) { registerExtensions(config) }
            } catch (error: Throwable) {
                messageCollector.error("Could not load KCML plugin with ID '$pluginId'", error)
            }
        }
        messageCollector.log("Initialized ${plugins.size} KCML plugins")
    }

    private fun buildPluginGraph(): Pair<DirectedGraph, HashMap<String, Vertex>> {
        val vertices = HashMap<String, Vertex>()
        val loadedPlugins = getLoadedPlugins()
        val graph = buildDirectedGraph {
            // First create a vertex for each plugin in the graph
            for (pluginId in loadedPlugins) {
                val vertex = addVertex()
                vertices[pluginId] = vertex
            }
            // Then connect vertices according to each plugins dependency metadata
            for (pluginId in loadedPlugins) {
                val pluginVertex = vertices[pluginId]!!
                val pluginMetadata = metadata[pluginId]!!
                for (dependency in pluginMetadata.dependencies) {
                    val dependencyId = dependency.id
                    // Handle required dependencies
                    if (dependencyId !in loadedPlugins) {
                        if (dependency.required) {
                            messageCollector.error("KCML plugin '$pluginId' is missing required dependency '$dependencyId'")
                        }
                        continue
                    }
                    // Check version requirement if present
                    val requiredVersion = dependency.version
                    val dependencyMetadata = metadata[dependencyId]!!
                    val dependencyVersion = dependencyMetadata.version
                    if (requiredVersion != null) { // No constraints mean any version is accepted
                        if (dependencyVersion == null || !requiredVersion.isSatisfiedBy(dependencyVersion)) {
                            messageCollector.error("KCML plugin '$pluginId' requested dependency '$dependencyId' version $requiredVersion, but got $dependencyVersion")
                            continue
                        }
                    }
                    // Connect the vertices according to load order
                    val dependencyVertex = vertices[dependencyId]!!
                    dependency.order.edgeFunctor(pluginVertex, dependencyVertex)
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
            messageCollector.error("Detected dependency cycle while loading KCML plugins", error)
        }
        return sorted
    }
}