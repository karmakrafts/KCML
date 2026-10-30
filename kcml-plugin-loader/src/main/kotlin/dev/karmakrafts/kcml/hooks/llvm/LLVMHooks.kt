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

import dev.karmakrafts.kcml.api.extension.llvm.NativeCodeGenerationExtension
import dev.karmakrafts.kcml.api.mixin.MixinBridge
import dev.karmakrafts.kcml.backend.llvm.LateNativeBackendImpl
import dev.karmakrafts.kcml.backend.llvm.NativeCodeGeneratorImpl
import dev.karmakrafts.kcml.hooks.ActualType
import dev.karmakrafts.kcml.hooks.KCMLHookApi
import dev.karmakrafts.kcml.plugin.PluginLoaderImpl
import kotlinx.cinterop.ExperimentalForeignApi
import llvm.LLVMValueRef
import org.jetbrains.kotlin.backend.konan.driver.NativePhaseContext
import org.jetbrains.kotlin.ir.IrElement
import kotlin.io.path.Path

@Suppress("UNUSED")
@KCMLHookApi
@OptIn(ExperimentalForeignApi::class)
object LLVMHooks {
    // CodeGeneratorVisitor

    @JvmStatic
    fun onGenerateElement( // @formatter:off
        @ActualType("CodeGeneratorVisitor") codeGeneratorVisitor: Any,
        element: IrElement
    ): LLVMValueRef? { // @formatter:on
        val extensions = PluginLoaderImpl.extensionDispatcher.lateNativeExtensions.filter { [_, extension] ->
            extension is NativeCodeGenerationExtension
        }
        if (extensions.isEmpty()) return null
        return CodeGeneratorVisitorView.fromImpl(codeGeneratorVisitor, PluginLoaderImpl)
            .fold(onSuccess = onSuccess@{ codeGeneratorVisitorView ->
                // Invoke all native code generation extensions
                val loggerFactory = codeGeneratorVisitorView.generationState.loggerFactory
                val logger = loggerFactory("KCML")
                for ([pluginId, extension] in extensions) {
                    require(extension is NativeCodeGenerationExtension)
                    val backend = LateNativeBackendImpl.fromView(
                        codeGeneratorVisitorView.generationState, pluginId, PluginLoaderImpl
                    )
                    try {
                        if (!extension.shouldProcess(element, backend)) continue
                        val result = with(extension) {
                            with(NativeCodeGeneratorImpl.fromView(codeGeneratorVisitorView, backend)) {
                                process(element)
                            }
                        }
                        if (result != null) return@onSuccess result
                    } catch (error: Throwable) {
                        logger.error("A native code generation extension from plugin '$pluginId' has caused an exception: ${error.stackTraceToString()}")
                    }
                }
                null // No extension has requested to process the element
            }, onFailure = { error ->
                error("Could not reflect CodeGeneratorVisitor in onGenerateElement: ${error.stackTraceToString()}")
            })
    }

    // TopLevelPhases

    @JvmStatic
    fun onRunAfterLowerings(@ActualType("NativeGenerationState") state: Any) {
        val config = (state as NativePhaseContext).config.configuration
        val pluginPaths = requireNotNull(MixinBridge.INSTANCE.constantTable["plugin_paths"] as? List<*>) {
            "KCML agent handshake has not supplied plugin paths"
        }
        PluginLoaderImpl.loadAndInvokeStandalone(
            config, pluginPaths.map { path -> Path(path as String).toUri().toURL() })
        NativeGenerationStateView.fromImpl(state, PluginLoaderImpl).fold(onSuccess = { stateView ->
            val logger = stateView.loggerFactory("KCML")
            logger.info("Post lowering stage")
            // Invoke all late native extensions' init functions
            val extensions = PluginLoaderImpl.extensionDispatcher.lateNativeExtensions
            for ([pluginId, extension] in extensions) {
                val backend = LateNativeBackendImpl.fromView(stateView, pluginId, PluginLoaderImpl)
                try {
                    extension.init(backend)
                } catch (error: Throwable) {
                    logger.error("A late extension from plugin '$pluginId' has caused an exception: ${error.stackTraceToString()}")
                }
            }
        }, onFailure = { error ->
            error("Could not reflect NativeGenerationState in onRunAfterLowerings: ${error.stackTraceToString()}")
        })
    }
}