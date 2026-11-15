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

import dev.karmakrafts.kcml.api.plugin.Plugin
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.io.InputStream
import java.net.URL
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.io.path.extension
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.walk

internal object PluginClassScanner {
    private val annotationDescriptor: Type = Type.getType(Plugin::class.java)

    fun scan(classpaths: List<URL>): List<String> {
        val candidates = LinkedHashSet<String>()
        for (classpath in classpaths) {
            val path = Path.of(classpath.toURI())
            if (path.isDirectory()) {
                path.walk().filter { filePath ->
                    filePath.isRegularFile() && filePath.extension == "class"
                }.forEach { filePath ->
                    filePath.inputStream().use { stream ->
                        scanClass(stream, candidates)
                    }
                }
            }
            else if (path.isRegularFile()) {
                JarFile(path.toFile()).use { archive ->
                    val entries = archive.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (!entry.isDirectory && entry.name.endsWith(".class")) {
                            archive.getInputStream(entry).use { scanClass(it, candidates) }
                        }
                    }
                }
            }
        }
        return candidates.toList()
    }

    private fun scanClass(bytes: InputStream, candidates: MutableSet<String>) {
        var className: String? = null
        var isConcrete = false
        var hasPluginAnnotation = false
        ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visit(
                version: Int,
                access: Int,
                name: String,
                signature: String?,
                superName: String?,
                interfaces: Array<out String>?
            ) {
                className = name.replace('/', '.')
                isConcrete = (access and (Opcodes.ACC_ABSTRACT or Opcodes.ACC_INTERFACE)) == 0
            }

            override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
                if (visible && descriptor == annotationDescriptor.descriptor) hasPluginAnnotation = true
                return null
            }
        }, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
        if (isConcrete && hasPluginAnnotation) className?.let(candidates::add)
    }
}