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

package dev.karmakrafts.kcml.gradle

import dev.karmakrafts.kcml.agent.proto.A2SHandshake
import dev.karmakrafts.kcml.agent.proto.A2SLog
import dev.karmakrafts.kcml.agent.proto.S2AHandshake
import dev.karmakrafts.kcml.agent.proto.io.AgentPacketHandler
import dev.karmakrafts.kcml.agent.proto.io.AgentPacketReader
import dev.karmakrafts.kcml.agent.proto.io.AgentPacketWriter
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import java.net.BindException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.random.Random
import kotlin.time.Clock

internal class AgentCommServer(
    port: Int, private val handshakeProvider: ((A2SHandshake) -> S2AHandshake)? = null
) : AutoCloseable {
    companion object {
        fun isPortInUse(port: Int): Boolean = try {
            ServerSocket(port).close()
            false
        } catch (_: BindException) {
            true
        } catch (_: Throwable) {
            false
        }

        fun findAvailablePort( // @formatter:off
            rangeStart: Int,
            rangeEnd: Int,
            maxAttempts: Int = 100
        ): Int? { // @formatter:on
            val random = Random(Clock.System.now().epochSeconds)
            var port = random.nextInt(rangeStart, rangeEnd)
            var attempts = 1
            while (isPortInUse(port) && attempts++ < maxAttempts) {
                port = random.nextInt(rangeStart, rangeEnd)
            }
            return when {
                isPortInUse(port) -> null
                else -> port
            }
        }
    }

    private val logger: Logger = Logging.getLogger(AgentCommServer::class.java)

    private class Connection(
        private val socket: Socket,
        private val handlerProvider: (String) -> AgentPacketHandler<*>?,
        handshakeProvider: ((A2SHandshake) -> S2AHandshake)?,
        private val onClosed: (Connection) -> Unit
    ) : AutoCloseable {
        private val isOpen: AtomicBoolean = AtomicBoolean(true)
        private val reader: AgentPacketReader = AgentPacketReader(socket.getInputStream())
        private val writer: AgentPacketWriter = AgentPacketWriter(socket.getOutputStream())
        private val writerLock: Any = Any()
        private val handshakeHandler: AgentPacketHandler<A2SHandshake>? = handshakeProvider?.let { provider ->
            AgentPacketHandler(A2SHandshake.serializer()) { request ->
                send(S2AHandshake.serializer(), provider(request))
            }
        }
        private val ioThread: Thread = Thread {
            try {
                while (isOpen.load()) {
                    reader.read { type -> handshakeHandler?.takeIf { it.type == type } ?: handlerProvider(type) }
                }
            } catch (_: Throwable) {
                // Can't do anything here
            }
            finally {
                close()
                onClosed(this)
            }
        }

        fun start() = ioThread.start()

        fun <T> send(serializer: KSerializer<T>, message: T) = synchronized(writerLock) {
            writer.write(serializer, message)
        }

        override fun close() {
            if (!isOpen.compareAndSet(expectedValue = true, newValue = false)) {
                return
            }
            runCatching { socket.close() }
            if (Thread.currentThread() != ioThread) {
                ioThread.join()
            }
        }
    }

    private val isRunning: AtomicBoolean = AtomicBoolean(true)
    private val socket: ServerSocket = ServerSocket(port, 100, InetAddress.getLoopbackAddress())
    private val connections: ConcurrentLinkedQueue<Connection> = ConcurrentLinkedQueue()
    private val handlers: ConcurrentHashMap<String, AgentPacketHandler<*>> = ConcurrentHashMap()

    val port: Int
        get() = socket.localPort

    val connectionCount: Int
        get() = connections.size

    private val connectionThread: Thread = Thread {
        try {
            while (isRunning.load()) {
                val socket = socket.accept()
                val connection = Connection(socket, handlers::get, handshakeProvider) { closedConnection ->
                    connections.remove(closedConnection)
                    logger.info("KCML agent disconnected")
                }
                connections += connection
                connection.start()
                logger.info("KCML agent connected")
            }
        } catch (_: Throwable) {
            // Can't do anything here
        }
    }

    init {
        receive<A2SLog> { message ->
            logger.info("[KCML Agent]${message.message}")
        }
        logger.info("Starting KCML agent comm server..")
        connectionThread.start()
    }

    inline fun <reified T> send(message: T) = send(serializer(), message)

    @PublishedApi
    internal fun <T> send(serializer: KSerializer<T>, message: T) {
        connections.forEach { connection ->
            runCatching { connection.send(serializer, message) }
        }
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

    override fun close() {
        if (!isRunning.compareAndSet(expectedValue = true, newValue = false)) {
            return
        }
        logger.info("Stopping KCML agent comm server..")
        socket.close()
        connectionThread.join()
        while (true) {
            val connection = connections.poll() ?: break
            runCatching { connection.close() }
        }
    }
}