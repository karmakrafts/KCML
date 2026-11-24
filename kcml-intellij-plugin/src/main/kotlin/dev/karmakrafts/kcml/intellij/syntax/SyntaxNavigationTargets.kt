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

package dev.karmakrafts.kcml.intellij.syntax

import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import dev.karmakrafts.kcml.intellij.syntax.runtime.SyntaxOffsetMap
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.KaSpiExtensionPoint
import org.jetbrains.kotlin.analysis.api.resolve.extensions.KaResolveExtensionNavigationTargetsProvider
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration

internal fun syntaxNavigationTarget(source: KtFile, element: PsiElement, offsets: SyntaxOffsetMap): PsiElement? {
    val anchor = (element as? KtNamedDeclaration)?.nameIdentifier ?: element
    val offset = offsets.toSource(anchor.textRange).startOffset
    val leaf = source.findElementAt(offset) ?: return null
    return generateSequence(leaf) { it.parent }.filterIsInstance<KtNamedDeclaration>().firstOrNull {
        it.nameIdentifier?.textRange?.containsOffset(offset) == true
    } ?: leaf
}

@OptIn(KaExperimentalApi::class, KaSpiExtensionPoint::class)
internal class SyntaxNavigationTargets(
    source: KtFile, private val offsets: SyntaxOffsetMap
) : KaResolveExtensionNavigationTargetsProvider() {
    private val source: SmartPsiElementPointer<KtFile?> = SmartPointerManager.createPointer(source)

    override fun KaSession.getNavigationTargets(element: KtElement): Collection<PsiElement> {
        val file = source.getElement() ?: return emptyList()
        val target = syntaxNavigationTarget(file, element, offsets)
        return if (target == null) emptyList() else listOf(target)
    }
}