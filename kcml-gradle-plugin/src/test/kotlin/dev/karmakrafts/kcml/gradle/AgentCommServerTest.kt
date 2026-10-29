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
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalSerializationApi::class)
class AgentCommServerTest {
    private class TestConnection(private val socket: Socket) {
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

    private fun withServer(
        connectionCount: Int = 1,
        handshakeProvider: ((A2SHandshake) -> S2AHandshake)? = null,
        test: (AgentCommServer, List<TestConnection>) -> Unit
    ) {
        AgentCommServer(0, handshakeProvider).use { server ->
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
        expectedMessages: List<A2SLog>, connectionCount: Int, sendMessages: (List<TestConnection>) -> Unit
    ) {
        val receivedMessages = ConcurrentLinkedQueue<A2SLog>()
        val messageLatch = CountDownLatch(expectedMessages.size)
        withServer(connectionCount) { server, connections ->
            server.receive<A2SLog> { message ->
                receivedMessages += message
                messageLatch.countDown()
            }
            sendMessages(connections)

            assertTrue(
                messageLatch.await(TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS),
                "Server did not receive all messages"
            )
            assertEquals(
                expectedMessages.map(A2SLog::message).sorted(), receivedMessages.map(A2SLog::message).sorted()
            )
        }
    }

    @Test
    fun `handshake replies only to the requesting client with current runtime paths`() {
        val generation = AtomicInteger()
        withServer(2, { request ->
            S2AHandshake(
                request.signature,
                "loader-${generation.get()}",
                "bridge-${generation.get()}",
                listOf("plugin"),
                true,
                "dump"
            )
        }) { _, connections ->
            connections[0].send(A2SHandshake("first"))
            assertEquals("first", connections[0].receive<S2AHandshake>().signature)
            generation.set(1)
            connections[1].send(A2SHandshake("second"))
            assertEquals(
                S2AHandshake("second", "loader-1", "bridge-1", listOf("plugin"), true, "dump"),
                connections[1].receive<S2AHandshake>()
            )
            connections[0].send(A2SHandshake("refresh"))
            assertEquals("loader-1", connections[0].receive<S2AHandshake>().loaderPath)
        }
    }

    @Test
    fun `receives log messages from one client`() {
        val messages = listOf(A2SLog("First message"), A2SLog("Second message"), A2SLog("Third message"))

        assertReceivesMessages(messages, connectionCount = 1) { connections ->
            messages.forEach(connections.single()::send)
        }
    }

    @Test
    fun `receives log messages from multiple clients`() {
        val messagesByClient = listOf(
            listOf(A2SLog("First client, first message"), A2SLog("First client, second message")),
            listOf(A2SLog("Second client, first message"), A2SLog("Second client, second message"))
        )

        assertReceivesMessages(messagesByClient.flatten(), connectionCount = messagesByClient.size) { connections ->
            connections.zip(messagesByClient).forEach { (connection, messages) ->
                messages.forEach(connection::send)
            }
        }
    }

    @Test
    fun `dispatches incoming messages by type`() = withServer { server, connections ->
        val handshake = A2SHandshake("signature")
        val log = A2SLog("message")
        val latch = CountDownLatch(2)
        var receivedHandshake: A2SHandshake? = null
        var receivedLog: A2SLog? = null
        server.receive<A2SHandshake> {
            receivedHandshake = it
            latch.countDown()
        }
        server.receive<A2SLog> {
            receivedLog = it
            latch.countDown()
        }

        connections.single().send(handshake)
        connections.single().send(log)

        assertTrue(latch.await(TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS))
        assertEquals(handshake, receivedHandshake)
        assertEquals(log, receivedLog)
    }

    @Test
    fun `skips messages without a registered handler`() = withServer { server, connections ->
        val log = A2SLog("message after skipped packet")
        val latch = CountDownLatch(1)
        var receivedLog: A2SLog? = null
        server.receive<A2SLog> {
            receivedLog = it
            latch.countDown()
        }

        connections.single().send(A2SHandshake("unhandled"))
        connections.single().send(log)

        assertTrue(latch.await(TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS))
        assertEquals(log, receivedLog)
    }

    @Test
    fun `broadcasts typed messages to clients`() = withServer(connectionCount = 2) { server, connections ->
        val handshake = S2AHandshake("signature", "loader", "bridge", listOf("plugin"))

        server.send(handshake)

        connections.forEach { assertEquals(handshake, it.receive<S2AHandshake>()) }
    }

    @Test
    fun `close closes connections`() = withServer { server, connections ->
        server.close()

        assertEquals(0, server.connectionCount)
        assertEquals(-1, connections.single().read())
    }

    @Test
    fun `client output shutdown closes connection`() = withServer { server, connections ->
        val connection = connections.single()
        connection.shutdownOutput()

        awaitNoConnections(server)
        assertEquals(-1, connection.read())
    }

    @Test
    fun `close can be called repeatedly`() = withServer(connectionCount = 0) { server, _ ->
        server.close()
        server.close()
    }

    private companion object {
        const val TIMEOUT_MILLIS: Int = 5_000
        val JSON: Json = Json { ignoreUnknownKeys = true }
    }
}