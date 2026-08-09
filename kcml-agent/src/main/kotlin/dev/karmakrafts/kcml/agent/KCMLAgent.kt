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

import dev.karmakrafts.kcml.agent.log.NoopLogger
import dev.karmakrafts.kcml.agent.log.RemoteLogger
import dev.karmakrafts.kcml.agent.mixin.MixinClassTransformer
import dev.karmakrafts.kcml.agent.mixin.MixinLoader
import dev.karmakrafts.kcml.agent.util.AgentCommClient
import dev.karmakrafts.kcml.agent.util.KCMLAgentArguments
import dev.karmakrafts.kcml.agent.util.commPort
import dev.karmakrafts.kcml.agent.util.logging
import java.lang.instrument.Instrumentation

object KCMLAgent {
    @JvmStatic
    fun agentmain(joinedArgs: String, instrumentation: Instrumentation) {
        val args = KCMLAgentArguments.parse(joinedArgs)
        val commClient = AgentCommClient(args.commPort)
        val logger = if (args.logging) RemoteLogger(commClient) else NoopLogger
        logger.info { "Initializing KCML compiler agent.." }
        val loader = MixinLoader(logger)
        instrumentation.addTransformer(MixinClassTransformer(logger, loader))
    }
}