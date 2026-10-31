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

/**
 * Provides KCML IR backend services for an Iridium compilation.
 *
 * Built-ins and declaration lookup delegate to the supplied compiler context. Logging uses
 * console-backed test loggers, and the plugin loader exposes no loaded plugins.
 *
 * @param pluginContext compiler context supplying IR built-ins and declaration finders.
 * @param moduleFragment IR module associated with this backend invocation.
 * @param compilerTarget Iridium target to expose as a KCML compile target.
 * @property config compiler configuration for the compilation.
 */
public class TestIrBackend( // @formatter:off
    private val pluginContext: IrPluginContext,
    private val moduleFragment: IrModuleFragment,
    private val compilerTarget: CompilerTarget,
    override val config: CompilerConfiguration
) : IrBackend { // @formatter:on
    /** Identifies the IR backend supplied by the Iridium test integration. */
    public companion object Type : BackendType

    /** Backend type identifying this Iridium test backend. */
    override val type: BackendType get() = Type

    /** IR built-ins supplied by the compiler context. */
    override val irBuiltIns: IrBuiltIns
        get() = pluginContext.irBuiltIns

    /** Declaration finder for the compiler's built-in declarations. */
    override val builtInsFinder: DeclarationFinder
        get() = pluginContext.finderForBuiltins()

    /**
     * Obtains a declaration finder for a source file from the compiler context.
     *
     * @param source IR file whose declarations should be searched.
     * @return the compiler's declaration finder for [source].
     */
    override fun getFinderForSource(source: IrFile): DeclarationFinder {
        return pluginContext.finderForSource(source)
    }

    /** Fixed plugin ID used by the Iridium test integration. */
    override val pluginId: String = "iridium"

    /** Lazily created KCML descriptor for the configured Iridium compiler target. */
    override val compileTarget: CompileTarget by lazy { compilerTarget.createCompileTarget(config) }

    /** Lazily created factory for named console-backed test loggers. */
    override val loggerFactory: LoggerFactory by lazy { TestLoggerFactory() }

    /** Logger named `Iridium` used by this backend. */
    override val logger: Logger by lazy { loggerFactory("Iridium") }

    /** Lazily created test loader that exposes no loaded plugins. */
    override val loader: PluginLoader by lazy { TestPluginLoader(logger) }
}