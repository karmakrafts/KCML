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
import dev.karmakrafts.kcml.api.target.OnlyInIDE
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.PsiNamedElement

/**
 * An editor completion candidate for original source syntax.
 *
 * @param lookupString nonblank text used to match and display this candidate.
 * @param insertText text inserted when the candidate is selected, defaulting to [lookupString].
 * @param tailText optional additional text displayed after the candidate.
 * @param typeText optional type or other right-aligned annotation displayed for the candidate.
 * @param element optional PSI anchor belonging to the original parsed source, before lowering.
 * Synthetic candidates may omit it.
 */
@OnlyInIDE
@ExperimentalKcmlApi
public data class Completion(
    public val lookupString: String,
    public val insertText: String = lookupString,
    public val tailText: String? = null,
    public val typeText: String? = null,
    public val element: PsiElement? = null
) {
    init {
        require(lookupString.isNotBlank()) { "Completion lookup string must not be blank" }
    }

    public companion object {
        /** Creates a PSI-anchored candidate using [element]'s name, or its text when unnamed. */
        @JvmStatic
        public fun forElement(
            element: PsiNamedElement,
            insertText: String = element.name ?: element.text,
            tailText: String? = null,
            typeText: String? = null
        ): Completion = Completion(element.name ?: element.text, insertText, tailText, typeText, element)
    }
}