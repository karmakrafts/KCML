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
import java.util.UUID;

/**
 * Sent from client to server for each class that's instrumented by the agent.
 *
 * @param name        The internal name of the class (using / instead of .).
 * @param classLoader The name of the {@link ClassLoader} this class is being loaded by.
 * @param data        The raw byte stream of the class being instrumented.
 */
public record C2STransformClassPacket(UUID clientId, String name, String classLoader, byte[] data)
    implements C2SPacket {
    public static final class Codec implements PacketCodec<C2STransformClassPacket> {
        public static final Codec INSTANCE = new Codec();

        private Codec() {
        }

        @Override
        public void serialize(final C2STransformClassPacket value, final ByteBuffer buffer) {
            PacketUtils.putUUID(buffer, value.clientId);
            PacketUtils.putStringUtf8(buffer, value.name);
            PacketUtils.putStringUtf8(buffer, value.classLoader);
            PacketUtils.putBytes(buffer, value.data);
        }

        @Override
        public C2STransformClassPacket deserialize(final ByteBuffer buffer) {
            final var clientId = PacketUtils.getUUID(buffer);
            final var name = PacketUtils.getStringUtf8(buffer);
            final var classLoader = PacketUtils.getStringUtf8(buffer);
            final var data = PacketUtils.getBytes(buffer);
            return new C2STransformClassPacket(clientId, name, classLoader, data);
        }
    }

    @Override
    public UUID getClientId() {
        return clientId;
    }
}
