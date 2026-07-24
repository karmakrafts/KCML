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

package dev.karmakrafts.kcml.log

import dev.karmakrafts.kcml.api.log.Logger
import dev.karmakrafts.kcml.api.log.LoggerFactory
import dev.karmakrafts.kcml.api.plugin.PluginLoader
import dev.karmakrafts.kcml.api.plugin.nameOrId
import org.jetbrains.kotlin.cli.common.messages.MessageCollector

internal class DefaultLoggerFactory( // @formatter:off
    val loader: PluginLoader,
    val messageCollector: MessageCollector
) : LoggerFactory { // @formatter:on
    private val loggers: HashMap<String, LoggerAdapter> = HashMap()

    override operator fun invoke(name: String): Logger = loggers.getOrPut(name) {
        LoggerAdapter(messageCollector, name)
    }

    override fun getForPlugin(pluginId: String): Logger {
        return this(loader.findMetadata(pluginId)?.nameOrId ?: pluginId)
    }
}