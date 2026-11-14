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

// TODO: document this
public interface CompileTargetRegistry {

    // TODO: document this
    public val factories: Map<CompileTargetType, CompileTargetFactory>

    // TODO: document this
    public fun register(type: CompileTargetType, factory: CompileTargetFactory)

    // TODO: document this
    public fun unregister(target: CompileTargetType)

    // TODO: document this
    public fun createTarget(type: CompileTargetType, config: CompilerConfiguration): CompileTarget
}

// TODO: document this
public inline val CompileTargetRegistry.types: Set<CompileTargetType>
    get() = factories.keys

// TODO: document this
@Suppress("NOTHING_TO_INLINE")
public inline fun CompileTargetRegistry.unregister(target: CompileTarget): Unit = unregister(target.type)