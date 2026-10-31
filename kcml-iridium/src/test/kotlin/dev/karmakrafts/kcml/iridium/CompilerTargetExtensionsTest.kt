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
import dev.karmakrafts.kcml.api.target.JsCompileTarget
import dev.karmakrafts.kcml.api.target.JvmCompileTarget
import dev.karmakrafts.kcml.api.target.NativeCompileTarget
import dev.karmakrafts.kcml.api.target.WasmCompileTarget
import org.jetbrains.kotlin.cli.create
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.konan.config.konanTarget
import org.jetbrains.kotlin.konan.target.KonanTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame

class CompilerTargetExtensionsTest {
    @Test
    fun `JVM target is not Android`() {
        val target = assertIs<JvmCompileTarget>(CompilerTarget.JVM.createCompileTarget(CompilerConfiguration.create()))
        assertEquals("JVM", target.name)
        assertFalse(target.isAndroid)
    }

    @Test
    fun `JavaScript and Wasm targets expose their platform names`() {
        val config = CompilerConfiguration.create()
        assertEquals("JavaScript", assertIs<JsCompileTarget>(CompilerTarget.JS.createCompileTarget(config)).name)
        assertEquals("WASM/JS", assertIs<WasmCompileTarget>(CompilerTarget.WASM.createCompileTarget(config)).name)
    }

    @Test
    fun `Native target resolves the configured predefined target`() {
        for ((name, konanTarget) in KonanTarget.predefinedTargets) {
            val config = CompilerConfiguration.create().apply { this.konanTarget = name }
            val target = assertIs<NativeCompileTarget>(CompilerTarget.NATIVE.createCompileTarget(config))
            assertSame(konanTarget, target.konanTarget)
            assertEquals(konanTarget.name, target.name)
        }
    }

    @Test
    fun `Native target is resolved lazily and cached`() {
        val config = CompilerConfiguration.create()
        val target = assertIs<NativeCompileTarget>(CompilerTarget.NATIVE.createCompileTarget(config))
        config.konanTarget = "linux_x64"
        assertSame(KonanTarget.LINUX_X64, target.konanTarget)
        config.konanTarget = "linux_arm64"
        assertSame(KonanTarget.LINUX_X64, target.konanTarget)
        assertEquals("linux_x64", target.name)
    }

    @Test
    fun `Missing and unknown Native targets are rejected when accessed`() {
        for (name in listOf(null, "not_a_target")) {
            val config = CompilerConfiguration.create().apply {
                if (name != null) konanTarget = name
            }
            val target = assertIs<NativeCompileTarget>(CompilerTarget.NATIVE.createCompileTarget(config))
            val error = assertFailsWith<IllegalArgumentException> { target.konanTarget }
            assertEquals("Could not find correct Konan target for KCML compile target", error.message)
            assertFailsWith<IllegalArgumentException> { target.name }
        }
    }
}