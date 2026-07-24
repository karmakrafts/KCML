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
import dev.karmakrafts.kcml.util.kcmlAgentLogFilePath
import dev.karmakrafts.kcml.util.kcmlModuleName
import dev.karmakrafts.kcml.util.kcmlPluginClasspaths
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import kotlin.io.path.Path

@Suppress("UNUSED")
@AutoService(CommandLineProcessor::class)
@OptIn(ExperimentalCompilerApi::class)
class KCMLCommandLineProcessor : CommandLineProcessor {
    companion object {
        private const val PLUGIN_CLASSPATHS: String = "pluginClasspaths"
        private const val AGENT_LOG_FILE_PATH: String = "agentLogFilePath"
        private const val MODULE_NAME: String = "moduleName"
    }

    override val pluginId: String get() = KCMLConstants.PLUGIN_ID

    override val pluginOptions: Collection<AbstractCliOption> = listOf( // @formatter:off
        CliOption(
            optionName = PLUGIN_CLASSPATHS,
            valueDescription = "<string>",
            description = "File paths to all JARs added to the KCML plugin class loader"
        ),
        CliOption(
            optionName = AGENT_LOG_FILE_PATH,
            valueDescription = "string",
            description = "Path to the file the KCML compiler agent will log to",
            required = false
        ),
        CliOption(
            optionName = MODULE_NAME,
            valueDescription = "<string>",
            description = "Name of the module currently being compiled",
            required = false
        )
    ) // @formatter:on

    override fun processOption(option: AbstractCliOption, value: String, configuration: CompilerConfiguration) {
        when (option.optionName) {
            PLUGIN_CLASSPATHS -> configuration.kcmlPluginClasspaths = value.split(";").map(::Path)
            AGENT_LOG_FILE_PATH -> configuration.kcmlAgentLogFilePath = Path(value)
            MODULE_NAME -> configuration.kcmlModuleName = value
        }
    }
}