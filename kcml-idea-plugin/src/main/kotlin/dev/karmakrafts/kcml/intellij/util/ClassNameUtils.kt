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
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiClass
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile

internal object ClassNameUtils {
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

    fun findCandidates(project: Project, scope: GlobalSearchScope): Set<String> {
        val candidates = LinkedHashSet<String>()
        val shortNamesCache = PsiShortNamesCache.getInstance(project)
        for (shortName in shortNamesCache.allClassNames) {
            ProgressManager.checkCanceled()
            for (psiClass in shortNamesCache.getClassesByName(shortName, scope)) {
                psiClass.jvmBinaryName()?.let(candidates::add)
            }
        }

        val kotlinClassIndex = KotlinFullClassNameIndex
        for (className in kotlinClassIndex.getAllKeys(project)) {
            ProgressManager.checkCanceled()
            for (declaration in kotlinClassIndex[className, project, scope]) {
                declaration.jvmBinaryName()?.let(candidates::add)
            }
        }

        val projectFileIndex = ProjectFileIndex.getInstance(project)
        for (classFile in FilenameIndex.getAllFilesByExt(project, "class", scope)) {
            ProgressManager.checkCanceled()
            val classRoot = projectFileIndex.getClassRootForFile(classFile) ?: continue
            val relativePath = VfsUtilCore.getRelativePath(classFile, classRoot, '/') ?: continue
            if (!relativePath.endsWith(".class") || relativePath.startsWith("META-INF/versions/")) continue
            val binaryName = relativePath.removeSuffix(".class").replace('/', '.')
            if (classFile.nameWithoutExtension in setOf("module-info", "package-info")) continue
            candidates.add(binaryName)
        }
        return candidates
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