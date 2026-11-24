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
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiPolyVariantReference
import dev.karmakrafts.kcml.intellij.syntax.runtime.SyntaxAnalysisContext
import dev.karmakrafts.kcml.intellij.syntax.runtime.SyntaxOffsetMap
import dev.karmakrafts.kcml.intellij.syntax.runtime.SyntaxRuntime
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
        // Non-physical lowered files must fall through to Kotlin rather than re-enter navigation.
        if (!source.isPhysical || DumbService.isDumb(source.project)) return null
        if (offset !in 0 until source.textLength) return null
        val module = sourceModule(source) ?: return null
        val service = source.project.service<SyntaxExtensionService>()
        val runtime = service.runtime(module) ?: return null
        if (!runtime.hasExtensions) return null
        val generation = service.modificationCount
        val targets = loweredTargets(source, offset, runtime) ?: return null
        // A runtime refresh invalidates the resolve-extension files used by this request too.
        if (generation != service.modificationCount) return null
        return targets.takeIf { it.isNotEmpty() }?.toTypedArray()
    }

    private fun sourceModule(source: KtFile): Module? {
        val virtualFile = source.virtualFile ?: return null
        val index = ProjectFileIndex.getInstance(source.project)
        if (!index.isInSourceContent(virtualFile)) return null
        return index.getModuleForFile(virtualFile)
    }

    private fun loweredTargets(source: KtFile, offset: Int, runtime: SyntaxRuntime): List<PsiElement>? {
        val lowered = runtime.lowerIfOpen(source) ?: return null
        if (source.text == lowered.text) return null
        SyntaxAnalysisContext.attach(lowered, source)
        // Resolve at the lowered offset using the source module's analysis context.
        val offsets = SyntaxOffsetMap(source.text, lowered.text)
        val reference = lowered.findReferenceAt(offsets.toLowered(offset)) ?: return null
        return analyze(lowered) {
            val resolved = if (reference is PsiPolyVariantReference) {
                reference.multiResolve(false).filter { it.isValidResult }.mapNotNull { it.element }
            }
            else listOfNotNull(reference.resolve())
            // Map lowered and resolve-extension declarations back to their source navigation targets.
            resolved.flatMap { target ->
                when {
                    target.containingFile === lowered -> listOfNotNull(syntaxNavigationTarget(source, target, offsets))
                    target is KtElement && target.isFromResolveExtension -> target.resolveExtensionNavigationElements.toList()
                    else -> listOf(target)
                }
            }.distinct()
        }
    }
}