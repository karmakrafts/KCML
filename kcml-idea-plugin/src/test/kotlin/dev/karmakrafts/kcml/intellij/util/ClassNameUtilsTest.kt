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

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.extensions.ProjectExtensionPointName
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.util.Processor
import org.jetbrains.org.objectweb.asm.ClassWriter
import org.jetbrains.org.objectweb.asm.Opcodes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.io.path.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.outputStream

class ClassNameUtilsTest : LightJavaCodeInsightFixtureTestCase() {
    fun testLibraryCandidatesAreReusedAcrossEditsAndInvalidatedByClasspathChanges() {
        val directory = createTempDirectory(Path("build"), "kcml-class-name-cache-")
        try {
            fun addLibrary(name: String) {
                val jar = directory.resolve("$name.jar")
                val className = "completion/library/$name"
                val writer = ClassWriter(0)
                writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", null)
                writer.visitEnd()
                JarOutputStream(jar.outputStream()).use { output ->
                    output.putNextEntry(JarEntry("$className.class"))
                    output.write(writer.toByteArray())
                    output.closeEntry()
                }
                WriteCommandAction.runWriteCommandAction(project) {
                    ModuleRootModificationUtil.addModuleLibrary(myFixture.module, "jar://${jar.toAbsolutePath()}!/")
                }
                IndexingTestUtil.waitUntilIndexesAreReady(project)
            }

            addLibrary("First")
            myFixture.configureByText("Mixin.kt", "val target = \"completion.library.<caret>\"")
            val scope = GlobalSearchScope.allScope(project)
            val initial = ClassIndex.findLibraryCandidates(project, scope)
            assertTrue("Missing initial library class", "completion.library.First" in initial)
            assertFalse(
                "Library candidates must respect the requested scope",
                "completion.library.First" in ClassIndex.findLibraryCandidates(
                    project, GlobalSearchScope.projectScope(project)
                )
            )

            val editOffset = myFixture.caretOffset
            WriteCommandAction.runWriteCommandAction(project) {
                myFixture.editor.document.insertString(editOffset, "F")
            }
            assertSame(initial, ClassIndex.findLibraryCandidates(project, scope))

            WriteCommandAction.runWriteCommandAction(project) {
                myFixture.editor.document.deleteString(editOffset, editOffset + 1)
            }
            assertSame(initial, ClassIndex.findLibraryCandidates(project, scope))

            addLibrary("Second")
            val updated = ClassIndex.findLibraryCandidates(project, scope)
            assertNotSame(initial, updated)
            assertTrue("Lost initial library class", "completion.library.First" in updated)
            assertTrue("Missing newly indexed library class", "completion.library.Second" in updated)
        }
        finally {
            directory.toFile().deleteRecursively()
        }
    }

    fun testCandidateEnumerationDoesNotUseGlobalShortNamesCache() {
        myFixture.addFileToProject(
            "completion/JavaOuter.java", """
                package completion;
                public class JavaOuter { private static class Nested {} }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "completion/KotlinOuter.kt", """
                package completion
                private class KotlinOuter { private class Nested }
            """.trimIndent()
        )
        ProjectExtensionPointName<PsiShortNamesCache>(PsiShortNamesCache.EP_NAME.name).getPoint(project)
            .registerExtension(
                object : PsiShortNamesCache() {
                    override fun getAllClassNames(): Array<String> = globalCacheAccess()
                    override fun getClassesByName(name: String, scope: GlobalSearchScope): Array<PsiClass> =
                        globalCacheAccess()

                    override fun getAllMethodNames(): Array<String> = globalCacheAccess()
                    override fun getMethodsByName(name: String, scope: GlobalSearchScope): Array<PsiMethod> =
                        globalCacheAccess()

                    override fun getMethodsByNameIfNotMoreThan(
                        name: String, scope: GlobalSearchScope, maxCount: Int
                    ): Array<PsiMethod> = globalCacheAccess()

                    override fun processMethodsWithName(
                        name: String, scope: GlobalSearchScope, processor: Processor<in PsiMethod>
                    ): Boolean = globalCacheAccess()

                    override fun getAllFieldNames(): Array<String> = globalCacheAccess()
                    override fun getFieldsByName(name: String, scope: GlobalSearchScope): Array<PsiField> =
                        globalCacheAccess()

                    override fun getFieldsByNameIfNotMoreThan(
                        name: String, scope: GlobalSearchScope, maxCount: Int
                    ): Array<PsiField> = globalCacheAccess()

                    private fun globalCacheAccess(): Nothing =
                        error("Completion must not query the global short-name cache")
                }, testRootDisposable
            )

        val candidates = ClassIndex.findCandidates(project, GlobalSearchScope.allScope(project)).toSet()
        assertTrue(
            candidates.toString(), candidates.containsAll(
                listOf(
                    "completion.JavaOuter",
                    "completion.JavaOuter\$Nested",
                    "completion.KotlinOuter",
                    "completion.KotlinOuter\$Nested"
                )
            )
        )
    }
}