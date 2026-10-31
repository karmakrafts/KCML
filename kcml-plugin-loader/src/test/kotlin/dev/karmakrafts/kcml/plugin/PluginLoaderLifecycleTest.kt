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

import dev.karmakrafts.kcml.api.extension.AbstractExtension
import dev.karmakrafts.kcml.api.extension.ExtensionId
import dev.karmakrafts.kcml.api.extension.llvm.LateNativeExtension
import dev.karmakrafts.kcml.api.plugin.CompilerPlugin
import dev.karmakrafts.kcml.api.plugin.Plugin
import dev.karmakrafts.kcml.api.plugin.PluginLoadContext
import dev.karmakrafts.kcml.hooks.CommonHooks
import dev.karmakrafts.kcml.hooks.KCMLHookApi
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

@Plugin("lifecycle_test_plugin")
class LifecycleTestPlugin : CompilerPlugin {
    override fun load(context: PluginLoadContext) {
        context.extensionRegistry.register(LifecycleTestExtension())
    }
}

@ExtensionId("lifecycle_test_extension")
private class LifecycleTestExtension : AbstractExtension(), LateNativeExtension

@OptIn(KCMLHookApi::class, CompilerConfiguration.Internals::class)
class PluginLoaderLifecycleTest {
    @Test
    fun `concurrent compiler invocations do not reset each others plugins`() {
        val compiler = K2JVMCompiler()
        val arguments = K2JVMCompilerArguments()
        CommonHooks.onExecImpl(compiler, arguments)
        PluginLoaderImpl.loadAndInvokeStandalone(CompilerConfiguration(), emptyList())
        val dispatcher = PluginLoaderImpl.extensionDispatcher
        try {
            CompletableFuture.runAsync {
                CommonHooks.onExecImpl(K2JVMCompiler(), K2JVMCompilerArguments())
                PluginLoaderImpl.loadAndInvokeStandalone(CompilerConfiguration(), emptyList())
                assertNotSame(dispatcher, PluginLoaderImpl.extensionDispatcher)
            }.get(10, TimeUnit.SECONDS)
            assertSame(dispatcher, PluginLoaderImpl.extensionDispatcher)
            assertSame(arguments, CommonHooks.compilerArguments)
        }
        finally {
            CommonHooks.onExecImpl(compiler, K2JVMCompilerArguments())
        }
    }

    @Test
    fun `code generation workers inherit the current compilation plugins`() {
        val compiler = K2JVMCompiler()
        CommonHooks.onExecImpl(compiler, K2JVMCompilerArguments())
        PluginLoaderImpl.loadAndInvokeStandalone(CompilerConfiguration(), emptyList())
        val dispatcher = PluginLoaderImpl.extensionDispatcher
        val result = CompletableFuture<Any>()
        val worker = Thread {
            try {
                result.complete(PluginLoaderImpl.extensionDispatcher)
            }
            catch (error: Throwable) {
                result.completeExceptionally(error)
            }
        }
        try {
            worker.start()
            assertSame(dispatcher, result.get(10, TimeUnit.SECONDS))
        }
        finally {
            worker.join(10_000)
            CommonHooks.onExecImpl(compiler, K2JVMCompilerArguments())
        }
    }

    private fun writePluginJar(path: Path, generation: Int) {
        val name = "dev/karmakrafts/kcml/test/ReloadablePlugin"
        val writer = ClassWriter(0)
        writer.visit(
            Opcodes.V21, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", arrayOf(
                "dev/karmakrafts/kcml/api/plugin/CompilerPlugin"
            )
        )
        writer.visitAnnotation("Ldev/karmakrafts/kcml/api/plugin/Plugin;", true).apply {
            visit("id", "reloadable_plugin")
            visitEnd()
        }
        writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null).apply {
            visitCode()
            visitVarInsn(Opcodes.ALOAD, 0)
            visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
            visitInsn(Opcodes.RETURN)
            visitMaxs(1, 1)
            visitEnd()
        }
        for ([method, parameter] in listOf(
            "load" to "dev/karmakrafts/kcml/api/plugin/PluginLoadContext",
            "processIpms" to "dev/karmakrafts/kcml/api/ipm/IPM"
        )) {
            writer.visitMethod(Opcodes.ACC_PUBLIC, method, "(L$parameter;)V", null, null).apply {
                visitCode()
                visitInsn(Opcodes.RETURN)
                visitMaxs(0, 2)
                visitEnd()
            }
        }
        writer.visitMethod(Opcodes.ACC_PUBLIC, "generation", "()I", null, null).apply {
            visitCode()
            visitLdcInsn(generation)
            visitInsn(Opcodes.IRETURN)
            visitMaxs(1, 1)
            visitEnd()
        }
        writer.visitEnd()
        JarOutputStream(Files.newOutputStream(path)).use { archive ->
            for ([entry, bytes] in mapOf(
                "$name.class" to writer.toByteArray(),
                "META-INF/services/${CompilerPlugin::class.java.name}" to name.replace('/', '.').toByteArray(),
                "reloadable_plugin.json" to """{"id":"reloadable_plugin"}""".toByteArray()
            )) {
                archive.putNextEntry(JarEntry(entry))
                archive.write(bytes)
                archive.closeEntry()
            }
        }
    }

    @Test
    fun `new compiler invocations observe replacement jars at the same path`() {
        val directory = Files.createTempDirectory(Path.of("build"), "plugin-reload-")
        val compiler = K2JVMCompiler()
        try {
            val archive = directory.resolve("plugin.jar")
            for (generation in 1..2) {
                CommonHooks.onExecImpl(compiler, K2JVMCompilerArguments())
                writePluginJar(archive, generation)
                PluginLoaderImpl.loadAndInvokeStandalone(CompilerConfiguration(), listOf(archive.toUri().toURL()))
                val plugin = PluginLoaderImpl.getPlugin("reloadable_plugin")
                assertEquals(generation, plugin.javaClass.getMethod("generation").invoke(plugin))
            }
        }
        finally {
            CommonHooks.onExecImpl(compiler, K2JVMCompilerArguments())
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `new compiler invocations reload plugins and clear removed plugins`() {
        val directory = Files.createTempDirectory(Path.of("build"), "plugin-lifecycle-")
        try {
            val service = directory.resolve("META-INF/services/${CompilerPlugin::class.java.name}")
            Files.createDirectories(service.parent)
            Files.writeString(service, LifecycleTestPlugin::class.java.name)
            val urls = listOf(directory.toUri().toURL())
            val compiler = K2JVMCompiler()

            CommonHooks.onExecImpl(compiler, K2JVMCompilerArguments())
            PluginLoaderImpl.loadAndInvokeStandalone(CompilerConfiguration(), urls)
            val first = PluginLoaderImpl.getPlugin("lifecycle_test_plugin")
            val firstIpm = PluginLoaderImpl.getIpm("lifecycle_test_plugin")
            val firstExtension = PluginLoaderImpl.extensionDispatcher.lateNativeExtensions.single().second
            assertEquals(listOf("lifecycle_test_plugin"), PluginLoaderImpl.allPluginsSorted())

            CommonHooks.onExecImpl(compiler, K2JVMCompilerArguments())
            PluginLoaderImpl.loadAndInvokeStandalone(CompilerConfiguration(), urls)
            assertNotSame(first, PluginLoaderImpl.getPlugin("lifecycle_test_plugin"))
            assertNotSame(firstIpm, PluginLoaderImpl.getIpm("lifecycle_test_plugin"))
            assertNotSame(firstExtension, PluginLoaderImpl.extensionDispatcher.lateNativeExtensions.single().second)

            CommonHooks.onExecImpl(compiler, K2JVMCompilerArguments())
            PluginLoaderImpl.loadAndInvokeStandalone(CompilerConfiguration(), emptyList())
            assertTrue(PluginLoaderImpl.allPluginsSorted().isEmpty())
            assertTrue(PluginLoaderImpl.extensionDispatcher.lateNativeExtensions.isEmpty())
        }
        finally {
            directory.toFile().deleteRecursively()
        }
    }
}