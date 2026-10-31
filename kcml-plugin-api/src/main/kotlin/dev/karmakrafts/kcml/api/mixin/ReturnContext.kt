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

package dev.karmakrafts.kcml.api.mixin

/**
 * Allows a mixin function to return early from its target function.
 *
 * A return context may appear at any point in the mixin function's parameter list.
 * The runtime compiler will turn calls to [returnFromTarget] into actual return instructions
 * in the targeted function when the mixin is applied.
 *
 * @param T return type of the target function.
 */
public interface ReturnContext<T> {
    /**
     * Returns from the target function with [value] after mixin rewriting.
     *
     * @param value result to return from the target function.
     * @throws NotImplementedError if invoked directly without mixin rewriting.
     */
    public fun returnFromTarget(value: T) {
        throw NotImplementedError("This should not be called directly")
    }
}

/**
 * Returns from a `Unit`-returning target function after mixin rewriting.
 *
 * @throws NotImplementedError if invoked directly without mixin rewriting.
 */
@Suppress("NOTHING_TO_INLINE")
public inline fun ReturnContext<Unit>.returnFromTarget(): Unit = returnFromTarget(Unit)