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

package dev.karmakrafts.kcml.intellij.completion

import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import dev.karmakrafts.kcml.intellij.util.TypeNames
import dev.karmakrafts.kcml.intellij.util.argument
import dev.karmakrafts.kcml.intellij.util.literalValue
import dev.karmakrafts.kcml.intellij.util.qualifiedName
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.kotlin.idea.stubindex.KotlinAnnotationsIndex
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

internal object PluginIds {
    fun isMetadata(file: PsiFile): Boolean {
        val virtualFile = file.virtualFile ?: return false
        if (virtualFile.extension != "json" || virtualFile.parent?.name != "META-INF") return false
        val module = ModuleUtilCore.findModuleForPsiElement(file) ?: return false
        val roots = ModuleRootManager.getInstance(module)
        val root = virtualFile.parent.parent ?: return false
        return root in roots.getSourceRoots(JavaResourceRootType.RESOURCE) || root in roots.getSourceRoots(
            JavaResourceRootType.TEST_RESOURCE
        )
    }

    fun fromMetadata(file: PsiFile): Set<String> {
        val module = ModuleUtilCore.findModuleForPsiElement(file) ?: return emptySet()
        val roots = ModuleRootManager.getInstance(module)
        val resourceRoots =
            roots.getSourceRoots(JavaResourceRootType.RESOURCE) + if (roots.fileIndex.isInTestSourceContent(file.virtualFile)) {
                roots.getSourceRoots(JavaResourceRootType.TEST_RESOURCE)
            }
            else emptyList()
        val manager = PsiManager.getInstance(file.project)
        return buildSet {
            for (root in resourceRoots) {
                for (candidate in root.findChild("META-INF")?.children.orEmpty()) {
                    ProgressManager.checkCanceled()
                    if (candidate.isDirectory || candidate.extension != "json") continue
                    val json = manager.findFile(candidate) as? JsonFile ?: continue
                    val metadata = json.topLevelValue as? JsonObject ?: continue
                    val id = (metadata.findProperty("id")?.value as? JsonStringLiteral)?.value ?: continue
                    if (isValid(id)) add(id)
                }
            }
        }
    }

    fun fromAnnotations(file: PsiFile): Set<String> {
        val module = ModuleUtilCore.findModuleForPsiElement(file) ?: return emptySet()
        val roots = ModuleRootManager.getInstance(module)
        val includeTests = roots.fileIndex.isInTestSourceContent(file.virtualFile)
        val scope = GlobalSearchScope.moduleScope(module)
        return buildSet {
            for (name in KotlinAnnotationsIndex.getAllKeys(file.project)) {
                ProgressManager.checkCanceled()
                for (annotation in KotlinAnnotationsIndex[name, file.project, scope]) {
                    ProgressManager.checkCanceled()
                    if (annotation.parent?.parent !is KtClassOrObject) continue
                    if (!includeTests && roots.fileIndex.isInTestSourceContent(annotation.containingFile.virtualFile)) continue
                    if (annotation.qualifiedName() != TypeNames.PLUGIN) continue
                    val id = (annotation.argument("id", 0)
                        ?.getArgumentExpression() as? KtStringTemplateExpression)?.literalValue() ?: continue
                    if (isValid(id)) add(id)
                }
            }
        }
    }

    private fun isValid(id: String): Boolean = id.isNotEmpty() && '/' !in id && '\\' !in id
}