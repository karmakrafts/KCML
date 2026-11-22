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

package dev.karmakrafts.kcml.frontend

import dev.karmakrafts.kcml.api.ExperimentalKcmlApi
import dev.karmakrafts.kcml.api.frontend.SyntaxFrontend
import dev.karmakrafts.kcml.api.log.LoggerFactory
import dev.karmakrafts.kcml.api.plugin.PluginLoader
import org.jetbrains.kotlin.config.CompilerConfiguration

@OptIn(ExperimentalKcmlApi::class)
internal class SyntaxFrontendImpl( // @formatter:off
    override val pluginId: String,
    override val config: CompilerConfiguration,
    override val loggerFactory: LoggerFactory,
    override val loader: PluginLoader
) : SyntaxFrontend // @formatter:on