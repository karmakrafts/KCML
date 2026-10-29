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

package dev.karmakrafts.kcml.agent.mixin

import dev.karmakrafts.kcml.agent.asm.Types
import dev.karmakrafts.kcml.agent.log.NoopLogger
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MixinClassTransformerTest {
    private fun createClass(name: String, body: ClassWriter.() -> Unit = {}): ByteArray = ClassWriter(0).apply {
        visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null)
        body()
        visitEnd()
    }.toByteArray()

    private fun withLoader(test: (MixinLoader, Path, ByteArray) -> Unit) {
        val directory = Files.createTempDirectory(Path.of("build"), "mixin-transformer-test-")
        try {
            val mixin = createClass("example/Mixin") {
                visitAnnotation(Types.Mixin.indirectMixin.descriptor, false).apply {
                    visit("target", "example.Target")
                    visitEnd()
                }
                visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "inject", "()V", null, null).apply {
                    visitAnnotation(Types.Mixin.inject.descriptor, false).apply {
                        visit("name", "value")
                        visitEnum("order", Types.Mixin.order.descriptor, "BEFORE")
                        visitAnnotation("target", "Ldev/karmakrafts/kcml/api/mixin/Inject\$Target;").apply {
                            visit("opcode", Opcodes.GETSTATIC)
                            visitEnd()
                        }
                        visitEnd()
                    }
                    visitCode()
                    visitInsn(Opcodes.ICONST_2)
                    visitFieldInsn(Opcodes.PUTSTATIC, "example/Target", "result", "I")
                    visitInsn(Opcodes.RETURN)
                    visitMaxs(1, 0)
                    visitEnd()
                }
            }
            val jar = directory.resolve("plugin.jar")
            JarOutputStream(Files.newOutputStream(jar)).use { output ->
                output.putNextEntry(JarEntry("example/Mixin.class"))
                output.write(mixin)
                output.closeEntry()
            }
            val loader = MixinLoader(NoopLogger).apply { load(listOf(jar)) }
            assertEquals(1, loader.mixins.size)
            test(loader, directory, mixin)
        }
        finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `preserves unrelated classes and malformed bytecode`() {
        val transformer = MixinClassTransformer(NoopLogger, MixinLoader(NoopLogger), null)
        val unrelated = createClass("example/Unrelated")
        val malformed = byteArrayOf(1, 2, 3)

        assertSame(unrelated, transformer.transform(null, null, "example/Unrelated", null, null, unrelated))
        assertSame(malformed, transformer.transform(null, null, "example/Broken", null, null, malformed))
    }

    @Test
    fun `does not transform loaded mixin definitions`() = withLoader { loader, _, mixin ->
        val transformer = MixinClassTransformer(NoopLogger, loader, null)

        assertSame(mixin, transformer.transform(null, null, "example/Mixin", null, null, mixin))
    }

    @Test
    fun `rejects explicit mixin instantiations outside blacklisted packages`() = withLoader { loader, _, _ ->
        val transformer = MixinClassTransformer(NoopLogger, loader, null)
        val consumer = createClass("example/Consumer") {
            visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "create", "()V", null, null).apply {
                visitCode()
                visitTypeInsn(Opcodes.NEW, "example/Mixin")
                visitInsn(Opcodes.POP)
                visitInsn(Opcodes.RETURN)
                visitMaxs(1, 0)
                visitEnd()
            }
        }

        val error = assertFailsWith<MixinRuntimeInstantiationException> {
            transformer.transform(null, null, "example/Consumer", null, null, consumer)
        }
        assertTrue(error.message.orEmpty().contains("example.Mixin"))
        assertTrue(error.message.orEmpty().contains("example.Consumer.create()V"))
        for (prefix in listOf("java/", "javax/", "dev/karmakrafts/kcml/agent/")) {
            assertSame(consumer, transformer.transform(null, null, "${prefix}Consumer", null, null, consumer))
        }
    }

    @Test
    fun `rewrites executable target bytecode and dumps the transformed class`() = withLoader { loader, directory, _ ->
        val target = createClass("example/Target") {
            visitField(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "result", "I", null, null).visitEnd()
            visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "value", "()I", null, null).apply {
                visitCode()
                visitFieldInsn(Opcodes.GETSTATIC, "example/Target", "result", "I")
                visitInsn(Opcodes.IRETURN)
                visitMaxs(1, 0)
                visitEnd()
            }
        }
        val transformer = MixinClassTransformer(NoopLogger, loader, directory)

        val transformed = transformer.transform(null, null, "example/Target", null, null, target)

        assertNotSame(target, transformed)
        val node = ClassNode().apply { ClassReader(transformed).accept(this, 0) }
        assertEquals("example/Target", node.name)
        assertContentEquals(transformed, Files.readAllBytes(directory.resolve("example.Target.class")))
        val targetClass = object : ClassLoader(javaClass.classLoader) {
            fun define(): Class<*> = defineClass("example.Target", transformed, 0, transformed.size)
        }.define()
        assertEquals(2, targetClass.getMethod("value").invoke(null))
    }
}