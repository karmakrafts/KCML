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

package dev.karmakrafts.kcml.extension

import dev.karmakrafts.kcml.api.extension.Extension
import dev.karmakrafts.kcml.api.extension.ExtensionRegistry
import dev.karmakrafts.kcml.api.log.Logger
import dev.karmakrafts.kcml.api.plugin.PluginLoader
import dev.karmakrafts.kcml.util.connectVertices
import io.github.alexandrepiveteau.graphs.DirectedGraph
import io.github.alexandrepiveteau.graphs.Vertex
import io.github.alexandrepiveteau.graphs.algorithms.topologicalSort
import io.github.alexandrepiveteau.graphs.builder.buildDirectedGraph

internal class DefaultExtensionRegistry( // @formatter:off
    private val loader: PluginLoader,
    override val pluginId: String,
    private val logger: Logger
) : ExtensionRegistry { // @formatter:on
    private val extensions: HashMap<String, Extension> = HashMap()
    private val sortedExtensions: LinkedHashMap<String, Extension> by lazy { sortExtensions() }
    private var isFrozen: Boolean = false

    val extensionCount: Int get() = extensions.size

    override fun register(extension: Extension) {
        check(!isFrozen) { "Extension registry is already frozen" }
        val id = extension.id
        require(id !in extensions) { "Extension with ID '$id' is already registered'" }
        extensions[id] = extension
    }

    override fun unregister(extension: Extension) {
        check(!isFrozen) { "Extension registry is already frozen" }
        extensions -= extension.id
    }

    override fun find(id: String): Extension? = extensions[id]

    override fun get(id: String): Extension = requireNotNull(extensions[id]) { "No extension with ID '$id'" }

    override fun contains(id: String): Boolean = id in extensions

    override fun contains(extension: Extension): Boolean = extensions.containsValue(extension)

    override fun all(): List<Extension> = extensions.values.toList()

    override fun allSorted(): List<Extension> {
        check(isFrozen) { "Extension registry hasn't been frozen yet" }
        return sortedExtensions.values.toList()
    }

    fun freeze() {
        check(!isFrozen) { "Extension registry is already frozen" }
        isFrozen = true
    }

    private fun buildExtensionsGraph(): Pair<DirectedGraph, HashMap<String, Vertex>> {
        val vertices = HashMap<String, Vertex>()
        val graph = buildDirectedGraph {
            for ((id, _) in extensions) {
                val vertex = addVertex()
                vertices[id] = vertex
            }
            for ((id, extension) in extensions) {
                val extensionVertex = vertices[id]!!
                for (dependency in extension.dependencies) {
                    val dependencyId = dependency.id
                    if (dependencyId !in extensions) {
                        if (dependency.required) {
                            logger.error("KCML extension '$id' for plugin with ID '${loader.loadingPluginId}' is missing required dependency '$dependencyId'")
                        }
                        continue
                    }
                    val dependencyVertex = vertices[dependencyId]!!
                    dependency.order.connectVertices(extensionVertex, dependencyVertex)
                }
            }
        }
        return graph to vertices
    }

    private fun sortExtensions(): LinkedHashMap<String, Extension> {
        val (graph, vertices) = buildExtensionsGraph()
        val sorted = LinkedHashMap<String, Extension>()
        try {
            val sortedVertices = graph.topologicalSort()
            for (vertex in sortedVertices) {
                val id = vertices.entries.find { it.value == vertex }?.key
                if (id == null) {
                    logger.error("Could not find KCML extension with ID '$id' for plugin with ID '${loader.loadingPluginId}' while sorting")
                    continue
                }
                val instance = extensions[id]
                if (instance == null) {
                    logger.error("Could not retrieve KCML extension instance with id '$id' for plugin with ID '${loader.loadingPluginId}'")
                    continue
                }
                sorted[id] = instance
            }
        } catch (error: IllegalArgumentException) {
            logger.error(
                "Detected dependency cycle while sorting KCML extensions for plugin with ID '${loader.loadingPluginId}'",
                error
            )
        }
        return sorted
    }
}