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

package dev.karmakrafts.kcml.util

import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey
import java.nio.file.Path

internal object KCMLConfigurationKeys {
    val pluginClasspaths: CompilerConfigurationKey<List<Path>> = CompilerConfigurationKey.create("kcmlPluginClasspaths")
    val agentLogFilePath: CompilerConfigurationKey<Path> = CompilerConfigurationKey.create("kcmlAgentLogFilePath")
    val moduleName: CompilerConfigurationKey<String> = CompilerConfigurationKey.create("kcmlModuleName")
}

internal var CompilerConfiguration.kcmlPluginClasspaths: List<Path>
    get() = get(KCMLConfigurationKeys.pluginClasspaths) ?: emptyList()
    set(value) {
        put(KCMLConfigurationKeys.pluginClasspaths, value)
    }

internal var CompilerConfiguration.kcmlAgentLogFilePath: Path?
    get() = get(KCMLConfigurationKeys.agentLogFilePath)
    set(value) {
        put(KCMLConfigurationKeys.agentLogFilePath, value ?: return)
    }

internal var CompilerConfiguration.kcmlModuleName: String?
    get() = get(KCMLConfigurationKeys.moduleName)
    set(value) {
        put(KCMLConfigurationKeys.moduleName, value ?: return)
    }