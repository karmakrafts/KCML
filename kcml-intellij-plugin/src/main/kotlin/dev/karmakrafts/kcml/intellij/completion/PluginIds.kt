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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.concurrency.AppExecutorUtil
import dev.karmakrafts.kcml.intellij.util.KcmlInitializationProgress
import dev.karmakrafts.kcml.intellij.util.PluginIdIndex
import org.jetbrains.jps.model.java.JavaResourceRootType
import java.util.concurrent.atomic.AtomicBoolean

internal object PluginIds {
    private val metadataKey = Key.create<CachedValue<Index>>("kcml.pluginIds.metadata")
    private val annotationsKey = Key.create<CachedValue<Index>>("kcml.pluginIds.annotations")

    private class Index {
        @Volatile
        var ids: PluginIdIndex.Ids? = null
        val scheduled = AtomicBoolean()

        @Synchronized
        fun compute(module: Module, scan: (Module) -> PluginIdIndex.Ids): PluginIdIndex.Ids {
            val cached = ids
            if (cached != null) return cached
            val computed = module.project.service<KcmlInitializationProgress>()
                .begin("Indexing KCML plugins: ${module.name}").use { scan(module) }
            ids = computed
            return computed
        }
    }

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

    fun fromMetadata(file: PsiFile): Set<String> = lookup(file, metadataKey, PluginIdIndex::fromMetadata)

    fun fromAnnotations(file: PsiFile): Set<String> = lookup(file, annotationsKey, PluginIdIndex::fromAnnotations)

    private fun lookup(file: PsiFile, key: Key<CachedValue<Index>>, scan: (Module) -> PluginIdIndex.Ids): Set<String> {
        val project = file.project
        if (project.isDisposed || !file.isValid) return emptySet()
        val module = ModuleUtilCore.findModuleForPsiElement(file) ?: return emptySet()
        if (module.isDisposed) return emptySet()
        val index = CachedValuesManager.getManager(project).getCachedValue(module, key, {
            CachedValueProvider.Result.create(
                Index(),
                PsiModificationTracker.MODIFICATION_COUNT,
                ProjectRootModificationTracker.getInstance(project),
                DumbService.getInstance(project).modificationTracker,
                VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS
            )
        }, false)
        (val production, val all) = index.ids
            ?: if (ApplicationManager.getApplication().isDispatchThread || DumbService.isDumb(project)) {
                if (index.scheduled.compareAndSet(false, true)) {
                    val progress = project.service<KcmlInitializationProgress>()
                        .begin("Indexing KCML plugins: ${module.name}")
                    ReadAction.nonBlocking<PluginIdIndex.Ids> { index.compute(module, scan) }
                        .inSmartMode(project)
                        .expireWith(module)
                        .submit(AppExecutorUtil.getAppExecutorService())
                        .onProcessed {
                            progress?.close()
                            index.scheduled.set(false)
                        }
                }
                return emptySet()
            }
            else index.compute(module, scan)
        val virtualFile = file.virtualFile ?: return emptySet()
        return if (ModuleRootManager.getInstance(module).fileIndex.isInTestSourceContent(virtualFile)) all
        else production
    }
}