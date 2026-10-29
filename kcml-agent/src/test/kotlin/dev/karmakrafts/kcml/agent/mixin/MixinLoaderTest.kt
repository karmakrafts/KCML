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
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MixinLoaderTest {
    private fun createMixin(name: String, annotationType: Type, target: Any?, priority: Int? = null): ClassNode =
        ClassNode().apply {
            version = Opcodes.V17
            access = Opcodes.ACC_PUBLIC
            this.name = name
            superName = "java/lang/Object"
            invisibleAnnotations = arrayListOf(AnnotationNode(annotationType.descriptor).apply {
                if (target != null) visit("target", target)
                if (priority != null) visit("priority", priority)
            })
        }

    private fun Path.createJar(name: String, vararg classes: ClassNode): Path = resolve(name).also { path ->
        JarOutputStream(Files.newOutputStream(path)).use { output ->
            output.putNextEntry(JarEntry("META-INF/"))
            output.closeEntry()
            output.putNextEntry(JarEntry("META-INF/plugin.txt"))
            output.write("not a class".toByteArray())
            output.closeEntry()
            for (classNode in classes) {
                val writer = ClassWriter(0)
                classNode.accept(writer)
                output.putNextEntry(JarEntry("${classNode.name}.class"))
                output.write(writer.toByteArray())
                output.closeEntry()
            }
        }
    }

    private fun withDirectory(test: (Path) -> Unit) {
        val directory = Files.createTempDirectory(Path.of("build"), "mixin-loader-test-")
        try {
            test(directory)
        }
        finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `loads direct and indirect mixins without loading their target classes`() = withDirectory { directory ->
        val target = Type.getObjectType("unavailable/Target")
        val direct = createMixin("example/Direct", Types.Mixin.directMixin, target)
        val indirect = createMixin("example/Indirect", Types.Mixin.indirectMixin, "unavailable.Target", 7)
        val ordinary = ClassNode().apply {
            version = Opcodes.V17
            access = Opcodes.ACC_PUBLIC
            name = "example/Ordinary"
            superName = "java/lang/Object"
        }
        val loader = MixinLoader(NoopLogger)

        loader.load(listOf(directory.createJar("plugin.jar", indirect, ordinary, direct)))

        assertEquals(listOf("example/Direct", "example/Indirect"), loader.mixins.map { it.mixinClass.name })
        assertEquals(listOf(0, 7), loader.mixins.map { it.priority })
        assertTrue(loader.mixins.all { it.target == target })
        assertTrue(loader.isMixin(Type.getObjectType(direct.name)))
        assertTrue(loader.isMixin(Type.getObjectType(indirect.name)))
        assertFalse(loader.isMixin(target))
        assertFalse(loader.isMixin(Type.getObjectType(ordinary.name)))
    }

    @Test
    fun `sorts priorities across all plugin jars regardless of input order`() = withDirectory { directory ->
        val target = Type.getObjectType("example/Target")
        val first = directory.createJar(
            "first.jar",
            createMixin("example/Last", Types.Mixin.directMixin, target, Int.MAX_VALUE),
            createMixin("example/Middle", Types.Mixin.directMixin, target, 0)
        )
        val second = directory.createJar(
            "second.jar", createMixin("example/First", Types.Mixin.directMixin, target, Int.MIN_VALUE)
        )
        val loader = MixinLoader(NoopLogger)

        for (paths in listOf(listOf(first, second), listOf(second, first))) {
            loader.load(paths)
            assertEquals(
                listOf("example/First", "example/Middle", "example/Last"), loader.mixins.map { it.mixinClass.name })
        }
    }

    @Test
    fun `skips mixins with missing targets while retaining valid entries`() = withDirectory { directory ->
        val loader = MixinLoader(NoopLogger)
        val jar = directory.createJar(
            "plugin.jar",
            createMixin("example/MissingDirect", Types.Mixin.directMixin, null),
            createMixin("example/MissingIndirect", Types.Mixin.indirectMixin, null),
            createMixin("example/Valid", Types.Mixin.indirectMixin, "example.Target")
        )

        loader.load(listOf(jar))

        assertEquals(listOf("example/Valid"), loader.mixins.map { it.mixinClass.name })
    }

    @Test
    fun `reload replaces mixins and ignores unsupported paths`() = withDirectory { directory ->
        val loader = MixinLoader(NoopLogger)
        val first = createMixin("example/First", Types.Mixin.indirectMixin, "example.Target")
        val second = createMixin("example/Second", Types.Mixin.indirectMixin, "example.Target")
        loader.load(listOf(directory.createJar("first.jar", first)))

        loader.load(
            listOf(directory, directory.resolve("missing.jar"), directory.createJar("second.jar", second))
        )

        assertEquals(listOf(second.name), loader.mixins.map { it.mixinClass.name })
        assertFalse(loader.isMixin(Type.getObjectType(first.name)))
        loader.load(emptyList())
        assertTrue(loader.mixins.isEmpty())
    }
}