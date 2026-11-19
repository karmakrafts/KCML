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

package dev.karmakrafts.kcml.intellij.icon

import com.intellij.ide.FileIconProvider
import com.intellij.openapi.components.service
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import dev.karmakrafts.kcml.intellij.util.TypeNames
import dev.karmakrafts.kcml.intellij.util.argument
import dev.karmakrafts.kcml.intellij.util.literalValue
import dev.karmakrafts.kcml.intellij.util.qualifiedName
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.kotlin.idea.stubindex.KotlinAnnotationsIndex
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import javax.swing.Icon

private val idsKey = Key.create<CachedValue<Pair<Set<String>, Set<String>>>>("kcml.pluginConfig.ids")

class PluginConfigIconProvider : FileIconProvider {
    private val icon: Icon = IconLoader.getIcon("/icons/dev/karmakrafts/kcml/intellij/kcml.svg", javaClass)

    override fun getIcon(file: VirtualFile, flags: Int, project: Project?): Icon? {
        if (project == null || project.isDisposed || file.isDirectory || file.extension != "json") return null
        val metaInf = file.parent ?: return null
        if (metaInf.name != "META-INF") return null
        val module = ModuleUtilCore.findModuleForFile(file, project) ?: return null
        val roots = ModuleRootManager.getInstance(module)
        val root = metaInf.parent ?: return null
        val isTest = root in roots.getSourceRoots(JavaResourceRootType.TEST_RESOURCE)
        if (!isTest && root !in roots.getSourceRoots(JavaResourceRootType.RESOURCE)) return null
        project.service<PluginConfigIconUpdater>().track(file)
        if (DumbService.isDumb(project)) return null
        val cachedIds = CachedValuesManager.getManager(project).getCachedValue(module, idsKey, {
            val production = mutableSetOf<String>()
            val all = mutableSetOf<String>()
            val scope = GlobalSearchScope.moduleScope(module)
            for (name in KotlinAnnotationsIndex.getAllKeys(project)) {
                for (annotation in KotlinAnnotationsIndex[name, project, scope]) {
                    if (annotation.parent?.parent !is KtClassOrObject) continue
                    if (annotation.qualifiedName() != TypeNames.PLUGIN) continue
                    val id = (annotation.argument("id", 0)
                        ?.getArgumentExpression() as? KtStringTemplateExpression)?.literalValue() ?: continue
                    all += id
                    if (!roots.fileIndex.isInTestSourceContent(annotation.containingFile.virtualFile)) production += id
                }
            }
            CachedValueProvider.Result.create(
                production to all,
                PsiModificationTracker.MODIFICATION_COUNT,
                ProjectRootModificationTracker.getInstance(project),
                DumbService.getInstance(project).modificationTracker
            )
        }, false)
        val ids = if (isTest) cachedIds.second else cachedIds.first
        return if (file.nameWithoutExtension in ids) icon else null
    }
}