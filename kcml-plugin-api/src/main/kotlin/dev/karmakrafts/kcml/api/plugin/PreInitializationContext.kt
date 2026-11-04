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

package dev.karmakrafts.kcml.api.plugin

import dev.karmakrafts.kcml.api.InternalKcmlApi
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration

public typealias OptionParser = (option: AbstractCliOption, value: String, config: CompilerConfiguration) -> Unit

public typealias ScopedOptionParser = (value: String, config: CompilerConfiguration) -> Unit

public class PreInitializationContext {
    private val optionsLock: Any = Any()

    private val options: ArrayList<AbstractCliOption> = ArrayList()

    private val optionParsers: ArrayList<OptionParser> = ArrayList()

    public fun addOption(option: AbstractCliOption, parser: ScopedOptionParser): Unit = synchronized(optionsLock) {
        options += option
        optionParsers += parser@{ currentOption, value, config ->
            if (currentOption !== option) return@parser
            parser(value, config)
        }
    }

    public fun addOption(
        optionName: String,
        valueDescription: String,
        description: String,
        required: Boolean = true,
        allowMultipleOccurrences: Boolean = false,
        parser: ScopedOptionParser
    ): Unit = addOption(
        CliOption(
            optionName = optionName,
            valueDescription = valueDescription,
            description = description,
            required = required,
            allowMultipleOccurrences = allowMultipleOccurrences
        ), parser
    )

    /** Creates an invocation-scoped processor from the options registered during pre-initialization. */
    @InternalKcmlApi
    @OptIn(ExperimentalCompilerApi::class)
    public fun createCommandLineProcessor(pluginId: String): CommandLineProcessor = synchronized(optionsLock) {
        val registeredOptions = options.toList()
        val registeredParsers = optionParsers.toList()
        object : CommandLineProcessor {
            override val pluginId: String = pluginId
            override val pluginOptions: Collection<AbstractCliOption> = registeredOptions

            override fun processOption(option: AbstractCliOption, value: String, configuration: CompilerConfiguration) {
                for (parser in registeredParsers) parser(option, value, configuration)
            }
        }
    }
}