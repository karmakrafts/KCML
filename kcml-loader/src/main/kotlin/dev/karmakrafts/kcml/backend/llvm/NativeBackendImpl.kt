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

package dev.karmakrafts.kcml.backend.llvm

import dev.karmakrafts.kcml.api.backend.llvm.NativeBackend
import dev.karmakrafts.kcml.api.log.Logger
import dev.karmakrafts.kcml.api.log.LoggerFactory
import dev.karmakrafts.kcml.api.plugin.PluginLoader
import dev.karmakrafts.kcml.api.target.NativeCompileTarget
import dev.karmakrafts.kcml.backend.AbstractIrBackend
import dev.karmakrafts.kcml.target.NativeCompileTargetImpl
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.konan.config.konanTarget
import org.jetbrains.kotlin.konan.target.KonanTarget

internal class NativeBackendImpl( // @formatter:off
    context: IrPluginContext,
    override val config: CompilerConfiguration,
    loggerFactory: LoggerFactory,
    logger: Logger,
    loader: PluginLoader
) : AbstractIrBackend(context, config, loggerFactory, logger, loader), NativeBackend { // @formatter:on
    private val konanTarget: KonanTarget by lazy {
        KonanTarget.predefinedTargets[config.konanTarget]
            ?: error("Could not determine Konan target for KCML native backend")
    }
    override val compileTarget: NativeCompileTarget by lazy {
        NativeCompileTargetImpl(konanTarget)
    }
}