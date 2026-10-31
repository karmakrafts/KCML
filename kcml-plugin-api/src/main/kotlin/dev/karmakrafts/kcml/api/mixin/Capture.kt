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
 * Captures a target function parameter or local variable as a mixin function parameter.
 * Usually used in conjunction with [Inject].
 *
 * By default, the annotation indicates that the value should be
 * matched by its literal name in the mixin code, however this behavior
 * can be overridden using the [name] and [index] parameters of this annotation.
 *
 * @property name name of the target local to match; [AUTOMATIC_NAME] uses the mixin parameter's name.
 * @property index JVM local-variable slot to capture, overriding name matching;
 *   [AUTOMATIC_INDEX] falls back to name matching.
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.VALUE_PARAMETER)
public annotation class Capture( // @formatter:off
    public val name: String = AUTOMATIC_NAME,
    public val index: Int = AUTOMATIC_INDEX
) { // @formatter:on
    /** Sentinel values that enable name-based capture. */
    public companion object {
        /** Uses the mixin parameter's name to find the target local. */
        public const val AUTOMATIC_NAME: String = ""

        /** Resolves the target local by name instead of an explicit JVM local-variable slot. */
        public const val AUTOMATIC_INDEX: Int = -1
    }
}