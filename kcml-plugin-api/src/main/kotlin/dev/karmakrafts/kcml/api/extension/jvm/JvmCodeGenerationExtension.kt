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

import dev.karmakrafts.kcml.api.backend.jvm.JvmCodeGenerator
import dev.karmakrafts.kcml.api.backend.jvm.LateJvmBackend
import org.jetbrains.kotlin.backend.jvm.codegen.PromisedValue
import org.jetbrains.kotlin.ir.IrElement

/** Intercepts JVM IR as it is translated to bytecode, including nested expressions. */
interface JvmCodeGenerationExtension : LateJvmExtension {
    /** Selects elements to transform or replace using [process]. */
    fun shouldProcess(element: IrElement, backend: LateJvmBackend): Boolean = true

    /**
     * Returns null to continue dispatch and normal generation after any in-place changes.
     * A non-null value replaces normal generation and stops dispatch. Replacements must
     * preserve argument evaluation, stack types, and control-flow invariants.
     */
    fun JvmCodeGenerator.process(element: IrElement): PromisedValue?
}