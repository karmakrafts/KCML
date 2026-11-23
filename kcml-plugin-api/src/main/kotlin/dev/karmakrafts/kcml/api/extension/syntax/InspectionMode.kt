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

package dev.karmakrafts.kcml.api.extension.syntax

import dev.karmakrafts.kcml.api.ExperimentalKcmlApi

/** How an IDE handles normal inspection reports overlapping a rule's original source range. */
@ExperimentalKcmlApi
public enum class InspectionMode {
    /** Forward all mapped lowered reports to the whole rule range, suppressing raw Kotlin reports. */
    DESUGARED,

    /** Suppress both raw Kotlin and mapped lowered normal inspection reports. */
    SUPPRESS
}