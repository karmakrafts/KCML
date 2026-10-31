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
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class TestIrBackendTest {
    @Test
    fun `Backend exposes compiler services and caches its own services`() = runCompilerTest {
        lateinit var config: CompilerConfiguration
        pipeline {
            defaultPipelineSpec()
            config { config = this }
        }
        source("package test\nclass Example")
        compiler shouldNotReport { error() }
        result irMatches {
            val context = pluginContext
            val backend = TestIrBackend(context, element, CompilerTarget.JVM, config)
            assertSame(TestIrBackend.Type, backend.type)
            assertSame(config, backend.config)
            backend.pluginId shouldBe "iridium"
            assertSame(context.irBuiltIns, backend.irBuiltIns)
            val stringId = ClassId.topLevel(FqName("kotlin.String"))
            assertSame(
                assertNotNull(context.finderForBuiltins().findClass(stringId)),
                backend.builtInsFinder.findClass(stringId)
            )
            val source = element.files.single()
            val exampleId = ClassId.topLevel(FqName("test.Example"))
            assertSame(
                assertNotNull(context.finderForSource(source).findClass(exampleId)),
                backend.getFinderForSource(source).findClass(exampleId)
            )
            assertSame(backend.compileTarget, backend.compileTarget)
            assertIs<TestLoggerFactory>(backend.loggerFactory)
            assertSame(backend.loggerFactory, backend.loggerFactory)
            assertIs<TestLogger>(backend.logger)
            assertSame(backend.loggerFactory("Iridium"), backend.logger)
            assertSame(backend.logger, backend.logger)
            backend.logger.name shouldBe "Iridium"
            assertIs<TestPluginLoader>(backend.loader)
            assertSame(backend.loader, backend.loader)
            assertSame(backend.logger, backend.loader.logger)

            val other = TestIrBackend(context, element, CompilerTarget.JVM, config)
            assertNotSame(backend.compileTarget, other.compileTarget)
            assertNotSame(backend.loggerFactory, other.loggerFactory)
            assertNotSame(backend.logger, other.logger)
            assertNotSame(backend.loader, other.loader)
        }
    }
}