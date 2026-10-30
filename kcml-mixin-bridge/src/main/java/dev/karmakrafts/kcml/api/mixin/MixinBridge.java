/*
 * Copyright 2026 Karma Krafts
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

package dev.karmakrafts.kcml.api.mixin;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A dependency-less runtime bridge to pass values into the agent directly.
 * Can be used for emplacing Mixin constant table values for example.
 */
public final class MixinBridge {
    public static final MixinBridge INSTANCE = new MixinBridge();
    private final AtomicReference<MixinSource> mixinSource = new AtomicReference<>(null);
    private final ConcurrentHashMap<MixinSource, ConcurrentHashMap<String, Object>> constantTable = new ConcurrentHashMap<>();
    // @formatter:on

    // @formatter:off
    private MixinBridge() {}

    public MixinSource getMixinSource() {
        return mixinSource.get();
    }

    public void setMixinSource(final MixinSource mixinSource) {
        this.mixinSource.set(mixinSource);
    }

    public ConcurrentHashMap<MixinSource, ConcurrentHashMap<String, Object>> getConstantTable() {
        return constantTable;
    }

    public Object getConstant(final String pluginId, final String name) {
        for (final var entry : constantTable.entrySet()) {
            final MixinSource source = entry.getKey();
            if (pluginId.equals("kcml") ? source.isBuiltin() : !source.isBuiltin() && pluginId.equals(source.getSourceName())) {
                return entry.getValue().get(name);
            }
        }
        return null;
    }

    public void putConstant(final MixinSource source, final String name, final Object value) {
        constantTable.computeIfAbsent(source, id -> new ConcurrentHashMap<>()).put(name, value);
    }

    public void putConstant(final String name, final Object value) {
        final MixinSource source = mixinSource.get();
        if (source == null) {
            throw new IllegalStateException("No mixin source is currently loading");
        }
        putConstant(source, name, value);
    }
}
