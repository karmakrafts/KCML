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

package dev.karmakrafts.kcml.api.util

/** Specifies an extension's execution order relative to a dependency. */
enum class Order {
    // @formatter:off
    /** No ordering constraint. */
    NONE,

    /** Runs before the dependency. */
    BEFORE,

    /** Runs after the dependency. */
    AFTER
    // @formatter:on
}