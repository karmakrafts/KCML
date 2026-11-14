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

public interface CompileTargetProvider {
    /** Platform target of the active compilation. */
    public val compileTarget: CompileTarget
}

/**
 * Only execute the given block of code if the current target matches one of the given
 * targets.
 * This may be used to register target specific extensions and run target specific code.
 *
 * @param types The compilation target(s) to run the given block of code for.
 * @param block The block of code to execute when the context matches one of the given targets.
 */
public inline fun CompileTargetProvider.whenCompilingFor(vararg types: CompileTargetType, block: () -> Unit) {
    contract {
        callsInPlace(block, InvocationKind.AT_MOST_ONCE)
    }
    if (compileTarget.type !in types) return
    block()
}

/**
 * Only execute the given block of code if the current target matches one of the given
 * targets.
 * This may be used to register target specific extensions and run target specific code.
 *
 * @param types The compilation target(s) to run the given block of code for.
 * @param block The block of code to execute when the context matches one of the given targets.
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