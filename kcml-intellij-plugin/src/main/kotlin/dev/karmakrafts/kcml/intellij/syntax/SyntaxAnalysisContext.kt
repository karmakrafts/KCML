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

import com.intellij.openapi.roots.ProjectFileIndex
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaPlatformInterface
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KaDanglingFileModuleImpl
import org.jetbrains.kotlin.analysis.api.projectStructure.KaDanglingFileResolutionMode
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.analysis.api.projectStructure.explicitModule
import org.jetbrains.kotlin.idea.base.projectStructure.KaSourceModuleKind
import org.jetbrains.kotlin.idea.base.projectStructure.toKaSourceModule
import org.jetbrains.kotlin.psi.KtFile

@OptIn(KaExperimentalApi::class)
internal object SyntaxAnalysisContext {
    fun sourceModule(source: KtFile): KaSourceModule? {
        val virtualFile = source.virtualFile ?: return null
        val index = ProjectFileIndex.getInstance(source.project)
        val module = index.getModuleForFile(virtualFile) ?: return null
        val kind =
            if (index.isInTestSourceContent(virtualFile)) KaSourceModuleKind.TEST else KaSourceModuleKind.PRODUCTION
        // Generic getModule would re-enter SyntaxModuleFactory for the source file
        return module.toKaSourceModule(kind)
    }

    // contextModule creates separate dangling modules but completion needs both lowered files in one module
    @OptIn(KaPlatformInterface::class)
    fun attach(files: List<KtFile>, context: KaModule) {
        val module = KaDanglingFileModuleImpl(files, context, KaDanglingFileResolutionMode.PREFER_SELF)
        for (file in files) {
            file.explicitModule = module
        }
    }

    fun attach(file: KtFile, source: KtFile) {
        val module = sourceModule(source) ?: return
        attach(listOf(file), module)
    }
}