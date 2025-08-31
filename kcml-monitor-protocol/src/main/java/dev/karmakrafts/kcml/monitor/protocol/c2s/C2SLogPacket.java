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

package dev.karmakrafts.kcml.monitor.protocol.c2s;

import dev.karmakrafts.kcml.monitor.protocol.MonitorLogLevel;
import dev.karmakrafts.kcml.monitor.protocol.PacketCodec;
import dev.karmakrafts.kcml.monitor.protocol.PacketUtils;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.UUID;

public record C2SLogPacket( // @formatter:off
    UUID clientId,
    Instant timestamp,
    MonitorLogLevel level,
    String message
) implements C2SPacket { // @formatter:on
    @Override
    public UUID getClientId() {
        return clientId;
    }

    @Override
    public Instant getTimestamp() {
        return timestamp;
    }

    public static final class Codec implements PacketCodec<C2SLogPacket> {
        public static final Codec INSTANCE = new Codec();

        private Codec() {
        }

        @Override
        public void serialize(final C2SLogPacket value, final ByteBuffer buffer) {
            PacketUtils.putUUID(buffer, value.clientId);
            PacketUtils.putInstant(buffer, value.timestamp);
            PacketUtils.putEnum(buffer, value.level);
            PacketUtils.putStringUtf8(buffer, value.message);
        }

        @Override
        public C2SLogPacket deserialize(final ByteBuffer buffer) {
            final var clientId = PacketUtils.getUUID(buffer);
            final var timestamp = PacketUtils.getInstant(buffer);
            final var level = PacketUtils.getEnum(buffer, MonitorLogLevel.class);
            final var message = PacketUtils.getStringUtf8(buffer);
            return new C2SLogPacket(clientId, timestamp, level, message);
        }
    }
}
