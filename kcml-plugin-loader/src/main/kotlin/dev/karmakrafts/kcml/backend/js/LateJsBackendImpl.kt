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

package dev.karmakrafts.kcml.backend.js

import dev.karmakrafts.kcml.api.backend.js.LateJsBackend
import dev.karmakrafts.kcml.api.log.Logger
import dev.karmakrafts.kcml.api.log.LoggerFactory
import dev.karmakrafts.kcml.api.plugin.PluginLoader
import dev.karmakrafts.kcml.api.target.JsCompileTarget
import dev.karmakrafts.kcml.log.MessageCollectorLoggerFactory
import dev.karmakrafts.kcml.target.JsCompileTargetImpl
import org.jetbrains.kotlin.config.MessageCollectorAccess
import org.jetbrains.kotlin.config.messageCollector
import org.jetbrains.kotlin.ir.backend.js.JsIrBackendContext

internal class LateJsBackendImpl(
    override val pluginId: String,
    override val context: JsIrBackendContext,
    override val loader: PluginLoader
) : LateJsBackend {
    override val compileTarget: JsCompileTarget get() = JsCompileTargetImpl

    @OptIn(MessageCollectorAccess::class)
    override val loggerFactory: LoggerFactory by lazy {
        MessageCollectorLoggerFactory(loader, context.configuration.messageCollector)
    }
    override val logger: Logger by lazy { loggerFactory.getForPlugin(pluginId) }
}