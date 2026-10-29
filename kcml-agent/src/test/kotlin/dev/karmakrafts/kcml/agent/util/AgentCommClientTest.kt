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
import dev.karmakrafts.kcml.agent.proto.A2SLog
import dev.karmakrafts.kcml.agent.proto.S2AHandshake
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.serializer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalSerializationApi::class)
class AgentCommClientTest {
    private class TestConnection(
        private val socket: Socket
    ) {
        private val input = DataInputStream(socket.getInputStream())
        private val output = DataOutputStream(socket.getOutputStream())

        fun read(): Int = input.read()

        fun shutdownOutput() = socket.shutdownOutput()

        inline fun <reified T> receive(): T {
            val serializer = serializer<T>()
            assertEquals(serializer.descriptor.serialName, input.readUTF())
            val payload = input.readNBytes(input.readInt())
            return JSON.decodeFromStream(serializer, ByteArrayInputStream(payload))
        }

        inline fun <reified T> send(message: T) {
            val serializer = serializer<T>()
            val payload = ByteArrayOutputStream().apply {
                JSON.encodeToStream(serializer, message, this)
            }.toByteArray()
            output.writeUTF(serializer.descriptor.serialName)
            output.writeInt(payload.size)
            output.write(payload)
            output.flush()
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
                    client.close()
                }
            }
        }
    }

    @Test
    fun `sends typed messages`() = withClient { client, connection ->
        val messages = listOf(
            A2SLog("First message"), A2SLog("Second message"), A2SLog("Third message")
        )
        messages.forEach(client::send)

        assertEquals(messages, List(messages.size) { connection.receive<A2SLog>() })
    }

    @Test
    fun `dispatches incoming messages by type`() = withClient { client, connection ->
        val handshake = S2AHandshake("signature", "loader", "bridge", listOf("plugin"))
        val log = A2SLog("message")
        val latch = CountDownLatch(2)
        var receivedHandshake: S2AHandshake? = null
        var receivedLog: A2SLog? = null
        client.receive<S2AHandshake> {
            receivedHandshake = it
            latch.countDown()
        }
        client.receive<A2SLog> {
            receivedLog = it
            latch.countDown()
        }

        connection.send(handshake)
        connection.send(log)

        assertTrue(latch.await(TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS))
        assertEquals(handshake, receivedHandshake)
        assertEquals(log, receivedLog)
    }

    @Test
    fun `handshake correlates responses and refreshes runtime paths`() = withClient { client, connection ->
        repeat(2) { index ->
            val response = CompletableFuture.supplyAsync { client.handshake(TIMEOUT_MILLIS.toLong()) }
            val request = connection.receive<A2SHandshake>()
            val expected = S2AHandshake(
                request.signature,
                "loader-$index",
                "bridge-$index",
                listOf("plugin-$index"),
                true,
                "dump-$index"
            )
            connection.send(expected.copy(signature = "another-client"))
            connection.send(expected)
            assertEquals(expected, response.get(TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS))
        }
    }

    @Test
    fun `handshake times out when server does not respond`() = withClient { client, _ ->
        assertFailsWith<TimeoutException> { client.handshake(50) }
    }

    @Test
    fun `handshake fails when server disconnects`() = withClient { client, connection ->
        val response = CompletableFuture.supplyAsync { client.handshake(TIMEOUT_MILLIS.toLong()) }
        connection.receive<A2SHandshake>()
        connection.shutdownOutput()
        assertFailsWith<ExecutionException> { response.get(TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS) }
    }

    @Test
    fun `handshake rejects missing runtime paths`() = withClient { client, connection ->
        val response = CompletableFuture.supplyAsync { client.handshake(TIMEOUT_MILLIS.toLong()) }
        val request = connection.receive<A2SHandshake>()
        connection.send(S2AHandshake(request.signature, "", "bridge", emptyList()))
        val error = assertFailsWith<ExecutionException> { response.get(TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS) }
        assertTrue(error.cause is IllegalArgumentException)
    }

    @Test
    fun `stops when remote closes connection`() = withClient { _, connection ->
        connection.shutdownOutput()

        assertEquals(-1, connection.read(), "Client did not close its connection")
    }

    private companion object {
        const val TIMEOUT_MILLIS: Int = 5_000
        val JSON: Json = Json { ignoreUnknownKeys = true }
    }
}