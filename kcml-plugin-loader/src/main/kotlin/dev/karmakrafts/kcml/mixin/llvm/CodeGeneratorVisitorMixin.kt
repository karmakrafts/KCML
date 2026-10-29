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

package dev.karmakrafts.kcml.mixin.llvm

import dev.karmakrafts.kcml.api.mixin.Capture
import dev.karmakrafts.kcml.api.mixin.IndirectMixin
import dev.karmakrafts.kcml.api.mixin.Inject
import dev.karmakrafts.kcml.api.mixin.Inject.Target
import dev.karmakrafts.kcml.api.mixin.ReturnContext
import dev.karmakrafts.kcml.api.mixin.ThisAware
import dev.karmakrafts.kcml.hooks.KCMLHookApi
import dev.karmakrafts.kcml.hooks.llvm.LLVMHooks
import kotlinx.cinterop.ExperimentalForeignApi
import llvm.LLVMValueRef
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.objectweb.asm.Opcodes

@OptIn(ExperimentalForeignApi::class, KCMLHookApi::class)
@Suppress("UNUSED_PARAMETER")
@IndirectMixin("org.jetbrains.kotlin.backend.konan.llvm.CodeGeneratorVisitor", -999)
internal class CodeGeneratorVisitorMixin : ThisAware<Any> {
    @Inject( // @formatter:off
        name = "evaluateFunctionCall",
        target = Target(opcode = Opcodes.NOP)
    )
    fun evaluateFunctionCall(
        @Capture callee: IrCall,
        @Capture args: List<LLVMValueRef>,
        returnContext: ReturnContext<LLVMValueRef>
    ) { // @formatter:on
        LLVMHooks.onEvaluateFunctionCall(getThis(), callee, args)?.let { result ->
            returnContext.returnFromTarget(result)
        }
    }
}