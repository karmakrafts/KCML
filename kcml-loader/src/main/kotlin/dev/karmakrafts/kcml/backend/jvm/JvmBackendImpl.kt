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

package dev.karmakrafts.kcml.backend.jvm

import dev.karmakrafts.kcml.api.backend.jvm.JvmBackend
import dev.karmakrafts.kcml.api.log.Logger
import dev.karmakrafts.kcml.api.log.LoggerFactory
import dev.karmakrafts.kcml.api.plugin.PluginLoader
import dev.karmakrafts.kcml.api.target.JvmCompileTarget
import dev.karmakrafts.kcml.backend.AbstractIrBackend
import dev.karmakrafts.kcml.target.JvmCompileTargetImpl
import dev.karmakrafts.kcml.util.kcmlIsAndroid
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.config.CompilerConfiguration

internal class JvmBackendImpl( // @formatter:off
    pluginId: String,
    context: IrPluginContext,
    config: CompilerConfiguration,
    loggerFactory: LoggerFactory,
    logger: Logger,
    loader: PluginLoader
) : AbstractIrBackend(pluginId, context, config, loggerFactory, logger, loader), JvmBackend {
    override val compileTarget: JvmCompileTarget by lazy {
        JvmCompileTargetImpl(config.kcmlIsAndroid)
    }
}