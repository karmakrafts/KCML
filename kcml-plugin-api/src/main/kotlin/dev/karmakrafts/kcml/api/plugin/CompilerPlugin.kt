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

import dev.karmakrafts.kcml.api.extension.ExtensionRegistry
import org.jetbrains.kotlin.config.CompilerConfiguration

/**
 * Entry point for a KCML compiler plugin.
 *
 * KCML invokes this contract while initializing a discovered plugin so it can contribute compiler
 * extensions for the active Kotlin compilation.
 */
interface CompilerPlugin {
    /**
     * Registers this plugin's KCML extensions.
     *
     * @param registry registry that will dispatch the registered extensions to Kotlin compiler phases.
     * @param config compiler configuration for the active Kotlin compilation.
     */
    fun registerExtensions(registry: ExtensionRegistry, config: CompilerConfiguration)
}