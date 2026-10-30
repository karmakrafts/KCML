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

import dev.karmakrafts.kcml.api.backend.wasm.LateWasmBackend
import dev.karmakrafts.kcml.api.backend.wasm.WasmCodeGenerator
import dev.karmakrafts.kcml.api.extension.wasm.WasmCodeGenerationExtension
import dev.karmakrafts.kcml.api.mixin.MixinBridge
import dev.karmakrafts.kcml.backend.wasm.LateWasmBackendImpl
import dev.karmakrafts.kcml.backend.wasm.WasmCodeGeneratorImpl
import dev.karmakrafts.kcml.hooks.KCMLHookApi
import dev.karmakrafts.kcml.plugin.PluginLoaderImpl
import dev.karmakrafts.kcml.util.ReflectionUtils
import org.jetbrains.kotlin.backend.wasm.WasmBackendContext
import org.jetbrains.kotlin.backend.wasm.ir2wasm.BodyGenerator
import org.jetbrains.kotlin.backend.wasm.ir2wasm.IrFileToWasmIrGenerator
import org.jetbrains.kotlin.backend.wasm.ir2wasm.WasmDeclarationCodegenContext
import org.jetbrains.kotlin.backend.wasm.ir2wasm.WasmTypeCodegenContext
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.wasm.ir.WasmExpressionBuilder
import java.lang.ref.WeakReference
import java.util.*
import kotlin.io.path.Path

@Suppress("UNUSED")
@KCMLHookApi
object WASMHooks {
    private val initializedInstances: MutableSet<CompilerConfiguration> = Collections.newSetFromMap(IdentityHashMap())
    private val initializationLock: Any = Any()
    private val typeContexts: MutableMap<WasmDeclarationCodegenContext, WeakReference<WasmTypeCodegenContext>> =
        Collections.synchronizedMap(WeakHashMap())

    // Function constants already pass through onGenerateElement before generateConstExpression.
    private val functionBodies: MutableMap<WasmExpressionBuilder, Boolean> = Collections.synchronizedMap(WeakHashMap())

    private fun initIfNeeded(config: CompilerConfiguration) = synchronized(initializationLock) {
        if (config in initializedInstances) return@synchronized
        val pluginPaths = requireNotNull(MixinBridge.INSTANCE.getConstant("kcml", "plugin_paths") as? List<*>) {
            "KCML agent handshake has not supplied plugin paths"
        }
        PluginLoaderImpl.loadAndInvokeStandalone(
            config, pluginPaths.map { path -> Path(path as String).toUri().toURL() })
        initializedInstances += config
    }

    // WasmModuleFragmentGeneratorKt

    @JvmStatic
    fun onCompileIrFiles( // @formatter:off
        file: IrFile,
        backendContext: WasmBackendContext,
        typeContext: WasmTypeCodegenContext,
        declarationContext: WasmDeclarationCodegenContext?
    ): Boolean { // @formatter:on
        initIfNeeded(backendContext.configuration)
        if (declarationContext != null) typeContexts[declarationContext] = WeakReference(typeContext)
        val extensions = PluginLoaderImpl.extensionDispatcher.lateWasmExtensions
        for ([pluginId, extension] in extensions) {
            val backend = LateWasmBackendImpl(
                pluginId = pluginId,
                context = backendContext,
                typeContext = typeContext,
                declarationContext = declarationContext,
                loader = PluginLoaderImpl
            )
            extension.init(backend)
        }
        return dispatch(file, backendContext, typeContext, declarationContext)
    }

    // BodyGenerator

    @JvmStatic
    fun onGenerateElement(element: IrElement, generator: BodyGenerator): Boolean {
        return hasCodeGenerationExtensions() && BodyGeneratorView.fromImpl(generator)
            .fold(onSuccess = { generatorView ->
                functionBodies[generatorView.body] = true
                dispatch(
                    element,
                    generatorView.backendContext,
                    generatorView.typeCodegenContext,
                    generatorView.declarationCodegenContext
                ) { backend -> WasmCodeGeneratorImpl.fromView(backend, generatorView) }
            }, onFailure = { error ->
                error("Could not reflect BodyGenerator in onGenerateElement: ${error.stackTraceToString()}")
            })
    }

    @JvmStatic
    fun onGenerateDeclaration(element: IrElement, generator: IrFileToWasmIrGenerator): Boolean {
        if (!hasCodeGenerationExtensions()) return false
        val typeContext = ReflectionUtils.getField<Any, WasmTypeCodegenContext>(
            "typeCodegenContext", generator.typeGenerator
        )
        val declarationContext = generator.declarationGenerator?.let { declarations ->
            ReflectionUtils.getField<Any, WasmDeclarationCodegenContext>("declarationCodegenContext", declarations)
        }
        return dispatch(element, generator.backendContext, typeContext, declarationContext)
    }

    @JvmStatic
    fun onGenerateConstant(
        expression: IrConst,
        body: WasmExpressionBuilder,
        backendContext: WasmBackendContext,
        declarationContext: WasmDeclarationCodegenContext
    ): Boolean {
        if (!hasCodeGenerationExtensions() || functionBodies.containsKey(body)) return false
        val typeContext = checkNotNull(typeContexts[declarationContext]?.get()) {
            "No WebAssembly type context for constant initializer"
        }
        return dispatch(expression, backendContext, typeContext, declarationContext) { backend ->
            WasmCodeGeneratorImpl(backend = backend, body = body)
        }
    }

    private fun hasCodeGenerationExtensions(): Boolean {
        return PluginLoaderImpl.extensionDispatcher.lateWasmExtensions.any { [_, extension] ->
            extension is WasmCodeGenerationExtension
        }
    }

    private fun dispatch(
        element: IrElement,
        backendContext: WasmBackendContext,
        typeContext: WasmTypeCodegenContext,
        declarationContext: WasmDeclarationCodegenContext?,
        generatorFactory: (LateWasmBackend) -> WasmCodeGenerator = { backend -> WasmCodeGeneratorImpl(backend) }
    ): Boolean {
        for ([pluginId, extension] in PluginLoaderImpl.extensionDispatcher.lateWasmExtensions) {
            if (extension !is WasmCodeGenerationExtension) continue
            val backend = LateWasmBackendImpl(
                pluginId = pluginId,
                context = backendContext,
                typeContext = typeContext,
                declarationContext = declarationContext,
                loader = PluginLoaderImpl
            )
            try {
                if (!extension.shouldProcess(element, backend)) continue
                val handled = with(extension) { generatorFactory(backend).process(element) }
                if (handled) return true
            } catch (error: Throwable) {
                PluginLoaderImpl.logger.error("A WASM code generation extension from plugin '$pluginId' has caused an exception: ${error.stackTraceToString()}")
            }
        }
        return false
    }
}