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

package dev.karmakrafts.kcml.intellij.util

import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.concurrency.ThreadingAssertions
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.kotlin.idea.stubindex.KotlinAnnotationsIndex
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

internal object PluginIdIndex {
    data class Ids(val production: Set<String>, val all: Set<String>)

    fun fromMetadata(module: Module): Ids {
        ThreadingAssertions.assertReadAccess()
        ThreadingAssertions.assertBackgroundThread()
        val roots = ModuleRootManager.getInstance(module)
        val manager = PsiManager.getInstance(module.project)

        val production = mutableSetOf<String>()
        val all = mutableSetOf<String>()
        for (type in listOf(JavaResourceRootType.RESOURCE, JavaResourceRootType.TEST_RESOURCE)) {
            for (root in roots.getSourceRoots(type)) {
                ProgressManager.checkCanceled()
                for (candidate in root.findChild("META-INF")?.children.orEmpty()) {
                    ProgressManager.checkCanceled()
                    if (candidate.isDirectory || candidate.extension != "json") continue
                    val json = manager.findFile(candidate) as? JsonFile ?: continue
                    val metadata = json.topLevelValue as? JsonObject ?: continue
                    val id = (metadata.findProperty("id")?.value as? JsonStringLiteral)?.value ?: continue
                    if (!isValid(id)) continue
                    all += id
                    if (type == JavaResourceRootType.RESOURCE) production += id
                }
            }
        }

        return Ids(production.toSet(), all.toSet())
    }

    fun fromAnnotations(module: Module): Ids {
        ThreadingAssertions.assertReadAccess()
        ThreadingAssertions.assertBackgroundThread()
        val project = module.project
        val roots = ModuleRootManager.getInstance(module)
        val scope = GlobalSearchScope.moduleScope(module)

        val production = mutableSetOf<String>()
        val all = mutableSetOf<String>()
        for (name in KotlinAnnotationsIndex.getAllKeys(project)) {
            ProgressManager.checkCanceled()
            for (annotation in KotlinAnnotationsIndex[name, project, scope]) {
                ProgressManager.checkCanceled()
                if (annotation.parent?.parent !is KtClassOrObject) continue
                if (annotation.qualifiedName() != TypeNames.PLUGIN) continue
                val id = (annotation.argument("id", 0)
                    ?.getArgumentExpression() as? KtStringTemplateExpression)?.literalValue() ?: continue
                if (!isValid(id)) continue
                all += id
                if (!roots.fileIndex.isInTestSourceContent(annotation.containingFile.virtualFile)) production += id
            }
        }

        return Ids(production.toSet(), all.toSet())
    }

    private fun isValid(id: String): Boolean = id.isNotEmpty() && '/' !in id && '\\' !in id
}