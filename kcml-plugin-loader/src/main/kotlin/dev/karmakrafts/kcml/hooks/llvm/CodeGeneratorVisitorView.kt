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

package dev.karmakrafts.kcml.hooks.llvm

import dev.karmakrafts.kcml.api.plugin.PluginLoader
import dev.karmakrafts.kcml.hooks.ActualType
import dev.karmakrafts.kcml.util.ReflectionUtils
import kotlinx.cinterop.ExperimentalForeignApi
import llvm.LLVMValueRef
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression

@OptIn(ExperimentalForeignApi::class)
internal data class CodeGeneratorVisitorView( // @formatter:off
    val generationState: NativeGenerationStateView,
    val codeGenerator: CodeGeneratorView,
    val evaluateExplicitArgsCallback: (IrFunctionAccessExpression) -> List<LLVMValueRef>,
    val functionGenContextGetter: () -> FunctionGenerationContextView
) { // @formatter:on
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromImpl( // @formatter:off
            @ActualType("CodeGeneratorVisitor") impl: Any,
            loader: PluginLoader
        ): Result<CodeGeneratorVisitorView> = runCatching { // @formatter:on
            loader.logger.info("Creating CodeGeneratorVisitorView")
            val codegen = ReflectionUtils.getField<Any, Any>("codegen", impl)
            loader.logger.info("Got CodeGenerator reference")
            val generationState = ReflectionUtils.getField<Any, Any>("generationState", impl)
            val evaluateExplicitArgs = impl::class.java.getDeclaredMethod(
                "evaluateExplicitArgs", IrFunctionAccessExpression::class.java
            ).apply { isAccessible = true }
            CodeGeneratorVisitorView( // @formatter:off
                generationState = NativeGenerationStateView.fromImpl(generationState, loader).getOrThrow(),
                codeGenerator = CodeGeneratorView.fromImpl(codegen, loader).getOrThrow(),
                evaluateExplicitArgsCallback = { expression ->
                    evaluateExplicitArgs.invoke(impl, expression) as List<LLVMValueRef>
                },
                functionGenContextGetter = {
                    val context = ReflectionUtils.getProperty<Any, Any>("functionGenerationContext", impl)
                    loader.logger.info("Got FunctionGenerationContext reference")
                    FunctionGenerationContextView.fromImpl(context, loader).getOrThrow()
                }
            ) // @formatter:on
        }
    }
}