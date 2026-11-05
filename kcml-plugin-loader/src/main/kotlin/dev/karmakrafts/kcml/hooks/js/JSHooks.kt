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

package dev.karmakrafts.kcml.hooks.js

import dev.karmakrafts.kcml.api.extension.js.JsCodeGenerationExtension
import dev.karmakrafts.kcml.api.mixin.MixinBridge
import dev.karmakrafts.kcml.backend.js.JsCodeGeneratorImpl
import dev.karmakrafts.kcml.backend.js.LateJsBackendImpl
import dev.karmakrafts.kcml.hooks.KCMLHookApi
import dev.karmakrafts.kcml.plugin.PluginLoaderImpl
import dev.karmakrafts.kcml.util.kcmlCompileTarget
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.backend.js.JsIrBackendContext
import org.jetbrains.kotlin.ir.backend.js.utils.JsGenerationContext
import org.jetbrains.kotlin.js.backend.ast.JsExpression
import org.jetbrains.kotlin.js.backend.ast.JsNode
import org.jetbrains.kotlin.js.backend.ast.JsStatement
import java.util.*
import kotlin.io.path.Path

@Suppress("UNUSED")
@KCMLHookApi
object JSHooks {
    private val initializedContexts: MutableSet<JsIrBackendContext> = Collections.newSetFromMap(WeakHashMap())

    private fun initIfNeeded(context: JsIrBackendContext) = synchronized(initializedContexts) {
        if (context in initializedContexts) return@synchronized
        val pluginPaths = requireNotNull(MixinBridge.INSTANCE.getConstant("kcml", "plugin_paths") as? List<*>) {
            "KCML agent handshake has not supplied plugin paths"
        }
        val config = context.configuration
        val loaderConfig = if (config.kcmlCompileTarget == null) {
            config.copy().apply { kcmlCompileTarget = "js" }
        }
        else config
        PluginLoaderImpl.loadAndInvokeStandalone(
            loaderConfig, pluginPaths.map { path -> Path(path as String).toUri().toURL() })
        for ([pluginId, extension] in PluginLoaderImpl.extensionDispatcher.lateJsExtensions) {
            extension.init(LateJsBackendImpl(pluginId, context, PluginLoaderImpl))
        }
        initializedContexts += context
    }

    @JvmStatic
    fun onGenerateExpression(element: IrElement, context: JsGenerationContext): JsExpression? {
        return dispatch(element, context, JsExpression::class.java)
    }

    @JvmStatic
    fun onGenerateStatement(element: IrElement, context: JsGenerationContext): JsStatement? {
        return dispatch(element, context, JsStatement::class.java)
    }

    private fun <T : JsNode> dispatch(element: IrElement, context: JsGenerationContext, nodeType: Class<T>): T? {
        val backendContext = context.staticContext.backendContext
        initIfNeeded(backendContext)
        for ([pluginId, extension] in PluginLoaderImpl.extensionDispatcher.lateJsExtensions) {
            if (extension !is JsCodeGenerationExtension) continue
            val backend = LateJsBackendImpl(pluginId, backendContext, PluginLoaderImpl)
            try {
                if (!extension.shouldProcess(element, backend)) continue
                val replacement = with(extension) { JsCodeGeneratorImpl(backend, context).process(element) } ?: continue
                val node = if (nodeType == JsStatement::class.java && replacement is JsExpression) {
                    replacement.makeStmt()
                }
                else replacement
                require(nodeType.isInstance(node)) {
                    "Expected ${nodeType.simpleName}, got ${node.javaClass.simpleName}"
                }
                return nodeType.cast(node)
            } catch (error: Throwable) {
                PluginLoaderImpl.logger.error("A JS code generation extension from plugin '$pluginId' has caused an exception: ${error.stackTraceToString()}")
            }
        }
        return null
    }
}