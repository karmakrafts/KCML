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

import dev.karmakrafts.iridium.pipeline.defaultPipelineSpec
import dev.karmakrafts.iridium.runCompilerTest
import org.jetbrains.kotlin.config.CompilerConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class TestFrontendTest {
    private fun testFrontend(block: (TestFrontend) -> Unit) = runCompilerTest {
        lateinit var config: CompilerConfiguration
        pipeline {
            defaultPipelineSpec()
            config { config = this }
        }
        compiler shouldNotReport { error() }
        result firMatches {
            block(TestFrontend(element.moduleData.session, config))
        }
    }

    @Test
    fun `Frontend retains the compiler session and configuration`() = runCompilerTest {
        lateinit var config: CompilerConfiguration
        pipeline {
            defaultPipelineSpec()
            config { config = this }
        }
        compiler shouldNotReport { error() }
        result firMatches {
            val frontend = TestFrontend(element.moduleData.session, config)
            assertSame(element.moduleData.session, frontend.session)
            assertSame(config, frontend.config)
            frontend.pluginId shouldBe "iridium"
        }
    }

    @Test
    fun `Frontend caches its logging and loader services`() = testFrontend { frontend ->
        assertIs<TestLoggerFactory>(frontend.loggerFactory)
        assertIs<TestLogger>(frontend.logger)
        assertIs<TestPluginLoader>(frontend.loader)
        assertSame(frontend.loggerFactory, frontend.loggerFactory)
        assertSame(frontend.loggerFactory("Iridium"), frontend.logger)
        assertEquals("Iridium", frontend.logger.name)
        assertSame(frontend.logger, frontend.logger)
        assertSame(frontend.loader, frontend.loader)
        assertSame(frontend.logger, frontend.loader.logger)
    }

    @Test
    fun `Frontends do not share logging or loader services`() = testFrontend { first ->
        val second = TestFrontend(first.session, first.config)
        assertNotSame(first.loggerFactory, second.loggerFactory)
        assertNotSame(first.logger, second.logger)
        assertNotSame(first.loader, second.loader)
    }
}