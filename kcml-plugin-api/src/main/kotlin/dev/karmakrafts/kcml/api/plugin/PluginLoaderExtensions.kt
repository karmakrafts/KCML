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
 * Finds a loaded plugin by its reified compiler-plugin type.
 *
 * @param P compiler-plugin subtype annotated with [Plugin].
 * @return the matching loaded plugin, or `null` when [P] is unannotated or unavailable.
 */
inline fun <reified P : CompilerPlugin> PluginLoader.findPlugin(): P? {
    val type = P::class.java
    val annotation = type.getAnnotation(Plugin::class.java) ?: return null
    return findPlugin(annotation.id) as? P
}

/**
 * Gets a loaded plugin by its reified compiler-plugin type.
 *
 * @param P compiler-plugin subtype annotated with [Plugin].
 * @return the matching loaded plugin.
 * @throws IllegalArgumentException if [P] is unannotated or no matching plugin is loaded.
 */
inline fun <reified P : CompilerPlugin> PluginLoader.getPlugin(): P = requireNotNull(findPlugin<P>())