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
import org.jetbrains.kotlin.com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.com.intellij.psi.PsiElement

/**
 * A custom editor inspection finding anchored to original source PSI.
 *
 * Like an IDE problem descriptor, the finding selects an [element] and optionally a [rangeInElement].
 * Its absolute [range] is captured during construction, before lowering can mutate the parsed tree.
 * Normal Kotlin [SyntaxExtension.inspectionRules] never suppress custom findings.
 *
 * @param element nonempty PSI element belonging to the callback's original parsed source, before lowering.
 * @param message nonblank message displayed for the finding.
 * @param severity editor severity, defaulting to [InspectionSeverity.WARNING].
 * @param rangeInElement optional nonempty UTF-16 subrange within [element]'s text; `null` selects all of it.
 */
@OnlyOn(IDECompileTarget::class)
@ExperimentalKcmlApi
public data class Inspection(
    public val element: PsiElement,
    public val message: String,
    public val severity: InspectionSeverity = InspectionSeverity.WARNING,
    public val rangeInElement: TextRange? = null
) {
    /** Absolute UTF-16 source range captured at construction, unaffected by subsequent PSI edits. */
    public val range: TextRange = element.captureInspectionRange(rangeInElement)

    init {
        require(message.isNotBlank()) { "Inspection message must not be blank" }
    }
}