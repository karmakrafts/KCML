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

package dev.karmakrafts.kcml.hooks.llvm

import dev.karmakrafts.kcml.api.mixin.MixinBridge
import dev.karmakrafts.kcml.api.target.NativeCompileTarget
import dev.karmakrafts.kcml.hooks.KCMLHookApi
import dev.karmakrafts.kcml.plugin.PluginLoaderImpl
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget
import kotlin.io.path.Path

@KCMLHookApi
object NativeTargetHooks {
    @JvmStatic
    fun onCreateConfiguration(configuration: CompilerConfiguration) {
        val pluginPaths = MixinBridge.INSTANCE.getConstant("kcml", "plugin_paths") as? List<*> ?: return
        PluginLoaderImpl.loadAndInvokeStandalone(
            configuration, pluginPaths.map { path -> Path(path as String).toUri().toURL() })
    }

    @JvmStatic
    fun onGetTargetValues(): List<KonanTarget>? {
        val target = (PluginLoaderImpl.compileTargetOrNull as? NativeCompileTarget)?.konanTarget ?: return null
        return KonanTarget.predefinedTargets.values.filterNot { it.name == target.name } + target
    }

    @JvmStatic
    fun onGetEnabled(hostManager: HostManager): List<KonanTarget>? {
        val target = (PluginLoaderImpl.compileTargetOrNull as? NativeCompileTarget)?.konanTarget ?: return null
        val enabled = hostManager.enabledByHost[HostManager.host] ?: return null
        return enabled.filterNot { it.name == target.name } + target
    }
}