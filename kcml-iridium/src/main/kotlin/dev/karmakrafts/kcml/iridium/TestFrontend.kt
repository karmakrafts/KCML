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

package dev.karmakrafts.kcml.iridium

import dev.karmakrafts.kcml.api.frontend.Frontend
import dev.karmakrafts.kcml.api.log.Logger
import dev.karmakrafts.kcml.api.log.LoggerFactory
import dev.karmakrafts.kcml.api.plugin.PluginLoader
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.FirSession

internal class TestFrontend( // @formatter:off
    override val session: FirSession,
    override val config: CompilerConfiguration
) : Frontend { // @formatter:on
    override val loggerFactory: LoggerFactory by lazy { TestLoggerFactory() }
    override val logger: Logger by lazy { loggerFactory("Iridium") }
    override val loader: PluginLoader by lazy { TestPluginLoader(logger) }
}