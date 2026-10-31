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

package dev.karmakrafts.kcml.api.mixin

import java.util.concurrent.ConcurrentHashMap
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class MixinBridgeTest {
    private data class TestMixinSource(val name: String, val builtin: Boolean = false) : MixinSource {
        override fun getSourceName(): String = name
        override fun isBuiltin(): Boolean = builtin
    }

    private val builtin = TestMixinSource("KCML", true)
    private val first = TestMixinSource("first")
    private val second = TestMixinSource("second")
    private val plugin = TestMixinSource("plugin")

    @BeforeTest
    @AfterTest
    fun clearConstantTable() {
        MixinBridge.INSTANCE.constantTable.clear()
        MixinBridge.INSTANCE.setMixinSource(null)
    }

    @Test
    fun `stores constants in separate concurrent tables by plugin ID`() {
        val bridge = MixinBridge.INSTANCE
        bridge.putConstant(builtin, "value", "builtin")
        bridge.putConstant(first, "value", "first value")
        bridge.putConstant(second, "value", "second value")

        val tables: ConcurrentHashMap<MixinSource, ConcurrentHashMap<String, Any>> = bridge.constantTable
        assertEquals(setOf<MixinSource>(builtin, first, second), tables.keys)
        assertEquals("builtin", tables[builtin]?.get("value"))
        assertEquals("first value", tables[first]?.get("value"))
        assertEquals("second value", tables[second]?.get("value"))
        assertNull(tables[TestMixinSource("missing")])
        assertEquals("builtin", bridge.getConstant("kcml", "value"))
        assertEquals("first value", bridge.getConstant("first", "value"))
        assertEquals("second value", bridge.getConstant("second", "value"))
        assertNull(bridge.getConstant("missing", "value"))
        assertNull(bridge.getConstant("first", "missing"))
    }

    @Test
    fun `updates a constant without replacing its plugin table or other constants`() {
        val bridge = MixinBridge.INSTANCE
        bridge.putConstant(plugin, "first", 1)
        bridge.putConstant(plugin, "second", 2)
        val table = bridge.constantTable[plugin]

        bridge.putConstant(plugin, "first", 3)

        assertSame(table, bridge.constantTable[plugin])
        assertEquals<Map<String, Any>?>(mapOf("first" to 3, "second" to 2), table)
    }

    @Test
    fun `preserves concurrent writes to initially absent plugin tables`() {
        val bridge = MixinBridge.INSTANCE
        (0 until 256).toList().parallelStream().forEach { index ->
            bridge.putConstant(first, "value$index", index)
            bridge.putConstant(second, "value$index", -index)
        }

        assertEquals<Map<String, Any>?>((0 until 256).associateBy { "value$it" }, bridge.constantTable[first])
        assertEquals<Map<String, Any>?>((0 until 256).associate { "value$it" to -it }, bridge.constantTable[second])
    }

    @Test
    fun `implicit registration follows the active source and rejects registration outside loading`() {
        val bridge = MixinBridge.INSTANCE
        assertNull(bridge.mixinSource)
        assertFailsWith<IllegalStateException> { bridge.putConstant("value", "outside") }

        bridge.mixinSource = builtin
        assertSame(builtin, bridge.mixinSource)
        bridge.putConstant("value", "builtin")
        bridge.mixinSource = plugin
        assertSame(plugin, bridge.mixinSource)
        bridge.putConstant("value", "plugin")
        bridge.setMixinSource(null)

        assertNull(bridge.mixinSource)
        assertEquals("builtin", bridge.getConstant("kcml", "value"))
        assertEquals("plugin", bridge.getConstant("plugin", "value"))
        assertFailsWith<IllegalStateException> { bridge.putConstant("value", "outside") }
    }

    @Test
    fun `distinguishes builtin and plugin sources with the same display name`() {
        val bridge = MixinBridge.INSTANCE
        val sameNamePlugin = TestMixinSource("KCML")
        bridge.putConstant(builtin, "value", "builtin")
        bridge.putConstant(sameNamePlugin, "value", "plugin")

        assertEquals(2, bridge.constantTable.size)
        assertEquals("builtin", bridge.getConstant("kcml", "value"))
        assertEquals("plugin", bridge.getConstant("KCML", "value"))
    }
}