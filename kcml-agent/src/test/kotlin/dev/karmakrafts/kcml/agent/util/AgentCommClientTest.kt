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
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

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
            client.use {
                server.accept().use { socket ->
                    socket.soTimeout = TIMEOUT_MILLIS
                    test(client, TestConnection(socket))
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
        val receivedHandshake = CompletableFuture<S2AHandshake>()
        val receivedLog = CompletableFuture<A2SLog>()
        client.receive<S2AHandshake> { receivedHandshake.complete(it) }
        client.receive<A2SLog> { receivedLog.complete(it) }

        connection.send(handshake)
        connection.send(log)

        assertEquals(handshake, receivedHandshake.get(TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS))
        assertEquals(log, receivedLog.get(TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS))
    }

    @Test
    fun `handshake correlates responses and refreshes runtime paths`() = withClient { client, connection ->
        repeat(2) { index ->
            val response = CompletableFuture.supplyAsync { client.handshake(TIMEOUT_MILLIS.toLong()) }
            val request = connection.receive<A2SHandshake>()
            val expected = S2AHandshake(
                request.signature, "loader-$index", "bridge-$index", listOf("plugin-$index"), true, "dump-$index"
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
    fun `handshake recovers after timeout and ignores late responses`() = withClient { client, connection ->
        val timedOut = CompletableFuture.supplyAsync { client.handshake(50) }
        val expiredRequest = connection.receive<A2SHandshake>()
        val error = assertFailsWith<ExecutionException> {
            timedOut.get(TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS)
        }
        assertIs<TimeoutException>(error.cause)

        val response = CompletableFuture.supplyAsync { client.handshake(TIMEOUT_MILLIS.toLong()) }
        val request = connection.receive<A2SHandshake>()
        val expected = S2AHandshake(request.signature, "loader", "bridge", emptyList())
        connection.send(expected.copy(signature = expiredRequest.signature, loaderPath = "expired-loader"))
        connection.send(expected)

        assertEquals(expected, response.get(TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS))
    }

    @Test
    fun `correlates overlapping handshakes with responses in reverse order`() = withClient { client, connection ->
        val first = CompletableFuture.supplyAsync { client.handshake(TIMEOUT_MILLIS.toLong()) }
        val firstRequest = connection.receive<A2SHandshake>()
        val second = CompletableFuture.supplyAsync { client.handshake(TIMEOUT_MILLIS.toLong()) }
        val secondRequest = connection.receive<A2SHandshake>()
        val firstResponse = S2AHandshake(firstRequest.signature, "first-loader", "first-bridge", emptyList())
        val secondResponse = S2AHandshake(secondRequest.signature, "second-loader", "second-bridge", emptyList())

        connection.send(secondResponse)
        connection.send(firstResponse)

        assertEquals(firstResponse, first.get(TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS))
        assertEquals(secondResponse, second.get(TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS))
    }

    @Test
    fun `handshake fails when server disconnects`() = withClient { client, connection ->
        val response = CompletableFuture.supplyAsync { client.handshake(TIMEOUT_MILLIS.toLong()) }
        connection.receive<A2SHandshake>()
        connection.shutdownOutput()
        val error = assertFailsWith<ExecutionException> {
            response.get(TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS)
        }
        val handshakeError = assertIs<ExecutionException>(error.cause)
        assertIs<IOException>(handshakeError.cause)
        assertEquals("KCML agent connection closed during handshake", handshakeError.cause?.message)
    }

    @Test
    fun `handshake rejects invalid runtime paths`() {
        val invalid = listOf(
            S2AHandshake("", "", "bridge", emptyList()) to "Handshake loader path must not be blank",
            S2AHandshake("", "loader", " \t", emptyList()) to "Handshake bridge path must not be blank",
            S2AHandshake("", "loader", "bridge", listOf("plugin", " ")) to "Handshake plugin paths must not be blank"
        )
        for ((message, expectedError) in invalid) withClient { client, connection ->
            val response = CompletableFuture.supplyAsync { client.handshake(TIMEOUT_MILLIS.toLong()) }
            val request = connection.receive<A2SHandshake>()
            connection.send(message.copy(signature = request.signature))
            val error = assertFailsWith<ExecutionException> {
                response.get(TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS)
            }
            val validationError = assertIs<IllegalArgumentException>(error.cause)
            assertEquals(expectedError, validationError.message)
        }
    }

    @Test
    fun `handshake rejects nonpositive timeouts without sending a request`() = withClient { client, connection ->
        for (timeout in listOf(0L, -1L, Long.MIN_VALUE)) {
            val error = assertFailsWith<IllegalArgumentException> { client.handshake(timeout) }
            assertEquals("Handshake timeout must be positive", error.message)
        }
        client.send(A2SLog("still connected"))
        assertEquals(A2SLog("still connected"), connection.receive<A2SLog>())
    }

    @Test
    fun `notifies every handler registered for the same message type`() = withClient { client, connection ->
        val first = CompletableFuture<A2SLog>()
        val second = CompletableFuture<A2SLog>()
        client.receive<A2SLog> { first.complete(it) }
        client.receive<A2SLog> { second.complete(it) }
        val message = A2SLog("both handlers")

        connection.send(message)

        assertEquals(message, first.get(TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS))
        assertEquals(message, second.get(TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS))
    }

    @Test
    fun `skips unhandled message types without losing subsequent messages`() = withClient { client, connection ->
        val received = CompletableFuture<A2SLog>()
        client.receive<A2SLog> { received.complete(it) }
        val message = A2SLog("after unhandled packet")

        connection.send(A2SHandshake("unhandled"))
        connection.send(message)

        assertEquals(message, received.get(TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS))
    }

    @Test
    fun `closing the client closes the connection and rejects further handshakes`() = withClient { client, connection ->
        client.close()

        assertEquals(-1, connection.read())
        assertFailsWith<IllegalStateException> { client.handshake(TIMEOUT_MILLIS.toLong()) }
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