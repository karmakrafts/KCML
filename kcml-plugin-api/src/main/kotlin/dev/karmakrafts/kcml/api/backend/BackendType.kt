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

package dev.karmakrafts.kcml.api.backend

/** Identifies the Kotlin compiler backend phase in which a KCML extension is executing. */
public interface BackendType {
    // @formatter:off
    /** Kotlin/JVM IR backend. */
    public data object Jvm : BackendType

    /** Late Kotlin/JVM bytecode-generation phase. */
    public data object LateJvm : BackendType

    /** Kotlin/JavaScript IR backend. */
    public data object Js : BackendType

    /** Kotlin/Wasm IR backend. */
    public data object Wasm : BackendType

    /** Late Kotlin/Wasm code-generation phase. */
    public data object LateWasm : BackendType

    /** Kotlin/Native IR backend. */
    public data object Native : BackendType

    /** Late Kotlin/Native LLVM code-generation phase. */
    public data object LateNative : BackendType
    // @formatter:on
}