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

import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/** Provides information about the platform target of the active compilation. */
public interface CompileTargetProvider {
    /** Platform target of the active compilation. */
    public val compileTarget: CompileTarget
}

/**
 * Executes [block] only when the current target's type matches one of [types].
 * Use this to register target-specific extensions or run target-specific code.
 *
 * @param types compilation target types for which to execute [block].
 * @param block code to execute when the current target matches one of [types].
 */
public inline fun CompileTargetProvider.whenCompilingFor(vararg types: CompileTargetType, block: () -> Unit) {
    contract {
        callsInPlace(block, InvocationKind.AT_MOST_ONCE)
    }
    if (compileTarget.type !in types) return
    block()
}

/**
 * Returns the result of [block] only when the current target's type matches one of [types].
 * Otherwise, returns [default]. Use this to register target-specific extensions or run target-specific code.
 *
 * @param R type returned by [block] and used for [default].
 * @param default value to return when the current target does not match any of [types].
 * @param types compilation target types for which to execute [block].
 * @param block code to execute when the current target matches one of [types].
 * @return the result of [block] when a type matches, or [default] otherwise.
 */
public inline fun <reified R> CompileTargetProvider.whenCompilingFor(
    default: R, vararg types: CompileTargetType, block: () -> R
): R {
    contract {
        callsInPlace(block, InvocationKind.AT_MOST_ONCE)
    }
    if (compileTarget.type !in types) return default
    return block()
}