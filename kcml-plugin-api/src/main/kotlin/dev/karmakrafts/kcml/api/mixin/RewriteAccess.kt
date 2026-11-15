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
 * Replaces the access flags of a mixin's target class.
 *
 * The replacement takes effect when the target class is loaded and cannot change access flags on classes
 * that were already loaded when the agent attached.
 *
 * @property value replacement JVM class access flags; these replace, rather than merge with, the target's existing
 * access flags. Defaults to [RETAIN_ACCESS].
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS)
public annotation class RewriteAccess(
    public val value: Int = RETAIN_ACCESS
) {
    /** Contains constants used by [RewriteAccess]. */
    public companion object {
        /** Sentinel value used as the default for [RewriteAccess.value] to indicate that access should be retained. */
        public const val RETAIN_ACCESS: Int = -1
    }
}
