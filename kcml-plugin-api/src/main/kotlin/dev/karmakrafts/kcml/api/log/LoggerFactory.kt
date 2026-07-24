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

package dev.karmakrafts.kcml.api.log

/**
 * Creates [Logger] instances that emit diagnostics for the active Kotlin compiler invocation.
 *
 * KCML uses logger names to identify the source of compiler diagnostics. Use [getForPlugin] when
 * logging on behalf of a discovered plugin and [invoke] for a distinct KCML component.
 */
interface LoggerFactory {
    /**
     * Creates a logger with a caller-defined diagnostic name.
     *
     * @param name name prepended to the logger's compiler diagnostics.
     * @return a logger named [name].
     */
    operator fun invoke(name: String): Logger

    /**
     * Creates a logger for a plugin identified by its stable KCML ID.
     *
     * @param pluginId identifier of the plugin whose diagnostics the logger represents.
     * @return a logger named for the plugin.
     */
    fun getForPlugin(pluginId: String): Logger
}