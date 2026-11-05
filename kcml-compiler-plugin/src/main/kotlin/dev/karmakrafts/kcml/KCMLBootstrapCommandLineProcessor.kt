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

package dev.karmakrafts.kcml

import com.google.auto.service.AutoService
import dev.karmakrafts.kcml.util.kcmlAgentCommPort
import dev.karmakrafts.kcml.util.kcmlAgentDumpClasses
import dev.karmakrafts.kcml.util.kcmlAgentDumpPath
import dev.karmakrafts.kcml.util.kcmlAgentLogging
import dev.karmakrafts.kcml.util.kcmlCompileTarget
import dev.karmakrafts.kcml.util.kcmlModuleName
import dev.karmakrafts.kcml.util.kcmlPluginClasspaths
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.konan.config.konanTarget
import kotlin.io.path.Path

@Suppress("UNUSED")
@AutoService(CommandLineProcessor::class)
@OptIn(ExperimentalCompilerApi::class)
class KCMLBootstrapCommandLineProcessor : CommandLineProcessor {
    companion object {
        private const val PLUGIN_CLASSPATHS: String = "pluginClasspaths"
        private const val AGENT_LOGGING: String = "agentLogging"
        private const val AGENT_COMM_PORT: String = "agentCommPort"
        private const val AGENT_DUMP_CLASSES: String = "agentDumpClasses"
        private const val AGENT_DUMP_PATH: String = "agentDumpPath"
        private const val MODULE_NAME: String = "moduleName"
        private const val COMPILE_TARGET: String = "compileTarget"
        private const val KONAN_TARGET: String = "konanTarget"
    }

    override val pluginId: String get() = "${KCMLConstants.PLUGIN_ID}.bootstrap"

    private var isPrepared: Boolean = false

    override val pluginOptions: Collection<AbstractCliOption> = listOf( // @formatter:off
        CliOption(
            optionName = PLUGIN_CLASSPATHS,
            valueDescription = "<string>",
            description = "File paths to all JARs added to the KCML plugin class loader"
        ),
        CliOption(
            optionName = COMPILE_TARGET,
            valueDescription = "<string>",
            description = "The current kotlin platform type the compilation belongs to",
            required = false
        ),
        CliOption(
            optionName = KONAN_TARGET,
            valueDescription = "<string>",
            description = "The current Kotlin/Native target",
            required = false
        ),
        CliOption(
            optionName = AGENT_LOGGING,
            valueDescription = "<true|false>",
            description = "Pipe log output from the KCML compiler agent to the Gradle plugin",
            required = false
        ),
        CliOption(
            optionName = AGENT_DUMP_CLASSES,
            valueDescription = "<true|false>",
            description = "Whether to dump classes transformed by KCML agent",
            required = false
        ),
        CliOption(
            optionName = AGENT_DUMP_PATH,
            valueDescription = "<string>",
            description = "The path where the KCML agent dumps transformed classes",
            required = false
        ),
        CliOption(
            optionName = MODULE_NAME,
            valueDescription = "<string>",
            description = "Name of the module currently being compiled",
            required = false
        ),
        // Bootstrap last so all settings are available before plugin pre-initialization.
        CliOption(
            optionName = AGENT_COMM_PORT,
            valueDescription = "<int>",
            description = "The port of the KCML agent comm server"
        )
    ) // @formatter:on

    override fun processOption(option: AbstractCliOption, value: String, configuration: CompilerConfiguration) {
        if (!isPrepared) {
            KCMLBootstrap.prepare(configuration)
            isPrepared = true
        }
        when (option.optionName) {
            PLUGIN_CLASSPATHS -> configuration.kcmlPluginClasspaths = value.split(";").map(::Path)
            AGENT_LOGGING -> configuration.kcmlAgentLogging = value.lowercase().toBooleanStrictOrNull() == true
            AGENT_COMM_PORT -> {
                configuration.kcmlAgentCommPort = value.toInt()
                KCMLBootstrap.init(configuration)
            }

            AGENT_DUMP_CLASSES -> configuration.kcmlAgentDumpClasses = value.lowercase().toBooleanStrictOrNull() == true
            AGENT_DUMP_PATH -> configuration.kcmlAgentDumpPath = Path(value)
            MODULE_NAME -> configuration.kcmlModuleName = value
            COMPILE_TARGET -> configuration.kcmlCompileTarget = value
            KONAN_TARGET -> configuration.konanTarget = value
        }
    }
}