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

internal class AgentCommServer private constructor(
    private val socket: ServerSocket, private val handshakeProvider: ((A2SHandshake) -> S2AHandshake)?
) : AutoCloseable {
    companion object {
        private const val SHUTDOWN_TIMEOUT_MILLIS: Long = 1_000

        private fun bindSocket(rangeStart: Int, rangeEnd: Int): ServerSocket {
            require(rangeStart in 1..65535 && rangeEnd in (rangeStart + 1)..65536) {
                "Invalid KCML agent port range [$rangeStart, $rangeEnd)"
            }
            val size = rangeEnd - rangeStart
            val offset = Random.nextInt(size)
            var failure: BindException? = null
            repeat(size) { index ->
                val port = rangeStart + (offset + index) % size
                try {
                    return ServerSocket(port, 100, InetAddress.getLoopbackAddress())
                } catch (exception: BindException) {
                    failure = exception
                }
            }
            throw IllegalStateException("Could not bind KCML agent comm server in port range [$rangeStart, $rangeEnd)", failure)
        }
    }

    constructor(port: Int, handshakeProvider: ((A2SHandshake) -> S2AHandshake)? = null) :
        this(ServerSocket(port, 100, InetAddress.getLoopbackAddress()), handshakeProvider)

    constructor(rangeStart: Int, rangeEnd: Int, handshakeProvider: ((A2SHandshake) -> S2AHandshake)? = null) :
        this(bindSocket(rangeStart, rangeEnd), handshakeProvider)

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
        }.apply { isDaemon = true }

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
                // A handler may be waiting on Gradle state held by the thread closing the service.
                ioThread.join(SHUTDOWN_TIMEOUT_MILLIS)
            }
        }
    }

    private val isRunning: AtomicBoolean = AtomicBoolean(true)
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
    }.apply { isDaemon = true }

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
        connectionThread.join(SHUTDOWN_TIMEOUT_MILLIS)
        while (true) {
            val connection = connections.poll() ?: break
            runCatching { connection.close() }
        }
    }
}