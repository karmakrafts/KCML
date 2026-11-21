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
import dev.karmakrafts.kcml.hacks.DiscouragedKcmlApi
import dev.karmakrafts.kcml.hacks.ReflectionUtils
import dev.karmakrafts.kcml.hooks.KCMLHookApi
import dev.karmakrafts.kcml.plugin.PluginLoaderImpl
import dev.karmakrafts.kcml.target.CustomNativeConfigurables
import kotlinx.cinterop.ExperimentalForeignApi
import llvm.LLVMModuleRef
import llvm.LLVMSetDataLayout
import llvm.LLVMSetTarget
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.konan.target.Architecture
import org.jetbrains.kotlin.konan.target.BinaryFormat
import org.jetbrains.kotlin.konan.target.Configurables
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.konan.util.ProgressCallback
import java.util.*
import kotlin.io.path.Path

@KCMLHookApi
object NativeTargetHooks {
    private val builtinFamilies =
        setOf(Family.LINUX, Family.MINGW, Family.OSX, Family.IOS, Family.TVOS, Family.WATCHOS, Family.ANDROID)
    private val builtinArchitectures = setOf(Architecture.X64, Architecture.X86, Architecture.ARM64, Architecture.ARM32)

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
        val enabled = hostManager.enabledByHost[HostManager.host].orEmpty()
        return enabled.filterNot { it == target } + target
    }

    @JvmStatic
    fun onLoadConfigurables(
        target: KonanTarget, properties: Properties, dependenciesRoot: String?, progressCallback: ProgressCallback
    ): Configurables? {
        val selected = (PluginLoaderImpl.compileTargetOrNull as? NativeCompileTarget)?.konanTarget ?: return null
        if (target != selected || target.family in builtinFamilies) return null
        return CustomNativeConfigurables(target, properties, dependenciesRoot, progressCallback)
    }

    @JvmStatic
    fun onBinaryFormat(target: KonanTarget): BinaryFormat? {
        val selected = (PluginLoaderImpl.compileTargetOrNull as? NativeCompileTarget) ?: return null
        if (target != selected.konanTarget) return null
        return PluginLoaderImpl.nativeArchitectureOrNull?.binaryFormat
            ?: BinaryFormat.ELF.takeIf { target.family !in builtinFamilies }
    }

    @JvmStatic
    fun onPointerBits(target: KonanTarget): Int? =
        target.architecture.bitness.takeIf { target.architecture !in builtinArchitectures }

    @JvmStatic
    fun onSupports64BitAtomics(target: KonanTarget): Boolean? {
        val selected = (PluginLoaderImpl.compileTargetOrNull as? NativeCompileTarget) ?: return null
        if (target != selected.konanTarget) return null
        return PluginLoaderImpl.nativeArchitectureOrNull?.supports64BitAtomics
            ?: false.takeIf { target.architecture !in builtinArchitectures }
    }

    @JvmStatic
    fun onSupportsUnalignedAccess(target: KonanTarget): Boolean? {
        val selected = (PluginLoaderImpl.compileTargetOrNull as? NativeCompileTarget) ?: return null
        if (target != selected.konanTarget) return null
        return PluginLoaderImpl.nativeArchitectureOrNull?.supportsUnalignedAccess
            ?: false.takeIf { target.architecture !in builtinArchitectures }
    }

    @OptIn(DiscouragedKcmlApi::class, ExperimentalForeignApi::class)
    @JvmStatic
    fun onRuntimeModule(runtime: Any) {
        val settings = PluginLoaderImpl.nativeArchitectureOrNull ?: return
        if (settings.targetTriple == null && settings.dataLayout == null) return
        val module = ReflectionUtils.getField<Any, LLVMModuleRef>("llvmModule", runtime)
        settings.targetTriple?.let { LLVMSetTarget(module, it) }
        settings.dataLayout?.let { LLVMSetDataLayout(module, it) }
    }
}