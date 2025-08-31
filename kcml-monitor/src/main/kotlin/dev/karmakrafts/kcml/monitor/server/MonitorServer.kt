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

package dev.karmakrafts.kcml.monitor.server

import dev.karmakrafts.kcml.monitor.protocol.C2SConnectPacket
import dev.karmakrafts.kcml.monitor.protocol.C2SPacket
import dev.karmakrafts.kcml.monitor.protocol.PacketCodecs
import dev.karmakrafts.kcml.monitor.protocol.S2CConnectAckPacket
import dev.karmakrafts.kcml.monitor.protocol.S2CPacket
import dev.karmakrafts.kcml.monitor.util.Logger
import dev.karmakrafts.kcml.monitor.util.getAgent
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.Channels
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.reflect.KClass

@OptIn(ExperimentalAtomicApi::class)
internal class MonitorServer(private val logger: Logger) {
    private val connectionPool: ConcurrentLinkedDeque<Socket> = ConcurrentLinkedDeque()
    private val packetHandlers: HashMap<KClass<out C2SPacket>, (Socket, C2SPacket) -> Unit> = HashMap()
    private val outgoingPackets: ConcurrentLinkedDeque<S2CPacket> = ConcurrentLinkedDeque()
    private val packetBuffer: ByteBuffer = ByteBuffer.allocate(100000).order(ByteOrder.nativeOrder())

    private val _isRunning: AtomicBoolean = AtomicBoolean(false)
    inline val isRunning: Boolean get() = _isRunning.load()
    inline val connectionCount: Int get() = connectionPool.size

    private var port: AtomicInt = AtomicInt(65000)
    private lateinit var executor: ExecutorService
    private lateinit var socket: ServerSocket
    private lateinit var connectionAcceptorTask: CompletableFuture<Unit>
    private lateinit var connectionCleanerTask: CompletableFuture<Unit>
    private lateinit var ioTask: CompletableFuture<Unit>

    val agents: ConcurrentLinkedDeque<Agent> = ConcurrentLinkedDeque()
    private val socketToAgent: ConcurrentHashMap<Socket, Agent> = ConcurrentHashMap()
    private var onAgentAdded: AtomicReference<(Agent) -> Unit> = AtomicReference {}
    private var onAgentRemoved: AtomicReference<(Agent) -> Unit> = AtomicReference {}

    init {
        onPacket<C2SConnectPacket> { socket, incomingPacket ->
            addAgent(socket, incomingPacket.getAgent())
            broadcastPacket(S2CConnectAckPacket(incomingPacket.clientId, Instant.now()))
        }
    }

    private fun addAgent(socket: Socket, agent: Agent) {
        check(agent !in agents) { "Agent $agent is already connected" }
        agents += agent
        socketToAgent[socket] = agent
    }

    private fun removeAgent(socket: Socket) {
        val agent = socketToAgent[socket] ?: error("Agent for socket $socket could not be found")
        agents -= agent
        socketToAgent -= socket
    }

    fun broadcastPacket(packet: S2CPacket) {
        outgoingPackets += packet
    }

    fun onAgentAdded(handler: (Agent) -> Unit) {
        val oldHandler = onAgentAdded.load()
        onAgentAdded.store { agent ->
            oldHandler(agent)
            handler(agent)
        }
    }

    fun onAgentRemoved(handler: (Agent) -> Unit) {
        val oldHandler = onAgentRemoved.load()
        onAgentRemoved.store { agent ->
            oldHandler(agent)
            handler(agent)
        }
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <reified P : C2SPacket> onPacket(crossinline handler: (Socket, P) -> Unit) {
        val oldHandler = packetHandlers[P::class]
        packetHandlers[P::class] = { socket, packet ->
            oldHandler?.invoke(socket, packet)
            handler(socket, packet as P)
        }
    }

    private fun handleIo() {
        Thread.currentThread().name = "Monitor Server IO"
        logger.info("Starting IO handler task")
        while (_isRunning.load()) {
            for (connection in connectionPool) {
                val outputStream = connection.getOutputStream()
                val outputChannel = Channels.newChannel(outputStream)
                val inputStream = connection.getInputStream()
                val inputChannel = Channels.newChannel(inputStream)
                // First process all outgoing packets..
                while (_isRunning.load() && !outgoingPackets.isEmpty()) {
                    val packet = outgoingPackets.removeFirst()
                    packetBuffer.clear()
                    PacketCodecs.serialize(packet, packetBuffer)
                    packetBuffer.flip()
                    outputChannel.write(packetBuffer)
                }
                // ..then process all incoming ones
                while (_isRunning.load()) {
                    packetBuffer.clear()
                    if (inputChannel.read(packetBuffer) <= 0) break
                    packetBuffer.flip()
                    while (packetBuffer.hasRemaining()) {
                        val packet = PacketCodecs.deserialize<C2SPacket>(packetBuffer)
                        packetHandlers[packet::class]!!(connection, packet)
                    }
                }
            }
        }
        logger.info("Stopped IO handler task")
    }

    private fun cleanConnections() {
        Thread.currentThread().name = "Monitor Server Cleaner"
        logger.info("Starting connection cleaner task")
        while (_isRunning.load()) {
            connectionPool -= connectionPool.filter { socket -> socket.isClosed || !socket.isConnected }
                .toSet()
                .onEach(::removeAgent)
            Thread.sleep(500)
        }
        logger.info("Stopped connection cleaner task")
    }

    private fun acceptConnections() {
        Thread.currentThread().name = "Monitor Server Acceptor"
        logger.info("Starting connection acceptor task")
        socket = ServerSocket(port.load()).apply {
            soTimeout = 100
        }
        while (_isRunning.load()) {
            try {
                connectionPool += socket.accept()
            } catch (_: Throwable) {
                // We can safely ignore this error
            }
        }
        socket.close()
        logger.info("Stopped connection acceptor task")
    }

    fun start(port: Int) {
        if (!_isRunning.compareAndSet(expectedValue = false, newValue = true)) return
        executor = Executors.newVirtualThreadPerTaskExecutor()
        this.port.store(port)
        connectionAcceptorTask = CompletableFuture.supplyAsync(::acceptConnections, executor)
        connectionCleanerTask = CompletableFuture.supplyAsync(::cleanConnections, executor)
        ioTask = CompletableFuture.supplyAsync(::handleIo, executor)
    }

    fun stop() {
        if (!_isRunning.compareAndSet(expectedValue = true, newValue = false)) return
        connectionAcceptorTask.join()
        connectionPool.forEach(Socket::close)
        connectionCleanerTask.join()
        connectionPool.clear()
        ioTask.join()
        executor.shutdown()
    }

    fun restart(port: Int) {
        stop()
        start(port)
    }
}