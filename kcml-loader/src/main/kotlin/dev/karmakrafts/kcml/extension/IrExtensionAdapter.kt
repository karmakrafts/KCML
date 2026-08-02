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

package dev.karmakrafts.kcml.extension

import dev.karmakrafts.kcml.api.backend.IrBackend
import dev.karmakrafts.kcml.api.extension.IrExtension
import dev.karmakrafts.kcml.api.log.LoggerFactory
import dev.karmakrafts.kcml.api.plugin.PluginLoader
import dev.karmakrafts.kcml.backend.create
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment

internal class IrExtensionAdapter( // @formatter:off
    private val loader: PluginLoader,
    private val config: CompilerConfiguration,
    private val loggerFactory: LoggerFactory,
    private val extensions: List<Pair<String, IrExtension>>,
) : IrGenerationExtension { // @formatter:on
    init {
        loader.logger.info("Initializing IR extension adapter")
    }

    override fun generate( // @formatter:off
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext
    ) { // @formatter:on
        for ((pluginId, extension) in extensions) {
            val logger = loggerFactory.getForPlugin(pluginId)
            val backend = IrBackend.create(pluginContext, config, loggerFactory, logger, loader)
            extension.process(moduleFragment, backend)
        }
    }
}