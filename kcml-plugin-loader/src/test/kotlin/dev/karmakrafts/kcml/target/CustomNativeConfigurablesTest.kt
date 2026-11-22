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

package dev.karmakrafts.kcml.target

import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.konan.target.hostTargetSuffix
import java.nio.file.Path
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class CustomNativeConfigurablesTest {
    @Test
    fun `generic configurations use configured LLVM tools without requiring a sysroot`() {
        val properties = Properties().apply {
            setProperty("dependenciesUrl", "https://download.jetbrains.com/kotlin/native")
            setProperty("llvmHome.${HostManager.host.name}", "/tools/llvm")
            setProperty("libffiDir.${HostManager.host.name}", "/tools/libffi")
        }
        val settings = CustomNativeConfigurables(
            KonanTarget.LINUX_X64, properties, Path.of("build").toAbsolutePath().toString()
        ) { _, _, _ -> }
        assertEquals("/tools/llvm", settings.absoluteTargetToolchain)
        assertEquals("", settings.absoluteTargetSysRoot)
    }

    @Test
    fun `generic configurations preserve explicit target toolchain and sysroot`() {
        val properties = Properties().apply {
            setProperty("dependenciesUrl", "https://download.jetbrains.com/kotlin/native")
            setProperty("llvmHome.${HostManager.host.name}", "/tools/llvm")
            setProperty("libffiDir.${HostManager.host.name}", "/tools/libffi")
            setProperty("targetToolchain.${hostTargetSuffix(HostManager.host, KonanTarget.LINUX_X64)}", "/tools/custom")
            setProperty("targetSysRoot.linux_x64", "/sdk")
        }
        val settings = CustomNativeConfigurables(
            KonanTarget.LINUX_X64, properties, Path.of("build").toAbsolutePath().toString()
        ) { _, _, _ -> }
        assertEquals("/tools/custom", settings.absoluteTargetToolchain)
        assertEquals("/sdk", settings.absoluteTargetSysRoot)
    }
}