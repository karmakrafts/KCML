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

import dev.karmakrafts.kcml.api.extension.NativeIntrinsicsExtension
import dev.karmakrafts.kcml.backend.LateNativeBackendImpl
import dev.karmakrafts.kcml.plugin.PluginLoaderImpl
import dev.karmakrafts.kcml.util.ReflectionUtils
import kotlinx.cinterop.ExperimentalForeignApi
import llvm.LLVMValueRef
import org.jetbrains.kotlin.ir.expressions.IrCall

@Suppress("UNUSED")
@KCMLHookApi
@OptIn(ExperimentalForeignApi::class)
object KCMLHooks {
    // CodeGeneratorVisitor

    @JvmStatic
    fun onEvaluateFunctionCall( // @formatter:off
        @ActualType("CodeGeneratorVisitor") codeGeneratorVisitor: Any,
        call: IrCall,
        args: List<LLVMValueRef>
    ): LLVMValueRef? { // @formatter:on
        val state = ReflectionUtils.getField<Any, Any>("generationState", codeGeneratorVisitor)
        return NativeGenerationStateView.fromImplementation(state, PluginLoaderImpl).fold(
            onSuccess = onSuccess@{ stateView ->
                stateView.loggerFactory("KCML").info("Code generation visitor stage")
                // Invoke all native intrinsic extensions
                val extensions =
                    PluginLoaderImpl.extensionDispatcher.lateNativeExtensions.filter { (_, extension) -> extension is NativeIntrinsicsExtension }
                for ((pluginId, extension) in extensions) {
                    require(extension is NativeIntrinsicsExtension)
                    val backend = LateNativeBackendImpl.fromStateView(stateView, pluginId, PluginLoaderImpl)
                    try {
                        if (!extension.shouldProcess(call, backend)) continue
                        return@onSuccess extension.process(call, args, backend)
                    } catch (error: Throwable) {
                        error("A native intrinsic extension from plugin '$pluginId' has caused an exception: $error")
                    }
                }
                null // No extension has requested to process the call
            }, onFailure = ::error
        )
    }

    // TopLevelPhases

    @JvmStatic
    fun onRunAfterLowerings(@ActualType("NativeGenerationState") state: Any) {
        NativeGenerationStateView.fromImplementation(state, PluginLoaderImpl).fold(
            onSuccess = { stateView ->
                stateView.loggerFactory("KCML").info("Post lowering stage")
                // Invoke all late native extensions' init functions
                val extensions = PluginLoaderImpl.extensionDispatcher.lateNativeExtensions
                for ((pluginId, extension) in extensions) {
                    val backend = LateNativeBackendImpl.fromStateView(stateView, pluginId, PluginLoaderImpl)
                    try {
                        extension.init(backend)
                    } catch (error: Throwable) {
                        error("A late extension from plugin '$pluginId' has caused an exception: $error")
                    }
                }
            }, onFailure = ::error
        )
    }
}