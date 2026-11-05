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

import dev.karmakrafts.kcml.api.plugin.CompilerPlugin
import dev.karmakrafts.kcml.api.plugin.InitializationContext
import dev.karmakrafts.kcml.api.plugin.Plugin
import dev.karmakrafts.kcml.api.plugin.PreInitializationContext
import dev.karmakrafts.kcml.hooks.CommonHooks
import dev.karmakrafts.kcml.hooks.KCMLHookApi
import dev.karmakrafts.kcml.util.kcmlCompileTarget
import dev.karmakrafts.kcml.util.kcmlPluginClasspaths
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.cli.plugins.processCompilerPluginsOptions
import org.jetbrains.kotlin.compiler.plugin.CliOptionProcessingException
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey
import org.jetbrains.kotlin.konan.config.konanTarget
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame

@Plugin("options_test")
class OptionsTestPlugin : CompilerPlugin {
    companion object {
        val valueKey: CompilerConfigurationKey<Int> = CompilerConfigurationKey.create("options_test.value")
        val entriesKey: CompilerConfigurationKey<List<String>> = CompilerConfigurationKey.create("options_test.entries")
    }

    var preInitCount: Int = 0
    var initCount: Int = 0
    var initializedValue: Int? = null
    var compileTargetName: String? = null

    override fun preInit(context: PreInitializationContext) {
        assertEquals("options_test", PluginLoaderImpl.loadingPluginId)
        ++preInitCount
        compileTargetName = context.compileTarget.name
        context.addOption("value", "<int>", "An integer") { value, config ->
            config.put(valueKey, value.toInt())
        }
        context.addOption(
            "entry", "<string>", "An entry", required = false, allowMultipleOccurrences = true
        ) { value, config ->
            config.add(entriesKey, value)
        }
    }

    override fun init(context: InitializationContext) {
        assertEquals(1, preInitCount)
        ++initCount
        initializedValue = context.config[valueKey]
    }
}

@Plugin("dependent_options_test")
class DependentOptionsTestPlugin : CompilerPlugin {
    var initializedValue: String? = null

    override fun preInit(context: PreInitializationContext) {
        val dependency = PluginLoaderImpl.getPlugin("options_test") as OptionsTestPlugin
        assertEquals(1, dependency.preInitCount)
        assertEquals(0, dependency.initCount)
        context.addOption("value", "<string>", "An independent value") { value, _ -> initializedValue = value }
    }

    override fun init(context: InitializationContext) {
        val dependency = context.loader.getPlugin("options_test") as OptionsTestPlugin
        assertEquals(1, dependency.initCount)
    }
}

@Plugin("failing_options_test")
class FailingOptionsTestPlugin : CompilerPlugin {
    override fun preInit(context: PreInitializationContext) {
        error("preInit failure")
    }

    override fun init(context: InitializationContext) {
        error("init must not run after preInit fails")
    }
}

@OptIn(KCMLHookApi::class, CompilerConfiguration.Internals::class, ExperimentalCompilerApi::class)
class PluginPreInitializationTest {
    @AfterTest
    fun reset() {
        CommonHooks.onExecImpl(K2JVMCompiler(), K2JVMCompilerArguments())
    }

    private fun withPlugin(
        options: Array<String>,
        plugins: List<Class<out CompilerPlugin>> = listOf(OptionsTestPlugin::class.java),
        compileTarget: String? = "jvm",
        action: (CompilerConfiguration, K2JVMCompilerArguments) -> Unit
    ) {
        val directory = Files.createTempDirectory(Path.of("build"), "plugin-options-")
        try {
            val service = directory.resolve("META-INF/services/${CompilerPlugin::class.java.name}")
            Files.createDirectories(service.parent)
            Files.writeString(service, plugins.joinToString("\n") { it.name })
            val arguments = K2JVMCompilerArguments().apply {
                pluginClasspaths = arrayOf(directory.toString())
                pluginOptions = options
            }
            CommonHooks.onExecImpl(K2JVMCompiler(), arguments)
            val config = CompilerConfiguration().apply {
                if (compileTarget != null) kcmlCompileTarget = compileTarget
            }
            action(config, arguments)
        }
        finally {
            reset()
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `preInit registers options before init and runs once per compilation`() = withPlugin(
        arrayOf(
            "plugin:kcml:options_testValue=42",
            "plugin:kcml:options_testEntry=first",
            "plugin:kcml:options_testEntry=second"
        )
    ) { config, arguments ->
        PluginLoaderImpl.loadAndInvokeStandalone(config, arguments)
        val plugin = PluginLoaderImpl.getPlugin("options_test") as OptionsTestPlugin
        assertEquals(1, plugin.preInitCount)
        assertEquals(1, plugin.initCount)
        assertEquals(42, plugin.initializedValue)
        assertEquals(listOf("first", "second"), config[OptionsTestPlugin.entriesKey])
        PluginLoaderImpl.loadAndInvokeStandalone(config, arguments)
        assertSame(plugin, PluginLoaderImpl.getPlugin("options_test"))
        assertEquals(1, plugin.preInitCount)
        assertEquals(1, plugin.initCount)
        assertEquals(listOf("first", "second"), config[OptionsTestPlugin.entriesKey])
    }

    @Test
    fun `native target is inferred from Konan configuration when KCML target is absent`() = withPlugin(
        arrayOf("plugin:kcml:options_testValue=1"), compileTarget = null
    ) { config, arguments ->
        config.konanTarget = "linux_x64"

        PluginLoaderImpl.loadAndInvokeStandalone(config, arguments)

        val plugin = PluginLoaderImpl.getPlugin("options_test") as OptionsTestPlugin
        assertEquals("linux_x64", plugin.compileTargetName)
    }

    @Test
    fun `missing required options are rejected before init`() = withPlugin(emptyArray()) { config, arguments ->
        assertFailsWith<CliOptionProcessingException> {
            PluginLoaderImpl.loadAndInvokeStandalone(config, arguments)
        }
        assertEquals(0, (PluginLoaderImpl.getPlugin("options_test") as OptionsTestPlugin).initCount)
    }

    @Test
    fun `single occurrence options reject repeated values`() = withPlugin(
        arrayOf("plugin:kcml:options_testValue=1", "plugin:kcml:options_testValue=2")
    ) { config, arguments ->
        assertFailsWith<CliOptionProcessingException> {
            PluginLoaderImpl.loadAndInvokeStandalone(config, arguments)
        }
    }

    @Test
    fun `unknown options for a loaded plugin are rejected`() = withPlugin(
        arrayOf("plugin:kcml:options_testValue=1", "plugin:kcml:options_testUnknown=value")
    ) { config, arguments ->
        assertFailsWith<CliOptionProcessingException> {
            PluginLoaderImpl.loadAndInvokeStandalone(config, arguments)
        }
    }

    @Test
    fun `parser failures are propagated before init`() = withPlugin(
        arrayOf("plugin:kcml:options_testValue=invalid")
    ) { config, arguments ->
        assertFailsWith<NumberFormatException> {
            PluginLoaderImpl.loadAndInvokeStandalone(config, arguments)
        }
        assertEquals(0, (PluginLoaderImpl.getPlugin("options_test") as OptionsTestPlugin).initCount)
    }

    @Test
    fun `option processing pre-initializes the same instance later used by the registrar`() = withPlugin(
        arrayOf("plugin:kcml:options_testValue=7", "plugin:kcml:options_testEntry=once")
    ) { config, arguments ->
        config.kcmlPluginClasspaths = arguments.pluginClasspaths.map(Path::of)
        PluginLoaderImpl.preInitialize(config)
        processCompilerPluginsOptions(
            config, arguments.pluginOptions.asIterable(), listOf(PluginLoaderImpl.commandLineProcessor)
        )
        val plugin = PluginLoaderImpl.getPlugin("options_test") as OptionsTestPlugin
        assertEquals(1, plugin.preInitCount)
        assertEquals(0, plugin.initCount)
        assertEquals(7, config[OptionsTestPlugin.valueKey])
        with(PluginLoaderImpl) { CompilerPluginRegistrar.ExtensionStorage().loadAndInvoke(config) }
        assertSame(plugin, PluginLoaderImpl.getPlugin("options_test"))
        assertEquals(1, plugin.preInitCount)
        assertEquals(1, plugin.initCount)
        assertEquals(7, plugin.initializedValue)
        assertEquals(listOf("once"), config[OptionsTestPlugin.entriesKey])
    }

    @Test
    fun `new invocations clear registered options and discover fresh plugin instances`() = withPlugin(
        arrayOf("plugin:kcml:options_testValue=1")
    ) { config, arguments ->
        PluginLoaderImpl.loadAndInvokeStandalone(config, arguments)
        val first = PluginLoaderImpl.getPlugin("options_test") as OptionsTestPlugin
        CommonHooks.onExecImpl(K2JVMCompiler(), arguments)
        arguments.pluginOptions = arrayOf("plugin:kcml:options_testValue=2")
        val secondConfig = CompilerConfiguration().apply { kcmlCompileTarget = "jvm" }
        PluginLoaderImpl.loadAndInvokeStandalone(secondConfig, arguments)
        val second = PluginLoaderImpl.getPlugin("options_test") as OptionsTestPlugin
        assertNotSame(first, second)
        assertEquals(1, second.preInitCount)
        assertEquals(2, second.initializedValue)
        CommonHooks.onExecImpl(K2JVMCompiler(), K2JVMCompilerArguments())
        PluginLoaderImpl.loadAndInvokeStandalone(CompilerConfiguration(), emptyList())
        assertEquals(emptyList(), PluginLoaderImpl.allPluginsSorted())
    }

    @Test
    fun `preInit follows dependency order and option names are isolated by plugin ID`() = withPlugin(
        arrayOf("plugin:kcml:options_testValue=42", "plugin:kcml:dependent_options_testValue=independent"),
        listOf(DependentOptionsTestPlugin::class.java, OptionsTestPlugin::class.java)
    ) { config, arguments ->
        PluginLoaderImpl.loadAndInvokeStandalone(config, arguments)
        assertEquals(listOf("options_test", "dependent_options_test"), PluginLoaderImpl.allPluginsSorted())
        assertEquals(42, (PluginLoaderImpl.getPlugin("options_test") as OptionsTestPlugin).initializedValue)
        assertEquals(
            "independent",
            (PluginLoaderImpl.getPlugin("dependent_options_test") as DependentOptionsTestPlugin).initializedValue
        )
    }

    @Test
    fun `preInit failures are propagated and clear the active plugin ID`() = withPlugin(
        emptyArray(), listOf(FailingOptionsTestPlugin::class.java)
    ) { config, arguments ->
        assertEquals("preInit failure", assertFailsWith<IllegalStateException> {
            PluginLoaderImpl.loadAndInvokeStandalone(config, arguments)
        }.message)
        assertNull(PluginLoaderImpl.loadingPluginId)
    }
}