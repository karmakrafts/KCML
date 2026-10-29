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

package dev.karmakrafts.kcml.agent.util

import dev.karmakrafts.kcml.agent.util.AgentArguments.Argument

internal object KCMLAgentArguments {
    val commPort: Argument<Int> = Argument.create("comm_port")
    val logging: Argument<Boolean> = Argument.create("logging")

    fun parse(input: String): AgentArguments = AgentArguments.parse(
        input, commPort, logging
    )
}

internal inline val AgentArguments.commPort: Int
    get() = this[KCMLAgentArguments.commPort]

internal inline val AgentArguments.logging: Boolean
    get() = this[KCMLAgentArguments.logging]