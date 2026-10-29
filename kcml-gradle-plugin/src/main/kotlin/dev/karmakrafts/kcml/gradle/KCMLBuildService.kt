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

import dev.karmakrafts.kcml.agent.proto.S2AHandshake
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.services.BuildService

internal abstract class KCMLBuildService : BuildService<KCMLBuildServiceParameters>, AutoCloseable {
    companion object {
        const val NAME: String = "kcml_build_service"
    }

    private val logger: Logger = Logging.getLogger(this::class.java)
    val agentCommServer: AgentCommServer = AgentCommServer(parameters.agentCommPort.get()) { request ->
        S2AHandshake(
            signature = request.signature,
            loaderPath = parameters.loaderFile.get().asFile.absolutePath,
            bridgePath = parameters.bridgeFile.get().asFile.absolutePath,
            pluginPaths = parameters.pluginPaths.get(),
            dumpClasses = parameters.dumpClasses.get(),
            dumpPath = parameters.dumpPath.orNull
        )
    }

    init {
        logger.info("Starting KCML build service..")
    }

    override fun close() {
        logger.info("Stopping KCML build service..")
        agentCommServer.close()
    }
}