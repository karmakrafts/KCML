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

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.testFramework.fixtures.CompletionAutoPopupTester
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.PathUtil
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

class IndirectMixinCompletionAutoPopupTest : LightJavaCodeInsightFixtureTestCase() {
    private lateinit var library: Path

    override fun runInDispatchThread(): Boolean = false

    override fun setUp() {
        super.setUp()
        library = Files.createTempDirectory(Path.of("build"), "kcml-popup-library-").resolve("annotation.jar")
        JarOutputStream(Files.newOutputStream(library)).use { output ->
            val classFile = "dev/karmakrafts/kcml/api/mixin/IndirectMixin.class"
            output.putNextEntry(JarEntry(classFile))
            javaClass.classLoader.getResourceAsStream(classFile)!!.use { it.copyTo(output) }
            output.closeEntry()
        }
        runInEdtAndWait {
            WriteCommandAction.runWriteCommandAction(project) {
                ModuleRootModificationUtil.addModuleLibrary(myFixture.module, "jar://${library.toAbsolutePath()}!/")
            }
            myFixture.addFileToProject(
                "completion/targets/Target.kt", "package completion.targets; class Target; class TargetOther"
            )
        }
    }

    override fun tearDown() {
        try {
            super.tearDown()
        }
        finally {
            if (::library.isInitialized) library.parent.toFile().deleteRecursively()
        }
    }

    fun testTypingInTargetStringShowsCompletionLookup() {
        assertTypingShowsLookup("@IndirectMixin(\"completion.targets.Tar<caret>\") class Example", "g")
    }

    fun testTypingMultipleCharactersInTargetStringKeepsCompletionLookup() {
        assertTypingShowsLookup("@IndirectMixin(\"<caret>\") class Example", "completion.targets.Targ")
    }

    fun testTypingInTargetStringWithCompilerLibraryShowsCompletionLookup() {
        addCompilerLibrary()
        assertTypingShowsLookup("@IndirectMixin(\"completion.targets.Tar<caret>\") class Example", "g")
    }

    fun testSmartCompletionWithCompilerLibraryOffersSourceAndLibraryTargets() {
        addCompilerLibrary()
        runInEdtAndWait {
            myFixture.configureByText(
                "Mixin.kt", """
                    import dev.karmakrafts.kcml.api.mixin.IndirectMixin
                    @IndirectMixin("KtAnnotationEnt<caret>") class Example
                """.trimIndent()
            )
            val variants = myFixture.complete(CompletionType.SMART).orEmpty()
            assertTrue(variants.joinToString { it.lookupString }, variants.any {
                it.lookupString == "org.jetbrains.kotlin.psi.KtAnnotationEntry"
            })
            LookupManager.getInstance(project).hideActiveLookup()
            myFixture.configureByText(
                "Mixin.kt", """
                    import dev.karmakrafts.kcml.api.mixin.IndirectMixin
                    @IndirectMixin("completion.targets.Tar<caret>") class Example
                """.trimIndent()
            )
            val sourceVariants = myFixture.complete(CompletionType.SMART).orEmpty()
            assertTrue(sourceVariants.joinToString { it.lookupString }, sourceVariants.any {
                it.lookupString == "completion.targets.Target"
            })
        }
    }

    fun testEditingExistingTargetStringShowsCompletionLookup() {
        assertTypingShowsLookup("@IndirectMixin(\"completion.targets.Tar<caret>OldSuffix\") class Example", "g")
    }

    fun testTypingInEmptyTargetStringShowsCompletionLookup() {
        assertTypingShowsLookup("@IndirectMixin(\"<caret>\") class Example", "T")
    }

    fun testTypingInRawNamedAliasedTargetShowsCompletionLookup() {
        assertTypingShowsLookup(
            "import dev.karmakrafts.kcml.api.mixin.IndirectMixin as Mixin\n" + "@Mixin(priority = 1, target = \"\"\"completion/targets/Tar<caret>OldSuffix\"\"\") class Example",
            "g"
        )
    }

    fun testTypingPackageSeparatorShowsCompletionLookup() {
        assertTypingShowsLookup("@IndirectMixin(\"completion<caret>targets.Target\") class Example", ".")
    }

    fun testTypingPackageSeparatorAtEndShowsCompletionLookup() {
        assertTypingShowsLookup("@IndirectMixin(\"completion<caret>\") class Example", ".")
    }

    fun testTypingSlashAtEndShowsCompletionLookup() {
        assertTypingShowsLookup("@IndirectMixin(\"completion<caret>\") class Example", "/")
    }

    fun testTypingInUnrelatedAndInterpolatedStringsDoesNotShowTargetLookup() {
        for (text in listOf(
            "fun unrelated() = \"completion.targets.Tar<caret>\"",
            "annotation class Unrelated(val target: String)\n@Unrelated(\"completion.targets.Tar<caret>\") class Example",
            "@IndirectMixin(\"completion.targets.Tar<caret>\${value}\") class Example",
            "@IndirectMixin(target = \"valid\", priority = \"completion.targets.Tar<caret>\") class Example"
        )) {
            val tester = CompletionAutoPopupTester(myFixture)
            tester.runWithAutoPopupEnabled {
                runInEdtAndWait {
                    myFixture.configureByText(
                        "Unrelated.kt", "import dev.karmakrafts.kcml.api.mixin.IndirectMixin\n$text"
                    )
                }
                tester.typeWithPauses("g")
                runInEdtAndWait {
                    assertFalse(
                        text,
                        tester.lookup?.items.orEmpty().any { "completion.targets.Target" in it.allLookupStrings })
                    tester.lookup?.hide()
                }
            }
        }
    }

    private fun addCompilerLibrary() {
        val compilerLibrary = PathUtil.getJarPathForClass(KtAnnotationEntry::class.java)
        runInEdtAndWait {
            WriteCommandAction.runWriteCommandAction(project) {
                ModuleRootModificationUtil.addModuleLibrary(myFixture.module, "jar://$compilerLibrary!/")
            }
        }
    }

    private fun assertTypingShowsLookup(text: String, typed: String) {
        val tester = CompletionAutoPopupTester(myFixture)
        tester.runWithAutoPopupEnabled {
            runInEdtAndWait {
                myFixture.configureByText("Mixin.kt", "import dev.karmakrafts.kcml.api.mixin.IndirectMixin\n$text")
            }

            tester.typeWithPauses(typed)

            runInEdtAndWait {
                val lookup = tester.lookup
                assertNotNull("Typing in an IndirectMixin target should open completion", lookup)
                assertTrue(lookup!!.items.any { "completion.targets.Target" in it.allLookupStrings })
                lookup.hide()
            }
        }
    }
}