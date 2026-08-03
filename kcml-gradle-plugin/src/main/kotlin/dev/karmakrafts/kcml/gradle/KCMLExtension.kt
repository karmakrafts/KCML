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

package dev.karmakrafts.kcml.gradle

import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

abstract class KCMLExtension @Inject internal constructor(
    private val project: Project
) {
    private inline val objectFactory: ObjectFactory
        get() = project.objects

    val agentLogging: Property<Boolean> = objectFactory.property(Boolean::class.java).convention(false)

    val agentPortRangeStart: Property<Int> = objectFactory.property(Int::class.java).convention(11000)

    val agentPortRangeEnd: Property<Int> = objectFactory.property(Int::class.java).convention(11999)

    internal val agentPort: Int by lazy {
        val portRangeStart = agentPortRangeStart.get()
        val portRangeEnd = agentPortRangeEnd.get()
        AgentCommServer.findAvailablePort(portRangeStart, portRangeEnd)
            ?: error("Could not find available port for KCML agent comm server")
    }
}