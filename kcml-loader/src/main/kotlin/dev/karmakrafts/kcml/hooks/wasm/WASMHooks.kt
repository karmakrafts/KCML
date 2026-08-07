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

import dev.karmakrafts.kcml.hooks.CommonHooks
import dev.karmakrafts.kcml.hooks.KCMLHookApi
import dev.karmakrafts.kcml.plugin.PluginLoaderImpl
import org.jetbrains.kotlin.backend.wasm.WasmBackendContext
import org.jetbrains.kotlin.backend.wasm.ir2wasm.BodyGenerator
import org.jetbrains.kotlin.backend.wasm.ir2wasm.WasmDeclarationCodegenContext
import org.jetbrains.kotlin.backend.wasm.ir2wasm.WasmLinkerDataCodegenContext
import org.jetbrains.kotlin.backend.wasm.ir2wasm.WasmTypeCodegenContext
import org.jetbrains.kotlin.com.google.common.collect.Sets
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression

@Suppress("UNUSED")
@KCMLHookApi
object WASMHooks {
    private val initializedInstances: MutableSet<CompilerConfiguration> = Sets.newIdentityHashSet()
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
        declarationContext: WasmDeclarationCodegenContext?,
        linkerDataContext: WasmLinkerDataCodegenContext?
    ) { // @formatter:on
        initIfNeeded(backendContext.configuration)
    }

    // BodyGenerator

    @JvmStatic
    fun onGenerateCall(call: IrFunctionAccessExpression, generator: BodyGenerator) {
        // TODO: implement this
    }
}