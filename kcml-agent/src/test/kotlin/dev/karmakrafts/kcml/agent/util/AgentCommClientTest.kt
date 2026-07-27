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

import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AgentCommClientTest {
    private class TestConnection(
        private val socket: Socket
    ) {
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

    private fun withClient(test: (AgentCommClient, TestConnection) -> Unit) {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            server.soTimeout = TIMEOUT_MILLIS
            val client = AgentCommClient(server.localPort)
            server.accept().use { socket ->
                socket.soTimeout = TIMEOUT_MILLIS
                try {
                    test(client, TestConnection(socket))
                }
                finally {
                    runCatching { socket.shutdownOutput() }
                }
            }
        }
    }

    @Test
    fun `sends log messages`() = withClient { client, connection ->
        val messages = listOf("First message", "Second message", "Third message")
        messages.forEach(client::log)

        assertEquals(messages, List(messages.size) { connection.readLine() })
    }

    @Test
    fun `treats shutdown as a regular server message`() = withClient { client, connection ->
        connection.send("shutdown")

        val messages = listOf("First message after shutdown", "Second message after shutdown")
        messages.forEach(client::log)
        assertEquals(messages, List(messages.size) { connection.readLine() })
    }

    @Test
    fun `stops when remote closes connection`() = withClient { _, connection ->
        connection.shutdownOutput()

        assertNull(connection.readLine(), "Client did not close its connection")
    }

    private companion object {
        const val TIMEOUT_MILLIS: Int = 5_000
    }
}