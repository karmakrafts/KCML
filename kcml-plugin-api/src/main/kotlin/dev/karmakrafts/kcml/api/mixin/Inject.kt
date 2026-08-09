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
 * Used to define injections into existing code within a [Mixin] class.
 * The parameters of this annotation are used to find a needle instruction
 * in the body of the target function, which is used as a reference point
 * of where to inject the trampoline call to the mixin function.
 *
 * @param name The name of the function to inject into.
 * @param descriptor The descriptor of the function to inject into.
 *  Empty means pick the first function you find with the given name.
 * @param target If the instruction being matched is a method instruction,
 *  this parameter may be used to match a call to a specific function.
 *  By default, an empty [Target] is passed which means match all calls.
 * @param order The ordering in which the injection happens relative to the needle.
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION)
annotation class Inject( // @formatter:off
    val name: String,
    val descriptor: String = ANY_DESCRIPTOR,
    val slice: Slice = Slice(),
    val target: Target = Target(),
    val order: Order = Order.AFTER
) { // @formatter:on
    companion object {
        const val ANY_DESCRIPTOR: String = ""
        const val ANY_NAME: String = ""
        const val ANY_OWNER: String = ""
        const val ANY_OPCODE: Int = -1
        const val ANY_INDEX: Int = -1
    }

    enum class Order {
        BEFORE, AFTER
    }

    annotation class Target(
        val owner: String = ANY_OWNER,
        val name: String = ANY_NAME,
        val descriptor: String = ANY_DESCRIPTOR,
        val index: Int = ANY_INDEX,
        val opcode: Int = ANY_OPCODE,
        val ordinal: Int = 0,
        val offset: Int = 0
    )

    annotation class Slice( // @formatter:off
        val start: Target = Target(),
        val end: Target = Target()
    ) // @formatter:on
}
