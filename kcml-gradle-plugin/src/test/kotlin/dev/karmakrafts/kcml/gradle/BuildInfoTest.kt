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

package dev.karmakrafts.kcml.gradle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class BuildInfoTest {
    @Test
    fun `uses the plugin version when a root version resource conflicts`() {
        val conflictingVersion = BuildInfo::class.java.getResourceAsStream("/kcml.version")!!.bufferedReader().use {
            it.readText()
        }
        assertEquals("conflicting-version", conflictingVersion)
        val componentVersion = BuildInfo::class.java
            .getResourceAsStream("/dev/karmakrafts/kcml/gradle/kcml.version")!!.bufferedReader().use {
                it.readText()
            }
        assertEquals(componentVersion, BuildInfo.version)
        assertNotEquals(conflictingVersion, BuildInfo.version)
    }
}