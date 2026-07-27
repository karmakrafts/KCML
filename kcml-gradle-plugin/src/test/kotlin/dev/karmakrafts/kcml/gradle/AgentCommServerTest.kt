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

import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentCommServerTest {
    private class TestConnection(private val socket: Socket) {
        private val reader = socket.getInputStream().bufferedReader()
        private val writer = socket.getOutputStream().bufferedWriter()

        fun readLine(): String? = reader.readLine()

        fun shutdownOutput() = socket.shutdownOutput()

        fun send(message: String) {
            writer.write(message)
            writer.newLine()
            writer.flush()
        }
    }

    private fun withServer(
        connectionCount: Int = 1,
        messageConsumer: (String) -> Unit = {},
        test: (AgentCommServer, List<TestConnection>) -> Unit
    ) {
        AgentCommServer(0, messageConsumer).use { server ->
            val sockets = List(connectionCount) {
                Socket(InetAddress.getLoopbackAddress(), server.port).apply {
                    soTimeout = TIMEOUT_MILLIS
                }
            }
            try {
                awaitConnections(server, connectionCount)
                test(server, sockets.map(::TestConnection))
            }
            finally {
                sockets.forEach(Socket::close)
            }
        }
    }

    private fun awaitConnections(server: AgentCommServer, expectedCount: Int) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(TIMEOUT_MILLIS.toLong())
        while (server.connectionCount < expectedCount && System.nanoTime() < deadline) {
            Thread.onSpinWait()
        }
        assertTrue(server.connectionCount >= expectedCount, "Server did not accept $expectedCount connection(s)")
    }

    private fun awaitNoConnections(server: AgentCommServer) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(TIMEOUT_MILLIS.toLong())
        while (server.connectionCount > 0 && System.nanoTime() < deadline) {
            Thread.onSpinWait()
        }
        assertEquals(0, server.connectionCount, "Server did not close its connections")
    }

    private fun assertReceivesMessages(
        expectedMessages: List<String>,
        connectionCount: Int,
        sendMessages: (List<TestConnection>) -> Unit
    ) {
        val receivedMessages = ConcurrentLinkedQueue<String>()
        val messageLatch = CountDownLatch(expectedMessages.size)
        withServer(connectionCount, messageConsumer = { message ->
            receivedMessages += message
            messageLatch.countDown()
        }) { _, connections ->
            sendMessages(connections)

            assertTrue(
                messageLatch.await(TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS),
                "Server did not receive all messages"
            )
            assertEquals(expectedMessages.sorted(), receivedMessages.sorted())
        }
    }

    @Test
    fun `receives log messages from one client`() {
        val messages = listOf("First message", "Second message", "Third message")

        assertReceivesMessages(messages, connectionCount = 1) { connections ->
            messages.forEach(connections.single()::send)
        }
    }

    @Test
    fun `receives log messages from multiple clients`() {
        val messagesByClient = listOf(
            listOf("First client, first message", "First client, second message"),
            listOf("Second client, first message", "Second client, second message")
        )

        assertReceivesMessages(messagesByClient.flatten(), connectionCount = messagesByClient.size) { connections ->
            connections.zip(messagesByClient).forEach { (connection, messages) ->
                messages.forEach(connection::send)
            }
        }
    }

    @Test
    fun `close closes connections`() = withServer { server, connections ->
        server.close()

        assertEquals(0, server.connectionCount)
        assertNull(connections.single().readLine())
    }

    @Test
    fun `client output shutdown closes connection`() = withServer { server, connections ->
        val connection = connections.single()
        connection.shutdownOutput()

        awaitNoConnections(server)
        assertNull(connection.readLine())
    }

    @Test
    fun `close can be called repeatedly`() = withServer(connectionCount = 0) { server, _ ->
        server.close()
        server.close()
    }

    private companion object {
        const val TIMEOUT_MILLIS: Int = 5_000
    }
}