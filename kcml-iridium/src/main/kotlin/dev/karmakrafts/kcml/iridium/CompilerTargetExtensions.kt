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
import dev.karmakrafts.kcml.api.target.CompileTarget
import dev.karmakrafts.kcml.api.target.JsCompileTarget
import dev.karmakrafts.kcml.api.target.JvmCompileTarget
import dev.karmakrafts.kcml.api.target.NativeCompileTarget
import dev.karmakrafts.kcml.api.target.WasmCompileTarget
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.konan.config.konanTarget
import org.jetbrains.kotlin.konan.target.KonanTarget

internal fun CompilerTarget.createCompileTarget(config: CompilerConfiguration): CompileTarget = when (this) {
    CompilerTarget.JVM -> object : JvmCompileTarget {
        override val isAndroid: Boolean = false // Iridium doesn't care about Android
        override val name: String = "JVM"
    }

    CompilerTarget.NATIVE -> object : NativeCompileTarget {
        override val konanTarget: KonanTarget by lazy {
            requireNotNull(KonanTarget.predefinedTargets[config.konanTarget]) {
                "Could not find correct Konan target for KCML compile target"
            }
        }
        override val name: String get() = konanTarget.name
    }

    CompilerTarget.JS -> object : JsCompileTarget {
        override val name: String = "JavaScript"
    }

    CompilerTarget.WASM -> object : WasmCompileTarget {
        override val name: String = "WASM/JS"
    }
}