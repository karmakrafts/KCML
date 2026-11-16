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

package dev.karmakrafts.kcml.backend

import dev.karmakrafts.kcml.api.backend.IrBackend
import dev.karmakrafts.kcml.api.log.Logger
import dev.karmakrafts.kcml.api.log.LoggerFactory
import dev.karmakrafts.kcml.api.plugin.PluginLoader
import dev.karmakrafts.kcml.backend.js.JsBackendImpl
import dev.karmakrafts.kcml.backend.jvm.JvmBackendImpl
import dev.karmakrafts.kcml.backend.llvm.NativeBackendImpl
import dev.karmakrafts.kcml.backend.wasm.WasmBackendImpl
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.platform.js.JsPlatforms
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.platform.konan.isNative
import org.jetbrains.kotlin.platform.wasm.isWasm

internal fun IrBackend.Companion.create( // @formatter:off
    pluginId: String,
    context: IrPluginContext,
    config: CompilerConfiguration,
    loggerFactory: LoggerFactory,
    logger: Logger,
    loader: PluginLoader
): IrBackend = when { // @formatter:on
    context.platform.isJvm() -> JvmBackendImpl(pluginId, context, config, loggerFactory, logger, loader)
    context.platform in JsPlatforms.allJsPlatforms -> JsBackendImpl(
        pluginId, context, config, loggerFactory, logger, loader
    )

    context.platform.isWasm() -> WasmBackendImpl(pluginId, context, config, loggerFactory, logger, loader)
    context.platform.isNative() -> NativeBackendImpl(pluginId, context, config, loggerFactory, logger, loader)
    else -> error("Unsupported compiler backend")
}