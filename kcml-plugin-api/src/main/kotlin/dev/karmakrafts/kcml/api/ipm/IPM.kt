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
public typealias IPMCallback = IPMData.() -> Unit

/**
 * Exchanges messages between plugins without a direct dependency.
 *
 * Implementations must be thread-safe.
 */
public interface IPM {
    /** Messages awaiting a receiver for their name. */
    @InternalKcmlApi
    public val queue: Deque<IPMMessage>

    /** Receivers keyed by message name; repeated registrations are composed into a single callback. */
    @InternalKcmlApi
    public val callbacks: MutableMap<String, IPMCallback>

    /**
     * Sends a message to a plugin, invoking an available receiver synchronously.
     *
     * Messages for a plugin without an available message service are ignored. Messages to another
     * plugin with a service but no receiver are queued until a receiver is registered. Messages to
     * this plugin itself are delivered only if a receiver is already registered; otherwise they are ignored.
     *
     * @param pluginId receiving plugin ID.
     * @param name message name.
     * @param data message payload.
     */
    public fun send(pluginId: String, name: String, data: IPMData)

    /**
     * Sends a message to every plugin, including this plugin, in dependency order.
     *
     * Each delivery follows the same receiver and queueing rules as [send].
     *
     * @param name message name.
     * @param data message payload.
     */
    public fun broadcast(name: String, data: IPMData)

    /**
     * Registers a receiver for a message name and delivers queued messages for that name.
     *
     * Queued payloads are passed to this callback synchronously before registration completes.
     * Existing receivers are retained; future deliveries invoke them in registration order.
     *
     * @param name message name.
     * @param callback message receiver.
     */
    public fun receive(name: String, callback: IPMCallback)
}

/**
 * Sends a message using a payload builder.
 *
 * @param pluginId receiving plugin ID.
 * @param name message name.
 * @param block adds message payload values.
 */
public inline fun IPM.send(pluginId: String, name: String, block: MutableMap<String, Any>.() -> Unit): Unit =
    send(pluginId, name, IPMData.build(block))

/**
 * Broadcasts a message using a payload builder.
 *
 * @param name message name.
 * @param block adds message payload values.
 */
public inline fun IPM.broadcast(name: String, block: MutableMap<String, Any>.() -> Unit): Unit =
    broadcast(name, IPMData.build(block))