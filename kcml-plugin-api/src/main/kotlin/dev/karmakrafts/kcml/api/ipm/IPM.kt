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

/** Handles a received inter-plugin message payload. */
typealias IPMCallback = IPMData.() -> Unit

/**
 * Exchanges messages between plugins without a direct dependency.
 *
 * Implementations must be thread-safe.
 */
interface IPM {
    /** Messages awaiting a receiver for their name. */
    @InternalKcmlApi
    val queue: Deque<IPMMessage>

    /** Receivers keyed by message name. */
    @InternalKcmlApi
    val callbacks: MutableMap<String, IPMCallback>

    /**
     * Sends a message to a plugin. Messages for an unavailable plugin are ignored; messages with
     * no receiver are queued until one is registered.
     *
     * @param pluginId receiving plugin ID.
     * @param name message name.
     * @param data message payload.
     */
    fun send(pluginId: String, name: String, data: IPMData)

    /**
     * Sends a message to every plugin in dependency order.
     *
     * @param name message name.
     * @param data message payload.
     */
    fun broadcast(name: String, data: IPMData)

    /**
     * Registers a receiver for a message name and delivers queued messages for that name.
     *
     * @param name message name.
     * @param callback message receiver.
     */
    fun receive(name: String, callback: IPMCallback)
}

/**
 * Sends a message using a payload builder.
 *
 * @param pluginId receiving plugin ID.
 * @param name message name.
 * @param block adds message payload values.
 */
inline fun IPM.send(pluginId: String, name: String, block: MutableMap<String, Any>.() -> Unit) =
    send(pluginId, name, IPMData.build(block))

/**
 * Broadcasts a message using a payload builder.
 *
 * @param name message name.
 * @param block adds message payload values.
 */
inline fun IPM.broadcast(name: String, block: MutableMap<String, Any>.() -> Unit) =
    broadcast(name, IPMData.build(block))