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
 * Injects a mixin function into an existing target function.
 *
 * [target] selects the reference instruction within [slice]. The runtime mixin compiler inserts
 * the mixin function's code before or after that instruction according to [order].
 *
 * @property name name of the target function.
 * @property descriptor JVM method descriptor of the target function; [ANY_DESCRIPTOR] selects
 *   the first function with the matching name.
 * @property slice instruction range in which to search for [target].
 * @property target selector for the reference instruction; the default selects the first executable instruction.
 * @property order position of the injection relative to the selected reference instruction.
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
    /** Wildcard values used by injection selectors. */
    public companion object {
        /** Matches any method or field descriptor. */
        public const val ANY_DESCRIPTOR: String = ""

        /** Matches any member name. */
        public const val ANY_NAME: String = ""

        /** Matches any member owner. */
        public const val ANY_OWNER: String = ""

        /** Matches any executable JVM opcode. */
        public const val ANY_OPCODE: Int = -1

        /** Matches any JVM local-variable slot. */
        public const val ANY_INDEX: Int = -1
    }

    /** Positions injected code relative to the reference instruction. */
    public enum class Order {
        /** Inserts the injection before the reference instruction. */
        BEFORE,

        /** Inserts the injection after the reference instruction. */
        AFTER
    }

    /**
     * Selects an instruction by filtering executable JVM instructions and choosing one occurrence.
     *
     * Owner, name, and descriptor filters apply to method and field instructions; the local-variable
     * index filter applies to variable instructions. [ordinal] is evaluated after filtering, then
     * [offset] shifts the selected position within the instruction list and the active slice.
     *
     * @property owner JVM internal member-owner name, using `/` as the package separator;
     *   [ANY_OWNER] does not constrain the owner.
     * @property name method or field name; [ANY_NAME] does not constrain the name.
     * @property descriptor JVM method or field descriptor; [ANY_DESCRIPTOR] does not constrain the descriptor.
     * @property index JVM local-variable slot; [ANY_INDEX] does not constrain the slot.
     * @property opcode JVM opcode to match; [ANY_OPCODE] accepts every executable opcode.
     * @property ordinal zero-based occurrence among instructions that pass the filters.
     * @property offset instruction-list offset added to the selected occurrence; may be negative.
     */
    public annotation class Target(
        public val owner: String = ANY_OWNER,
        public val name: String = ANY_NAME,
        public val descriptor: String = ANY_DESCRIPTOR,
        public val index: Int = ANY_INDEX,
        public val opcode: Int = ANY_OPCODE,
        public val ordinal: Int = 0,
        public val offset: Int = 0
    )

    /**
     * Restricts the instruction range searched for an injection target.
     *
     * Bounds are resolved against the full method. A default or unresolved bound leaves that side
     * unrestricted.
     *
     * @property start selector for the inclusive start instruction; the default starts at the method's beginning.
     * @property end selector for the exclusive end instruction; the default extends to the method's end.
     */
    public annotation class Slice( // @formatter:off
        public val start: Target = Target(),
        public val end: Target = Target()
    ) // @formatter:on
}
