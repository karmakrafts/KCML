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

package dev.karmakrafts.kcml.intellij.syntax

import dev.karmakrafts.kcml.intellij.util.TypeNames
import dev.karmakrafts.kcml.internal.org.objectweb.asm.ClassReader
import dev.karmakrafts.kcml.internal.org.objectweb.asm.ClassWriter
import dev.karmakrafts.kcml.internal.org.objectweb.asm.Opcodes
import dev.karmakrafts.kcml.internal.org.objectweb.asm.commons.ClassRemapper
import dev.karmakrafts.kcml.internal.org.objectweb.asm.commons.Remapper
import java.net.URL
import java.net.URLClassLoader

internal class SyntaxPluginClassLoader(urls: Array<URL>, parent: ClassLoader) : URLClassLoader(urls, parent) {
    companion object {
        private val embeddedPrefix: String = TypeNames.EMBEDDED_INTELLIJ_PACKAGE.replace('.', '/')
        private val kotlinPrefix: String = TypeNames.KOTLIN_PACKAGE.replace('.', '/')
    }

    override fun findClass(name: String): Class<*> {
        val resource = findResource(name.replace('.', '/') + ".class") ?: throw ClassNotFoundException(name)
        val writer = ClassWriter(0)
        val connection = resource.openConnection()
        connection.useCaches = false
        connection.getInputStream().use { input ->
            ClassReader(input).accept(ClassRemapper(writer, object : Remapper(Opcodes.ASM9) {
                override fun map(internalName: String): String = if (internalName.startsWith(embeddedPrefix)) {
                    internalName.removePrefix(kotlinPrefix)
                }
                else internalName
            }), 0)
        }
        val bytes = writer.toByteArray()
        return defineClass(name, bytes, 0, bytes.size)
    }
}
