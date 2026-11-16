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

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiClass
import com.intellij.psi.impl.java.stubs.index.JavaStubIndexKeys
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.IdFilter
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import java.util.concurrent.ConcurrentHashMap

internal object ClassNameUtils {
    private val libraryCandidatesKey = Key.create<CachedValue<ConcurrentHashMap<GlobalSearchScope, List<String>>>>(
        "kcml.indirectMixin.libraryCandidates"
    )

    fun lookupNames(binaryName: String): Set<String> {
        val className = binaryName.substringAfterLast('.')
        val names = linkedSetOf(binaryName, className, className.replace('$', '.'))
        var index = className.indexOf('$')
        while (index >= 0) {
            val nestedName = className.substring(index + 1)
            names.add(nestedName)
            names.add(nestedName.replace('$', '.'))
            index = className.indexOf('$', index + 1)
        }
        return names
    }

    fun findCandidates(project: Project, scope: GlobalSearchScope): Sequence<String> = sequence {
        val sourceScope = scope.intersectWith(GlobalSearchScope.projectScope(project))
        val sourceFiles = IdFilter.getProjectIdFilter(project, false)
        val stubIndex = StubIndex.getInstance()
        val shortNames = buildList<String> {
            stubIndex.processAllKeys(JavaStubIndexKeys.CLASS_SHORT_NAMES, { add(it) }, sourceScope, sourceFiles)
        }
        for (shortName in shortNames) {
            ProgressManager.checkCanceled()
            for (psiClass in StubIndex.getElements(
                JavaStubIndexKeys.CLASS_SHORT_NAMES, shortName, project, sourceScope, PsiClass::class.java
            )) {
                psiClass.jvmBinaryName()?.let { yield(it) }
            }
        }

        val kotlinClassIndex = KotlinFullClassNameIndex
        val kotlinClassNames = buildList<String> {
            kotlinClassIndex.processAllKeys(sourceScope, sourceFiles) { add(it) }
        }
        for (className in kotlinClassNames) {
            ProgressManager.checkCanceled()
            for (declaration in kotlinClassIndex[className, project, sourceScope]) {
                declaration.jvmBinaryName()?.let { yield(it) }
            }
        }

        for (binaryName in findLibraryCandidates(project, scope)) {
            ProgressManager.checkCanceled()
            yield(binaryName)
        }
    }.distinct()

    fun findLibraryCandidates(project: Project, scope: GlobalSearchScope): List<String> {
        val cache = CachedValuesManager.getManager(project).getCachedValue(project, libraryCandidatesKey, {
            CachedValueProvider.Result.create(
                ConcurrentHashMap<GlobalSearchScope, List<String>>(),
                ProjectRootModificationTracker.getInstance(project),
                VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS,
                ModificationTracker {
                    FileBasedIndex.getInstance().getIndexModificationStamp(FilenameIndex.NAME, project)
                })
        }, false)
        return cache.computeIfAbsent(scope) {
            val projectFileIndex = ProjectFileIndex.getInstance(project)
            buildList {
                for (classFile in FilenameIndex.getAllFilesByExt(project, "class", scope)) {
                    ProgressManager.checkCanceled()
                    val classRoot = projectFileIndex.getClassRootForFile(classFile) ?: continue
                    val relativePath = VfsUtilCore.getRelativePath(classFile, classRoot, '/') ?: continue
                    if (!relativePath.endsWith(".class") || relativePath.startsWith("META-INF/versions/")) continue
                    if (classFile.nameWithoutExtension in setOf("module-info", "package-info")) continue
                    add(relativePath.removeSuffix(".class").replace('/', '.'))
                }
            }
        }
    }

    private fun PsiClass.jvmBinaryName(): String? {
        val classNames = ArrayList<String>()
        var current: PsiClass? = this
        while (current != null) {
            classNames.add(current.name ?: return null)
            current = current.containingClass
        }
        var topLevelClass = this
        while (topLevelClass.containingClass != null) {
            topLevelClass = topLevelClass.containingClass!!
        }
        val topLevelName = topLevelClass.name ?: return null
        val qualifiedName = topLevelClass.qualifiedName ?: return null
        val packagePrefix = qualifiedName.removeSuffix(topLevelName)
        val className = classNames.asReversed().joinToString("$")
        return "$packagePrefix$className"
    }

    private fun KtClassOrObject.jvmBinaryName(): String? {
        if (fqName == null) return null
        val classNames = ArrayList<String>()
        var current: KtClassOrObject? = this
        while (current != null) {
            classNames.add(current.name ?: return null)
            current = current.parent as? KtClassOrObject ?: (current.parent as? KtClassBody)?.parent as? KtClassOrObject
        }
        val packageName = (containingFile as? KtFile)?.packageFqName?.asString().orEmpty()
        val className = classNames.asReversed().joinToString("$")
        return if (packageName.isEmpty()) className else "$packageName.$className"
    }
}