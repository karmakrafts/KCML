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
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;

public final class PacketUtils {
    private PacketUtils() {
    }

    public static void putBytes(final ByteBuffer buffer, final byte[] bytes) {
        buffer.putInt(bytes.length);
        buffer.put(bytes);
    }

    public static void putStringUtf8(final ByteBuffer buffer, final String value) {
        putBytes(buffer, value.getBytes(StandardCharsets.UTF_8));
    }

    public static void putInstant(final ByteBuffer buffer, final Instant instant) {
        buffer.putLong(instant.toEpochMilli());
    }

    public static Instant getInstant(final ByteBuffer buffer) {
        return Instant.ofEpochMilli(buffer.getLong());
    }

    public static void putUUID(final ByteBuffer buffer, final UUID uuid) {
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
    }

    public static void putEnum(final ByteBuffer buffer, final Enum<?> value) {
        buffer.putInt(value.ordinal());
    }

    public static <T> void putList(final ByteBuffer buffer,
                                   final BiConsumer<ByteBuffer, T> serializer,
                                   final List<T> values) {
        buffer.putInt(values.size());
        for (final var value : values) {
            serializer.accept(buffer, value);
        }
    }

    public static <K, V> void putMap(final ByteBuffer buffer,
                                     final BiConsumer<ByteBuffer, K> keySerializer,
                                     final BiConsumer<ByteBuffer, V> valueSerializer,
                                     final Map<? extends K, ? extends V> map) {
        buffer.putInt(map.size());
        for (final var entry : map.entrySet()) {
            keySerializer.accept(buffer, entry.getKey());
            valueSerializer.accept(buffer, entry.getValue());
        }
    }

    public static void putPath(final ByteBuffer buffer, final Path path) {
        putStringUtf8(buffer, path.toString());
    }

    public static byte[] getBytes(final ByteBuffer buffer) {
        final var size = buffer.getInt();
        final var bytes = new byte[size];
        buffer.get(bytes);
        return bytes;
    }

    public static String getStringUtf8(final ByteBuffer buffer) {
        return new String(getBytes(buffer), StandardCharsets.UTF_8);
    }

    public static UUID getUUID(final ByteBuffer buffer) {
        final var msb = buffer.getLong();
        final var lsb = buffer.getLong();
        return new UUID(msb, lsb);
    }

    public static <E extends Enum<E>> E getEnum(final ByteBuffer buffer, final Class<E> type) {
        final var ordinal = buffer.getInt();
        return type.getEnumConstants()[ordinal];
    }

    public static <K, V> Map<K, V> getMap(final ByteBuffer buffer,
                                          final Function<ByteBuffer, K> keyDeserializer,
                                          final Function<ByteBuffer, V> valueDeserializer) {
        final var size = buffer.getInt();
        if (size == 0) {
            return Collections.emptyMap();
        }
        final var map = new HashMap<K, V>(size);
        for (var i = 0; i < size; i++) {
            map.put(keyDeserializer.apply(buffer), valueDeserializer.apply(buffer));
        }
        return map;
    }

    public static Path getPath(final ByteBuffer buffer) {
        return Path.of(getStringUtf8(buffer));
    }

    public static <T> List<T> getList(final ByteBuffer buffer, final Function<ByteBuffer, T> deserializer) {
        final var size = buffer.getInt();
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
