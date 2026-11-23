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
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.analysis.api.KaPlatformInterface
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KaDanglingFileModuleImpl
import org.jetbrains.kotlin.analysis.api.projectStructure.KaDanglingFileResolutionMode
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.idea.base.fir.projectStructure.FirKaModuleFactory
import org.jetbrains.kotlin.psi.KtFile

@Suppress("JetBrainsInternalApiUsage") // Usually not allowed to be implemented by other plugins
@OptIn(KaPlatformInterface::class)
internal class SyntaxModuleFactory : FirKaModuleFactory {
    override fun createKaModuleByPsiFile(file: PsiFile): KaModule? {
        if (file !is KtFile || !file.isPhysical || DumbService.isDumb(file.project)) return null
        val virtualFile = file.virtualFile ?: return null
        val index = ProjectFileIndex.getInstance(file.project)
        if (!index.isInSourceContent(virtualFile)) return null
        val module = index.getModuleForFile(virtualFile) ?: return null
        val runtime = file.project.service<SyntaxExtensionService>().runtime(module) ?: return null
        if (!runtime.hasExtensions) return null
        val context = SyntaxAnalysisContext.sourceModule(file) ?: return null
        return KaDanglingFileModuleImpl(listOf(file), context, KaDanglingFileResolutionMode.IGNORE_SELF)
    }
}