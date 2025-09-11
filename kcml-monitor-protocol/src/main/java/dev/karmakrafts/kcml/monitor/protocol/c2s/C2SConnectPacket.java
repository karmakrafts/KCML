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

public record C2SConnectPacket( // @formatter:off
    UUID clientId,
    Instant timestamp,
    long processId,
    String jvmVendor,
    String jvmName,
    String jvmVersion,
    Map<String, String> jvmOptions,
    Map<String, String> agentOptions
) implements C2SPacket { // @formatter:on
    @Override
    public UUID getClientId() {
        return clientId;
    }

    @Override
    public Instant getTimestamp() {
        return timestamp;
    }

    public static final class Codec implements PacketCodec<C2SConnectPacket> {
        public static final Codec INSTANCE = new Codec();

        private Codec() {
        }

        @Override
        public void serialize(final C2SConnectPacket value, final ByteBuf buffer) {
            PacketUtils.putUUID(buffer, value.clientId);
            PacketUtils.putInstant(buffer, value.timestamp);
            buffer.writeLong(value.processId);
            PacketUtils.putStringUtf8(buffer, value.jvmVendor);
            PacketUtils.putStringUtf8(buffer, value.jvmName);
            PacketUtils.putStringUtf8(buffer, value.jvmVersion);
            PacketUtils.putMap(buffer, PacketUtils::putStringUtf8, PacketUtils::putStringUtf8, value.jvmOptions);
            PacketUtils.putMap(buffer, PacketUtils::putStringUtf8, PacketUtils::putStringUtf8, value.agentOptions);
        }

        @Override
        public C2SConnectPacket deserialize(final ByteBuf buffer) {
            final var uuid = PacketUtils.getUUID(buffer);
            final var timestamp = PacketUtils.getInstant(buffer);
            final var processId = buffer.readLong();
            final var jvmVendor = PacketUtils.getStringUtf8(buffer);
            final var jvmName = PacketUtils.getStringUtf8(buffer);
            final var jvmVersion = PacketUtils.getStringUtf8(buffer);
            final var jvmOptions = PacketUtils.getMap(buffer, PacketUtils::getStringUtf8, PacketUtils::getStringUtf8);
            final var agentOptions = PacketUtils.getMap(buffer, PacketUtils::getStringUtf8, PacketUtils::getStringUtf8);
            return new C2SConnectPacket(uuid,
                timestamp,
                processId,
                jvmVendor,
                jvmName,
                jvmVersion,
                jvmOptions,
                agentOptions);
        }
    }
}
