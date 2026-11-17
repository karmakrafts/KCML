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

package dev.karmakrafts.kcml.api.target

import org.jetbrains.kotlin.konan.target.Architecture
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class KonanTargetsTest {
    @Test
    fun `creates fresh targets after native classes are preloaded`() {
        val host = HostManager.host
        val predefined = KonanTarget.predefinedTargets.toMap()
        assertTrue(Modifier.isAbstract(KonanTarget::class.java.modifiers))
        val name = "kcml_test_preloaded_target"

        val first = KonanTargets.create(name, host.family, host.architecture)
        val second = KonanTargets.create(name, host.family, host.architecture)

        assertNotSame(first, second)
        assertSame(first.javaClass, second.javaClass)
        for (target in listOf(first, second)) {
            assertEquals(name, target.name)
            assertSame(host.family, target.family)
            assertSame(host.architecture, target.architecture)
            assertFalse(target in KonanTarget.predefinedTargets.values)
        }
        assertFalse(name in KonanTarget.predefinedTargets)
        assertEquals(predefined, KonanTarget.predefinedTargets)
        assertSame(host, HostManager.host)
    }

    @Test
    fun `creates targets with runtime family and architecture without an agent`() {
        val host = HostManager.host
        val predefined = KonanTarget.predefinedTargets.toMap()
        assertTrue(Modifier.isAbstract(KonanTarget::class.java.modifiers))
        val family = KonanTargets.createFamily("KCML_TEST_TARGET_FAMILY", ".bin", "dyn", ".so", "stat", ".a")
        val architecture = KonanTargets.createArchitecture("KCML_TEST_TARGET_ARCHITECTURE", 64)
        val name = "kcml_test_runtime_target"

        val first = KonanTargets.create(name, family, architecture)
        val second = KonanTargets.create(name, family, architecture)

        assertNotSame(first, second)
        for (target in listOf(first, second)) {
            assertEquals(name, target.name)
            assertSame(family, target.family)
            assertSame(architecture, target.architecture)
            assertFalse(target in KonanTarget.predefinedTargets.values)
        }
        assertSame(family, Family.valueOf(family.name))
        assertSame(architecture, Architecture.valueOf(architecture.name))
        assertFalse(name in KonanTarget.predefinedTargets)
        assertEquals(predefined, KonanTarget.predefinedTargets)
        assertSame(host, HostManager.host)
    }

    @Test
    fun `creates and registers family with all constructor properties`() {
        val name = "KCML_TEST_FAMILY_PROPERTIES"
        val oldValues = Family.entries

        val family = KonanTargets.createFamily(name, ".exe", "lib", ".so", "lib", ".a")

        assertEquals(name, family.name)
        assertEquals(oldValues.size, family.ordinal)
        assertEquals(".exe", family.exeSuffix)
        assertEquals("lib", family.dynamicPrefix)
        assertEquals(".so", family.dynamicSuffix)
        assertEquals("lib", family.staticPrefix)
        assertEquals(".a", family.staticSuffix)
        assertSame(family, Family.entries.last())
        assertSame(family, Family.entries.last())
        assertSame(family, Family.valueOf(name))
        assertSame(family, Family::class.java.enumConstants.last())
    }

    @Test
    fun `creates and registers architectures with their bitness`() {
        val name32 = "KCML_TEST_ARCHITECTURE_32"
        val name64 = "KCML_TEST_ARCHITECTURE_64"
        val oldValues = Architecture.entries

        val architecture32 = KonanTargets.createArchitecture(name32, 32)
        val architecture64 = KonanTargets.createArchitecture(name64, 64)

        assertEquals(32, architecture32.bitness)
        assertEquals(64, architecture64.bitness)
        assertEquals(oldValues.size, architecture32.ordinal)
        assertEquals(oldValues.size + 1, architecture64.ordinal)
        assertSame(architecture32, Architecture.entries[oldValues.size])
        assertSame(architecture64, Architecture.entries.last())
        assertSame(architecture32, Architecture.entries[oldValues.size])
        assertSame(architecture64, Architecture.entries.last())
        assertSame(architecture32, Architecture.valueOf(name32))
        assertSame(architecture64, Architecture.valueOf(name64))
        assertSame(architecture64, Architecture::class.java.enumConstants.last())
    }

    @Test
    fun `rejects empty and duplicate family names without changing enum values`() {
        val name = "KCML_TEST_FAMILY_DUPLICATE"
        KonanTargets.createFamily(name, ".exe", "lib", ".so", "lib", ".a")
        val valuesAfterCreate = Family.entries

        assertFailsWith<IllegalArgumentException> {
            KonanTargets.createFamily("", ".exe", "lib", ".so", "lib", ".a")
        }
        assertEquals(valuesAfterCreate, Family.entries)
        assertFailsWith<IllegalArgumentException> {
            KonanTargets.createFamily(name, ".bin", "dyn", ".dylib", "stat", ".lib")
        }
        assertEquals(valuesAfterCreate, Family.entries)
    }

    @Test
    fun `rejects empty and duplicate architecture names without changing enum values`() {
        val name = "KCML_TEST_ARCHITECTURE_DUPLICATE"
        KonanTargets.createArchitecture(name, 64)
        val valuesAfterCreate = Architecture.entries

        assertFailsWith<IllegalArgumentException> {
            KonanTargets.createArchitecture("", 32)
        }
        assertEquals(valuesAfterCreate, Architecture.entries)
        assertFailsWith<IllegalArgumentException> {
            KonanTargets.createArchitecture(name, 32)
        }
        assertEquals(valuesAfterCreate, Architecture.entries)
    }
}