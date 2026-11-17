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

package dev.karmakrafts.kcml.target

import dev.karmakrafts.kcml.api.target.CommonCompileTarget
import dev.karmakrafts.kcml.api.target.CompileTarget
import dev.karmakrafts.kcml.api.target.CompileTargetFactory
import dev.karmakrafts.kcml.api.target.CompileTargetRegistry
import dev.karmakrafts.kcml.api.target.CompileTargetType
import dev.karmakrafts.kcml.api.target.JsCompileTarget
import dev.karmakrafts.kcml.api.target.JvmCompileTarget
import dev.karmakrafts.kcml.api.target.NativeCompileTarget
import dev.karmakrafts.kcml.api.target.WasmCompileTarget
import dev.karmakrafts.kcml.util.kcmlCompileTarget
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.konan.config.konanTarget
import org.jetbrains.kotlin.konan.target.KonanTarget
import java.util.concurrent.ConcurrentHashMap

internal class CompileTargetRegistryImpl : CompileTargetRegistry {
    override val factories: ConcurrentHashMap<CompileTargetType, CompileTargetFactory> = ConcurrentHashMap()

    init {
        factories[CommonCompileTarget] = CompileTargetFactory { CommonCompileTargetImpl }
        factories[JvmCompileTarget] = CompileTargetFactory { config ->
            JvmCompileTargetImpl(config.kcmlCompileTarget == "android")
        }
        factories[NativeCompileTarget] = CompileTargetFactory { config ->
            val konanTarget =
                NativeTargetConfiguration.get(config)?.target ?: KonanTarget.predefinedTargets[config.konanTarget]
                ?: error("Could not determine Konan target for KCML native backend")
            NativeCompileTargetImpl(konanTarget)
        }
        factories[JsCompileTarget] = CompileTargetFactory { JsCompileTargetImpl }
        factories[WasmCompileTarget] = CompileTargetFactory { WasmCompileTargetImpl }
    }

    override fun register(type: CompileTargetType, factory: CompileTargetFactory) {
        require(!factories.containsKey(type)) { "Compile target $type is already registered" }
        factories[type] = factory
    }

    override fun unregister(target: CompileTargetType) {
        require(factories.containsKey(target)) { "Compile target $target is not registered" }
        factories -= target
    }

    override fun createTarget(type: CompileTargetType, config: CompilerConfiguration): CompileTarget {
        return requireNotNull(factories[type]) { "No compile target factory registered for type $type" }(config)
    }

    override fun createTarget(config: CompilerConfiguration): CompileTarget {
        val compileTargetName = requireNotNull(
            config.kcmlCompileTarget ?: if (config.konanTarget != null) "native" else null
        ) { "KCML compile target not specified" }
        val targetType =
            factories.keys.firstOrNull { type -> compileTargetName in type.aliases && type.isSelected(config) }
                ?: NativeCompileTarget.takeIf { NativeTargetConfiguration.get(config) != null }
                ?: throw NoSuchElementException("No compile target registered for '$compileTargetName'")
        return createTarget(targetType, config)
    }
}