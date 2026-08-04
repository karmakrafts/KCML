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

package dev.karmakrafts.kcml.api.backend.llvm

import dev.karmakrafts.kcml.api.backend.BackendType
import dev.karmakrafts.kcml.api.backend.IrBackend
import dev.karmakrafts.kcml.api.target.NativeCompileTarget

/**
 * Marks a [IrBackend] context executing Kotlin's native IR backend.
 *
 * This specialization allows a KCML extension to run only for native compilations while using the
 * target-independent services inherited from [IrBackend].
 */
interface NativeBackend : IrBackend {
    /** Identifies this context as the Kotlin/Native IR backend. */
    override val type: BackendType
        get() = BackendType.Native

    /** Native compilation target processed by this backend invocation. */
    override val compileTarget: NativeCompileTarget
}