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
 * Used to define injections into existing code within a mixin class.
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
@ComponentMarker
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION)
public annotation class Inject( // @formatter:off
    public val name: String,
    public val descriptor: String = ANY_DESCRIPTOR,
    public val slice: Slice = Slice(),
    public val target: Target = Target(),
    public val order: Order = Order.AFTER
) { // @formatter:on
    public companion object {
        public const val ANY_DESCRIPTOR: String = ""
        public const val ANY_NAME: String = ""
        public const val ANY_OWNER: String = ""
        public const val ANY_OPCODE: Int = -1
        public const val ANY_INDEX: Int = -1
    }

    public enum class Order {
        BEFORE, AFTER
    }

    public annotation class Target(
        public val owner: String = ANY_OWNER,
        public val name: String = ANY_NAME,
        public val descriptor: String = ANY_DESCRIPTOR,
        public val index: Int = ANY_INDEX,
        public val opcode: Int = ANY_OPCODE,
        public val ordinal: Int = 0,
        public val offset: Int = 0
    )

    public annotation class Slice( // @formatter:off
        public val start: Target = Target(),
        public val end: Target = Target()
    ) // @formatter:on
}
