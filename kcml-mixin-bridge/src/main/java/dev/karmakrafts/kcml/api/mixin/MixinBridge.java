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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A dependency-less runtime bridge to pass values into the agent directly.
 * Can be used for emplacing Mixin constant table values for example.
 */
public final class MixinBridge {
    public static final MixinBridge INSTANCE = new MixinBridge();

    // @formatter:off
    private MixinBridge() {}
    // @formatter:on

    private final ConcurrentHashMap<String, Object> constantTable = new ConcurrentHashMap<>();

    public Map<String, Object> getConstantTable() {
        return constantTable;
    }

    public void putConstant(final String name, final Object value) {
        if (constantTable.containsKey(name)) {
            throw new IllegalArgumentException(String.format("Mixin constant %s is already defined", name));
        }
        constantTable.put(name, value);
    }
}
