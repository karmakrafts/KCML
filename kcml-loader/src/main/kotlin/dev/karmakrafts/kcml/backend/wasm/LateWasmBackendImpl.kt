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

package dev.karmakrafts.kcml.backend.wasm

import dev.karmakrafts.kcml.api.backend.wasm.LateWasmBackend
import dev.karmakrafts.kcml.api.log.Logger
import dev.karmakrafts.kcml.api.log.LoggerFactory
import dev.karmakrafts.kcml.api.plugin.PluginLoader
import dev.karmakrafts.kcml.api.target.CompileTarget
import dev.karmakrafts.kcml.log.MessageCollectorLoggerFactory
import dev.karmakrafts.kcml.target.WasmCompileTargetImpl
import org.jetbrains.kotlin.backend.wasm.WasmBackendContext
import org.jetbrains.kotlin.backend.wasm.ir2wasm.WasmDeclarationCodegenContext
import org.jetbrains.kotlin.backend.wasm.ir2wasm.WasmTypeCodegenContext
import org.jetbrains.kotlin.config.messageCollector

internal class LateWasmBackendImpl(
    override val pluginId: String,
    override val context: WasmBackendContext,
    override val typeContext: WasmTypeCodegenContext,
    override val declarationContext: WasmDeclarationCodegenContext?,
    override val loader: PluginLoader
) : LateWasmBackend {
    override val compileTarget: CompileTarget get() = WasmCompileTargetImpl
    override val loggerFactory: LoggerFactory by lazy {
        MessageCollectorLoggerFactory(loader, context.configuration.messageCollector)
    }
    override val logger: Logger by lazy { loggerFactory.getForPlugin(pluginId) }
}