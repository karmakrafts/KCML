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

package dev.karmakrafts.kcml.hooks.wasm

import dev.karmakrafts.kcml.api.extension.wasm.WasmIntrinsicsExtension
import dev.karmakrafts.kcml.backend.wasm.LateWasmBackendImpl
import dev.karmakrafts.kcml.backend.wasm.WasmCodeGeneratorImpl
import dev.karmakrafts.kcml.hooks.CommonHooks
import dev.karmakrafts.kcml.hooks.KCMLHookApi
import dev.karmakrafts.kcml.plugin.PluginLoaderImpl
import org.jetbrains.kotlin.backend.wasm.WasmBackendContext
import org.jetbrains.kotlin.backend.wasm.ir2wasm.BodyGenerator
import org.jetbrains.kotlin.backend.wasm.ir2wasm.WasmDeclarationCodegenContext
import org.jetbrains.kotlin.backend.wasm.ir2wasm.WasmTypeCodegenContext
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.util.render
import java.util.*

@Suppress("UNUSED")
@KCMLHookApi
object WASMHooks {
    private val initializedInstances: MutableSet<CompilerConfiguration> = Collections.newSetFromMap(IdentityHashMap())
    private val initializationLock: Any = Any()

    private fun initIfNeeded(config: CompilerConfiguration) = synchronized(initializationLock) {
        if (config in initializedInstances) return@synchronized
        PluginLoaderImpl.loadAndInvokeStandalone(config, CommonHooks.compilerArguments)
        initializedInstances += config
    }

    // WasmModuleFragmentGeneratorKt

    @JvmStatic
    fun onCompileIrFiles( // @formatter:off
        file: IrFile,
        backendContext: WasmBackendContext,
        typeContext: WasmTypeCodegenContext,
        declarationContext: WasmDeclarationCodegenContext?
    ) { // @formatter:on
        initIfNeeded(backendContext.configuration)
        val extensions = PluginLoaderImpl.extensionDispatcher.lateWasmExtensions
        for ((pluginId, extension) in extensions) {
            val backend = LateWasmBackendImpl(
                pluginId = pluginId,
                context = backendContext,
                typeContext = typeContext,
                declarationContext = declarationContext,
                loader = PluginLoaderImpl
            )
            extension.init(backend)
        }
    }

    // BodyGenerator

    @JvmStatic
    fun onGenerateCall(call: IrFunctionAccessExpression, generator: BodyGenerator): Boolean {
        return BodyGeneratorView.fromImpl(generator).fold(onSuccess = onSuccess@{ generatorView ->
            // @formatter:off
            val extensions = PluginLoaderImpl.extensionDispatcher.lateWasmExtensions
                .filter { (_, extension) -> extension is WasmIntrinsicsExtension }
                .map { (pluginId, extension) -> pluginId to extension as WasmIntrinsicsExtension }
            // @formatter:on
            for ((pluginId, extension) in extensions) {
                val backend = LateWasmBackendImpl(
                    pluginId = pluginId,
                    context = generatorView.backendContext,
                    typeContext = generatorView.typeCodegenContext,
                    declarationContext = generatorView.declarationCodegenContext,
                    loader = PluginLoaderImpl
                )
                try {
                    if (!extension.shouldProcess(call, backend)) continue
                    backend.loggerFactory.getForPlugin(pluginId).info("Processing WASM intrinsic call ${call.render()}")
                    with(extension) {
                        with(WasmCodeGeneratorImpl.fromView(backend, generatorView)) {
                            process(call)
                        }
                    }
                    return@onSuccess true
                } catch (error: Throwable) {
                    PluginLoaderImpl.logger.error("A WASM intrinsic extension from plugin '$pluginId' has caused an exception: ${error.stackTraceToString()}")
                    return@onSuccess false
                }
            }
            false
        }, onFailure = { error ->
            error("Could not reflect BodyGenerator in onGenerateCall: ${error.stackTraceToString()}")
        })
    }
}