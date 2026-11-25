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
import dev.karmakrafts.kcml.api.target.IDECompileTarget
import dev.karmakrafts.kcml.api.target.OnlyOn

/** Severity of a custom editor inspection finding. */
@OnlyOn(IDECompileTarget::class)
@ExperimentalKcmlApi
public enum class InspectionSeverity {
    // @formatter:off
    ERROR,
    WARNING,
    WEAK_WARNING,
    INFORMATION
    // @formatter:on
}