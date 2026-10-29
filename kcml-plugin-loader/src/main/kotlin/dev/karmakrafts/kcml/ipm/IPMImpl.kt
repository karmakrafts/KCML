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

package dev.karmakrafts.kcml.ipm

import dev.karmakrafts.kcml.api.InternalKcmlApi
import dev.karmakrafts.kcml.api.ipm.IPM
import dev.karmakrafts.kcml.api.ipm.IPMCallback
import dev.karmakrafts.kcml.api.ipm.IPMData
import dev.karmakrafts.kcml.api.ipm.IPMMessage
import dev.karmakrafts.kcml.api.plugin.PluginLoader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

@OptIn(InternalKcmlApi::class)
internal class IPMImpl( // @formatter:off
    private val pluginId: String,
    private val loader: PluginLoader
) : IPM { // @formatter:on
    override val queue: ConcurrentLinkedDeque<IPMMessage> = ConcurrentLinkedDeque()
    override val callbacks: ConcurrentHashMap<String, IPMCallback> = ConcurrentHashMap()

    init {
        loader.logger.info("Created IPM instance for plugin '$pluginId'")
    }

    override fun send(pluginId: String, name: String, data: IPMData) {
        if (pluginId == this.pluginId) {
            // Sending messages to ourselves is probably not very useful, but we allow it anyway
            callbacks[name]?.invoke(data)
            return
        }
        val ipm = loader.findIpm(pluginId) ?: return
        val externalCallback = ipm.callbacks[name]
        if (externalCallback == null) {
            ipm.queue += IPMMessage(name, data)
            return
        }
        externalCallback(data)
    }

    override fun broadcast(name: String, data: IPMData) {
        for (pluginId in loader.allPluginsSorted()) {
            send(pluginId, name, data)
        }
    }

    override fun receive(name: String, callback: IPMCallback) {
        /// @formatter:off
        queue -= queue.filter { msg -> msg.name == name }
            .onEach { (_, data) -> data.callback() }
            .toSet()
        // @formatter:on
        val oldCallback = callbacks[name]
        if (oldCallback == null) {
            callbacks[name] = callback
            return
        }
        callbacks[name] = {
            oldCallback()
            callback()
        }
    }
}