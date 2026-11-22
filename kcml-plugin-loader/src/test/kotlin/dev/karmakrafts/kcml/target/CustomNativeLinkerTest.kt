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

import org.jetbrains.kotlin.konan.TempFiles
import org.jetbrains.kotlin.konan.exec.Command
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.konan.target.LinkerArguments
import org.jetbrains.kotlin.konan.target.LinkerOutputKind
import org.jetbrains.kotlin.konan.target.SanitizerKind
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CustomNativeLinkerTest {
    private inline fun withTempFiles(action: (TempFiles) -> Unit) {
        val tempFiles = TempFiles()
        try {
            action(tempFiles)
        }
        finally {
            tempFiles.dispose()
        }
    }

    private fun configurables(
        tempFiles: TempFiles, triple: String = "riscv64-unknown-none-elf", configure: Properties.() -> Unit = {}
    ): CustomNativeConfigurables = CustomNativeConfigurables(
        KonanTarget.LINUX_X64,
        Properties().apply {
            setProperty("llvmHome", tempFiles.create("llvm tools").absolutePathString())
            setProperty("libffiDir", tempFiles.create("libffi").absolutePathString())
            setProperty("targetTriple.linux_x64", triple)
            setProperty("dependenciesUrl", "https://example.invalid")
            setProperty("airplaneMode", "true")
            configure()
        },
        tempFiles.create("dependencies").absolutePathString(),
        { _, _, _ -> error("Command generation must not download dependencies") })

    private fun arguments(
        tempFiles: TempFiles,
        kind: LinkerOutputKind = LinkerOutputKind.EXECUTABLE,
        objectFiles: List<String> = listOf("first.o", "second object.o"),
        staticLibraries: List<String> = listOf("libstatic.a", "library.lib"),
        dynamicLibraries: List<String> = listOf("libdynamic.so", "dynamic.dylib"),
        linkerArgs: List<String> = listOf("-Wl,--entry=custom_entry", "-lcustom"),
        optimize: Boolean = false,
        debug: Boolean = false,
        sanitizer: SanitizerKind? = null
    ): LinkerArguments = LinkerArguments(
        tempFiles = tempFiles,
        objectFiles = objectFiles,
        executable = "output binary",
        staticLibraries = staticLibraries,
        dynamicLibraries = dynamicLibraries,
        linkerArgs = linkerArgs,
        optimize = optimize,
        debug = debug,
        kind = kind,
        outputDsymBundle = "unused.dSYM",
        sanitizer = sanitizer
    )

    private fun Command.responseArguments(): List<String> {
        val responseFile = Path(args.single { it.startsWith('@') }.drop(1))
        assertTrue(responseFile.exists())
        return responseFile.readLines().map { line ->
            assertTrue(line.startsWith('"') && line.endsWith('"'))
            line.substring(1, line.lastIndex).replace(Regex("\\\\(.)")) { it.groupValues[1] }
        }
    }

    private fun CustomNativeLinker.commands(arguments: LinkerArguments): List<Command> =
        with(this) { arguments.finalLinkCommands() }

    @Test
    fun `executable commands use configured LLVM and triple rather than target family`() = withTempFiles { tempFiles ->
        for (triple in listOf("riscv64-unknown-none-elf", "wasm32-unknown-unknown", "aarch64-unknown-linux-gnu")) {
            val configurables = configurables(tempFiles, triple)
            val linker = CustomNativeLinker(configurables)
            val arguments = arguments(tempFiles)
            val command = linker.commands(arguments).single()
            assertTrue(linker.useCompilerDriverAsLinker)
            assertEquals("${configurables.absoluteLlvmHome}/bin/clang", command.argsWithExecutable.first())
            assertEquals(listOf("--rsp-quoting=posix"), command.args.dropLast(1))
            val flags = command.responseArguments()
            assertEquals(triple, flags[flags.indexOf("-target") + 1])
            assertEquals(arguments.executable, flags[flags.indexOf("-o") + 1])
            assertTrue("-fuse-ld=lld" in flags)
            assertFalse(flags.any { it.startsWith("--sysroot") })
            assertFalse("-shared" in flags)
            val inputs =
                arguments.objectFiles + arguments.staticLibraries + arguments.dynamicLibraries + arguments.linkerArgs
            assertEquals(inputs, flags.takeLast(inputs.size))
        }
    }

    @Test
    fun `dynamic and executable flags preserve compiler properties and debug optimization choices`() =
        withTempFiles { tempFiles ->
            val sysroot = tempFiles.create("sys root").absolutePathString()
            val linker = CustomNativeLinker(configurables(tempFiles) {
                setProperty("targetSysRoot.linux_x64", sysroot)
                setProperty("clangFlags.linux_x64", "-ffreestanding -fuse-ld=custom")
                setProperty("clangNooptFlags.linux_x64", "-O0")
                setProperty("clangOptFlags.linux_x64", "-O3")
                setProperty("clangDebugFlags.linux_x64", "-gdwarf-4")
                setProperty("linkerOptimizationFlags.linux_x64", "-Wl,--gc-sections")
                setProperty("linkerNoDebugFlags.linux_x64", "-Wl,--strip-debug")
                setProperty("linkerDynamicFlags.linux_x64", "-Wl,--export-dynamic")
                setProperty("linkerKonanFlags.linux_x64", "-nostdlib -lcompiler_runtime")
            })
            for (kind in listOf(LinkerOutputKind.EXECUTABLE, LinkerOutputKind.DYNAMIC_LIBRARY)) {
                for (optimize in listOf(false, true)) {
                    for (debug in listOf(false, true)) {
                        val flags = linker.commands(arguments(tempFiles, kind, optimize = optimize, debug = debug))
                            .single()
                            .responseArguments()
                        assertTrue("--sysroot=$sysroot" in flags)
                        assertTrue("-ffreestanding" in flags)
                        assertTrue("-fuse-ld=custom" in flags)
                        assertEquals(optimize, "-O3" in flags)
                        assertEquals(!optimize, "-O0" in flags)
                        assertEquals(optimize, "-Wl,--gc-sections" in flags)
                        assertEquals(debug, "-g" in flags)
                        assertEquals(debug, "-gdwarf-4" in flags)
                        assertEquals(!debug, "-Wl,--strip-debug" in flags)
                        assertEquals(kind == LinkerOutputKind.DYNAMIC_LIBRARY, "-shared" in flags)
                        assertEquals(kind == LinkerOutputKind.DYNAMIC_LIBRARY, "-Wl,--export-dynamic" in flags)
                        assertEquals(listOf("-nostdlib", "-lcompiler_runtime"), flags.takeLast(2))
                    }
                }
            }
        }

    @Test
    fun `static commands flatten archives and put every member in a response file`() = withTempFiles { tempFiles ->
        val configurables = configurables(tempFiles)
        val linker = CustomNativeLinker(configurables)
        val objects = (0 until 5000).map { "object directory/object$it.o" }
        val libraries = listOf("libdependency.a", "windows dependency.lib")
        val command = linker.commands(
            arguments(
                tempFiles,
                LinkerOutputKind.STATIC_LIBRARY,
                objectFiles = objects,
                staticLibraries = libraries,
                dynamicLibraries = emptyList()
            )
        ).single()
        assertEquals("${configurables.absoluteLlvmHome}/bin/llvm-ar", command.argsWithExecutable.first())
        assertEquals(listOf("--rsp-quoting=posix", "qcsDL", "output binary"), command.args.dropLast(1))
        assertEquals(objects + libraries, command.responseArguments())
    }

    @Test
    fun `response files preserve spaces quotes and Windows backslashes for all link inputs`() =
        withTempFiles { tempFiles ->
            val linker = CustomNativeLinker(configurables(tempFiles))
            val paths = listOf("path with spaces/a.o", "C:\\objects\\test.o", "a\"quoted.o", "C:\\trailing\\")
            for (kind in LinkerOutputKind.entries) {
                val command = linker.commands(
                    arguments(
                        tempFiles,
                        kind,
                        objectFiles = paths,
                        staticLibraries = emptyList(),
                        dynamicLibraries = emptyList(),
                        linkerArgs = emptyList()
                    )
                ).single()
                assertTrue(command.responseArguments().containsAll(paths))
                val lines = Path(command.args.last().drop(1)).readLines()
                assertTrue("\"C:\\\\objects\\\\test.o\"" in lines)
                assertTrue("\"a\\\"quoted.o\"" in lines)
                assertTrue("\"C:\\\\trailing\\\\\"" in lines)
            }
        }

    @Test
    fun `commands sharing compiler temporary files retain independent response files`() = withTempFiles { tempFiles ->
        val linker = CustomNativeLinker(configurables(tempFiles))
        for (kind in LinkerOutputKind.entries) {
            val objects = (0 until 5000).map { "object directory/first$it.o" }
            val first = linker.commands(
                arguments(
                    tempFiles, kind, objectFiles = objects, dynamicLibraries = emptyList()
                )
            ).single()
            val originalArguments = first.responseArguments()
            val second = linker.commands(
                arguments(
                    tempFiles, kind, objectFiles = listOf("second.o"), dynamicLibraries = emptyList()
                )
            ).single()
            assertTrue(first.args.last() != second.args.last())
            assertTrue(first.argsWithExecutable.sumOf { it.length } < 4096)
            assertTrue(originalArguments.containsAll(objects))
            assertEquals(originalArguments, first.responseArguments())
            assertTrue("second.o" in second.responseArguments())
        }
    }

    @Test
    fun `prelink is relocatable without startup files or final link libraries`() = withTempFiles { tempFiles ->
        val linker = CustomNativeLinker(configurables(tempFiles) {
            setProperty("clangFlags.linux_x64", "-ffreestanding")
            setProperty("linkerKonanFlags.linux_x64", "-lnot_for_prelink")
        })
        val objects = listOf("a.o", "b object.o")
        val command = linker.preLinkCommands(objects, "combined.o", tempFiles).single()
        val flags = command.responseArguments()
        assertTrue(flags.containsAll(listOf("-r", "-nostdlib", "-ffreestanding")))
        assertEquals("riscv64-unknown-none-elf", flags[flags.indexOf("-target") + 1])
        assertEquals("combined.o", flags[flags.indexOf("-o") + 1])
        assertEquals(objects, flags.takeLast(objects.size))
        assertFalse("-lnot_for_prelink" in flags)
        assertFalse("-shared" in flags)
    }

    @Test
    fun `archive filtering preserves Unix and Windows libraries and their order`() = withTempFiles { tempFiles ->
        val linker = CustomNativeLinker(configurables(tempFiles))
        val libraries = listOf("one.a", "two.lib", "three.dll.a", "FOUR.LIB", "one.a")
        assertEquals(
            libraries, linker.filterStaticLibraries(libraries + listOf("object.o", "libshared.so", "dynamic.dll"))
        )
        assertEquals(libraries, linker.linkStaticLibraries(libraries))
    }

    @Test
    fun `sanitizers fail explicitly for every output kind`() = withTempFiles { tempFiles ->
        val linker = CustomNativeLinker(configurables(tempFiles))
        for (kind in LinkerOutputKind.entries) {
            for (sanitizer in SanitizerKind.entries) {
                val failure = assertFailsWith<IllegalArgumentException> {
                    linker.commands(arguments(tempFiles, kind, sanitizer = sanitizer))
                }
                assertTrue(failure.message.orEmpty().contains(sanitizer.name))
            }
        }
    }

    @Test
    fun `static archives reject shared dependencies rather than silently discarding them`() =
        withTempFiles { tempFiles ->
            val linker = CustomNativeLinker(configurables(tempFiles))
            val failure = assertFailsWith<IllegalArgumentException> {
                linker.commands(arguments(tempFiles, LinkerOutputKind.STATIC_LIBRARY))
            }
            assertTrue(failure.message.orEmpty().contains("dynamic libraries"))
        }
}