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

import dev.karmakrafts.kcml.monitor.protocol.PacketCodec;
import dev.karmakrafts.kcml.monitor.protocol.util.PacketUtils;
import io.netty.buffer.ByteBuf;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record C2SUpdateJvmOptionsPacket( // @formatter:off
    UUID clientId,
    Instant timestamp,
    Map<String, String> jvmOptions
) implements C2SPacket { // @formatter:on
    @Override
    public UUID getClientId() {
        return clientId;
    }

    @Override
    public Instant getTimestamp() {
        return timestamp;
    }

    public static final class Codec implements PacketCodec<C2SUpdateJvmOptionsPacket> {
        public static final Codec INSTANCE = new Codec();

        private Codec() {
        }

        @Override
        public void serialize(final C2SUpdateJvmOptionsPacket value, final ByteBuf buffer) {
            PacketUtils.putUUID(buffer, value.clientId);
            PacketUtils.putInstant(buffer, value.timestamp);
            PacketUtils.putMap(buffer, PacketUtils::putStringUtf8, PacketUtils::putStringUtf8, value.jvmOptions);
        }

        @Override
        public C2SUpdateJvmOptionsPacket deserialize(final ByteBuf buffer) {
            final var clientId = PacketUtils.getUUID(buffer);
            final var timestamp = PacketUtils.getInstant(buffer);
            final var jvmOptions = PacketUtils.getMap(buffer, PacketUtils::getStringUtf8, PacketUtils::getStringUtf8);
            return new C2SUpdateJvmOptionsPacket(clientId, timestamp, jvmOptions);
        }
    }
}
