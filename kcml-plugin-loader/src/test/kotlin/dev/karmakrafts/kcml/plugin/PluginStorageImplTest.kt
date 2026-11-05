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

package dev.karmakrafts.kcml.plugin

import dev.karmakrafts.kcml.api.plugin.PluginStorage.Key
import dev.karmakrafts.kcml.api.plugin.delegateFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class PluginStorageImplTest {
    @Test
    fun `storage exposes its owner plugin id`() {
        val storage = PluginStorageImpl("storage_test")
        assertEquals("storage_test", storage.ownerPluginId)
    }

    @Test
    fun `values with different types can be stored under different names`() {
        val storage = PluginStorageImpl("storage_test")
        val textKey = Key.create<String>("text")
        val numberKey = Key.create<Int>("number")
        storage[textKey] = "value"
        storage[numberKey] = 42
        assertEquals("value", storage[textKey])
        assertEquals(42, storage[numberKey])
    }

    @Test
    fun `reading a value preserves its identity`() {
        val storage = PluginStorageImpl("storage_test")
        val key = Key.create<Any>("value")
        val value = Any()
        storage[key] = value
        assertSame(value, storage[key])
    }

    @Test
    fun `non-final values can be overwritten`() {
        val storage = PluginStorageImpl("storage_test")
        val key = Key.create<String>("value")
        storage[key] = "first"
        storage[key] = "second"
        assertEquals("second", storage[key])
        assertEquals(1, storage.values.size)
    }

    @Test
    fun `reading a missing value reports the key and owner`() {
        val storage = PluginStorageImpl("storage_test")
        val key = Key.create<String>("missing")
        val exception = assertFailsWith<IllegalArgumentException> {
            storage[key]
        }
        assertEquals(
            "No plugin storage value with key 'missing' for plugin 'storage_test'", exception.message
        )
        assertFalse(storage.values.containsKey(key))
    }

    @Test
    fun `final values can be set once and read repeatedly`() {
        val storage = PluginStorageImpl("storage_test")
        val key = Key.create<String>("value", isFinal = true)
        storage[key] = "first"
        assertEquals("first", storage[key])
        assertEquals("first", storage[key])
    }

    @Test
    fun `overwriting a final value fails without changing the stored value`() {
        val storage = PluginStorageImpl("storage_test")
        val key = Key.create<String>("value", isFinal = true)
        storage[key] = "first"
        val exception = assertFailsWith<IllegalArgumentException> {
            storage[key] = "second"
        }
        assertEquals(
            "Plugin storage value with key 'value' for plugin 'storage_test' is final and cannot be set again",
            exception.message
        )
        assertEquals("first", storage[key])
    }

    @Test
    fun `setting a final value again fails even when the value is unchanged`() {
        val storage = PluginStorageImpl("storage_test")
        val key = Key.create<Int>("value", isFinal = true)
        storage[key] = 42
        assertFailsWith<IllegalArgumentException> {
            storage[key] = 42
        }
        assertEquals(42, storage[key])
    }

    @Test
    fun `reading a value with an incompatible runtime type reports the key owner and type`() {
        val storage = PluginStorageImpl("storage_test")
        val key = Key.create<String>("value")
        storage.values[key] = 42
        val exception = assertFailsWith<IllegalArgumentException> {
            storage[key]
        }
        assertEquals(
            "Plugin storage value with key 'value' for plugin 'storage_test' is not of type ${key.type}",
            exception.message
        )
    }

    @Test
    fun `values can be instances of a subtype of the key type`() {
        val storage = PluginStorageImpl("storage_test")
        val key = Key.create<Number>("number")
        storage[key] = 42
        assertEquals(42, storage[key])
        storage[key] = 3.5
        assertEquals(3.5, storage[key])
    }

    @Test
    fun `keys with the same name cannot use different types`() {
        val storage = PluginStorageImpl("storage_test")
        val textKey = Key.create<String>("value")
        val numberKey = Key.create<Int>("value")
        storage[textKey] = "original"
        val exception = assertFailsWith<IllegalArgumentException> {
            storage[numberKey] = 42
        }
        assertEquals(
            "Plugin storage value with key 'value' for plugin 'storage_test' expected type ${textKey.type} but got ${numberKey.type}",
            exception.message
        )
        assertEquals("original", storage[textKey])
        assertFalse(storage.values.containsKey(numberKey))
        assertEquals(1, storage.values.size)
    }

    @Test
    fun `equal keys share the same stored value`() {
        val storage = PluginStorageImpl("storage_test")
        val firstKey = Key.create<String>("value")
        val secondKey = Key.create<String>("value")
        assertNotSame(firstKey, secondKey)
        assertEquals(firstKey, secondKey)
        storage[firstKey] = "first"
        assertEquals("first", storage[secondKey])
        storage[secondKey] = "second"
        assertEquals("second", storage[firstKey])
        assertEquals("second", storage[secondKey])
        assertEquals(1, storage.values.size)
    }

    @Test
    fun `equal final keys cannot overwrite the stored value`() {
        val storage = PluginStorageImpl("storage_test")
        val firstKey = Key.create<String>("value", isFinal = true)
        val secondKey = Key.create<String>("value", isFinal = true)
        assertNotSame(firstKey, secondKey)
        assertEquals(firstKey, secondKey)
        storage[firstKey] = "first"
        assertEquals("first", storage[secondKey])
        val exception = assertFailsWith<IllegalArgumentException> {
            storage[secondKey] = "second"
        }
        assertEquals(
            "Plugin storage value with key 'value' for plugin 'storage_test' is final and cannot be set again",
            exception.message
        )
        assertEquals("first", storage[firstKey])
        assertEquals("first", storage[secondKey])
        assertEquals(1, storage.values.size)
    }

    @Test
    fun `storage instances do not share values or final key state`() {
        val firstStorage = PluginStorageImpl("first_plugin")
        val secondStorage = PluginStorageImpl("second_plugin")
        val key = Key.create<String>("value", isFinal = true)
        firstStorage[key] = "first"
        assertFailsWith<IllegalArgumentException> {
            secondStorage[key]
        }
        secondStorage[key] = "second"
        assertEquals("first", firstStorage[key])
        assertEquals("second", secondStorage[key])
    }

    @Test
    fun `delegated reads preserve identity and reflect storage updates`() {
        val storage = PluginStorageImpl("storage_test")
        val key = Key.create<Any>("value")
        val first = Any()
        val second = Any()
        storage[key] = first
        val value by storage.delegateFor(key)
        assertSame(first, value)
        storage[key] = second
        assertSame(second, value)
    }

    @Test
    fun `delegated writes initialize and update the stored value`() {
        val storage = PluginStorageImpl("storage_test")
        val key = Key.create<String>("value")
        var value by storage.delegateFor(key)
        value = "first"
        assertEquals("first", storage[key])
        assertEquals("first", value)
        value = "second"
        assertEquals("second", storage[key])
        assertEquals("second", value)
    }

    @Test
    fun `delegates defer missing value errors until read and allow later initialization`() {
        val storage = PluginStorageImpl("storage_test")
        val key = Key.create<String>("missing")
        val value by storage.delegateFor(key)
        assertFalse(storage.values.containsKey(key))
        val exception = assertFailsWith<IllegalArgumentException> {
            value
        }
        assertEquals(
            "No plugin storage value with key 'missing' for plugin 'storage_test'", exception.message
        )
        assertFalse(storage.values.containsKey(key))
        storage[key] = "initialized"
        assertEquals("initialized", value)
    }

    @Test
    fun `delegated writes enforce final keys without changing the stored value`() {
        val storage = PluginStorageImpl("storage_test")
        val key = Key.create<String>("value", isFinal = true)
        var value by storage.delegateFor(key)
        value = "first"
        assertEquals("first", storage[key])
        assertEquals("first", value)
        val exception = assertFailsWith<IllegalArgumentException> {
            value = "second"
        }
        assertEquals(
            "Plugin storage value with key 'value' for plugin 'storage_test' is final and cannot be set again",
            exception.message
        )
        assertEquals("first", storage[key])
        assertEquals("first", value)
    }

    @Test
    fun `delegated reads enforce runtime type validation`() {
        val storage = PluginStorageImpl("storage_test")
        val key = Key.create<String>("value")
        val value by storage.delegateFor(key)
        storage.values[key] = 42
        val exception = assertFailsWith<IllegalArgumentException> {
            value
        }
        assertEquals(
            "Plugin storage value with key 'value' for plugin 'storage_test' is not of type ${key.type}",
            exception.message
        )
    }

    @Test
    fun `delegated writes reject conflicting key types`() {
        val storage = PluginStorageImpl("storage_test")
        val textKey = Key.create<String>("value")
        val numberKey = Key.create<Int>("value")
        storage[textKey] = "original"
        var value by storage.delegateFor(numberKey)
        val exception = assertFailsWith<IllegalArgumentException> {
            value = 42
        }
        assertEquals(
            "Plugin storage value with key 'value' for plugin 'storage_test' expected type ${textKey.type} but got ${numberKey.type}",
            exception.message
        )
        assertEquals("original", storage[textKey])
        assertFalse(storage.values.containsKey(numberKey))
    }

    @Test
    fun `delegates for equal keys share the same stored value`() {
        val storage = PluginStorageImpl("storage_test")
        val firstKey = Key.create<String>("value")
        val secondKey = Key.create<String>("value")
        assertNotSame(firstKey, secondKey)
        assertEquals(firstKey, secondKey)
        var first by storage.delegateFor(firstKey)
        var second by storage.delegateFor(secondKey)
        first = "first"
        assertEquals("first", second)
        second = "second"
        assertEquals("second", first)
        assertEquals("second", second)
        assertEquals("second", storage[firstKey])
        assertEquals("second", storage[secondKey])
        assertEquals(1, storage.values.size)
    }

    @Test
    fun `delegated writes with equal final keys cannot overwrite the stored value`() {
        val storage = PluginStorageImpl("storage_test")
        val firstKey = Key.create<String>("value", isFinal = true)
        val secondKey = Key.create<String>("value", isFinal = true)
        assertNotSame(firstKey, secondKey)
        assertEquals(firstKey, secondKey)
        var first by storage.delegateFor(firstKey)
        var second by storage.delegateFor(secondKey)
        first = "first"
        assertEquals("first", second)
        val exception = assertFailsWith<IllegalArgumentException> {
            second = "second"
        }
        assertEquals(
            "Plugin storage value with key 'value' for plugin 'storage_test' is final and cannot be set again",
            exception.message
        )
        assertEquals("first", first)
        assertEquals("first", second)
        assertEquals("first", storage[firstKey])
        assertEquals("first", storage[secondKey])
        assertEquals(1, storage.values.size)
    }

    @Test
    fun `delegates for the same key use their own storage instance`() {
        val firstStorage = PluginStorageImpl("first_plugin")
        val secondStorage = PluginStorageImpl("second_plugin")
        val key = Key.create<String>("value")
        var first by firstStorage.delegateFor(key)
        var second by secondStorage.delegateFor(key)
        first = "first"
        second = "second"
        assertEquals("first", first)
        assertEquals("second", second)
        assertEquals("first", firstStorage[key])
        assertEquals("second", secondStorage[key])
    }
}