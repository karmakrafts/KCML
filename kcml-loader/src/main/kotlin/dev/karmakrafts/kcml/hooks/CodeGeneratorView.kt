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

package dev.karmakrafts.kcml.hooks

import dev.karmakrafts.kcml.api.plugin.PluginLoader
import org.jetbrains.kotlin.backend.konan.llvm.LlvmCallable
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction

internal data class CodeGeneratorView( // @formatter:off
    val tryMaterializeFunctionCallback: (IrSimpleFunction) -> LlvmCallable?
) {
    // @formatter:on
    companion object {
        fun fromImpl( // @formatter:off
            @ActualType("CodeGenerator") impl: Any,
            loader: PluginLoader
        ): Result<CodeGeneratorView> = runCatching { // @formatter:on
            loader.logger.info("Creating CodeGeneratorView")
            val type = impl::class.java
            val tryMaterializeFunction = type.declaredMethods.first { method -> method.name == "llvmFunctionOrNull" }
            loader.logger.info("Got llvmFunctionOrNull method reference")
            CodeGeneratorView( // @formatter:off
                tryMaterializeFunctionCallback = { function ->
                    tryMaterializeFunction.invoke(impl, function) as? LlvmCallable
                }
            ) // @formatter:on
        }
    }
}