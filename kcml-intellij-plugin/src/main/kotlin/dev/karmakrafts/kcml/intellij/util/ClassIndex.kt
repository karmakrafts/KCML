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

import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType
import com.intellij.psi.impl.java.stubs.index.JavaStubIndexKeys
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.TypeConversionUtil
import com.intellij.util.indexing.IdFilter
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Opcodes
import java.io.IOException
import java.lang.ref.SoftReference
import java.util.concurrent.ConcurrentHashMap

internal data class JvmMethod(val name: String, val descriptor: String)

internal data class CompiledClass(val binaryName: String, val superNames: List<String>, val methods: List<JvmMethod>)

internal object ClassIndex {
    private data class StampedClass(val stamp: Long, val metadata: CompiledClass?)

    private val libraryCandidatesKey = Key.create<CachedValue<ConcurrentHashMap<GlobalSearchScope, List<String>>>>(
        "kcml.classIndex.libraryCandidates"
    )

    private val compiledClassKey = Key.create<SoftReference<StampedClass>>("kcml.classIndex.compiledClass")

    fun findClass(project: Project, scope: GlobalSearchScope, binaryName: String): PsiElement? {
        ProgressManager.checkCanceled()
        val sourceName = binaryName.replace('$', '.')
        val kotlinClass = KotlinFullClassNameIndex.Helper[sourceName, project, scope].firstOrNull()
        if (kotlinClass != null) return kotlinClass
        val facade = JavaPsiFacade.getInstance(project)
        return facade.findClass(binaryName, scope) ?: facade.findClass(sourceName, scope)
    }

    fun findCandidates(project: Project, scope: GlobalSearchScope): Sequence<String> = sequence {
        val sourceScope = scope.intersectWith(GlobalSearchScope.projectScope(project))
        val sourceFiles = IdFilter.getProjectIdFilter(project, false)
        val stubIndex = StubIndex.getInstance()
        val shortNames = buildList<String> {
            stubIndex.processAllKeys(JavaStubIndexKeys.CLASS_SHORT_NAMES, {
                ProgressManager.checkCanceled()
                add(it)
            }, sourceScope, sourceFiles)
        }
        for (shortName in shortNames) {
            ProgressManager.checkCanceled()
            for (psiClass in StubIndex.getElements(
                JavaStubIndexKeys.CLASS_SHORT_NAMES, shortName, project, sourceScope, PsiClass::class.java
            )) {
                val binaryName = psiClass.binaryName() ?: continue
                yield(binaryName)
            }
        }

        val kotlinClassIndex = KotlinFullClassNameIndex.Helper
        val kotlinClassNames = buildList<String> {
            kotlinClassIndex.processAllKeys(sourceScope, sourceFiles) {
                ProgressManager.checkCanceled()
                add(it)
            }
        }
        for (className in kotlinClassNames) {
            ProgressManager.checkCanceled()
            for (declaration in kotlinClassIndex[className, project, sourceScope]) {
                val binaryName = declaration.binaryName() ?: continue
                yield(binaryName)
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
                DumbService.getInstance(project).modificationTracker
            )
        }, false)
        return cache.computeIfAbsent(scope) {
            val projectFileIndex = ProjectFileIndex.getInstance(project)
            val packagePrefixes = hashMapOf<VirtualFile, String?>()
            buildList {
                FileTypeIndex.processFiles(JavaClassFileType.INSTANCE, { classFile ->
                    ProgressManager.checkCanceled()
                    val name = classFile.nameWithoutExtension
                    if (name == "module-info" || name == "package-info") return@processFiles true
                    val directory = classFile.parent ?: return@processFiles true
                    val prefix = packagePrefixes.getOrPut(directory) {
                        val root = projectFileIndex.getClassRootForFile(directory) ?: return@getOrPut null
                        val path = VfsUtilCore.getRelativePath(directory, root, '/') ?: return@getOrPut null
                        if (path == "META-INF/versions" || path.startsWith("META-INF/versions/")) null
                        else if (path.isEmpty()) "" else "${path.replace('/', '.')}."
                    }
                    if (prefix != null) add(prefix + name)
                    true
                }, scope)
            }
        }
    }

    fun compiledClass(file: VirtualFile): CompiledClass? {
        ProgressManager.checkCanceled()
        if (!file.isValid) return null
        val stamp = file.modificationStamp
        val cached = file.getUserData(compiledClassKey)?.get()
        if (cached != null && cached.stamp == stamp) return cached.metadata
        val metadata = readClass(file)
        file.putUserData(compiledClassKey, SoftReference(StampedClass(stamp, metadata)))
        return metadata
    }

    fun findMethods(target: PsiClass): List<JvmMethod> {
        val methods = linkedSetOf<JvmMethod>()
        val visited = hashSetOf<String>()
        val facade = JavaPsiFacade.getInstance(target.project)
        fun collect(type: PsiClass) {
            ProgressManager.checkCanceled()
            val binaryName = type.binaryName() ?: return
            if (!visited.add(binaryName)) return
            val file = type.containingFile?.virtualFile
            if (type is PsiCompiledElement || file?.extension == "class") {
                val fileName = "${binaryName.substringAfterLast('.')}.class"
                val classFile = (if (file?.name == fileName) file else file?.parent?.findChild(fileName)) ?: return
                (val compiledBinaryName = binaryName, val superNames, val compiledMethods = methods) = compiledClass(
                    classFile
                ) ?: return
                if (compiledBinaryName != binaryName) return
                methods += compiledMethods
                for (superName in superNames) {
                    ProgressManager.checkCanceled()
                    if (superName in visited) continue
                    val superClass = facade.findClass(superName.replace('$', '.'), target.resolveScope) ?: continue
                    collect(superClass)
                }
            }
            else {
                for (method in type.methods) {
                    ProgressManager.checkCanceled()
                    if (method.isConstructor) continue
                    val descriptor = method.descriptor() ?: continue
                    methods += JvmMethod(method.name, descriptor)
                }
                type.supers.forEach(::collect)
            }
        }
        collect(target)
        return methods.toList()
    }

    fun findMethod(target: PsiClass, name: String, descriptor: String): PsiMethod? =
        target.findMethodsByName(name, true).firstOrNull {
            ProgressManager.checkCanceled()
            !it.isConstructor && (descriptor.isEmpty() || it.descriptor(allowUnresolved = true) == descriptor)
        }

    private fun readClass(file: VirtualFile): CompiledClass? = try {
        ProgressManager.checkCanceled()
        val reader = ClassReader(file.contentsToByteArray())
        val methods = buildList {
            reader.accept(object : ClassVisitor(Opcodes.ASM9) {
                override fun visitMethod(
                    access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?
                ): MethodVisitor? {
                    ProgressManager.checkCanceled()
                    if (name != "<init>" && name != "<clinit>") add(JvmMethod(name, descriptor))
                    return null
                }
            }, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
        }
        CompiledClass(
            reader.className.replace('/', '.'),
            (listOfNotNull(reader.superName) + reader.interfaces).map { it.replace('/', '.') },
            methods
        )
    } catch (_: IOException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: IndexOutOfBoundsException) {
        null
    }

    private fun PsiMethod.descriptor(allowUnresolved: Boolean = false): String? = buildString {
        append('(')
        for (parameter in parameterList.parameters) {
            ProgressManager.checkCanceled()
            append(parameter.type.descriptor(allowUnresolved) ?: return null)
        }
        append(')')
        append(returnType?.descriptor(allowUnresolved) ?: return null)
    }

    private fun PsiType.descriptor(allowUnresolved: Boolean): String? {
        ProgressManager.checkCanceled()
        return when (val erased = TypeConversionUtil.erasure(this)) {
            is PsiArrayType -> {
                val component = erased.componentType.descriptor(allowUnresolved) ?: return null
                "[$component"
            }

            is PsiClassType -> {
                var name = erased.resolve()?.binaryName()
                if (name == null) {
                    val unresolvedName = erased.rawType().canonicalText
                    if (!allowUnresolved || '.' !in unresolvedName) return null
                    name = unresolvedName
                }
                "L${name.replace('.', '/')};"
            }

            is PsiPrimitiveType -> when (erased.canonicalText) {
                "void" -> "V"
                "boolean" -> "Z"
                "byte" -> "B"
                "char" -> "C"
                "short" -> "S"
                "int" -> "I"
                "long" -> "J"
                "float" -> "F"
                "double" -> "D"
                else -> null
            }

            else -> null
        }
    }

    // TODO: get rid of recursion
    private fun PsiClass.binaryName(): String? {
        ProgressManager.checkCanceled()
        val outer = containingClass ?: return qualifiedName
        val outerName = outer.binaryName() ?: return null
        val className = name ?: return null
        return "${outerName}$$className"
    }

    private fun KtClassOrObject.binaryName(): String? {
        if (fqName == null) return null
        val classNames = ArrayList<String>()
        var current: KtClassOrObject? = this
        while (current != null) {
            ProgressManager.checkCanceled()
            classNames.add(current.name ?: return null)
            current = current.parent as? KtClassOrObject ?: (current.parent as? KtClassBody)?.parent as? KtClassOrObject
        }
        val packageName = (containingFile as? KtFile)?.packageFqName?.asString().orEmpty()
        val className = classNames.asReversed().joinToString("$")
        return if (packageName.isEmpty()) className else "$packageName.$className"
    }
}