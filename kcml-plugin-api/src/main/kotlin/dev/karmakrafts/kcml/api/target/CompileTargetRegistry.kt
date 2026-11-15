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

/** Registry of factories for creating compilation targets. */
public interface CompileTargetRegistry {

    /** Factories currently registered for each compilation target type. */
    public val factories: Map<CompileTargetType, CompileTargetFactory>

    /**
     * Registers a factory for [type].
     *
     * @param type type handled by [factory].
     * @param factory factory used to create targets of [type].
     * @throws IllegalArgumentException if [type] is already registered.
     */
    public fun register(type: CompileTargetType, factory: CompileTargetFactory)

    /**
     * Removes the factory registered for [target].
     *
     * @param target type whose factory should be removed.
     * @throws IllegalArgumentException if [target] is not registered.
     */
    public fun unregister(target: CompileTargetType)

    /**
     * Creates a target by invoking the factory registered for [type].
     *
     * @param type type of target to create.
     * @param config compiler configuration passed to the factory.
     * @return the target created by the registered factory.
     * @throws IllegalArgumentException if no factory is registered for [type].
     */
    public fun createTarget(type: CompileTargetType, config: CompilerConfiguration): CompileTarget

    /**
     * Selects a registered target type whose alias matches the resolved target name and whose selection predicate
     * accepts [config], then creates a target using its factory. If more than one type matches, the first match from
     * the registry is used.
     *
     * @param config compiler configuration used to select and create the target.
     * @return the target created by the selected factory.
     * @throws IllegalArgumentException if the configuration does not specify a target name.
     * @throws NoSuchElementException if no registered target type matches the configured name and configuration.
     */
    public fun createTarget(config: CompilerConfiguration): CompileTarget
}

/** The types for which this registry currently has factories. */
public inline val CompileTargetRegistry.types: Set<CompileTargetType>
    get() = factories.keys

/**
 * Removes the factory for [target]'s type from this registry.
 *
 * @param target target whose type's factory should be removed.
 * @throws IllegalArgumentException if [target]'s type is not registered.
 */
@Suppress("NOTHING_TO_INLINE")
public inline fun CompileTargetRegistry.unregister(target: CompileTarget): Unit = unregister(target.type)