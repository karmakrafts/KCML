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
import dev.karmakrafts.iridium.pipeline.defaultPipelineSpec
import dev.karmakrafts.iridium.runCompilerTest
import dev.karmakrafts.kcml.api.backend.IrBackend
import dev.karmakrafts.kcml.api.extension.ExtensionDependency
import dev.karmakrafts.kcml.api.extension.FirExtension
import dev.karmakrafts.kcml.api.extension.IrExtension
import dev.karmakrafts.kcml.api.frontend.Frontend
import dev.karmakrafts.kcml.api.target.JsCompileTarget
import dev.karmakrafts.kcml.api.target.JvmCompileTarget
import dev.karmakrafts.kcml.api.target.NativeCompileTarget
import dev.karmakrafts.kcml.api.target.WasmCompileTarget
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey
import org.jetbrains.kotlin.fir.extensions.PluginServicesInitialization
import org.jetbrains.kotlin.fir.extensions.extensionService
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.konan.config.konanTarget
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(PluginServicesInitialization::class, UnsafeDuringIrConstructionAPI::class)
class CompilerPipelineBuilderExtensionsTest {
    @Test
    fun `KCML extensions receive active compiler state on every target`() {
        val marker = CompilerConfigurationKey.create<String>("kcml-iridium test marker")
        for (target in CompilerTarget.entries) {
            lateinit var config: CompilerConfiguration
            val frontends = ArrayList<Frontend>()
            val backends = ArrayList<IrBackend>()
            val modules = ArrayList<IrModuleFragment>()
            runCompilerTest {
                pipeline {
                    defaultPipelineSpec()
                    kcmlFirExtension(object : FirExtension {
                        override val id: String = "fir"
                        override val dependencies: List<ExtensionDependency> = emptyList()

                        override fun getTopLevelClassIds(frontend: Frontend): Set<ClassId> {
                            frontends += frontend
                            return emptySet()
                        }
                    })
                    kcmlIrExtension(object : IrExtension {
                        override val id: String = "ir"
                        override val dependencies: List<ExtensionDependency> = emptyList()

                        override fun process(module: IrModuleFragment, backend: IrBackend) {
                            modules += module
                            backends += backend
                        }
                    })
                    compilerTarget = target
                    config {
                        config = this
                        put(marker, target.name)
                        if (target == CompilerTarget.NATIVE) konanTarget = "linux_x64"
                    }
                }
                source("class Example\nfun answer(): Int = 42")
                compiler shouldNotReport { error() }
                result firMatches {
                    assertTrue(frontends.isNotEmpty(), "$target: FIR extension was not invoked")
                    for (frontend in frontends) {
                        assertIs<TestFrontend>(frontend)
                        assertSame(element.moduleData.session, frontend.session)
                        assertSame(config, frontend.config)
                        frontend.config[marker] shouldBe target.name
                    }
                }
                result irMatches {
                    assertSame(element, modules.single())
                    val backend = assertIs<TestIrBackend>(backends.single())
                    assertSame(config, backend.config)
                    backend.config[marker] shouldBe target.name
                    assertSame(pluginContext.irBuiltIns, backend.irBuiltIns)
                    when (target) {
                        CompilerTarget.JVM -> assertIs<JvmCompileTarget>(backend.compileTarget)
                        CompilerTarget.JS -> assertIs<JsCompileTarget>(backend.compileTarget)
                        CompilerTarget.WASM -> assertIs<WasmCompileTarget>(backend.compileTarget)
                        CompilerTarget.NATIVE -> {
                            val compileTarget = assertIs<NativeCompileTarget>(backend.compileTarget)
                            assertSame(KonanTarget.LINUX_X64, compileTarget.konanTarget)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Multiple IR extensions are invoked in registration order`() = runCompilerTest {
        val calls = ArrayList<String>()
        val modules = ArrayList<IrModuleFragment>()
        pipeline {
            defaultPipelineSpec()
            for (name in listOf("first", "second")) {
                kcmlIrExtension(object : IrExtension {
                    override val id: String = name
                    override val dependencies: List<ExtensionDependency> = emptyList()

                    override fun process(module: IrModuleFragment, backend: IrBackend) {
                        calls += name
                        modules += module
                    }
                })
            }
        }
        compiler shouldNotReport { error() }
        result irMatches {
            calls shouldBe listOf("first", "second")
            modules.forEach { assertSame(element, it) }
            element.files.single() matches {
                containsNoChild<IrDeclaration>()
            }
        }
    }

    @Test
    fun `Multiple FIR extensions are registered for the same session`() = runCompilerTest {
        val calls = ArrayList<Pair<String, Frontend>>()
        pipeline {
            defaultPipelineSpec()
            for (name in listOf("first", "second")) {
                kcmlFirExtension(object : FirExtension {
                    override val id: String = name
                    override val dependencies: List<ExtensionDependency> = emptyList()

                    override fun hasPackage(frontend: Frontend, packageFqName: FqName): Boolean {
                        calls += name to frontend
                        return false
                    }
                })
            }
        }
        source("package test\nclass Example")
        compiler shouldNotReport { error() }
        result firMatches {
            val adapters = element.moduleData.session.extensionService.getAllExtensions()
                .filterIsInstance<FirExtensionTestAdapter>()
            adapters.size shouldBe 2
            calls.clear()
            adapters.forEach { assertFalse(it.hasPackage(FqName("test"))) }
            calls.map { it.first }.toSet() shouldBe setOf("first", "second")
            calls.forEach { (_, frontend) -> assertSame(element.moduleData.session, frontend.session) }
        }
    }
}