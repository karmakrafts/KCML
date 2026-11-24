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
import java.io.IOException
import java.net.JarURLConnection
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Path
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.jar.JarFile
import kotlin.concurrent.read
import kotlin.concurrent.write

internal class SyntaxPluginClassLoader(urls: Array<URL>, parent: ClassLoader) : URLClassLoader(urls, parent) {
    companion object {
        init {
            registerAsParallelCapable()
        }

        private val embeddedPrefix: String = TypeNames.EMBEDDED_INTELLIJ_PACKAGE.replace('.', '/')
        private val kotlinPrefix: String = TypeNames.KOTLIN_PACKAGE.replace('.', '/')
    }

    private val archives = HashMap<URL, JarFile>()
    private val resourceLock = ReentrantReadWriteLock()

    override fun findClass(name: String): Class<*> = resourceLock.read {
        val resource = findResource(name.replace('.', '/') + ".class") ?: throw ClassNotFoundException(name)
        val writer = ClassWriter(0)
        ClassReader(readClass(resource)).accept(ClassRemapper(writer, object : Remapper(Opcodes.ASM9) {
            override fun map(internalName: String): String = if (internalName.startsWith(embeddedPrefix)) {
                internalName.removePrefix(kotlinPrefix)
            }
            else internalName
        }), 0)
        val bytes = writer.toByteArray()
        defineClass(name, bytes, 0, bytes.size)
    }

    private fun readClass(resource: URL): ByteArray {
        val connection = resource.openConnection()
        if (connection is JarURLConnection && connection.jarFileURL.protocol == "file") {
            // Keep archives local to this loader: global URL caches can retain stale plugin builds.
            val archive = synchronized(archives) {
                archives.getOrPut(connection.jarFileURL) { JarFile(Path.of(connection.jarFileURL.toURI()).toFile()) }
            }
            return archive.getInputStream(archive.getJarEntry(connection.entryName)).use { it.readBytes() }
        }
        connection.useCaches = false
        return connection.getInputStream().use { it.readBytes() }
    }

    override fun close(): Unit = resourceLock.write {
        var failure: IOException? = null
        try {
            super.close()
        } catch (exception: IOException) {
            failure = exception
        }
        for (archive in archives.values) {
            try {
                archive.close()
            } catch (exception: IOException) {
                if (failure == null) failure = exception else failure.addSuppressed(exception)
            }
        }
        archives.clear()
        failure?.let { throw it }
    }
}
