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

package dev.karmakrafts.kcml.api.extension.llvm

import dev.karmakrafts.kcml.api.backend.llvm.LateNativeBackend
import dev.karmakrafts.kcml.api.backend.llvm.NativeCodeGenerator
import kotlinx.cinterop.ExperimentalForeignApi
import llvm.LLVMValueRef
import org.jetbrains.kotlin.ir.IrElement

/**
 * Intercepts Kotlin/Native IR immediately before LLVM code generation.
 *
 * Extensions are consulted in dispatch order for declarations, local variables, and every
 * expression, including nested expressions. Children are intercepted when they are generated,
 * not in a separate traversal before their parent is generated.
 */
@OptIn(ExperimentalForeignApi::class)
public interface NativeCodeGenerationExtension : LateNativeExtension {
    /**
     * Selects elements to transform or replace using [process].
     *
     * @param element IR element about to be generated.
     * @param backend KCML context exposing the active native compilation and LLVM state.
     * @return `true` to invoke [process]; the default selects every element.
     */
    public fun shouldProcess(element: IrElement, backend: LateNativeBackend): Boolean = true

    /**
     * Transforms [element] in place or replaces its code generation.
     *
     * Return `null` to continue with subsequent extensions and then the compiler's normal
     * generation, using any changes made to [element]. A non-null value stops dispatch and
     * replaces an expression's LLVM result. For declarations and local variables, a non-null
     * value suppresses normal generation; [NativeCodeGenerator.unitInstance] can be used for this.
     *
     * The function builder is only available inside an active function. Replacements are
     * responsible for preserving argument evaluation, result types, and other LLVM invariants.
     *
     * @receiver generator for the current native code-generation context.
     * @param element IR element selected by [shouldProcess].
     * @return the replacement LLVM value, or `null` to continue normal generation.
     */
    public fun NativeCodeGenerator.process(element: IrElement): LLVMValueRef?
}