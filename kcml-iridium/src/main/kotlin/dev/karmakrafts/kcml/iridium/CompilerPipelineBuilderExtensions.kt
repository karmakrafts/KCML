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

import dev.karmakrafts.iridium.pipeline.CompilerPipelineBuilder
import dev.karmakrafts.kcml.api.extension.FirExtension
import dev.karmakrafts.kcml.api.extension.IrExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

/**
 * Registers a KCML [IrExtension] with this compiler pipeline.
 *
 * The extension receives each compiled module after frontend analysis and may inspect or transform
 * its Kotlin IR before target code generation continues.
 *
 * @param extension extension to invoke for Kotlin IR processing.
 */
fun CompilerPipelineBuilder.kcmlIrExtension(extension: IrExtension) {
    config { // Hack to get the current compiler configuration instance
        val config = this
        irExtension { moduleFragment, pluginContext ->
            val backend = TestIrBackend(pluginContext, moduleFragment, compilerTarget, config)
            extension.process(moduleFragment, backend)
        }
    }
}

/**
 * Registers a KCML [FirExtension] with this compiler pipeline.
 *
 * Kotlin FIR declaration-generation queries are forwarded to [extension] for the active compiler
 * session.
 *
 * @param extension extension that supplies declarations to Kotlin FIR.
 */
fun CompilerPipelineBuilder.kcmlFirExtension(extension: FirExtension) {
    config { // Hack to get the current compiler configuration instance
        val config = this
        firExtensionRegistrar {
            object : FirExtensionRegistrar() {
                override fun ExtensionRegistrarContext.configurePlugin() {
                    +FirDeclarationGenerationExtension.Factory { session ->
                        val frontend = TestFrontend(session, config)
                        FirExtensionTestAdapter(session, frontend, extension)
                    }
                }
            }
        }
    }
}