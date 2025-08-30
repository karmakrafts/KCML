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
import java.util.Map;
import java.util.UUID;

/**
 * Sent from the client to the server upon initial successful connection.
 * This is what makes a client appear in the monitor.
 */
public record C2SConnectPacket( // @formatter:off
    UUID clientId,
    long processId,
    String jvmInfo,
    Map<String, String> agentOptions
) implements C2SPacket { // @formatter:on
    public static final class Codec implements PacketCodec<C2SConnectPacket> {
        public static final Codec INSTANCE = new Codec();

        private Codec() {
        }

        @Override
        public void serialize(final C2SConnectPacket value, final ByteBuffer buffer) {
            PacketUtils.putUUID(buffer, value.clientId);
            buffer.putLong(value.processId);
            PacketUtils.putStringUtf8(buffer, value.jvmInfo);
            PacketUtils.putMap(buffer, PacketUtils::putStringUtf8, PacketUtils::putStringUtf8, value.agentOptions);
        }

        @Override
        public C2SConnectPacket deserialize(final ByteBuffer buffer) {
            final var uuid = PacketUtils.getUUID(buffer);
            final var processId = buffer.getLong();
            final var jvmInfo = PacketUtils.getStringUtf8(buffer);
            final var agentOptions = PacketUtils.getMap(buffer, PacketUtils::getStringUtf8, PacketUtils::getStringUtf8);
            return new C2SConnectPacket(uuid, processId, jvmInfo, agentOptions);
        }
    }

    @Override
    public UUID getClientId() {
        return clientId;
    }
}
