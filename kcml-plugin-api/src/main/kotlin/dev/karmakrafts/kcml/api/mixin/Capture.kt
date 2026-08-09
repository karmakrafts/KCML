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
 * May be used to capture locals as parameters to a mixin function.
 * Usually used in conjunction with [Inject].
 * This may be used to pass through function parameters of the target
 * functions or locals within its scope.
 *
 * By default, the annotation indicates that the value should be
 * matched by its literal name in the mixin code, however this behavior
 * can be overridden using the [name] and [index] parameters of this annotation.
 *
 * @param name The name of the local to match.
 *  Empty means match the literal name of the local as it appears in the mixin code.
 * @param index If set overrides the name matching and allows capturing any local
 *  by its stack index. -1 means fall back to name matching.
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class Capture( // @formatter:off
    val name: String = AUTOMATIC_NAME,
    val index: Int = AUTOMATIC_INDEX
) { // @formatter:on
    companion object {
        const val AUTOMATIC_NAME: String = ""
        const val AUTOMATIC_INDEX: Int = -1
    }
}