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

import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.concurrent.atomics.AtomicBoolean

internal class AgentCommServer( // @formatter:off
    port: Int,
    private val messageConsumer: (String) -> Unit = Logging.getLogger(AgentCommServer::class.java)::info
) : AutoCloseable { // @formatter:on
    private val logger: Logger = Logging.getLogger(AgentCommServer::class.java)

    private class Connection(
        private val socket: Socket,
        private val messageConsumer: (String) -> Unit,
        private val onClosed: (Connection) -> Unit
    ) : AutoCloseable {
        private val isOpen: AtomicBoolean = AtomicBoolean(true)
        private val reader: BufferedReader = BufferedReader(InputStreamReader(socket.getInputStream()))
        private val ioThread: Thread = Thread {
            try {
                while (isOpen.load()) {
                    val line = reader.readLine() ?: break
                    messageConsumer(line)
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

        override fun close() {
            if (!isOpen.compareAndSet(expectedValue = true, newValue = false)) {
                return
            }
            runCatching { socket.close() }
            runCatching { reader.close() }
            if (Thread.currentThread() != ioThread) {
                ioThread.join()
            }
        }
    }

    private val isRunning: AtomicBoolean = AtomicBoolean(true)
    private val socket: ServerSocket = ServerSocket(port, 100, InetAddress.getLoopbackAddress())
    private val connections: ConcurrentLinkedQueue<Connection> = ConcurrentLinkedQueue()

    val port: Int
        get() = socket.localPort

    val connectionCount: Int
        get() = connections.size

    private val connectionThread: Thread = Thread {
        try {
            while (isRunning.load()) {
                val socket = socket.accept()
                val connection = Connection(socket, messageConsumer) { closedConnection ->
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
        logger.info("Starting KCML agent comm server..")
        connectionThread.start()
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