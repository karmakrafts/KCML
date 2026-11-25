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
import dev.karmakrafts.kcml.api.target.IdeCompileTarget
import dev.karmakrafts.kcml.api.target.OnlyOn
import org.jetbrains.kotlin.com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.com.intellij.psi.PsiElement

/**
 * An editor inspection rule anchored to original source PSI.
 *
 * Like an IDE problem descriptor, the rule selects an [element] and optionally a [rangeInElement].
 * Its absolute [range] is captured during construction, before lowering can mutate the parsed tree.
 * The rule controls only normal Kotlin inspections, never [SyntaxExtension.inspect] findings.
 *
 * @param element nonempty PSI element belonging to the callback's original parsed source, before lowering.
 * @param mode inspection handling mode, defaulting to forwarding all mapped lowered reports.
 * @param rangeInElement optional nonempty UTF-16 subrange within [element]'s text; `null` selects all of it.
 */
@OnlyOn(IdeCompileTarget::class)
@ExperimentalKcmlApi
public data class InspectionRule(
    public val element: PsiElement,
    public val mode: InspectionMode = InspectionMode.DESUGARED,
    public val rangeInElement: TextRange? = null
) {
    /** Absolute UTF-16 source range captured at construction, unaffected by subsequent PSI edits. */
    public val range: TextRange = element.captureInspectionRange(rangeInElement)
}

@OnlyOn(IdeCompileTarget::class)
internal fun PsiElement.captureInspectionRange(rangeInElement: TextRange?): TextRange {
    val elementRange = requireNotNull(textRange) { "Inspection element must have a source range" }
    require(textLength > 0 && !elementRange.isEmpty) { "Inspection element must not be empty" }
    if (rangeInElement == null) return elementRange
    require(!rangeInElement.isEmpty) { "Inspection range must not be empty" }
    require(rangeInElement.startOffset >= 0 && rangeInElement.endOffset <= textLength) {
        "Inspection range must be within its element"
    }
    return rangeInElement.shiftRight(elementRange.startOffset)
}