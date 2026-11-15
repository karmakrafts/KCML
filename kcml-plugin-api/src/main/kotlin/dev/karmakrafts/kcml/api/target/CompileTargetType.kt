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

package dev.karmakrafts.kcml.api.target

import org.jetbrains.kotlin.config.CompilerConfiguration

/** Describes a category of compilation targets and how it is selected from compiler configuration. */
public interface CompileTargetType {
    /** Names accepted to select this type from compiler configuration. */
    public val aliases: Set<String>

    /**
     * Determines whether this type can be selected for [config]. The default implementation accepts every
     * configuration.
     *
     * @param config compiler configuration used to determine whether this type is applicable.
     * @return `true` if this type is eligible for the configuration, otherwise `false`.
     */
    public fun isSelected(config: CompilerConfiguration): Boolean = true
}