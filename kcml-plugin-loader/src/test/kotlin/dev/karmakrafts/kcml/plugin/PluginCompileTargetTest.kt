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

import dev.karmakrafts.kcml.api.backend.IrBackend
import dev.karmakrafts.kcml.api.plugin.CompilerPlugin
import dev.karmakrafts.kcml.api.plugin.InitializationContext
import dev.karmakrafts.kcml.api.plugin.Plugin
import dev.karmakrafts.kcml.api.plugin.PreInitializationContext
import dev.karmakrafts.kcml.api.target.CompileTarget
import dev.karmakrafts.kcml.api.target.CompileTargetFactory
import dev.karmakrafts.kcml.api.target.CompileTargetRegistry
import dev.karmakrafts.kcml.api.target.CompileTargetType
import dev.karmakrafts.kcml.api.target.JsCompileTarget
import dev.karmakrafts.kcml.api.target.JvmCompileTarget
import dev.karmakrafts.kcml.api.target.NativeCompileTarget
import dev.karmakrafts.kcml.api.target.WasmCompileTarget
import dev.karmakrafts.kcml.backend.create
import dev.karmakrafts.kcml.hooks.CommonHooks
import dev.karmakrafts.kcml.hooks.KCMLHookApi
import dev.karmakrafts.kcml.hooks.llvm.NativeTargetHooks
import dev.karmakrafts.kcml.log.MessageCollectorLoggerFactory
import dev.karmakrafts.kcml.util.kcmlCompileTarget
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.konan.config.konanTarget
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.js.JsPlatforms
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.platform.konan.NativePlatformWithTarget
import org.jetbrains.kotlin.platform.toTargetPlatform
import org.jetbrains.kotlin.platform.wasm.WasmPlatforms
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.lang.invoke.MethodHandles
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private val customKonanTarget: KonanTarget by lazy {
    val name = "dev/karmakrafts/kcml/plugin/CustomKonanTarget"
    val writer = ClassWriter(0)
    writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, name, null, "org/jetbrains/kotlin/konan/target/KonanTarget", null)
    writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null).apply {
        visitCode()
        visitVarInsn(Opcodes.ALOAD, 0)
        visitLdcInsn("custom_linux_x64")
        visitFieldInsn(
            Opcodes.GETSTATIC,
            "org/jetbrains/kotlin/konan/target/Family",
            "LINUX",
            "Lorg/jetbrains/kotlin/konan/target/Family;"
        )
        visitFieldInsn(
            Opcodes.GETSTATIC,
            "org/jetbrains/kotlin/konan/target/Architecture",
            "X64",
            "Lorg/jetbrains/kotlin/konan/target/Architecture;"
        )
        visitInsn(Opcodes.ACONST_NULL)
        visitMethodInsn(
            Opcodes.INVOKESPECIAL,
            "org/jetbrains/kotlin/konan/target/KonanTarget",
            "<init>",
            "(Ljava/lang/String;Lorg/jetbrains/kotlin/konan/target/Family;Lorg/jetbrains/kotlin/konan/target/Architecture;Lkotlin/jvm/internal/DefaultConstructorMarker;)V",
            false
        )
        visitInsn(Opcodes.RETURN)
        visitMaxs(5, 1)
        visitEnd()
    }
    writer.visitEnd()
    MethodHandles.lookup().defineClass(writer.toByteArray()).getConstructor().newInstance() as KonanTarget
}

@Plugin("target_wiring_test")
class TargetWiringTestPlugin : CompilerPlugin {
    var creationCount: Int = 0
    lateinit var preInitTarget: CompileTarget
    lateinit var initTarget: CompileTarget

    override fun registerTargets(registry: CompileTargetRegistry) {
        for (alias in listOf("custom_jvm", "custom_js", "custom_native", "custom_wasm")) {
            registry.register(object : CompileTargetType {
                override val aliases: Set<String> = setOf(alias)
            }, CompileTargetFactory {
                ++creationCount
                when (alias) {
                    "custom_jvm" -> object : JvmCompileTarget {
                        override val name: String = "Custom Android"
                        override val isAndroid: Boolean = true
                    }

                    "custom_js" -> object : JsCompileTarget {
                        override val name: String = "Custom JS"
                    }

                    "custom_native" -> object : NativeCompileTarget {
                        override val name: String = "Custom Native"
                        override val konanTarget: KonanTarget = customKonanTarget
                    }

                    else -> object : WasmCompileTarget {
                        override val name: String = "Custom Wasm"
                    }
                }
            })
        }
    }

    override fun preInit(context: PreInitializationContext) {
        preInitTarget = context.compileTarget
    }

    override fun init(context: InitializationContext) {
        initTarget = context.compileTarget
    }
}

@OptIn(KCMLHookApi::class, CompilerConfiguration.Internals::class)
class PluginCompileTargetTest {
    private fun withTarget(alias: String, action: (CompilerConfiguration, TargetWiringTestPlugin) -> Unit) {
        val directory = Files.createTempDirectory(Path.of("build"), "plugin-target-")
        try {
            val pluginClass = TargetWiringTestPlugin::class.java
            val classResource = pluginClass.name.replace('.', '/') + ".class"
            val classFile = directory.resolve(classResource)
            Files.createDirectories(classFile.parent)
            pluginClass.getResourceAsStream("/$classResource")!!.use { Files.copy(it, classFile) }
            CommonHooks.onExecImpl(K2JVMCompiler(), K2JVMCompilerArguments())
            val config = CompilerConfiguration().apply { kcmlCompileTarget = alias }
            PluginLoaderImpl.loadAndInvokeStandalone(config, listOf(directory.toUri().toURL()))
            action(config, PluginLoaderImpl.getPlugin("target_wiring_test") as TargetWiringTestPlugin)
        }
        finally {
            CommonHooks.onExecImpl(K2JVMCompiler(), K2JVMCompilerArguments())
            directory.toFile().deleteRecursively()
        }
    }

    private fun backend(config: CompilerConfiguration, platform: TargetPlatform): IrBackend {
        val context = Proxy.newProxyInstance(
            IrPluginContext::class.java.classLoader, arrayOf(IrPluginContext::class.java)
        ) { _, method, _ ->
            check(method.name == "getPlatform") { "Unexpected context access: ${method.name}" }
            platform
        } as IrPluginContext
        val loggerFactory = MessageCollectorLoggerFactory(PluginLoaderImpl, MessageCollector.NONE)
        return IrBackend.create(
            "target_wiring_test", context, config, loggerFactory, PluginLoaderImpl.logger, PluginLoaderImpl
        )
    }

    @Test
    fun `JVM backend retains custom Android target`() = withTarget("custom_jvm") { config, plugin ->
        val backend = backend(config, JvmPlatforms.defaultJvmPlatform)
        assertSame(plugin.initTarget, backend.compileTarget)
        assertTrue((backend.compileTarget as JvmCompileTarget).isAndroid)
    }

    @Test
    fun `JS backend retains custom target`() = withTarget("custom_js") { config, plugin ->
        assertSame(plugin.initTarget, backend(config, JsPlatforms.defaultJsPlatform).compileTarget)
    }

    @Test
    fun `Native backend accepts custom Konan platform`() = withTarget("custom_native") { config, plugin ->
        val target = plugin.initTarget as NativeCompileTarget
        val backend = backend(config, NativePlatformWithTarget(target.konanTarget).toTargetPlatform())
        assertSame(target, backend.compileTarget)
        assertSame(target.konanTarget, (backend.compileTarget as NativeCompileTarget).konanTarget)
        assertEquals(target.konanTarget.name, config.konanTarget)
    }

    @Test
    fun `Wasm backend retains custom target`() = withTarget("custom_wasm") { config, plugin ->
        assertSame(plugin.initTarget, backend(config, WasmPlatforms.wasmJs).compileTarget)
    }

    @Test
    fun `target is realized once and shared between lifecycle phases`() = withTarget("custom_jvm") { _, plugin ->
        assertEquals(1, plugin.creationCount)
        assertSame(plugin.preInitTarget, plugin.initTarget)
    }

    @Test
    fun `code generation workers inherit the selected target`() = withTarget("custom_jvm") { config, plugin ->
        val result = CompletableFuture<CompileTarget>()
        val worker = Thread {
            try {
                result.complete(PluginLoaderImpl.getCompileTarget(config))
            } catch (error: Throwable) {
                result.completeExceptionally(error)
            }
        }
        try {
            worker.start()
            assertSame(plugin.initTarget, result.get(10, TimeUnit.SECONDS))
            assertEquals(1, plugin.creationCount)
        }
        finally {
            worker.join(10_000)
        }
    }

    @Test
    fun `native target hooks are scoped to the current compilation`() = withTarget("custom_native") { _, plugin ->
        val target = (plugin.initTarget as NativeCompileTarget).konanTarget
        assertSame(target, NativeTargetHooks.onGetTargetValues()!!.single { it.name == target.name })
        assertSame(target, NativeTargetHooks.onGetEnabled(HostManager())!!.single { it.name == target.name })
        assertFalse(KonanTarget.predefinedTargets.containsKey(target.name))

        CommonHooks.onExecImpl(K2JVMCompiler(), K2JVMCompilerArguments())

        assertNull(PluginLoaderImpl.compileTargetOrNull)
        assertNull(NativeTargetHooks.onGetTargetValues())
        assertNull(NativeTargetHooks.onGetEnabled(HostManager()))
        val config = CompilerConfiguration().apply { kcmlCompileTarget = "jvm" }
        assertFalse((PluginLoaderImpl.getCompileTarget(config) as JvmCompileTarget).isAndroid)
    }

    @Test
    fun `incompatible target is not silently replaced by a built in target`() = withTarget("custom_js") { config, _ ->
        assertFailsWith<ClassCastException> {
            backend(config, JvmPlatforms.defaultJvmPlatform).compileTarget
        }
    }
}