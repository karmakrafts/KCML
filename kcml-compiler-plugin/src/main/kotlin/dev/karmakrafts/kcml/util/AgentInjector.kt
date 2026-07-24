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

import com.sun.tools.attach.VirtualMachine
import sun.misc.Unsafe
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.absolutePathString
import kotlin.io.path.deleteRecursively
import kotlin.io.path.div

internal object AgentInjector {
    private val agentDirectory: Path = Files.createTempDirectory("kcml")
    private val agentPath: Path = agentDirectory / "agent.jar"

    @Suppress("DEPRECATION")
    private fun tryOverwriteAttachPermissions() {
        try {
            val unsafeClass = Class.forName("sun.misc.Unsafe")
            val unsafeField = unsafeClass.getDeclaredField("theUnsafe")
            unsafeField.isAccessible = true
            val unsafe = unsafeField.get(null) as Unsafe
            unsafeField.isAccessible = false
            val hotspotVirtualMachine = Class.forName("sun.tools.attach.HotSpotVirtualMachine")
            val allowAttachSelfField = hotspotVirtualMachine.getDeclaredField("ALLOW_ATTACH_SELF")
            val allowAttachSelfBase = unsafe.staticFieldBase(allowAttachSelfField)
            val allowAttachSelfOffset = unsafe.staticFieldOffset(allowAttachSelfField)
            unsafe.putBooleanVolatile(allowAttachSelfBase, allowAttachSelfOffset, true)
        } catch (_: ClassNotFoundException) {
            // Skip overwriting access flag if class is not found (J9/Graal)
        } catch (error: Throwable) {
            error("Could not overwrite attach permissions: ${error.stackTraceToString()}")
        }
    }

    internal fun tryAttachSelf(): VirtualMachine? {
        tryOverwriteAttachPermissions()
        val pid = ProcessHandle.current().pid().toString()
        val virtualMachines = VirtualMachine.list()
        val descriptor = virtualMachines.find { pid in it.id() } ?: return null
        return try {
            VirtualMachine.attach(descriptor)
        } catch (_: Throwable) {
            return null
        }
    }

    fun inject(options: Map<String, String?> = emptyMap()) {
        this::class.java.getResourceAsStream("/kcml-agent.jar")?.use {
            Files.copy(it, agentPath, StandardCopyOption.REPLACE_EXISTING)
        }
        val vm = tryAttachSelf() ?: error("Could not attach to current VM")
        val args = options.map { (key, value) -> value?.let { "$key=$it" } ?: key }.joinToString(":")
        vm.loadAgent(agentPath.absolutePathString(), args)
        vm.detach()
    }

    @OptIn(ExperimentalPathApi::class)
    fun cleanup() {
        agentDirectory.deleteRecursively()
    }
}