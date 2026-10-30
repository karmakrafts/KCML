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

package dev.karmakrafts.kcml.agent

import dev.karmakrafts.kcml.agent.log.Logger
import dev.karmakrafts.kcml.agent.log.NoopLogger
import dev.karmakrafts.kcml.agent.log.RemoteLogger
import dev.karmakrafts.kcml.agent.mixin.MixinClassTransformer
import dev.karmakrafts.kcml.agent.mixin.MixinLoader
import dev.karmakrafts.kcml.agent.util.AgentCommClient
import dev.karmakrafts.kcml.agent.util.KCMLAgentArguments
import dev.karmakrafts.kcml.agent.util.commPort
import dev.karmakrafts.kcml.agent.util.logging
import dev.karmakrafts.kcml.api.mixin.MixinBridge
import java.io.File
import java.lang.instrument.Instrumentation
import java.util.jar.JarFile
import kotlin.io.path.Path

@Suppress("UNUSED")
object KCMLAgent {
    internal fun injectMixinBridge( // @formatter:off
        path: String,
        logger: Logger,
        instrumentation: Instrumentation
    ) { // @formatter:on
        logger.info { "Injecting KCML mixin bridge JAR $path into bootstrap classpath" }
        val file = JarFile(File(path))
        instrumentation.appendToBootstrapClassLoaderSearch(file)
    }

    @JvmStatic
    fun agentmain(joinedArgs: String?, instrumentation: Instrumentation) {
        require(joinedArgs != null) { "KCML compiler agent requires initial startup options" }
        val args = KCMLAgentArguments.parse(joinedArgs)
        val commClient = AgentCommClient(args.commPort)
        val handshake = try {
            commClient.handshake()
        } catch (error: Throwable) {
            commClient.close()
            throw error
        }
        val logger = if (args.logging) RemoteLogger(commClient) else NoopLogger
        logger.info { "Agent invoked with options: $joinedArgs" }
        logger.info { "Initializing KCML compiler agent.." }
        injectMixinBridge(handshake.bridgePath, logger, instrumentation)
        // ================== Mixin Bridge boundary ==================
        logger.info { "Initializing default constant table entries" }
        MixinBridge.INSTANCE.apply {
            putConstant("loader_path", handshake.loaderPath)
            putConstant("bridge_path", handshake.bridgePath)
            putConstant("plugin_paths", handshake.pluginPaths)
        }
        val loader = MixinLoader(logger)
        logger.info { "Loading builtin loader and plugin mixins" }
        loader.load((listOf(handshake.loaderPath) + handshake.pluginPaths).map(::Path))
        val dumpPath = if (handshake.dumpClasses) handshake.dumpPath?.let(::Path) else null
        instrumentation.addTransformer(MixinClassTransformer(logger, loader, dumpPath))
    }
}