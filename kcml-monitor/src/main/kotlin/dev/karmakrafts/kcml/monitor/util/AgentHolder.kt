/*
 * Copyright 2025 Karma Krafts & associates
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

package dev.karmakrafts.kcml.monitor.util

import dev.karmakrafts.kcml.monitor.server.Agent
import dev.karmakrafts.kcml.monitor.ui.AgentPanel

/**
 * Holder class for agents in the UI **only**.
 */
internal data class AgentHolder(
    var isConnected: Boolean, var agent: Agent
) {
    companion object {
        const val CLOSED_TAB_INDEX: Int = -1
    }

    lateinit var panel: AgentPanel
    var tabIndex: Int = CLOSED_TAB_INDEX
}