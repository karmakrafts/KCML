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

package dev.karmakrafts.kcml.intellij.gutter

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.PsiNavigateUtil
import dev.karmakrafts.kcml.intellij.icon.Icons
import dev.karmakrafts.kcml.intellij.util.TypeNames
import dev.karmakrafts.kcml.intellij.util.argument
import dev.karmakrafts.kcml.intellij.util.literalValue
import dev.karmakrafts.kcml.intellij.util.qualifiedName
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

class PluginLineMarkerProvider : LineMarkerProvider {
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

    private fun KtClassOrObject.resolveMetadata(): PsiFile? {
        val annotation = annotationEntries.firstOrNull { it.qualifiedName() == TypeNames.PLUGIN } ?: return null
        val id = (annotation.argument("id", 0)?.getArgumentExpression() as? KtStringTemplateExpression)?.literalValue()
            ?: return null
        if (id.isEmpty() || '/' in id || '\\' in id) return null
        val module = ModuleUtilCore.findModuleForPsiElement(this) ?: return null
        val roots = ModuleRootManager.getInstance(module)
        val resourceRoots =
            roots.getSourceRoots(JavaResourceRootType.RESOURCE) + if (roots.fileIndex.isInTestSourceContent(
                    containingFile.virtualFile
                )
            ) {
                roots.getSourceRoots(JavaResourceRootType.TEST_RESOURCE)
            }
            else emptyList()
        return resourceRoots.firstNotNullOfOrNull { root ->
            val file = root.findFileByRelativePath("META-INF/$id.json") ?: return@firstNotNullOfOrNull null
            if (file.isDirectory) return@firstNotNullOfOrNull null
            PsiManager.getInstance(project).findFile(file)
        }
    }

    override fun collectSlowLineMarkers(elements: List<PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) {
        for (element in elements) {
            val declaration = element.parent as? KtClassOrObject ?: continue
            if (declaration.nameIdentifier != element) continue
            if (declaration.annotationEntries.none { it.qualifiedName() == TypeNames.PLUGIN }) continue
            val target = declaration.resolveMetadata()
            val navigationHandler = if (target == null) null
            else GutterIconNavigationHandler<PsiElement> { _, source ->
                val plugin = source.parent as? KtClassOrObject ?: return@GutterIconNavigationHandler
                val current = plugin.resolveMetadata() ?: return@GutterIconNavigationHandler
                PsiNavigateUtil.navigate(current, true)
            }
            result.add(
                NavigationLineMarkerInfo(
                    element, Icons.kcml, "KCML plugin", navigationHandler
                )
            )
        }
    }
}