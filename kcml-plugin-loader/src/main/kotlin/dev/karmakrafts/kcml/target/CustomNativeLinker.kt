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
import org.jetbrains.kotlin.konan.target.LinkerArguments
import org.jetbrains.kotlin.konan.target.LinkerFlags
import org.jetbrains.kotlin.konan.target.LinkerOutputKind
import java.util.*
import kotlin.io.path.absolutePathString
import kotlin.io.path.writeLines

internal class CustomNativeLinker(private val properties: CustomNativeConfigurables) : LinkerFlags(properties) {
    private val llvmBin get() = "${properties.absoluteLlvmHome}/bin"
    private val clang get() = "$llvmBin/clang"

    override val useCompilerDriverAsLinker: Boolean get() = true

    override fun filterStaticLibraries(binaries: List<String>): List<String> = binaries.filter {
        it.endsWith(".a", ignoreCase = true) || it.endsWith(".lib", ignoreCase = true)
    }

    private fun driverArguments(): List<String> = buildList {
        add("-B$llvmBin")
        add("-fuse-ld=lld")
        addAll(properties.clangFlags)
        addAll(listOf("-target", properties.targetTriple.toString()))
        if (!properties.targetSysRoot.isNullOrBlank()) {
            add("--sysroot=${properties.absoluteTargetSysRoot}")
        }
    }

    private fun responseFileArgument(tempFiles: TempFiles, prefix: String, arguments: List<String>): String {
        val responseFile = tempFiles.create("$prefix-${UUID.randomUUID()}", ".rsp")
        responseFile.writeLines(arguments.map { argument ->
            "\"${argument.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        })
        return "@${responseFile.absolutePathString()}"
    }

    private fun clangCommand(tempFiles: TempFiles, prefix: String, arguments: List<String>): Command =
        Command(clang).apply {
            +"--rsp-quoting=posix"
            +responseFileArgument(tempFiles, prefix, arguments)
        }

    override fun LinkerArguments.finalLinkCommands(): List<Command> {
        require(sanitizer == null) {
            "Sanitizer $sanitizer is unsupported for custom Native target ${properties.target}"
        }
        if (kind == LinkerOutputKind.STATIC_LIBRARY) {
            require(dynamicLibraries.isEmpty()) {
                "Static archives cannot contain dynamic libraries: ${dynamicLibraries.joinToString()}"
            }
            return listOf(Command("$llvmBin/llvm-ar").apply {
                +"--rsp-quoting=posix"
                +"qcsDL"
                +executable
                +responseFileArgument(tempFiles, "kcml-ar-members", objectFiles + staticLibraries)
            })
        }
        val arguments = buildList {
            addAll(driverArguments())
            addAll(if (optimize) properties.clangOptFlags else properties.clangNooptFlags)
            if (debug) {
                add("-g")
                addAll(properties.clangDebugFlags)
            }
            if (optimize) addAll(properties.linkerOptimizationFlags)
            if (!debug) addAll(properties.linkerNoDebugFlags)
            if (kind == LinkerOutputKind.DYNAMIC_LIBRARY) {
                add("-shared")
                addAll(properties.linkerDynamicFlags)
            }
            addAll(listOf("-o", executable))
            addAll(objectFiles)
            addAll(staticLibraries)
            addAll(dynamicLibraries)
            addAll(linkerArgs)
            addAll(properties.linkerKonanFlags)
        }
        return listOf(clangCommand(tempFiles, "kcml-link", arguments))
    }

    internal fun preLinkCommands(objectFiles: List<String>, output: String, tempFiles: TempFiles): List<Command> =
        listOf(clangCommand(tempFiles, "kcml-prelink", buildList {
            addAll(driverArguments())
            addAll(listOf("-r", "-nostdlib", "-o", output))
            addAll(objectFiles)
        }))

    override fun preLinkCommands(objectFiles: List<String>, output: String): List<Command> {
        val tempFiles = TempFiles()
        try {
            val command = preLinkCommands(objectFiles, output, tempFiles).single()
            return listOf(object : Command(command.argsWithExecutable) {
                override fun runProcess(): Int = try {
                    super.runProcess()
                }
                finally {
                    tempFiles.dispose()
                }
            })
        } catch (exception: Throwable) {
            tempFiles.dispose()
            throw exception
        }
    }
}