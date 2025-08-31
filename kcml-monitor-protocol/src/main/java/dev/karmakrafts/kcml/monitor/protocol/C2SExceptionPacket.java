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
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record C2SExceptionPacket( // @formatter:off
    UUID clientId,
    Instant timestamp,
    String message,
    List<String> stackTrace
) implements C2SPacket { // @formatter:on
    @Override
    public UUID getClientId() {
        return clientId;
    }

    @Override
    public Instant getTimestamp() {
        return timestamp;
    }

    public static final class Codec implements PacketCodec<C2SExceptionPacket> {
        public static final Codec INSTANCE = new Codec();

        private Codec() {
        }

        @Override
        public void serialize(final C2SExceptionPacket value, final ByteBuffer buffer) {
            PacketUtils.putUUID(buffer, value.clientId);
            PacketUtils.putInstant(buffer, value.timestamp);
            PacketUtils.putStringUtf8(buffer, value.message);
            PacketUtils.putList(buffer, PacketUtils::putStringUtf8, value.stackTrace);
        }

        @Override
        public C2SExceptionPacket deserialize(final ByteBuffer buffer) {
            final var clientId = PacketUtils.getUUID(buffer);
            final var timestamp = PacketUtils.getInstant(buffer);
            final String message = PacketUtils.getStringUtf8(buffer);
            final var stackTrace = PacketUtils.getList(buffer, PacketUtils::getStringUtf8);
            return new C2SExceptionPacket(clientId, timestamp, message, stackTrace);
        }
    }
}
