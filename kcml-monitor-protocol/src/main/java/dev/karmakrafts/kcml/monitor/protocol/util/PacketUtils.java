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

package dev.karmakrafts.kcml.monitor.protocol.util;

import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;

public final class PacketUtils {
    private PacketUtils() {
    }

    public static void putBytes(final ByteBuf buffer, final byte[] bytes) {
        buffer.writeInt(bytes.length);
        buffer.writeBytes(bytes);
    }

    public static void putStringUtf8(final ByteBuf buffer, final String value) {
        putBytes(buffer, value.getBytes(StandardCharsets.UTF_8));
    }

    public static void putInstant(final ByteBuf buffer, final Instant instant) {
        buffer.writeLong(instant.toEpochMilli());
    }

    public static Instant getInstant(final ByteBuf buffer) {
        return Instant.ofEpochMilli(buffer.readLong());
    }

    public static void putUUID(final ByteBuf buffer, final UUID uuid) {
        buffer.writeLong(uuid.getMostSignificantBits());
        buffer.writeLong(uuid.getLeastSignificantBits());
    }

    public static void putEnum(final ByteBuf buffer, final Enum<?> value) {
        buffer.writeInt(value.ordinal());
    }

    public static <T> void putList(final ByteBuf buffer,
                                   final BiConsumer<ByteBuf, T> serializer,
                                   final List<T> values) {
        buffer.writeInt(values.size());
        for (final var value : values) {
            serializer.accept(buffer, value);
        }
    }

    public static <K, V> void putMap(final ByteBuf buffer,
                                     final BiConsumer<ByteBuf, K> keySerializer,
                                     final BiConsumer<ByteBuf, V> valueSerializer,
                                     final Map<? extends K, ? extends V> map) {
        buffer.writeInt(map.size());
        for (final var entry : map.entrySet()) {
            keySerializer.accept(buffer, entry.getKey());
            valueSerializer.accept(buffer, entry.getValue());
        }
    }

    public static void putPath(final ByteBuf buffer, final Path path) {
        putStringUtf8(buffer, path.toString());
    }

    public static byte[] getBytes(final ByteBuf buffer) {
        final var size = buffer.readInt();
        final var bytes = new byte[size];
        buffer.readBytes(bytes);
        return bytes;
    }

    public static String getStringUtf8(final ByteBuf buffer) {
        return new String(getBytes(buffer), StandardCharsets.UTF_8);
    }

    public static UUID getUUID(final ByteBuf buffer) {
        final var msb = buffer.readLong();
        final var lsb = buffer.readLong();
        return new UUID(msb, lsb);
    }

    public static <E extends Enum<E>> E getEnum(final ByteBuf buffer, final Class<E> type) {
        final var ordinal = buffer.readInt();
        return type.getEnumConstants()[ordinal];
    }

    public static <K, V> Map<K, V> getMap(final ByteBuf buffer,
                                          final Function<ByteBuf, K> keyDeserializer,
                                          final Function<ByteBuf, V> valueDeserializer) {
        final var size = buffer.readInt();
        if (size == 0) {
            return Collections.emptyMap();
        }
        final var map = new HashMap<K, V>(size);
        for (var i = 0; i < size; i++) {
            map.put(keyDeserializer.apply(buffer), valueDeserializer.apply(buffer));
        }
        return map;
    }

    public static Path getPath(final ByteBuf buffer) {
        return Path.of(getStringUtf8(buffer));
    }

    public static <T> List<T> getList(final ByteBuf buffer, final Function<ByteBuf, T> deserializer) {
        final var size = buffer.readInt();
        if (size == 0) {
            return Collections.emptyList();
        }
        final var values = new ArrayList<T>();
        for (var i = 0; i < size; i++) {
            values.add(deserializer.apply(buffer));
        }
        return values;
    }
}
