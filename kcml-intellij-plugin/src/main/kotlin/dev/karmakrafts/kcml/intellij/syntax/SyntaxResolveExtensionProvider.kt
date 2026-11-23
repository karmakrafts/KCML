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
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaPlatformInterface
import org.jetbrains.kotlin.analysis.api.KaSpiExtensionPoint
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.analysis.api.resolve.extensions.KaResolveExtension
import org.jetbrains.kotlin.analysis.api.resolve.extensions.KaResolveExtensionFile
import org.jetbrains.kotlin.analysis.api.resolve.extensions.KaResolveExtensionNavigationTargetsProvider
import org.jetbrains.kotlin.analysis.api.resolve.extensions.KaResolveExtensionProvider
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtTypeAlias

@OptIn(KaExperimentalApi::class, KaPlatformInterface::class, KaSpiExtensionPoint::class)
internal class SyntaxResolveExtensionProvider : KaResolveExtensionProvider() {
    override fun provideExtensionsFor(module: KaModule): List<KaResolveExtension> {
        if (module !is KaSourceModule || DumbService.isDumb(module.project)) return emptyList()
        val ideModule = ModuleManager.getInstance(module.project).findModuleByName(module.name) ?: return emptyList()
        val service = module.project.service<SyntaxExtensionService>()
        val runtime = service.runtime(ideModule) ?: return emptyList()
        val generation = service.modificationCount
        if (!runtime.hasExtensions) return emptyList()
        val manager = PsiManager.getInstance(module.project)

        return listOf(object : KaResolveExtension() {
            private val files by lazy {
                FileTypeIndex.getFiles(KotlinFileType.INSTANCE, module.baseContentScope).mapNotNull { virtualFile ->
                    ProgressManager.checkCanceled()
                    val source = manager.findFile(virtualFile) as? KtFile ?: return@mapNotNull null
                    val lowered = runtime.lowerIfOpen(source) ?: return@mapNotNull null
                    if (source.text == lowered.text) return@mapNotNull null
                    if (generation != service.modificationCount) return@mapNotNull null
                    File(source, lowered)
                }
            }

            override fun getKtFiles(): List<KaResolveExtensionFile> = files

            override fun getContainedPackages(): Set<FqName> = files.flatMap { file ->
                generateSequence(file.getFilePackageName()) { if (it.isRoot) null else it.parent() }.toList()
            }.toSet()

            override fun getShadowedScope(): GlobalSearchScope =
                GlobalSearchScope.filesScope(module.project, files.map { it.source.virtualFile })
        })
    }

    private class File(val source: KtFile, lowered: KtFile) : KaResolveExtensionFile() {
        private val text: String = lowered.text
        private val packageName: FqName = lowered.packageFqName

        private val classifiers: Set<Name> = lowered.declarations.filter { it is KtClassOrObject || it is KtTypeAlias }
            .filterIsInstance<KtNamedDeclaration>()
            .mapNotNull { it.nameAsName }
            .toSet()

        private val callables: Set<Name> =
            lowered.declarations.filterIsInstance<KtCallableDeclaration>().mapNotNull { it.nameAsName }.toSet()

        override fun getFileName(): String = source.name
        override fun getFilePackageName(): FqName = packageName
        override fun getTopLevelClassifierNames(): Set<Name> = classifiers
        override fun getTopLevelCallableNames(): Set<Name> = callables
        override fun buildFileText(): String = text

        override fun createNavigationTargetsProvider(): KaResolveExtensionNavigationTargetsProvider =
            SyntaxNavigationTargets(source, SyntaxOffsetMap(source.text, text))
    }
}