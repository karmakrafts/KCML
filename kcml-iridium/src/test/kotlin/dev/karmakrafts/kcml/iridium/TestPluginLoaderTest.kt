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

import dev.karmakrafts.kcml.api.InternalKcmlApi
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(InternalKcmlApi::class)
class TestPluginLoaderTest {
    @Test
    fun `Loader retains its logger and has no active or loaded plugins`() {
        val logger = TestLogger("test")
        val loader = TestPluginLoader(logger)
        assertSame(logger, loader.logger)
        assertNull(loader.loadingPluginId)
        assertTrue(loader.allPlugins().isEmpty())
        assertTrue(loader.allPluginsSorted().isEmpty())
    }

    @Test
    fun `Optional lookups return null for every plugin id`() {
        val loader = TestPluginLoader(TestLogger("test"))
        for (id in listOf("plugin", "iridium", "")) {
            assertNull(loader.findPlugin(id))
            assertNull(loader.findMetadata(id))
            assertNull(loader.findExtensionRegistry(id))
            assertNull(loader.findIpm(id))
        }
    }

    @Test
    fun `Required lookups explicitly report unsupported operations`() {
        val loader = TestPluginLoader(TestLogger("test"))
        for (id in listOf("plugin", "")) {
            assertFailsWith<NotImplementedError> { loader.getPlugin(id) }
            assertFailsWith<NotImplementedError> { loader.getMetadata(id) }
            assertFailsWith<NotImplementedError> { loader.getExtensionRegistry(id) }
            assertFailsWith<NotImplementedError> { loader.getIpm(id) }
        }
    }
}