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

package dev.karmakrafts.kcml.agent.asm

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import java.io.IOException

internal class NonLoadingClassWriter(
    flags: Int,
    private val loader: ClassLoader? = null,
    classNode: ClassNode? = null
) : ClassWriter(flags) {
    private data class Header(val superName: String?, val interfaces: List<String>, val isInterface: Boolean)

    private val headers = mutableMapOf<String, Header?>().apply {
        if (classNode != null) {
            put(classNode.name, Header(
                classNode.superName, classNode.interfaces.toList(), classNode.access and Opcodes.ACC_INTERFACE != 0
            ))
        }
    }

    /**
     * Resolve resource headers without loading classes: loading during transformation can cause circularity.
     * The current class header takes precedence because the target may not be defined or have a resource yet.
     */
    override fun getCommonSuperClass(type1: String?, type2: String?): String {
        if (type1 == null || type2 == null) return Types.any.internalName
        return commonSuperClass(type1, type2)
    }

    private fun header(name: String): Header? {
        if (headers.containsKey(name)) return headers[name]
        val result = try {
            (loader ?: ClassLoader.getPlatformClassLoader()).getResourceAsStream("$name.class")?.use {
                val reader = ClassReader(it)
                Header(reader.superName, reader.interfaces.toList(), reader.access and Opcodes.ACC_INTERFACE != 0)
            }
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
        headers[name] = result
        return result
    }

    private fun componentType(array: String): String? = when (array[1]) {
        '[' -> array.substring(1)
        'L' -> array.substring(2, array.length - 1)
        else -> null
    }

    private fun isAssignableFrom(target: String, source: String, visited: MutableSet<String> = mutableSetOf()): Boolean {
        if (target == source || target == Types.any.internalName) return true
        if (!visited.add(source)) return false
        if (source.startsWith('[')) {
            if (target == "java/lang/Cloneable" || target == "java/io/Serializable") return true
            if (!target.startsWith('[')) return false
            val targetComponent = componentType(target) ?: return false
            val sourceComponent = componentType(source) ?: return false
            return isAssignableFrom(targetComponent, sourceComponent)
        }
        if (target.startsWith('[')) return false
        val sourceHeader = header(source) ?: return false
        return sourceHeader.superName?.let { isAssignableFrom(target, it, visited) } == true ||
            sourceHeader.interfaces.any { isAssignableFrom(target, it, visited) }
    }

    private fun commonSuperClass(type1: String, type2: String): String {
        if (isAssignableFrom(type1, type2)) return type1
        if (isAssignableFrom(type2, type1)) return type2
        if (type1.startsWith('[') && type2.startsWith('[')) {
            val component1 = componentType(type1)
            val component2 = componentType(type2)
            if (component1 != null && component2 != null) {
                val common = commonSuperClass(component1, component2)
                return if (common.startsWith('[')) "[$common" else "[L$common;"
            }
            return Types.any.internalName
        }
        if (type1.startsWith('[') || type2.startsWith('[')) return Types.any.internalName
        val header1 = header(type1) ?: return Types.any.internalName
        val header2 = header(type2) ?: return Types.any.internalName
        if (header1.isInterface || header2.isInterface) return Types.any.internalName
        val visited = mutableSetOf(type1)
        var parent = header1.superName
        while (parent != null && visited.add(parent)) {
            if (isAssignableFrom(parent, type2)) return parent
            parent = header(parent)?.superName
        }
        return Types.any.internalName
    }
}