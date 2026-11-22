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

package dev.karmakrafts.kcml.intellij.syntax

import dev.karmakrafts.kcml.util.kcmlCompileTarget
import dev.karmakrafts.kcml.util.kcmlModuleName
import dev.karmakrafts.kcml.util.kcmlPluginClasspaths
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import java.nio.file.Path

internal data class SyntaxConfiguration( // @formatter:off
    val paths: List<Path>,
    val options: List<String>,
    val target: String,
    val moduleName: String
) { // @formatter:on
    @OptIn(CompilerConfiguration.Internals::class)
    fun compilerConfiguration(): CompilerConfiguration = CompilerConfiguration().apply {
        kcmlPluginClasspaths = paths
        kcmlCompileTarget = target
        kcmlModuleName = moduleName
        put(CommonConfigurationKeys.MODULE_NAME, moduleName)
    }

    companion object {
        private const val BOOTSTRAP: String = "plugin:kcml.bootstrap:"

        fun fromOptions(options: List<String>, moduleName: String): SyntaxConfiguration? {
            // @formatter:off
            val bootstrap = options.filter { option -> option.startsWith(BOOTSTRAP) }
                .associate { option ->
                    option.removePrefix(BOOTSTRAP).substringBefore('=') to
                        option.substringAfter('=', "")
                }
            // @formatter:on
            val paths = bootstrap["pluginClasspaths"]?.split(';')
                ?.filter(String::isNotBlank)
                ?.map { path -> Path.of(path).toAbsolutePath().normalize() }
                ?.distinct()
                .orEmpty()
            if (paths.isEmpty()) return null
            return SyntaxConfiguration(
                paths,
                options.filter { option -> option.startsWith("plugin:kcml:") },
                bootstrap["compileTarget"] ?: "common",
                bootstrap["moduleName"] ?: moduleName
            )
        }
    }
}