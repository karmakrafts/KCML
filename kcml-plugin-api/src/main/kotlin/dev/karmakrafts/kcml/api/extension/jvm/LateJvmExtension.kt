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

package dev.karmakrafts.kcml.api.extension.jvm

import dev.karmakrafts.kcml.api.backend.jvm.LateJvmBackend
import dev.karmakrafts.kcml.api.extension.Extension

/** Runs after JVM lowerings, before bytecode generation. */
public interface LateJvmExtension : Extension {
    /**
     * Initializes this extension for a JVM backend invocation.
     *
     * The default implementation performs no initialization.
     *
     * @param backend KCML context exposing the active JVM compilation state.
     */
    public fun init(backend: LateJvmBackend): Unit = Unit
}