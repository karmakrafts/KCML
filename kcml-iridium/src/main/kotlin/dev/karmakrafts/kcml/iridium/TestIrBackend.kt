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

package dev.karmakrafts.kcml.iridium

import dev.karmakrafts.iridium.pipeline.CompilerTarget
import dev.karmakrafts.kcml.api.backend.BackendType
import dev.karmakrafts.kcml.api.backend.IrBackend
import dev.karmakrafts.kcml.api.log.Logger
import dev.karmakrafts.kcml.api.log.LoggerFactory
import dev.karmakrafts.kcml.api.plugin.PluginLoader
import dev.karmakrafts.kcml.api.target.CompileTarget
import org.jetbrains.kotlin.backend.common.extensions.DeclarationFinder
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.ir.IrBuiltIns
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment

internal class TestIrBackend( // @formatter:off
    private val pluginContext: IrPluginContext,
    private val moduleFragment: IrModuleFragment,
    private val compilerTarget: CompilerTarget,
    override val config: CompilerConfiguration
) : IrBackend { // @formatter:on
    companion object Type : BackendType

    override val type: BackendType get() = Type

    override val irBuiltIns: IrBuiltIns
        get() = pluginContext.irBuiltIns
    override val builtInsFinder: DeclarationFinder
        get() = pluginContext.finderForBuiltins()

    override fun getFinderForSource(source: IrFile): DeclarationFinder {
        return pluginContext.finderForSource(source)
    }

    override val pluginId: String = "iridium"
    override val compileTarget: CompileTarget by lazy { compilerTarget.createCompileTarget(config) }
    override val loggerFactory: LoggerFactory by lazy { TestLoggerFactory() }
    override val logger: Logger by lazy { loggerFactory("Iridium") }
    override val loader: PluginLoader by lazy { TestPluginLoader(logger) }
}