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

package dev.karmakrafts.kcml.agent.client;

import dev.karmakrafts.kcml.monitor.protocol.*;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class MonitorClient implements AutoCloseable {
    private static final InetSocketAddress ADDRESS = InetSocketAddress.createUnresolved("localhost", 65000);
    public final UUID id = UUID.randomUUID();
    public final RemoteLogger logger = new RemoteLogger(this);

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Socket socket = new Socket();
    private final AtomicBoolean isConnecting = new AtomicBoolean(false);
    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    private final ConcurrentLinkedDeque<C2SPacket> outgoingPackets = new ConcurrentLinkedDeque<>();
    private final HashMap<Class<S2CPacket>, Consumer<S2CPacket>> packetHandlers = new HashMap<>();
    private final ByteBuffer packetBuffer = ByteBuffer.allocate(100000).order(ByteOrder.nativeOrder());
    private CompletableFuture<Void> ioTask;

    public MonitorClient() {
        onPacket(S2CConnectAckPacket.class, incomingPacket -> {
            if (!incomingPacket.clientId().equals(id)) {
                return;
            }
            isConnected.set(true); // Now we are acknowledged and we can exchange packets
        });
        // When the server shuts down, we also terminate gracefully
        onPacket(S2CShutdownPacket.class, incomingPacket -> close());
    }

    @SuppressWarnings("BusyWait")
    private void handleIo() {
        try {
            final var outputStream = socket.getOutputStream();
            final var outputChannel = Channels.newChannel(outputStream);
            final var inputStream = socket.getInputStream();
            final var inputChannel = Channels.newChannel(inputStream);
            while (socket.isConnected()) {
                // First process all queued up outgoing packets..
                while (socket.isConnected() && !outgoingPackets.isEmpty()) {
                    final var packet = outgoingPackets.removeFirst();
                    packetBuffer.clear();
                    PacketCodecs.serialize(packet, packetBuffer);
                    packetBuffer.flip();
                    outputChannel.write(packetBuffer);
                }
                // ..then process all incoming packets until the current socket buffer is empty
                while (true) {
                    packetBuffer.clear();
                    if (inputChannel.read(packetBuffer) <= 0) {
                        break;
                    }
                    packetBuffer.flip();
                    while (packetBuffer.hasRemaining()) {
                        final var packet = PacketCodecs.deserialize(packetBuffer);
                        if (!(packet instanceof S2CPacket serverPacket))
                            continue;
                        packetHandlers.get(packet.getClass()).accept(serverPacket);
                    }
                }
                Thread.sleep(10);
            }
        }
        catch (Throwable error) {
            // If an error happens in the IO processing logic, terminate connection
            close();
        }
    }

    @SuppressWarnings("unchecked")
    public <P extends S2CPacket> void onPacket(final Class<P> packetType, final Consumer<P> handler) {
        if (packetHandlers.containsKey(packetType)) {
            throw new IllegalArgumentException(String.format("Packet handler for packet type %s already exists",
                packetType));
        }
        packetHandlers.put((Class<S2CPacket>) packetType, (Consumer<S2CPacket>) handler);
    }

    public void sendPacket(final C2SPacket packet) {
        if (!isConnected.get()) {
            return; // Can't send packets if we're not connected
        }
        outgoingPackets.addLast(packet);
    }

    public void handleException(final Throwable error) {
        // @formatter:off
        final var stackTrace = Arrays.stream(error.getStackTrace())
            .map(StackTraceElement::toString)
            .toList();
        // @formatter:on
        sendPacket(new C2SExceptionPacket(id, Instant.now(), error.getMessage(), stackTrace));
    }

    public void tryConnect(final Map<String, String> agentOptions) {
        if (!isConnecting.compareAndSet(false, true)) {
            return;
        }
        var failedAttempts = 0;
        while (failedAttempts < 2) {
            try {
                socket.connect(ADDRESS);
            }
            catch (Throwable error) {
                // If we can't connect, we can silently fail and assume there's no monitor running
                failedAttempts++;
                continue;
            }
            // If connection is successful, spin up IO thread for handling packet queues
            ioTask = CompletableFuture.runAsync(this::handleIo);
            // Send connect packet to server with all static info
            sendConnectPacket(agentOptions);
            return;
        }
    }

    private void sendConnectPacket(final Map<String, String> agentOptions) {
        final var processId = ProcessHandle.current().pid();
        final var jvmVendor = System.getProperty("java.vm.vendor");
        final var jvmName = System.getProperty("java.vm.name");
        final var jvmVersion = System.getProperty("java.vm.version");
        sendPacket(new C2SConnectPacket(id, Instant.now(), processId, jvmVendor, jvmName, jvmVersion, agentOptions));
    }

    public void sendClassTransformedPacket(final String className,
                                           final String classLoader,
                                           final byte[] originalData,
                                           final byte[] transformedData) {
        sendPacket(new C2SClassTransformedPacket(id,
            Instant.now(),
            className,
            classLoader,
            originalData,
            transformedData));
    }

    @Override
    public void close() {
        if (!isConnected.compareAndSet(true, false)) {
            return;
        }
        isConnecting.set(false); // Abort any active connection attempts
        try {
            socket.close();
            ioTask.join();
            executor.shutdown();
        }
        catch (Throwable error) {
            // We don't need to handle this error
        }
    }
}
