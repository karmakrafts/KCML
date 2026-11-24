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
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.psi.KtFile
import com.intellij.lang.documentation.impl.documentationTargets as fileDocumentationTargets

internal class SyntaxDocumentationTargetProvider : DocumentationTargetProvider {
    override fun documentationTargets(file: PsiFile, offset: Int): List<DocumentationTarget> {
        val source = file as? KtFile ?: return emptyList()
        // Non-physical lowered files must fall through to Kotlin rather than re-enter this provider.
        if (!source.isPhysical || DumbService.isDumb(source.project)) return emptyList()
        if (offset !in 0 until source.textLength) return emptyList()
        val virtualFile = source.virtualFile ?: return emptyList()
        val index = ProjectFileIndex.getInstance(source.project)
        if (!index.isInSourceContent(virtualFile)) return emptyList()
        val module = index.getModuleForFile(virtualFile) ?: return emptyList()
        val service = source.project.service<SyntaxExtensionService>()
        val runtime = service.runtime(module) ?: return emptyList()
        if (!runtime.hasExtensions) return emptyList()
        val generation = service.modificationCount
        val lowered = runtime.lowerIfOpen(source) ?: return emptyList()
        if (source.text == lowered.text) return emptyList()
        SyntaxAnalysisContext.attach(lowered, source)
        val offsets = SyntaxOffsetMap(source.text, lowered.text)
        val targets = fileDocumentationTargets(lowered, offsets.toLowered(offset))
        return if (generation == service.modificationCount) targets else emptyList()
    }
}