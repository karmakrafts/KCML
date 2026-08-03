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

package dev.karmakrafts.kcml.backend

import dev.karmakrafts.kcml.api.backend.NativeCodeGenerator
import dev.karmakrafts.kcml.hooks.CodeGeneratorVisitorView
import kotlinx.cinterop.ExperimentalForeignApi
import llvm.LLVMBuilderRef
import org.jetbrains.kotlin.backend.konan.llvm.LlvmCallable
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction

@OptIn(ExperimentalForeignApi::class)
internal class NativeCodeGeneratorImpl( // @formatter:off
    private val tryMaterializeFunctionCallback: (IrSimpleFunction) -> LlvmCallable?,
    private val functionBuilderGetter: () -> LLVMBuilderRef
) : NativeCodeGenerator { // @formatter:on
    companion object {
        fun fromView(view: CodeGeneratorVisitorView): NativeCodeGeneratorImpl = NativeCodeGeneratorImpl(
            tryMaterializeFunctionCallback = view.codeGenerator.tryMaterializeFunctionCallback,
            functionBuilderGetter = { view.functionGenContextGetter().builderGetter() })
    }

    override val functionBuilder: LLVMBuilderRef
        get() = functionBuilderGetter()

    override fun tryMaterializeFunction(function: IrSimpleFunction): LlvmCallable? {
        return tryMaterializeFunctionCallback(function)
    }
}