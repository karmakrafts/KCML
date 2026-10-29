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

package dev.karmakrafts.kcml.agent.util

import dev.karmakrafts.kcml.agent.proto.A2SHandshake
import dev.karmakrafts.kcml.agent.proto.S2AHandshake
import dev.karmakrafts.kcml.agent.proto.io.AgentPacketHandler
import dev.karmakrafts.kcml.agent.proto.io.AgentPacketReader
import dev.karmakrafts.kcml.agent.proto.io.AgentPacketWriter
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

internal class AgentCommClient(port: Int) : AutoCloseable {
    private val socket: Socket = Socket(InetAddress.getLoopbackAddress(), port)
    private val reader: AgentPacketReader = AgentPacketReader(socket.getInputStream())
    private val writer: AgentPacketWriter = AgentPacketWriter(socket.getOutputStream())
    private val handlers: ConcurrentHashMap<String, AgentPacketHandler<*>> = ConcurrentHashMap()
    private val writerLock: Any = Any()
    private val pendingHandshakes: ConcurrentHashMap<String, CompletableFuture<S2AHandshake>> = ConcurrentHashMap()

    private val thread: Thread = Thread {
        try {
            while (!socket.isClosed) {
                reader.read(handlers::get)
            }
        } catch (_: Throwable) {
            // The server closed the connection
        }
        finally {
            socket.close()
            pendingHandshakes.values.forEach { future ->
                future.completeExceptionally(IOException("KCML agent connection closed during handshake"))
            }
        }
    }

    init {
        receive<S2AHandshake> { message -> pendingHandshakes[message.signature]?.complete(message) }
        thread.isDaemon = true
        thread.start()
    }

    fun handshake(timeoutMillis: Long = 10_000): S2AHandshake {
        require(timeoutMillis > 0) { "Handshake timeout must be positive" }
        val signature = UUID.randomUUID().toString()
        val response = CompletableFuture<S2AHandshake>()
        pendingHandshakes[signature] = response
        try {
            check(!socket.isClosed) { "KCML agent connection is closed" }
            send(A2SHandshake(signature))
            return response.get(timeoutMillis, TimeUnit.MILLISECONDS).also { message ->
                require(message.loaderPath.isNotBlank()) { "Handshake loader path must not be blank" }
                require(message.bridgePath.isNotBlank()) { "Handshake bridge path must not be blank" }
                require(message.pluginPaths.none(String::isBlank)) { "Handshake plugin paths must not be blank" }
            }
        }
        finally {
            pendingHandshakes.remove(signature)
        }
    }

    override fun close() {
        socket.close()
        if (Thread.currentThread() != thread) thread.join()
    }

    inline fun <reified T> send(message: T) = send(serializer(), message)

    @PublishedApi
    internal fun <T> send(serializer: KSerializer<T>, message: T) = synchronized(writerLock) {
        writer.write(serializer, message)
    }

    inline fun <reified T> receive(noinline handler: (T) -> Unit) {
        receive(serializer(), handler)
    }

    @PublishedApi
    internal fun <T> receive(serializer: KSerializer<T>, handler: (T) -> Unit) {
        val packetHandler = AgentPacketHandler(serializer, handler)
        handlers[packetHandler.type]?.let { existingHandler -> // @formatter:off
            @Suppress("UNCHECKED_CAST")
            handlers[packetHandler.type] = (existingHandler as AgentPacketHandler<T>) + packetHandler
            return
        } // @formatter:on
        handlers[packetHandler.type] = packetHandler
    }
}