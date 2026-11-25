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

package dev.karmakrafts.kcml.example

import dev.karmakrafts.kcml.api.plugin.CompilerPlugin
import dev.karmakrafts.kcml.api.plugin.InitializationContext
import dev.karmakrafts.kcml.api.plugin.Plugin
import dev.karmakrafts.kcml.api.target.CompileTargetRegistry
import dev.karmakrafts.kcml.api.target.NativeCompileTarget
import dev.karmakrafts.kcml.api.target.whenCompilingFor
import dev.karmakrafts.kcml.example.js.ExampleJsCodeGenerationExtension
import dev.karmakrafts.kcml.example.js.ExampleJsCompileTarget
import dev.karmakrafts.kcml.example.js.ExampleJsIntrinsicsExtension
import dev.karmakrafts.kcml.example.js.ExampleLateJsExtension
import dev.karmakrafts.kcml.example.jvm.ExampleJvmCodeGenerationExtension
import dev.karmakrafts.kcml.example.jvm.ExampleJvmCompileTarget
import dev.karmakrafts.kcml.example.jvm.ExampleJvmIntrinsicsExtension
import dev.karmakrafts.kcml.example.llvm.ExampleLateNativeExtension
import dev.karmakrafts.kcml.example.llvm.ExampleNativeCodeGenerationExtension
import dev.karmakrafts.kcml.example.llvm.ExampleNativeCompileTarget
import dev.karmakrafts.kcml.example.llvm.ExampleNativeIntrinsicsExtension
import dev.karmakrafts.kcml.example.syntax.ExampleSyntaxExtension
import dev.karmakrafts.kcml.example.wasm.ExampleLateWasmExtension
import dev.karmakrafts.kcml.example.wasm.ExampleWasmCodeGenerationExtension
import dev.karmakrafts.kcml.example.wasm.ExampleWasmCompileTarget
import dev.karmakrafts.kcml.example.wasm.ExampleWasmIntrinsicsExtension

@Plugin("example")
class ExamplePlugin : CompilerPlugin {
    override fun registerTargets(registry: CompileTargetRegistry) {
        val nativeFactory = registry.factories.getValue(NativeCompileTarget)
        registry.apply {
            register(ExampleJvmCompileTarget) { ExampleJvmCompileTarget(false) }
            register(ExampleJvmCompileTarget.AndroidType) { ExampleJvmCompileTarget(true) }
            register(ExampleJsCompileTarget.Type) { ExampleJsCompileTarget }
            register(ExampleWasmCompileTarget.Type) { ExampleWasmCompileTarget }
            register(ExampleNativeCompileTarget) { config ->
                @Suppress("KCML_TARGET_SPECIFIC_CODE") // TODO: find a way to avoid this
                ExampleNativeCompileTarget((nativeFactory(config) as NativeCompileTarget).konanTarget)
            }
        }
    }

    override fun init(context: InitializationContext) {
        context.logger.info("Loading example plugin!")
        context.extensionRegistry.apply {
            // Target-agnostic extensions
            register(ExampleSyntaxExtension())
            register(ExampleFirExtension())
            register(ExampleIrExtension())
            register(ExampleIrIntrinsicsExtension())

            // JVM
            context.whenCompilingFor(ExampleJvmCompileTarget, ExampleJvmCompileTarget.AndroidType) {
                register(ExampleJvmCodeGenerationExtension())
                register(ExampleJvmIntrinsicsExtension())
            }
            // JS
            context.whenCompilingFor(ExampleJsCompileTarget.Type) {
                register(ExampleLateJsExtension())
                register(ExampleJsCodeGenerationExtension())
                register(ExampleJsIntrinsicsExtension())
            }
            // LLVM
            context.whenCompilingFor(ExampleNativeCompileTarget) {
                register(ExampleLateNativeExtension())
                register(ExampleNativeCodeGenerationExtension())
                register(ExampleNativeIntrinsicsExtension())
            }
            // WASM
            context.whenCompilingFor(ExampleWasmCompileTarget.Type) {
                register(ExampleLateWasmExtension())
                register(ExampleWasmCodeGenerationExtension())
                register(ExampleWasmIntrinsicsExtension())
            }
        }
    }
}