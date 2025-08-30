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

package dev.karmakrafts.kcml.agent;

import dev.karmakrafts.kcml.monitor.protocol.*;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

final class MonitorClient implements AutoCloseable {
    private static final InetSocketAddress ADDRESS = InetSocketAddress.createUnresolved("localhost", 65000);
    public static final MonitorClient INSTANCE = new MonitorClient();
    public final UUID id = UUID.randomUUID();
    private final Socket socket = new Socket();
    private final AtomicBoolean isConnecting = new AtomicBoolean(false);
    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    private final ConcurrentLinkedDeque<C2SPacket> outgoingPackets = new ConcurrentLinkedDeque<>();
    private final HashMap<Class<S2CPacket>, Consumer<S2CPacket>> packetHandlers = new HashMap<>();
    private Thread ioThread;
    private final ByteBuffer packetBuffer = ByteBuffer.allocate(100000);

    private MonitorClient() {
        onPacket(S2ConnectAckPacket.class, incomingPacket -> {
            if(!incomingPacket.clientId().equals(id)) {
                return;
            }
            isConnected.set(true); // Now we are acknowledged and we can exchange packets
        });
    }

    @SuppressWarnings("BusyWait")
    private void performIo() {
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
        outgoingPackets.addLast(packet);
    }

    public boolean tryConnect(final Map<String, String> agentOptions) {
        if (!isConnecting.compareAndSet(false, true)) {
            return false;
        }
        var failedAttempts = 0;
        while (failedAttempts < 2) {
            try {
                socket.connect(ADDRESS);
                // If connection is successful, spin up IO thread for handling packet queues
                ioThread = new Thread(this::performIo);
                ioThread.start();
                // Send connect packet to server with all static info
                sendConnectPacket(agentOptions);
                return true;
            }
            catch (Throwable error) {
                // If we can't connect, we can silently fail and assume there's no monitor running
                failedAttempts++;
            }
        }
        return false;
    }

    private void sendConnectPacket(final Map<String, String> agentOptions) {
        final var processId = ProcessHandle.current().pid();
        final var jvmInfo = String.format("%s %s %s",
            System.getProperty("java.vm.vendor"),
            System.getProperty("java.vm.name"),
            System.getProperty("java.vm.version"));
        sendPacket(new C2SConnectPacket(id, processId, jvmInfo, agentOptions));
    }

    @Override
    public void close() {
        if (!isConnecting.compareAndSet(true, false)) {
            return;
        }
        try {
            socket.close();
            ioThread.join();
        }
        catch (Throwable error) {
            // We don't need to handle this error
        }
    }
}
