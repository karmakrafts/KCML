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

import dev.karmakrafts.kcml.monitor.protocol.PacketCodecs
import dev.karmakrafts.kcml.monitor.protocol.TargetedPacket
import dev.karmakrafts.kcml.monitor.protocol.c2s.C2SConnectPacket
import dev.karmakrafts.kcml.monitor.protocol.c2s.C2SExceptionPacket
import dev.karmakrafts.kcml.monitor.protocol.c2s.C2SLogPacket
import dev.karmakrafts.kcml.monitor.protocol.c2s.C2SPacket
import dev.karmakrafts.kcml.monitor.protocol.c2s.C2SUpdateJvmOptionsPacket
import dev.karmakrafts.kcml.monitor.protocol.log.Logger
import dev.karmakrafts.kcml.monitor.protocol.log.NoopLogger
import dev.karmakrafts.kcml.monitor.protocol.s2c.S2CPacket
import dev.karmakrafts.kcml.monitor.protocol.s2c.S2CUpdateJvmOptionsPacket
import dev.karmakrafts.kcml.monitor.util.getAgent
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.group.DefaultChannelGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.util.concurrent.GlobalEventExecutor
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.concurrent.atomics.incrementAndFetch

@OptIn(ExperimentalAtomicApi::class)
internal class MonitorServer(
    private val logger: Logger = NoopLogger.INSTANCE,
    private val agentLogger: (UUID) -> Logger = { NoopLogger.INSTANCE }
) : AutoCloseable {
    private val errorHandler: AtomicReference<(Throwable) -> Unit> = AtomicReference {}
    private var bossGroup: NioEventLoopGroup? = null
    private var workerGroup: NioEventLoopGroup? = null
    private var serverChannel: Channel? = null
    private var channelGroup: DefaultChannelGroup? = null
    private val packetHandlers: ConcurrentHashMap<Class<out C2SPacket>, (Channel, C2SPacket) -> Unit> =
        ConcurrentHashMap()
    private val agentsById: ConcurrentHashMap<UUID, Agent> = ConcurrentHashMap()
    private val channelById: ConcurrentHashMap<UUID, Channel> = ConcurrentHashMap()
    private val connectHandler: AtomicReference<(Channel, Agent) -> Unit> = AtomicReference { _, _ -> }
    private val disconnectHandler: AtomicReference<(Channel, Agent) -> Unit> = AtomicReference { _, _ -> }
    private val updateHandler: AtomicReference<(Channel, Agent) -> Unit> = AtomicReference { _, _ -> }

    private val _isRunning: AtomicBoolean = AtomicBoolean(false)
    inline val isRunning: Boolean get() = _isRunning.load()

    private val _connectionCount: AtomicInt = AtomicInt(0)
    inline val connectionCount: Int get() = _connectionCount.load()

    // Resolved underlying Logger for the current Agent
    private inline val C2SPacket.logger: Logger
        get() = agentLogger(clientId)

    init {
        onPacket<C2SConnectPacket> { channel, packet ->
            val id = packet.clientId
            if (agentsById.containsKey(id)) {
                logger.error("Client $id could not connect, already connected")
                return@onPacket
            }
            val agent = packet.getAgent()
            agentsById[id] = agent // Remember agent data
            channelById[id] = channel
            connectHandler.load()(channel, agent)
            logger.info("Client $id connected")
        }
        onPacket<C2SLogPacket> { _, packet ->
            packet.logger.log(packet.level, packet.message)
        }
        onPacket<C2SExceptionPacket> { _, packet ->
            packet.logger.error("Uncaught exception in agent: ${packet.message}\n${packet.stackTrace.joinToString("\n")}")
        }
        onPacket<C2SUpdateJvmOptionsPacket> { channel, packet ->
            val agent = agentsById[packet.clientId] ?: return@onPacket
            agent.jvmOptions = packet.jvmOptions
            updateHandler.load()(channel, agent)
        }
    }

    fun sendUpdateJvmOptionsPacket(clientId: UUID) = sendPacket(S2CUpdateJvmOptionsPacket(clientId, Instant.now()))

    @Sharable
    private inner class ChannelInboundHandler : SimpleChannelInboundHandler<C2SPacket>(C2SPacket::class.java) {
        override fun messageReceived(ctx: ChannelHandlerContext, msg: C2SPacket) {
            logger.debug("Received ${msg::class.simpleName}: $msg")
            packetHandlers[msg::class.java]?.invoke(ctx.channel(), msg)
        }

        override fun channelActive(ctx: ChannelHandlerContext) {
            val channel = ctx.channel()
            channelGroup?.add(channel)
            _connectionCount.incrementAndFetch()
            logger.debug("Channel $channel active")
            super.channelActive(ctx)
        }

        override fun channelInactive(ctx: ChannelHandlerContext) {
            val channel = ctx.channel()
            // @formatter:off
            channelById.entries
                .find { (_, chann) -> chann === channel }
                ?.let { (id, chann) ->
                    val agent = agentsById[id] ?: return@let
                    disconnectHandler.load()(chann, agent)
                    agentsById -= id
                    channelById -= id
                    logger.info("Client $id disconnected")
                }
            // @formatter:on
            channelGroup?.remove(channel)
            _connectionCount.decrementAndFetch()
            logger.debug("Channel $channel inactive")
            super.channelInactive(ctx)
        }

        override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
            logger.error("Could not handle packet: ${cause.stackTraceToString()}")
            ctx.close()
        }
    }

    inline fun onClientConnect(crossinline handler: (Channel, Agent) -> Unit) {
        val oldHandler = connectHandler.load()
        connectHandler.store { channel, agent ->
            oldHandler(channel, agent)
            handler(channel, agent)
        }
    }

    inline fun onClientDisconnect(crossinline handler: (Channel, Agent) -> Unit) {
        val oldHandler = disconnectHandler.load()
        disconnectHandler.store { channel, agent ->
            oldHandler(channel, agent)
            handler(channel, agent)
        }
    }

    inline fun onError(crossinline handler: (Throwable) -> Unit) {
        val oldHandler = errorHandler.load()
        errorHandler.store { error ->
            oldHandler(error)
            handler(error)
        }
    }

    inline fun onClientUpdate(crossinline handler: (Channel, Agent) -> Unit) {
        val oldHandler = updateHandler.load()
        updateHandler.store { channel, agent ->
            oldHandler(channel, agent)
            handler(channel, agent)
        }
    }

    inline fun <reified P : C2SPacket> onPacket(crossinline handler: (Channel, P) -> Unit) {
        val type = P::class.java
        if (!packetHandlers.containsKey(type)) {
            packetHandlers[type] = { channel, packet -> handler(channel, packet as P) }
            return
        }
        val oldHandler = packetHandlers[type]!!
        packetHandlers[type] = { channel, packet ->
            oldHandler(channel, packet)
            handler(channel, packet as P)
        }
    }

    fun broadcastPacket(packet: S2CPacket) {
        channelGroup?.writeAndFlush(packet)
    }

    fun <P> sendPacket(packet: P) where P : S2CPacket, P : TargetedPacket {
        channelById[packet.clientId]?.writeAndFlush(packet)
    }

    fun start(hostName: String, port: Int) {
        if (_isRunning.load()) return
        try {
            logger.info("Starting server on $hostName:$port")
            bossGroup = NioEventLoopGroup(1)
            workerGroup = NioEventLoopGroup()
            channelGroup = DefaultChannelGroup(GlobalEventExecutor.INSTANCE)
            val bootstrap = ServerBootstrap().apply {
                group(bossGroup, workerGroup)
                channel(NioServerSocketChannel::class.java)
                option(ChannelOption.SO_BACKLOG, 128)
                childOption(ChannelOption.SO_KEEPALIVE, true)
                childHandler(object : ChannelInitializer<SocketChannel>() {
                    override fun initChannel(channel: SocketChannel) {
                        channel.pipeline().apply {
                            PacketCodecs.configurePipeline(this)
                            addLast(ChannelInboundHandler())
                        }
                    }
                })
            }
            val future = bootstrap.bind(hostName, port).sync()
            serverChannel = future.channel()
            logger.info("Server started")
        } catch (error: Throwable) {
            errorHandler.load()(error)
        }
        _isRunning.store(true)
    }

    override fun close() {
        if (!_isRunning.load()) return
        logger.info("Stopping server")
        channelGroup?.close()?.syncUninterruptibly()
        channelGroup = null
        serverChannel?.close()?.syncUninterruptibly()
        serverChannel = null
        workerGroup?.shutdownGracefully()
        workerGroup = null
        bossGroup?.shutdownGracefully()
        bossGroup = null
        agentsById.clear()
        logger.info("Server stopped")
        _isRunning.store(false)
    }
}