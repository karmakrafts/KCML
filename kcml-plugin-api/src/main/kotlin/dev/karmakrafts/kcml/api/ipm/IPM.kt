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

package dev.karmakrafts.kcml.api.ipm

import dev.karmakrafts.kcml.api.InternalKcmlApi
import java.util.*

typealias IPMCallback = IPMData.() -> Unit

/**
 * Inter-plugin messaging, totally not stolen from FML's IMCs.
 * This allows sending messages (events) to other plugins without
 * needing a hard dependency on them.
 * The trade-off is the data not really being typesafe anymore.
 *
 * All implementations of this interface must be thread safe.
 */
interface IPM {
    /**
     * The queue of messages that are invoked as soon as the first handler
     * is registered for the given message type.
     */
    @InternalKcmlApi
    val queue: Deque<IPMMessage>

    /**
     * A map of all callbacks registered for the various message types the
     * associated plugin can handle.
     */
    @InternalKcmlApi
    val callbacks: MutableMap<String, IPMCallback>

    /**
     * Sends a message of the given type to another plugin with the given ID.
     * If the target plugin is not present, the call will be ignored.
     * If the plugin is present, but hasn't made a call to [receive] for the matching
     * message type yet, the message will be queued until it has.
     */
    fun send(pluginId: String, name: String, data: IPMData)

    /**
     * Same as [send] but for all [dev.karmakrafts.kcml.api.plugin.PluginLoader.allPluginsSorted].
     */
    fun broadcast(name: String, data: IPMData)

    /**
     * Receive messages of the given type for the current plugin.
     * If messages have been queued for the given type, the callback
     * will be invoked immediately for all of them.
     */
    fun receive(name: String, callback: IPMCallback)
}

/**
 * Same as [IPM.send], but with a trailing closure for composing the IPM data.
 */
inline fun IPM.send(pluginId: String, name: String, block: MutableMap<String, Any>.() -> Unit) =
    send(pluginId, name, IPMData.build(block))

/**
 * Same as [IPM.broadcast], but with a trailing closure for composing the IPM data.
 */
inline fun IPM.broadcast(name: String, block: MutableMap<String, Any>.() -> Unit) =
    broadcast(name, IPMData.build(block))