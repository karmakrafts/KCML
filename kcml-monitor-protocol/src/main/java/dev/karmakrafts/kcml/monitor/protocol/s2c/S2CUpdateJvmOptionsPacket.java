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

package dev.karmakrafts.kcml.monitor.protocol.s2c;

import dev.karmakrafts.kcml.monitor.protocol.PacketCodec;
import dev.karmakrafts.kcml.monitor.protocol.TargetedPacket;
import dev.karmakrafts.kcml.monitor.protocol.util.PacketUtils;
import io.netty.buffer.ByteBuf;

import java.time.Instant;
import java.util.UUID;

public record S2CUpdateJvmOptionsPacket( // @formatter:off
    UUID clientId,
    Instant timestamp
) implements S2CPacket, TargetedPacket { // @formatter:on
    @Override
    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public UUID getClientId() {
        return clientId;
    }

    public static final class Codec implements PacketCodec<S2CUpdateJvmOptionsPacket> {
        public static final Codec INSTANCE = new Codec();

        private Codec() {
        }

        @Override
        public void serialize(final S2CUpdateJvmOptionsPacket value, final ByteBuf buffer) {
            PacketUtils.putUUID(buffer, value.clientId);
            PacketUtils.putInstant(buffer, value.timestamp);
        }

        @Override
        public S2CUpdateJvmOptionsPacket deserialize(final ByteBuf buffer) {
            final var clientId = PacketUtils.getUUID(buffer);
            final var timestamp = PacketUtils.getInstant(buffer);
            return new S2CUpdateJvmOptionsPacket(clientId, timestamp);
        }
    }
}
