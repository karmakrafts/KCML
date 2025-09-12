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

package dev.karmakrafts.kcml.monitor.protocol;

import dev.karmakrafts.kcml.monitor.protocol.c2s.C2SConnectPacket;
import dev.karmakrafts.kcml.monitor.protocol.c2s.C2SExceptionPacket;
import dev.karmakrafts.kcml.monitor.protocol.c2s.C2SPacket;
import dev.karmakrafts.kcml.monitor.protocol.c2s.C2SUpdateJvmOptionsPacket;
import dev.karmakrafts.kcml.monitor.protocol.log.Logger;
import dev.karmakrafts.kcml.monitor.protocol.log.RemoteLogger;
import dev.karmakrafts.kcml.monitor.protocol.s2c.S2CPacket;
import dev.karmakrafts.kcml.monitor.protocol.s2c.S2CUpdateJvmOptionsPacket;
import dev.karmakrafts.kcml.monitor.protocol.util.Pair;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class MonitorClient implements AutoCloseable {
    public final UUID id = UUID.randomUUID();
    public final RemoteLogger logger = new RemoteLogger(this);
    public final Logger localLogger;
    private final AtomicReference<Consumer<Throwable>> errorHandler = new AtomicReference<>(error -> {
    });
    private final AtomicReference<Runnable> connectHandler = new AtomicReference<>(() -> {
    });
    private final AtomicReference<Runnable> disconnectHandler = new AtomicReference<>(() -> {
    });
    private final ConcurrentHashMap<Class<? extends S2CPacket>, BiConsumer<Channel, S2CPacket>> packetHandlers = new ConcurrentHashMap<>();
    private NioEventLoopGroup eventLoopGroup;
    private Channel channel;

    public MonitorClient(final Logger localLogger) {
        this.localLogger = localLogger;
        onTargetedPacket(S2CUpdateJvmOptionsPacket.class, (channel, packet) -> {
            sendUpdateJvmOptionsPacket(); // Answer with current JVM options
        });
    }

    @SuppressWarnings("all")
    public <P extends S2CPacket> void onPacket(final Class<P> type, final BiConsumer<Channel, P> handler) {
        if (!packetHandlers.containsKey(type)) {
            packetHandlers.put(type, (BiConsumer<Channel, S2CPacket>) handler);
            return;
        }
        final var oldHandler = packetHandlers.get(type);
        packetHandlers.put(type, (channel, packet) -> {
            oldHandler.accept(channel, packet);
            handler.accept(channel, (P) packet);
        });
    }

    public <P extends S2CPacket & TargetedPacket> void onTargetedPacket(final Class<P> type,
                                                                        final BiConsumer<Channel, P> handler) {
        onPacket(type, (channel, packet) -> {
            if (!packet.getClientId().equals(id)) {
                return;
            }
            handler.accept(channel, packet);
        });
    }

    private Map<String, String> getJvmOptions() { // @formatter:off
        return System.getProperties().entrySet()
            .stream()
            .map(entry -> new Pair<>(entry.getKey().toString(), entry.getValue().toString()))
            .collect(Collectors.toMap(Pair::left, Pair::right));
    } // @formatter:on

    public void sendConnectPacket(final Map<String, String> agentOptions) {
        final var processId = ProcessHandle.current().pid();
        final var jvmVendor = System.getProperty("java.vm.vendor");
        final var jvmName = System.getProperty("java.vm.name");
        final var jvmVersion = System.getProperty("java.vm.version");
        sendPacket(new C2SConnectPacket(id,
            Instant.now(),
            processId,
            jvmVendor,
            jvmName,
            jvmVersion,
            getJvmOptions(),
            agentOptions));
    }

    public void sendPacket(final C2SPacket packet) {
        if (!isConnected()) {
            return;
        }
        channel.writeAndFlush(packet);
    }

    public void sendExceptionPacket(final Throwable error) {
        // @formatter:off
        final var stackTrace = Arrays.stream(error.getStackTrace())
            .map(StackTraceElement::toString)
            .toList();
        // @formatter:on
        sendPacket(new C2SExceptionPacket(id, Instant.now(), error.getLocalizedMessage(), stackTrace));
    }

    public void sendUpdateJvmOptionsPacket() {
        sendPacket(new C2SUpdateJvmOptionsPacket(id, Instant.now(), getJvmOptions()));
    }

    public boolean isConnected() {
        return channel != null && channel.isActive();
    }

    public void onConnect(final Runnable handler) {
        final var oldHandler = connectHandler.get();
        connectHandler.set(() -> {
            oldHandler.run();
            handler.run();
        });
    }

    public void onDisconnect(final Runnable handler) {
        final var oldHandler = disconnectHandler.get();
        disconnectHandler.set(() -> {
            oldHandler.run();
            handler.run();
        });
    }

    public void onError(final Consumer<Throwable> handler) {
        final var oldHandler = errorHandler.get();
        errorHandler.set(error -> {
            oldHandler.accept(error);
            handler.accept(error);
        });
    }

    public boolean tryConnect(final int port, final Map<String, String> agentOptions) {
        var attempts = 0;
        while (attempts < 2) {
            try {
                eventLoopGroup = new NioEventLoopGroup();
                // @formatter:off
                final var bootstrap = new Bootstrap()
                    .group(eventLoopGroup)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(final SocketChannel channel) {
                            final var pipeline = channel.pipeline();
                            PacketCodecs.configurePipeline(pipeline);
                            pipeline.addLast(new ChannelInboundHandler());
                        }
                    })
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 100)
                    .option(ChannelOption.SO_KEEPALIVE, true);
                // @formatter:on
                channel = bootstrap.connect("localhost", port).sync().channel();
                sendConnectPacket(agentOptions);
                return true;
            }
            catch (Throwable error) {
                errorHandler.get().accept(error);
                attempts++;
            }
        }
        return false;
    }

    @Override
    public void close() {
        if (channel != null) {
            channel.close().syncUninterruptibly();
            channel = null;
        }
        if (eventLoopGroup != null) {
            eventLoopGroup.shutdownGracefully();
            eventLoopGroup = null;
        }
    }

    @Sharable
    private final class ChannelInboundHandler extends SimpleChannelInboundHandler<S2CPacket> {
        ChannelInboundHandler() {
            super(S2CPacket.class);
        }

        @Override
        protected void messageReceived(final ChannelHandlerContext ctx, final S2CPacket msg) {
            final var packetType = msg.getClass();
            final var handler = packetHandlers.get(packetType);
            if (handler == null) {
                return;
            }
            localLogger.debug("Received %s: %s", packetType, msg);
            handler.accept(ctx.channel(), msg);
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            super.channelActive(ctx);
            connectHandler.get().run();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            super.channelInactive(ctx);
            disconnectHandler.get().run();
        }
    }
}
