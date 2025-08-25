/*
 * Copyright 2025 Karma Krafts & associates
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

package dev.karmakrafts.kcml.extension

import dev.karmakrafts.kcml.plugin.PluginLoader
import dev.karmakrafts.kcml.util.log
import kotlinx.cinterop.ExperimentalForeignApi
import llvm.LLVMValueRef
import org.jetbrains.kotlin.backend.konan.llvm.LlvmCallable
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.util.dump

class UnloweredLLVMIntrinsicException(message: String) : RuntimeException(message)

@OptIn(ExperimentalForeignApi::class)
interface LLVMIntrinsicsExtension {
    companion object {
        internal fun evaluateAll(
            callable: LlvmCallable,
            callSite: IrCall,
            args: List<LLVMValueRef>,
            result: LLVMValueRef?
        ): LLVMValueRef {
            PluginLoader.messageCollector.log("Evaluating LLVM intrinsics extensions")
            val pluginIds = PluginLoader.getLoadedSortedPlugins()
            for (pluginId in pluginIds) {
                val extensions = PluginLoader[pluginId]!!.llvmIntrinsicsExtensions
                for (extension in extensions) {
                    return extension.evaluate(callable, callSite, args, result) ?: continue
                }
            }
            throw UnloweredLLVMIntrinsicException("Could not lower LLVM intrinsic ${callSite.dump()}")
        }
    }

    fun evaluate(
        callable: LlvmCallable,
        callSite: IrCall,
        args: List<LLVMValueRef>,
        result: LLVMValueRef?
    ): LLVMValueRef?
}