/*
 * Copyright 2025 Karma Krafts & associates
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

package dev.karmakrafts.kcml.monitor

import dev.karmakrafts.kcml.monitor.protocol.C2SConnectPacket
import dev.karmakrafts.kcml.monitor.protocol.C2SPacket
import dev.karmakrafts.kcml.monitor.protocol.PacketCodecs
import dev.karmakrafts.kcml.monitor.protocol.S2CPacket
import dev.karmakrafts.kcml.monitor.protocol.S2ConnectAckPacket
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.util.concurrent.ConcurrentLinkedDeque
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.reflect.KClass

@OptIn(ExperimentalAtomicApi::class)
internal class MonitorServer(private val logger: Logger) {
    private val connectionPool: ConcurrentLinkedDeque<Socket> = ConcurrentLinkedDeque()
    private val isRunning: AtomicBoolean = AtomicBoolean(false)
    private val packetHandlers: HashMap<KClass<out C2SPacket>, (C2SPacket) -> Unit> = HashMap()
    private val outgoingPackets: ConcurrentLinkedDeque<S2CPacket> = ConcurrentLinkedDeque()
    private val packetBuffer: ByteBuffer = ByteBuffer.allocate(100000)

    private var port: AtomicInt = AtomicInt(65000)
    private lateinit var socket: ServerSocket
    private lateinit var connectionAcceptorThread: Thread
    private lateinit var connectionCleanerThread: Thread
    private lateinit var ioThread: Thread

    init {
        onPacket<C2SConnectPacket> { incomingPacket ->
            val clientId = incomingPacket.clientId
            broadcastPacket(S2ConnectAckPacket(clientId))
        }
    }

    fun broadcastPacket(packet: S2CPacket) {
        outgoingPackets += packet
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <reified P : C2SPacket> onPacket(noinline handler: (P) -> Unit) {
        packetHandlers[P::class] = handler as (C2SPacket) -> Unit
    }

    private fun handleIo() {
        Thread.currentThread().name = "IO Handler"
        logger.info("Starting IO handler thread")
        while (isRunning.load()) {
            for (connection in connectionPool) {
                val outputStream = connection.getOutputStream()
                val outputChannel = Channels.newChannel(outputStream)
                val inputStream = connection.getInputStream()
                val inputChannel = Channels.newChannel(inputStream)
                // First process all outgoing packets..
                while (isRunning.load() && !outgoingPackets.isEmpty()) {
                    val packet = outgoingPackets.removeFirst()
                    packetBuffer.clear()
                    PacketCodecs.serialize(packet, packetBuffer)
                    outputChannel.write(packetBuffer)
                }
                // ..then process all incoming ones
                while (isRunning.load()) {
                    packetBuffer.clear()
                    if (inputChannel.read(packetBuffer) <= 0) break
                    packetBuffer.flip()
                    while (packetBuffer.hasRemaining()) {
                        val packet = PacketCodecs.deserialize<C2SPacket>(packetBuffer)
                        packetHandlers[packet::class]!!(packet)
                    }
                }
            }
            Thread.sleep(10)
        }
        logger.info("Stopped IO handler thread")
    }

    private fun cleanConnections() {
        Thread.currentThread().name = "Connection Cleaner"
        logger.info("Starting connection cleaner thread")
        while (isRunning.load()) {
            connectionPool.removeIf { !it.isConnected || it.isClosed }
            Thread.sleep(500)
        }
        logger.info("Stopped connection cleaner thread")
    }

    private fun acceptConnections() {
        Thread.currentThread().name = "Connection Acceptor"
        logger.info("Starting connection acceptor thread")
        socket = ServerSocket(port.load()).apply {
            soTimeout = 100
        }
        while (isRunning.load()) {
            try {
                connectionPool += socket.accept()
            } catch (_: Throwable) {
                // We can safely ignore this error
            }
        }
        socket.close()
        logger.info("Stopped connection acceptor thread")
    }

    fun start(port: Int) {
        this.port.store(port)
        isRunning.store(true)
        connectionAcceptorThread = Thread(::acceptConnections).apply {
            start()
        }
        connectionCleanerThread = Thread(::cleanConnections).apply {
            start()
        }
        ioThread = Thread(::handleIo).apply {
            start()
        }
    }

    fun stop() {
        isRunning.store(false)
        connectionAcceptorThread.join()
        connectionPool.forEach(Socket::close)
        connectionCleanerThread.join()
        connectionPool.clear()
        ioThread.join()
    }

    fun restart(port: Int) {
        stop()
        start(port)
    }
}