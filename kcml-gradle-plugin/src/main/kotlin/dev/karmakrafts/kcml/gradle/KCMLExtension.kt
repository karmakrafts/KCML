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
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

/**
 * Configures the KCML Gradle plugin for a project.
 */
abstract class KCMLExtension @Inject internal constructor( // @formatter:off
    private val project: Project,
    objects: ObjectFactory
) { // @formatter:on

    /**
     * Whether logging from the compiler agent is enabled.
     *
     * The default value is `false`.
     */
    val agentLogging: Property<Boolean> = objects.property(Boolean::class.java).convention(false)

    /**
     * The inclusive lower bound of the port range used for agent communication.
     *
     * The default value is `11000`.
     */
    val agentPortRangeStart: Property<Int> = objects.property(Int::class.java).convention(11000)

    /**
     * The exclusive upper bound of the port range used for agent communication.
     *
     * The default value is `11999`.
     */
    val agentPortRangeEnd: Property<Int> = objects.property(Int::class.java).convention(11999)

    /**
     * Whether the KCML agent should dump transformed classes to a known directory.
     *
     * The default value is `false`.
     */
    val agentDumpClasses: Property<Boolean> = objects.property(Boolean::class.java).convention(false)

    /**
     * The default path where the KCML agent dumps transformed classes if
     * [agentDumpClasses] is `true`.
     *
     * The default value is `./build/kcml_agent`.
     */
    val agentDumpPath: DirectoryProperty =
        objects.directoryProperty().convention(project.layout.buildDirectory.dir("kcml_agent"))

    internal val agentPort: Int by lazy {
        val portRangeStart = agentPortRangeStart.get()
        val portRangeEnd = agentPortRangeEnd.get()
        AgentCommServer.findAvailablePort(portRangeStart, portRangeEnd)
            ?: error("Could not find available port for KCML agent comm server")
    }
}