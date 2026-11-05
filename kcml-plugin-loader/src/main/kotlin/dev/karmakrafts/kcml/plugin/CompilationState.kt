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

import dev.karmakrafts.kcml.api.log.Logger
import dev.karmakrafts.kcml.api.plugin.CompilerPlugin
import dev.karmakrafts.kcml.extension.DefaultExtensionRegistry
import dev.karmakrafts.kcml.extension.ExtensionDispatcher
import dev.karmakrafts.kcml.ipm.IPMImpl
import dev.karmakrafts.kcml.log.MessageCollectorLoggerFactory
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import java.net.URLClassLoader
import java.util.concurrent.ConcurrentHashMap

@OptIn(ExperimentalCompilerApi::class)
internal class CompilationState {
    val owner: Thread = Thread.currentThread()
    lateinit var loggerFactory: MessageCollectorLoggerFactory
    val plugins: HashMap<String, CompilerPlugin> = HashMap()
    val metadata: HashMap<String, SerializablePluginMetadata> = HashMap()
    val sortedPlugins: LinkedHashMap<String, CompilerPlugin> = LinkedHashMap()
    val optionProcessors: ArrayList<CommandLineProcessor> = ArrayList()
    var commandLineProcessor: CommandLineProcessor? = null
    var arePluginsPreInitialized: Boolean = false
    var areOptionsProcessed: Boolean = false
    var arePluginsLoaded: Boolean = false
    var pluginClassLoader: URLClassLoader? = null
    val extensionRegistries: HashMap<String, DefaultExtensionRegistry> = HashMap()
    var dispatcher: ExtensionDispatcher? = null
    val ipms: ConcurrentHashMap<String, IPMImpl> = ConcurrentHashMap()
    val storages: ConcurrentHashMap<String, PluginStorageImpl> = ConcurrentHashMap()
    lateinit var logger: Logger
    var loadingPluginId: String? = null
}