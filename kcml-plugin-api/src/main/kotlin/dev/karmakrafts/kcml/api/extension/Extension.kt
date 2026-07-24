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

package dev.karmakrafts.kcml.api.extension

/**
 * Base contract for a KCML contribution to one or more Kotlin compiler extension points.
 *
 * Implementations are registered by KCML and dispatched to their FIR, IR, or native-intrinsics
 * adapter according to their specialized extension type.
 */
sealed interface Extension {
    /** Stable identifier used to resolve this extension and its dependencies. */
    val id: String

    /** Dependencies that KCML resolves before dispatching this extension. */
    val dependencies: List<ExtensionDependency>
}