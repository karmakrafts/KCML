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

package dev.karmakrafts.kcml.agent.log

import dev.karmakrafts.kcml.agent.util.AgentCommClient

internal class RemoteLogger(val client: AgentCommClient) : Logger {
    override fun debug(message: () -> Any?) {
        client.log("[DEBUG] ${message()}")
    }

    override fun info(message: () -> Any?) {
        client.log("[-INFO] ${message()}")
    }

    override fun warn(message: () -> Any?) {
        client.log("[-WARN] ${message()}")
    }

    override fun error(message: () -> Any?) {
        client.log("[ERROR] ${message()}")
    }
}