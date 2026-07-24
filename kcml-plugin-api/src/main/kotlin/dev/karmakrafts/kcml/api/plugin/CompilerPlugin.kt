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

package dev.karmakrafts.kcml.api.plugin

/**
 * Entry point for a KCML compiler plugin discovered through Java's [java.util.ServiceLoader].
 *
 * KCML calls [load] once after ordering discovered plugins by their declared dependencies and
 * before it installs the Kotlin FIR and IR extension adapters. Implementations register the
 * extensions that should participate in this compilation through the supplied context.
 */
interface CompilerPlugin {
    /**
     * Configures this plugin for the current Kotlin compiler invocation.
     *
     * @param context services scoped to the active plugin load, including the registry to which
     *   this plugin contributes extensions.
     */
    fun load(context: PluginLoadContext)
}