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

import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiPolyVariantReference
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.resolve.extensions.isFromResolveExtension
import org.jetbrains.kotlin.analysis.api.resolve.extensions.resolveExtensionNavigationElements
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile

@OptIn(KaExperimentalApi::class)
internal object SyntaxNavigation {
    fun targets(file: PsiFile, offset: Int): Array<PsiElement>? {
        val source = file as? KtFile ?: return null
        if (!source.isPhysical || DumbService.isDumb(source.project)) return null
        if (offset !in 0 until source.textLength) return null
        val virtualFile = source.virtualFile ?: return null
        val index = ProjectFileIndex.getInstance(source.project)
        if (!index.isInSourceContent(virtualFile)) return null
        val module = index.getModuleForFile(virtualFile) ?: return null
        val service = source.project.service<SyntaxExtensionService>()
        val runtime = service.runtime(module) ?: return null
        if (!runtime.hasExtensions) return null
        val generation = service.modificationCount
        val lowered = runtime.lowerIfOpen(source) ?: return null
        if (source.text == lowered.text) return null
        SyntaxAnalysisContext.attach(lowered, source)
        val offsets = SyntaxOffsetMap(source.text, lowered.text)
        val reference = lowered.findReferenceAt(offsets.toLowered(offset)) ?: return null
        val targets = analyze(lowered) {
            val resolved = if (reference is PsiPolyVariantReference) {
                reference.multiResolve(false).filter { it.isValidResult }.mapNotNull { it.element }
            }
            else listOfNotNull(reference.resolve())
            resolved.flatMap { target ->
                when {
                    target.containingFile === lowered -> listOfNotNull(syntaxNavigationTarget(source, target, offsets))
                    target is KtElement && target.isFromResolveExtension -> target.resolveExtensionNavigationElements.toList()
                    else -> listOf(target)
                }
            }.distinct()
        }
        // A runtime refresh invalidates the resolve-extension files used by this request too.
        if (generation != service.modificationCount) return null
        return targets.takeIf { it.isNotEmpty() }?.toTypedArray()
    }
}