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

internal data class CodeGeneratorVisitorView( // @formatter:off
    val generationState: NativeGenerationStateView,
    val codeGenerator: CodeGeneratorView,
    val functionGenContextGetter: () -> FunctionGenerationContextView
) { // @formatter:on
    companion object {
        fun fromImpl( // @formatter:off
            @ActualType("CodeGeneratorVisitor") impl: Any,
            loader: PluginLoader
        ): Result<CodeGeneratorVisitorView> = runCatching { // @formatter:on
            loader.logger.info("Creating CodeGeneratorVisitorView")
            val codegen = ReflectionUtils.getField<Any, Any>("codegen", impl)
            loader.logger.info("Got CodeGenerator reference")
            val generationState = ReflectionUtils.getField<Any, Any>("generationState", impl)
            CodeGeneratorVisitorView( // @formatter:off
                generationState = NativeGenerationStateView.fromImpl(generationState, loader).getOrThrow(),
                codeGenerator = CodeGeneratorView.fromImpl(codegen, loader).getOrThrow(),
                functionGenContextGetter = {
                    val context = ReflectionUtils.getProperty<Any, Any>("functionGenerationContext", impl)
                    loader.logger.info("Got FunctionGenerationContext reference")
                    FunctionGenerationContextView.fromImpl(context, loader).getOrThrow()
                }
            ) // @formatter:on
        }
    }
}