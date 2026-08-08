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
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.div

class AgentInjector( // @formatter:off
    directory: Path,
    logger: (String) -> Unit = {}
) { // @formatter:on
    private val agentJar: EmbeddedJar = EmbeddedJar("/kcml-agent.jar", logger)
    private val agentPath: Path = directory / "agent.jar"

    @Suppress("DEPRECATION")
    private fun tryOverwriteAttachPermissions() {
        try {
            val unsafe = UnsafeUtils.unsafe
            val hotspotVirtualMachine = Class.forName("sun.tools.attach.HotSpotVirtualMachine")
            val allowAttachSelfField = hotspotVirtualMachine.getDeclaredField("ALLOW_ATTACH_SELF")
            val allowAttachSelfBase = unsafe.staticFieldBase(allowAttachSelfField)
            val allowAttachSelfOffset = unsafe.staticFieldOffset(allowAttachSelfField)
            unsafe.putBooleanVolatile(allowAttachSelfBase, allowAttachSelfOffset, true)
        } catch (_: ClassNotFoundException) {
            // Skip overwriting access flag if class is not found (J9/Graal)
        }
    }

    private fun tryAttachSelf(): Result<VirtualMachine> = runCatching {
        System.setProperty("jdk.attach.allowAttachSelf", "true")
        tryOverwriteAttachPermissions()
        val pid = ProcessHandle.current().pid().toString()
        val virtualMachines = VirtualMachine.list()
        val descriptor = virtualMachines.first { pid in it.id() || pid in it.displayName() }
        VirtualMachine.attach(descriptor)
    }

    fun inject(options: Map<String, String> = emptyMap()): Boolean {
        agentJar.unpackIfNeeded(agentPath)
        return tryAttachSelf().fold(onSuccess = { vm ->
            val args = options.map { (key, value) -> "$key=$value" }.joinToString(":")
            vm.loadAgent(agentPath.absolutePathString(), args)
            vm.detach()
            true
        }, onFailure = { false })
    }
}