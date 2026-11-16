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

import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals

class PluginClassScannerTest {
    private fun classBytes(name: String, access: Int, annotation: String?, visible: Boolean = true): ByteArray {
        val writer = ClassWriter(0)
        writer.visit(Opcodes.V21, access, name, null, "java/lang/Object", null)
        annotation?.let { writer.visitAnnotation(it, visible).visitEnd() }
        writer.visitEnd()
        return writer.toByteArray()
    }

    @Test
    fun `scans class directories and jars without loading unrelated classes`() {
        val directory = Files.createTempDirectory(Path.of("build"), "plugin-scan-")
        try {
            val classes = linkedMapOf(
                "sample/Plugin.class" to classBytes(
                    "sample/Plugin", Opcodes.ACC_PUBLIC, "Ldev/karmakrafts/kcml/api/plugin/Plugin;"
                ), "sample/AbstractPlugin.class" to classBytes(
                    "sample/AbstractPlugin",
                    Opcodes.ACC_PUBLIC or Opcodes.ACC_ABSTRACT,
                    "Ldev/karmakrafts/kcml/api/plugin/Plugin;"
                ), "sample/PluginInterface.class" to classBytes(
                    "sample/PluginInterface",
                    Opcodes.ACC_PUBLIC or Opcodes.ACC_ABSTRACT or Opcodes.ACC_INTERFACE,
                    "Ldev/karmakrafts/kcml/api/plugin/Plugin;"
                ), "sample/OtherAnnotation.class" to classBytes(
                    "sample/OtherAnnotation", Opcodes.ACC_PUBLIC, "Ldev/karmakrafts/kcml/api/plugin/PluginExtra;"
                ), "sample/InvisiblePlugin.class" to classBytes(
                    "sample/InvisiblePlugin",
                    Opcodes.ACC_PUBLIC,
                    "Ldev/karmakrafts/kcml/api/plugin/Plugin;",
                    visible = false
                ), "sample/UnlinkablePlugin.class" to ClassWriter(0).run {
                    visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/UnlinkablePlugin", null, "missing/Parent", null)
                    visitAnnotation("Ldev/karmakrafts/kcml/api/plugin/Plugin;", true).visitEnd()
                    visitEnd()
                    toByteArray()
                })
            for ((entry, bytes) in classes) {
                val classFile = directory.resolve(entry)
                Files.createDirectories(classFile.parent)
                Files.write(classFile, bytes)
            }
            val archive = directory.resolve("plugins.jar")
            JarOutputStream(Files.newOutputStream(archive)).use { jar ->
                for ((entry, bytes) in classes) {
                    jar.putNextEntry(JarEntry(entry))
                    jar.write(bytes)
                    jar.closeEntry()
                }
            }

            val candidates = PluginClassScanner.scan(
                listOf(directory.toUri().toURL(), directory.toUri().toURL(), archive.toUri().toURL())
            )

            assertEquals(setOf("sample.Plugin", "sample.UnlinkablePlugin"), candidates.toSet())
            assertEquals(2, candidates.size)
        }
        finally {
            directory.toFile().deleteRecursively()
        }
    }
}