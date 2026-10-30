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

package dev.karmakrafts.kcml.hooks.jvm

import dev.karmakrafts.kcml.api.extension.jvm.JvmCodeGenerationExtension
import dev.karmakrafts.kcml.api.mixin.MixinBridge
import dev.karmakrafts.kcml.backend.jvm.JvmCodeGeneratorImpl
import dev.karmakrafts.kcml.backend.jvm.LateJvmBackendImpl
import dev.karmakrafts.kcml.hooks.KCMLHookApi
import dev.karmakrafts.kcml.plugin.PluginLoaderImpl
import org.jetbrains.kotlin.backend.jvm.JvmBackendContext
import org.jetbrains.kotlin.backend.jvm.codegen.BlockInfo
import org.jetbrains.kotlin.backend.jvm.codegen.ExpressionCodegen
import org.jetbrains.kotlin.backend.jvm.codegen.PromisedValue
import org.jetbrains.kotlin.ir.IrElement
import java.util.*
import kotlin.io.path.Path

@Suppress("UNUSED")
@KCMLHookApi
object JVMHooks {
    private val initializedInstances: MutableSet<JvmBackendContext> = Collections.newSetFromMap(WeakHashMap())
    private val initializationLock: Any = Any()

    @JvmStatic
    fun onGenerateClass(context: JvmBackendContext) = synchronized(initializationLock) {
        if (context in initializedInstances) return@synchronized
        val pluginPaths = requireNotNull(MixinBridge.INSTANCE.getConstant("kcml", "plugin_paths") as? List<*>) {
            "KCML agent handshake has not supplied plugin paths"
        }
        PluginLoaderImpl.loadAndInvokeStandalone(
            context.configuration, pluginPaths.map { path -> Path(path as String).toUri().toURL() })
        for ([pluginId, extension] in PluginLoaderImpl.extensionDispatcher.lateJvmExtensions) {
            extension.init(LateJvmBackendImpl(pluginId, context, PluginLoaderImpl))
        }
        initializedInstances += context
    }

    @JvmStatic
    fun onGenerateElement(element: IrElement, generator: ExpressionCodegen, blockInfo: BlockInfo): PromisedValue? {
        for ([pluginId, extension] in PluginLoaderImpl.extensionDispatcher.lateJvmExtensions) {
            if (extension !is JvmCodeGenerationExtension) continue
            val backend = LateJvmBackendImpl(pluginId, generator.context, PluginLoaderImpl)
            try {
                if (!extension.shouldProcess(element, backend)) continue
                val result = with(extension) { JvmCodeGeneratorImpl(backend, generator, blockInfo).process(element) }
                if (result != null) return result
            } catch (error: Throwable) {
                PluginLoaderImpl.logger.error("A JVM code generation extension from plugin '$pluginId' has caused an exception: ${error.stackTraceToString()}")
            }
        }
        return null
    }
}