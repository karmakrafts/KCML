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

/**
 * Parses a compiler CLI option into the current invocation's configuration.
 *
 * Receives the registered option definition, its raw string value, and the configuration later
 * supplied to [CompilerPlugin.init]. Used internally to dispatch to a [ScopedOptionParser].
 */
public typealias OptionParser = (option: AbstractCliOption, value: String, config: CompilerConfiguration) -> Unit

/**
 * Parses a value for one option registered with [PreInitializationContext.addOption].
 *
 * Receives the raw string value and the compiler configuration later supplied to [CompilerPlugin.init].
 * The parser is responsible for validating and converting the value before storing its result.
 */
public typealias ScopedOptionParser = (value: String, config: CompilerConfiguration) -> Unit

/**
 * Registers a plugin's compiler CLI options during [CompilerPlugin.preInit].
 *
 * KCML snapshots the registered options and parsers after pre-initialization, then processes option
 * values before calling [CompilerPlugin.init]. Option names are local to the plugin; KCML prefixes
 * them with the plugin ID and uppercases their first character when exposing them on the command line.
 *
 * @property storage storage owned by this plugin for the current compiler invocation; the same
 *   instance is supplied through [InitializationContext.storage] during initialization.
 */
public data class PreInitializationContext( // @formatter:off
    public val storage: PluginStorage
) { // @formatter:on
    private val optionsLock: Any = Any()
    private val options: ArrayList<AbstractCliOption> = ArrayList()
    private val optionParsers: ArrayList<OptionParser> = ArrayList()

    /**
     * Registers an option definition and the parser for its values.
     *
     * The parser runs once for each supplied occurrence of this option. Registration does not invoke
     * the parser; values are processed after pre-initialization and before [CompilerPlugin.init].
     *
     * @param option option definition with an unprefixed, plugin-local name.
     * @param parser validates and stores each value in the compiler configuration.
     */
    public fun addOption(option: AbstractCliOption, parser: ScopedOptionParser): Unit = synchronized(optionsLock) {
        options += option
        optionParsers += parser@{ currentOption, value, config ->
            if (currentOption !== option) return@parser
            parser(value, config)
        }
    }

    /**
     * Creates and registers a compiler CLI option and its value parser.
     *
     * For example, a plugin with ID `example` registering `enabled` exposes the option as
     * `-P plugin:kcml:exampleEnabled=<value>`.
     *
     * @param optionName unprefixed, plugin-local option name.
     * @param valueDescription description of the expected value shown in compiler help.
     * @param description description of the option shown in compiler help.
     * @param required whether the compiler requires a value for this option; defaults to `true`.
     * @param allowMultipleOccurrences whether the option may be supplied more than once; defaults to `false`.
     * @param parser validates and stores each supplied value in the compiler configuration before [CompilerPlugin.init].
     */
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

    /**
     * Creates an invocation-scoped processor from a snapshot of the registered options and parsers.
     *
     * Later registrations do not affect the returned processor. It retains the original option names
     * and dispatches parsers by option identity; KCML applies the command-line prefixes separately.
     *
     * @param pluginId ID of the plugin whose options are being processed.
     * @return a processor exposing the registered options under [pluginId].
     */
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