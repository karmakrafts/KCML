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
import dev.karmakrafts.kcml.api.mixin.returnFromTarget
import dev.karmakrafts.kcml.hooks.KCMLHookApi
import dev.karmakrafts.kcml.hooks.llvm.LLVMHooks
import kotlinx.cinterop.ExperimentalForeignApi
import llvm.LLVMValueRef
import org.jetbrains.kotlin.ir.declarations.IrAnonymousInitializer
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.objectweb.asm.Opcodes

@OptIn(ExperimentalForeignApi::class, KCMLHookApi::class)
@Suppress("UNUSED_PARAMETER")
@IndirectMixin("org.jetbrains.kotlin.backend.konan.llvm.CodeGeneratorVisitor", -999)
internal class CodeGeneratorVisitorMixin : ThisAware<Any> {
    @Inject( // @formatter:off
        name = "evaluateExpression",
        target = Target(name = "updateBuilderDebugLocation", opcode = Opcodes.INVOKESPECIAL)
    )
    fun evaluateExpression(
        @Capture value: IrExpression,
        returnContext: ReturnContext<LLVMValueRef>
    ) { // @formatter:on
        LLVMHooks.onGenerateElement(getThis(), value)?.let { result ->
            returnContext.returnFromTarget(result)
        }
    }

    @Inject(name = "visitModuleFragment", order = Inject.Order.BEFORE)
    fun visitModuleFragment(@Capture declaration: IrModuleFragment, returnContext: ReturnContext<Unit>) {
        if (LLVMHooks.onGenerateElement(getThis(), declaration) != null) returnContext.returnFromTarget()
    }

    @Inject(name = "visitFile", order = Inject.Order.BEFORE)
    fun visitFile(@Capture declaration: IrFile, returnContext: ReturnContext<Unit>) {
        if (LLVMHooks.onGenerateElement(getThis(), declaration) != null) returnContext.returnFromTarget()
    }

    @Inject(name = "visitClass", order = Inject.Order.BEFORE)
    fun visitClass(@Capture declaration: IrClass, returnContext: ReturnContext<Unit>) {
        if (LLVMHooks.onGenerateElement(getThis(), declaration) != null) returnContext.returnFromTarget()
    }

    @Inject(name = "visitSimpleFunction", order = Inject.Order.BEFORE)
    fun visitSimpleFunction(@Capture declaration: IrSimpleFunction, returnContext: ReturnContext<Unit>) {
        if (LLVMHooks.onGenerateElement(getThis(), declaration) != null) returnContext.returnFromTarget()
    }

    @Inject(name = "visitConstructor", order = Inject.Order.BEFORE)
    fun visitConstructor(@Capture declaration: IrConstructor, returnContext: ReturnContext<Unit>) {
        if (LLVMHooks.onGenerateElement(getThis(), declaration) != null) returnContext.returnFromTarget()
    }

    @Inject(name = "visitAnonymousInitializer", order = Inject.Order.BEFORE)
    fun visitAnonymousInitializer(@Capture declaration: IrAnonymousInitializer, returnContext: ReturnContext<Unit>) {
        if (LLVMHooks.onGenerateElement(getThis(), declaration) != null) returnContext.returnFromTarget()
    }

    @Inject(name = "visitProperty", order = Inject.Order.BEFORE)
    fun visitProperty(@Capture declaration: IrProperty, returnContext: ReturnContext<Unit>) {
        if (LLVMHooks.onGenerateElement(getThis(), declaration) != null) returnContext.returnFromTarget()
    }

    @Inject(name = "visitField", order = Inject.Order.BEFORE)
    fun visitField(@Capture declaration: IrField, returnContext: ReturnContext<Unit>) {
        if (LLVMHooks.onGenerateElement(getThis(), declaration) != null) returnContext.returnFromTarget()
    }

    @Inject(name = "generateVariable", order = Inject.Order.BEFORE)
    fun generateVariable(@Capture variable: IrVariable, returnContext: ReturnContext<Unit>) {
        if (LLVMHooks.onGenerateElement(getThis(), variable) != null) returnContext.returnFromTarget()
    }
}