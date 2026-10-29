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
    private val agentPath: Path = directory / "agent-${FileChecksum.forResource(agentJar.path)}.jar"
    private val lockPath: Path = directory / "agent.lock"

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

    fun inject(options: Map<String, Any> = emptyMap()): Boolean {
        // Serialize across compiler classloaders to avoid overflowing the JVM attach listener's backlog.
        return FileLock(lockPath).use { lock ->
            lock.withLock {
                agentJar.unpackIfNeeded(agentPath)
                tryAttachSelf().fold(onSuccess = { vm ->
                    val args = options.map { (key, value) -> "$key=$value" }.joinToString(":")
                    try {
                        vm.loadAgent(agentPath.absolutePathString(), "${agentPath.toUri()}\n$args")
                    } finally {
                        vm.detach()
                    }
                    true
                }, onFailure = { false })
            }
        }
    }
}