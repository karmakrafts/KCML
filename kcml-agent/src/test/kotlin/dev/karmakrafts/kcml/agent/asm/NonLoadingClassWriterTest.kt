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
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NonLoadingClassWriterTest {
    private class ResourceClassLoader(val definitions: MutableMap<String, ByteArray>) : ClassLoader(null) {
        val loadRequests = mutableListOf<String>()

        override fun getResourceAsStream(name: String): InputStream? =
            definitions[name.removeSuffix(".class")]?.let(::ByteArrayInputStream)
                ?: ClassLoader.getPlatformClassLoader().getResourceAsStream(name)

        override fun loadClass(name: String, resolve: Boolean): Class<*> {
            loadRequests += name
            return super.loadClass(name, resolve)
        }

        override fun findClass(name: String): Class<*> {
            val bytes = definitions[name.replace('.', '/')] ?: throw ClassNotFoundException(name)
            return defineClass(name, bytes, 0, bytes.size)
        }
    }

    private fun hierarchyClass(name: String, superName: String = "java/lang/Object"): ByteArray {
        val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, name, null, superName, null)
        writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null).apply {
            visitCode()
            visitVarInsn(Opcodes.ALOAD, 0)
            visitMethodInsn(Opcodes.INVOKESPECIAL, superName, "<init>", "()V", false)
            visitInsn(Opcodes.RETURN)
            visitMaxs(0, 0)
            visitEnd()
        }
        writer.visitEnd()
        return writer.toByteArray()
    }

    private fun mergedConstructor(
        first: String, second: String, fieldType: String, name: String = "test/Holder"
    ): ClassNode {
        val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null)
        writer.visitField(Opcodes.ACC_PUBLIC, "value", "L$fieldType;", null, null).visitEnd()
        writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(Z)V", null, null).apply {
            visitCode()
            visitVarInsn(Opcodes.ALOAD, 0)
            visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
            visitVarInsn(Opcodes.ALOAD, 0)
            visitVarInsn(Opcodes.ILOAD, 1)
            val otherwise = Label()
            val merge = Label()
            visitJumpInsn(Opcodes.IFEQ, otherwise)
            visitTypeInsn(Opcodes.NEW, first)
            visitInsn(Opcodes.DUP)
            visitMethodInsn(Opcodes.INVOKESPECIAL, first, "<init>", "()V", false)
            visitJumpInsn(Opcodes.GOTO, merge)
            visitLabel(otherwise)
            visitTypeInsn(Opcodes.NEW, second)
            visitInsn(Opcodes.DUP)
            visitMethodInsn(Opcodes.INVOKESPECIAL, second, "<init>", "()V", false)
            visitLabel(merge)
            visitFieldInsn(Opcodes.PUTFIELD, name, "value", "L$fieldType;")
            visitInsn(Opcodes.RETURN)
            visitMaxs(0, 0)
            visitEnd()
        }
        writer.visitEnd()
        return ClassNode().also { ClassReader(writer.toByteArray()).accept(it, 0) }
    }

    private fun verifyMergedConstructor(first: String, second: String, name: String = "test/Holder") {
        val loader = ResourceClassLoader(
            mutableMapOf(
                "test/Base" to hierarchyClass("test/Base"),
                "test/Child" to hierarchyClass("test/Child", "test/Base"),
                "test/Sibling" to hierarchyClass("test/Sibling", "test/Base")
            )
        )
        val node = mergedConstructor(first, second, "test/Base", name)
        if (name == "test/Base") {
            val base = ClassNode().also { ClassReader(loader.definitions.remove(name)!!).accept(it, 0) }
            node.methods.addAll(base.methods)
        }
        val writer = NonLoadingClassWriter(ClassWriter.COMPUTE_FRAMES, loader, node)
        node.accept(writer)
        assertTrue(loader.loadRequests.isEmpty(), "Frame computation must not load classes")
        loader.definitions[node.name] = writer.toByteArray()
        val holder = loader.loadClass(name.replace('/', '.'))
        val constructor = holder.getConstructor(Boolean::class.javaPrimitiveType)
        for ((branch, expected) in listOf(true to first, false to second)) {
            val instance = constructor.newInstance(branch)
            assertEquals(expected.replace('/', '.'), holder.getField("value").get(instance).javaClass.name)
        }
    }

    @Test
    fun `constructor merges a subtype with its base before putfield`() {
        verifyMergedConstructor("test/Child", "test/Base")
    }

    @Test
    fun `constructor merges sibling subclasses before putfield`() {
        verifyMergedConstructor("test/Child", "test/Sibling")
    }

    @Test
    fun `constructor uses current class header when target has no resource`() {
        verifyMergedConstructor("test/Child", "test/Base", "test/Base")
    }

    private fun commonSuperClass(writer: NonLoadingClassWriter, first: String, second: String): String {
        val method = NonLoadingClassWriter::class.java.getDeclaredMethod(
            "getCommonSuperClass", String::class.java, String::class.java
        ).apply { isAccessible = true }
        return method.invoke(writer, first, second) as String
    }

    @Test
    fun `uses object as the common superclass without loading classes`() {
        val loader = ResourceClassLoader(mutableMapOf())
        val writer = NonLoadingClassWriter(ClassWriter.COMPUTE_FRAMES, loader)
        assertEquals(
            Types.any.internalName, commonSuperClass(writer, "unavailable/First", "unavailable/Second")
        )
        assertTrue(loader.loadRequests.isEmpty())
    }

    @Test
    fun `current header takes precedence over resource header`() {
        val loader = ResourceClassLoader(mutableMapOf("test/Current" to hierarchyClass("test/Current")))
        val node = ClassNode().apply {
            name = "test/Current"
            superName = "java/util/ArrayList"
        }
        val writer = NonLoadingClassWriter(ClassWriter.COMPUTE_FRAMES, loader, node)
        assertEquals("java/util/AbstractList", commonSuperClass(writer, node.name, "java/util/AbstractList"))
        assertEquals("java/util/List", commonSuperClass(writer, "java/util/List", node.name))
        assertTrue(loader.loadRequests.isEmpty())
    }

    @Test
    fun `resolves inherited interfaces and unrelated classes without loading`() {
        val loader = ResourceClassLoader(mutableMapOf())
        val writer = NonLoadingClassWriter(ClassWriter.COMPUTE_FRAMES, loader)
        val cases = listOf(
            Triple("java/util/List", "java/util/ArrayList", "java/util/List"),
            Triple("java/util/ArrayList", "java/util/List", "java/util/List"),
            Triple("java/util/List", "java/util/Set", "java/lang/Object"),
            Triple("java/lang/String", "java/lang/Integer", "java/lang/Object"),
            Triple("java/util/ArrayList", "java/util/LinkedList", "java/util/AbstractList")
        )
        for ((first, second, expected) in cases) {
            assertEquals(expected, commonSuperClass(writer, first, second), "$first / $second")
        }
        assertTrue(loader.loadRequests.isEmpty())
    }

    @Test
    fun `resolves reference and primitive array hierarchies without loading`() {
        val loader = ResourceClassLoader(mutableMapOf())
        val writer = NonLoadingClassWriter(ClassWriter.COMPUTE_FRAMES, loader)
        val cases = listOf(
            Triple("[Ljava/lang/Integer;", "[Ljava/lang/Double;", "[Ljava/lang/Number;"),
            Triple("[[Ljava/lang/Integer;", "[Ljava/lang/Number;", "[Ljava/lang/Object;"),
            Triple("[I", "[I", "[I"),
            Triple("[I", "[J", "java/lang/Object"),
            Triple("[[I", "[[J", "[Ljava/lang/Object;"),
            Triple("[I", "[Ljava/lang/Object;", "java/lang/Object"),
            Triple("[[I", "[Ljava/lang/Object;", "[Ljava/lang/Object;"),
            Triple("[I", "java/lang/Cloneable", "java/lang/Cloneable"),
            Triple("java/io/Serializable", "[I", "java/io/Serializable")
        )
        for ((first, second, expected) in cases) {
            assertEquals(expected, commonSuperClass(writer, first, second), "$first / $second")
        }
        assertTrue(loader.loadRequests.isEmpty())
    }
}