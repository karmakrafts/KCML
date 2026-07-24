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

import dev.karmakrafts.kcml.api.extension.Extension
import dev.karmakrafts.kcml.api.extension.ExtensionRegistry
import dev.karmakrafts.kcml.api.extension.FirExtension
import dev.karmakrafts.kcml.api.extension.IrExtension
import dev.karmakrafts.kcml.api.log.LoggerFactory
import dev.karmakrafts.kcml.api.plugin.PluginLoader
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar.ExtensionStorage
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.compiler.plugin.registerExtension
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

@OptIn(ExperimentalCompilerApi::class)
internal class ExtensionDispatcher( // @formatter:off
    private val loader: PluginLoader,
    private val registries: Map<String, ExtensionRegistry>
) { // @formatter:on
    private val extensions: List<Extension> = registries.values.flatMap(ExtensionRegistry::allSorted)

    /**
     * Registers all adapters required to wire through existing compiler extension APIs
     * to KCML extension APIs.
     */
    fun registerAdapters( // @formatter:off
        storage: ExtensionStorage,
        config: CompilerConfiguration,
        loggerFactory: LoggerFactory
    ) = with(storage) { // @formatter:on
        FirExtensionRegistrar.registerExtension(object : FirExtensionRegistrar() {
            override fun ExtensionRegistrarContext.configurePlugin() {
                +FirDeclarationGenerationExtension.Factory { session ->
                    val firExtensions = extensions.filterIsInstance<FirExtension>()
                    FirExtensionAdapter(loader, config, loggerFactory, session, firExtensions, registries)
                }
            }
        })
        val irExtensions = extensions.filterIsInstance<IrExtension>()
        IrGenerationExtension.registerExtension(
            IrExtensionAdapter(loader, config, loggerFactory, irExtensions, registries)
        )
    }
}