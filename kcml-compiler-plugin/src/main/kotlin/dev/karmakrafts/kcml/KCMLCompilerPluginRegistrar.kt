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
import dev.karmakrafts.kcml.plugin.PluginLoaderImpl
import dev.karmakrafts.kcml.util.AgentInjector
import dev.karmakrafts.kcml.util.kcmlAgentCommPort
import dev.karmakrafts.kcml.util.kcmlAgentLogging
import dev.karmakrafts.kcml.util.kcmlModuleName
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.messageCollector

@Suppress("UNUSED")
@OptIn(ExperimentalCompilerApi::class)
@AutoService(CompilerPluginRegistrar::class)
class KCMLCompilerPluginRegistrar : CompilerPluginRegistrar() {
    private fun buildAgentArgs(
        configuration: CompilerConfiguration
    ): Map<String, String> = buildMap {
        configuration.kcmlAgentCommPort?.let { port ->
            this["logging"] = configuration.kcmlAgentLogging.toString()
            this["comm_port"] = port.toString()
        }
        val moduleName = configuration.kcmlModuleName
        if (moduleName?.isNotEmpty() == true) {
            this["module_name"] = moduleName
        }
    }

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        configuration.messageCollector.report(CompilerMessageSeverity.INFO, "Bootstrapping KCML..")
        val agentArgs = buildAgentArgs(configuration)
        AgentInjector(KCMLBootstrap.tempDirectory).inject(agentArgs)
        registerDisposable(KCMLBootstrap::cleanup)
        with(PluginLoaderImpl) { loadAndInvoke(configuration) }
    }

    override val pluginId: String get() = KCMLConstants.PLUGIN_ID
    override val supportsK2: Boolean = true
}