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

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map.Entry;

public final class PacketCodecs {
    private static final HashMap<Class<?>, PacketCodec<?>> codecs = new HashMap<>();
    private static final HashMap<Class<?>, Integer> ids = new HashMap<>();
    private static int currentId = 0;

    static {
        // Client-to-Server
        register(C2SConnectPacket.class, C2SConnectPacket.Codec.INSTANCE);
        register(C2SLogPacket.class, C2SLogPacket.Codec.INSTANCE);
        register(C2STransformClassPacket.class, C2STransformClassPacket.Codec.INSTANCE);
        // Server-to-Client
        register(S2ConnectAckPacket.class, S2ConnectAckPacket.Codec.INSTANCE);
    }

    @SuppressWarnings("unchecked")
    public static void serialize(final Packet packet, final ByteBuffer buffer) {
        final var packetType = packet.getClass();
        final var id = ids.get(packetType);
        buffer.putInt(id);
        ((PacketCodec<Packet>) codecs.get(packetType)).serialize(packet, buffer);
    }

    @SuppressWarnings("unchecked")
    public static <T extends Packet> T deserialize(final ByteBuffer buffer) {
        final var id = buffer.getInt();
        // @formatter:off
        final var packetType = ids.entrySet().stream()
            .filter(e -> e.getValue() == id)
            .map(Entry::getKey)
            .findFirst()
            .orElseThrow();
        // @formatter:on
        return ((PacketCodec<T>) codecs.get(packetType)).deserialize(buffer);
    }

    public static <T extends Packet> void register(final Class<T> packetType, final PacketCodec<T> codec) {
        codecs.put(packetType, codec);
        ids.put(packetType, currentId++);
    }
}
