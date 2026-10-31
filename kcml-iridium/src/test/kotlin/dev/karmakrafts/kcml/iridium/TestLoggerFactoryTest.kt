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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class TestLoggerFactoryTest {
    @Test
    fun `Loggers are cached by name across both accessors`() {
        val factory = TestLoggerFactory()
        val logger = assertIs<TestLogger>(factory("plugin"))
        assertEquals("plugin", logger.name)
        assertSame(logger, factory("plugin"))
        assertSame(logger, factory.getForPlugin("plugin"))
        val other = factory.getForPlugin("other")
        assertSame(other, factory.getForPlugin("other"))
        assertSame(other, factory("other"))
        assertNotSame(logger, other)
    }

    @Test
    fun `Cache distinguishes case and permits empty names`() {
        val factory = TestLoggerFactory()
        assertNotSame(factory("Plugin"), factory("plugin"))
        val logger = factory("")
        assertEquals("", logger.name)
        assertSame(logger, factory.getForPlugin(""))
    }

    @Test
    fun `Factory instances do not share loggers`() {
        assertNotSame(TestLoggerFactory()("plugin"), TestLoggerFactory()("plugin"))
    }
}