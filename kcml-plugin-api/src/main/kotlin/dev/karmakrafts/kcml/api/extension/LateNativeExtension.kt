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

package dev.karmakrafts.kcml.api.extension

import dev.karmakrafts.kcml.api.backend.LateNativeBackend

/**
 * Participates in Kotlin/Native's late LLVM code-generation phase through KCML.
 *
 * Implementations can inspect the initialized native backend state or register behavior that is
 * required while Kotlin/Native lowers IR declarations to LLVM.
 */
interface LateNativeExtension : Extension {
    /**
     * Initializes this extension for a Kotlin/Native backend invocation.
     *
     * The default implementation performs no initialization.
     *
     * @param backend KCML context exposing the active native compilation and LLVM state.
     */
    fun init(backend: LateNativeBackend) = Unit
}