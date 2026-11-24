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

package dev.karmakrafts.kcml.intellij.syntax

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.daemon.ProblemHighlightFilter
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.facet.FacetManager
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaPlatformInterface
import org.jetbrains.kotlin.analysis.api.KaSpiExtensionPoint
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import org.jetbrains.kotlin.idea.facet.KotlinFacetType
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.psi.KtFile
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

@OptIn(KaExperimentalApi::class, KaPlatformInterface::class, KaSpiExtensionPoint::class)
class SyntaxCodeInsightTest : JavaCodeInsightFixtureTestCase() {
    private var facet: KotlinFacet? = null
    private lateinit var configured: Module

    fun testOrdinaryKotlinCompletionIsUnchanged() {
        myFixture.configureByText(
            "Completion.kt", """
            fun generatedFirst() = 1
            fun generatedSecond() = 2
            fun usage() = generated<caret>
        """.trimIndent()
        )
        val variants = myFixture.complete(CompletionType.BASIC).orEmpty()
        assertContainsElements(variants.map { it.lookupString }, "generatedFirst", "generatedSecond")
    }

    override fun tearDown() {
        try {
            facet?.let {
                runWriteAction {
                    FacetManager.getInstance(configured).createModifiableModel().apply {
                        removeFacet(it)
                        commit()
                    }
                }
            }
            if (::configured.isInitialized && !configured.isDisposed) runWriteAction {
                ModuleManager.getInstance(project).disposeModule(configured)
            }
            project.service<SyntaxExtensionService>().refresh()
        }
        finally {
            super.tearDown()
        }
    }

    fun testBasicCompletionResolvesDesugaredDeclarationsAndInsertsIntoSource() {
        myFixture.configureByText(
            "Completion.kt", """
            fn generatedFirst() = magic
            fn generatedSecond() = magic
            fun usage() = generated<caret>
        """.trimIndent()
        )
        configureSyntax()
        val contributors = CompletionContributor.forLanguage(KotlinLanguage.INSTANCE, myFixture.editor)
        assertTrue(
            contributors.map { it.javaClass.name }.toString(),
            contributors.indexOfFirst { it is SyntaxCompletionContributor } < contributors.indexOfFirst {
                it.javaClass.name.endsWith("KotlinFirCompletionContributor")
            })
        val runtime = project.service<SyntaxExtensionService>().runtime(configured)
        assertNotNull("Syntax runtime must be available before invoking completion", runtime)
        assertEquals(
            "fun generatedFirst() = 42\nfun generatedSecond() = 42\nfun usage() = generated",
            runtime!!.lower(myFixture.file as KtFile).text
        )
        val source = myFixture.file as KtFile
        assertTrue(
            "Completion file must be in module source roots",
            ProjectFileIndex.getInstance(project).isInSourceContent(source.virtualFile)
        )
        assertSame(configured, ProjectFileIndex.getInstance(project).getModuleForFile(source.virtualFile))
        val names = AppExecutorUtil.getAppExecutorService().submit<List<String>> {
            runReadActionBlocking {
                analyze(source) {
                    val module = SyntaxAnalysisContext.sourceModule(source)!!
                    val extensions = SyntaxResolveExtensionProvider().provideExtensionsFor(module)
                    assertFalse("No resolve extension for $module", extensions.isEmpty())
                    extensions.flatMap { it.getKtFiles() }
                        .flatMap { it.getTopLevelCallableNames() }
                        .map { it.asString() }
                }
            }
        }.get(30, TimeUnit.SECONDS)
        assertContainsElements(names, "generatedFirst", "generatedSecond")
        val variants = myFixture.complete(CompletionType.BASIC).orEmpty()
        assertContainsElements(variants.map { it.lookupString }, "generatedFirst", "generatedSecond")
        myFixture.lookup.currentItem = variants.single { it.lookupString == "generatedFirst" }
        myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
        myFixture.checkResult(
            """
            fn generatedFirst() = magic
            fn generatedSecond() = magic
            fun usage() = generatedFirst()<caret>
        """.trimIndent()
        )
        val rejected = ProblemHighlightFilter.EP_NAME.extensionList.filterNot { it.shouldHighlight(myFixture.file) }
        assertTrue("Syntax files must remain highlightable: ${rejected.map { it.javaClass.name }}", rejected.isEmpty())
        assertEmpty(myFixture.doHighlighting().filter { it.severity == HighlightSeverity.ERROR })
    }

    fun testCompletionAndInspectionsResolveDesugaredDeclarationsInOtherFiles() {
        myFixture.configureByText(
            "Completion.kt", """
            fn generatedFirst() = magic
            fn generatedSecond() = magic
        """.trimIndent()
        )
        configureSyntax()
        val root = myFixture.file.virtualFile.parent
        val usage = runWriteAction {
            root.createChildData(this, "Usage.kt").also { VfsUtil.saveText(it, "fun usage(): Int = generated") }
        }
        myFixture.configureFromExistingVirtualFile(usage)
        myFixture.editor.caretModel.moveToOffset(myFixture.file.textLength)
        val variants = myFixture.complete(CompletionType.BASIC).orEmpty()
        assertContainsElements(variants.map { it.lookupString }, "generatedFirst", "generatedSecond")
        myFixture.lookup.currentItem = variants.single { it.lookupString == "generatedFirst" }
        myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
        myFixture.checkResult("fun usage(): Int = generatedFirst()<caret>")
        assertEmpty(myFixture.doHighlighting().filter { it.severity == HighlightSeverity.ERROR })
    }

    fun testSmartCompletionUsesDesugaredExpectedTypes() {
        Registry.get("kotlin.k2.smart.completion.enabled").setValue(true, testRootDisposable)
        myFixture.configureByText(
            "Completion.kt", """
            class Desired
            class Other
            fn generatedFirst() = Desired()
            fn generatedSecond() = Desired()
            fn generatedOther() = Other()
            fun usage(): Desired = generated<caret>
        """.trimIndent()
        )
        configureSyntax()
        val names = myFixture.complete(CompletionType.SMART).orEmpty().map { it.lookupString }
        assertContainsElements(names, "generatedFirst", "generatedSecond")
        assertFalse(names.toString(), "generatedOther" in names)
    }

    fun testMemberCompletionUsesDesugaredClassMembers() {
        myFixture.configureByText(
            "Completion.kt", """
            class Widget {
                fn generatedFirst() = magic
                fn generatedSecond() = magic
            }
            fun usage(widget: Widget) = widget.generated<caret>
        """.trimIndent()
        )
        configureSyntax()
        val variants = myFixture.complete(CompletionType.BASIC).orEmpty()
        assertContainsElements(variants.map { it.lookupString }, "generatedFirst", "generatedSecond")
        myFixture.lookup.currentItem = variants.single { it.lookupString == "generatedFirst" }
        myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
        assertEmpty(myFixture.doHighlighting().filter { it.severity == HighlightSeverity.ERROR })
    }

    fun testGeneratedDeclarationsRefreshAfterEditsAndDisappearAfterRuntimeUnload() {
        myFixture.configureByText("Completion.kt", "fn generatedFirst() = magic\nfn generatedSecond() = magic")
        configureSyntax()
        val library = myFixture.file.virtualFile
        val usage = runWriteAction {
            library.parent.createChildData(this, "Usage.kt").also { VfsUtil.saveText(it, "fun usage() = generated") }
        }
        myFixture.configureFromExistingVirtualFile(usage)
        myFixture.editor.caretModel.moveToOffset(myFixture.file.textLength)
        assertContainsElements(
            myFixture.completeBasic().orEmpty().map { it.lookupString },
            "generatedFirst",
            "generatedSecond"
        )
        LookupManager.hideActiveLookup(project)
        runWriteAction {
            FileDocumentManager.getInstance().getDocument(library)!!.setText(
                "fn generatedNewFirst() = magic\nfn generatedNewSecond() = magic"
            )
            PsiDocumentManager.getInstance(project).commitAllDocuments()
        }
        val updated = myFixture.completeBasic().orEmpty().map { it.lookupString }
        assertContainsElements(updated, "generatedNewFirst", "generatedNewSecond")
        assertFalse(updated.toString(), "generatedFirst" in updated || "generatedSecond" in updated)
        LookupManager.hideActiveLookup(project)
        runWriteAction {
            FacetManager.getInstance(configured).createModifiableModel().apply {
                removeFacet(facet!!)
                commit()
            }
        }
        facet = null
        project.service<SyntaxExtensionService>().loadRuntime(configured, null).get(15, TimeUnit.SECONDS)
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        val unloaded = myFixture.completeBasic().orEmpty().map { it.lookupString }
        assertFalse(unloaded.toString(), "generatedNewFirst" in unloaded || "generatedNewSecond" in unloaded)
    }

    private fun configureSyntax() {
        val text = myFixture.file.text
        val caret = myFixture.caretOffset
        val directory = Files.createTempDirectory(Path.of("build").toAbsolutePath(), "syntax-completion-")
        Disposer.register(testRootDisposable) { FileUtil.delete(directory.toFile()) }
        VfsRootAccess.allowRootAccess(testRootDisposable, directory.toString())
        val content = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(directory)!!
        val root = runWriteAction { content.createChildDirectory(this, "src") }
        configured = runWriteAction {
            ModuleManager.getInstance(project).newModule(directory.resolve("configured.iml"), "JAVA_MODULE")
        }
        PsiTestUtil.addContentRoot(configured, content)
        PsiTestUtil.addSourceRoot(configured, root)
        val file = runWriteAction {
            root.createChildData(this, "Completion.kt").also { VfsUtil.saveText(it, text) }
        }
        myFixture.configureFromExistingVirtualFile(file)
        myFixture.editor.caretModel.moveToOffset(caret)
        val manager = FacetManager.getInstance(configured)
        val kotlinFacet = manager.createFacet(KotlinFacetType.INSTANCE, "Kotlin", null)
        facet = kotlinFacet
        kotlinFacet.configuration.settings.apply {
            useProjectSettings = false
            targetPlatform = JvmPlatforms.defaultJvmPlatform
            compilerArguments = K2JVMCompilerArguments().apply {
                pluginOptions = arrayOf(
                    "plugin:kcml.bootstrap:pluginClasspaths=${System.getProperty("kcml.test.examplePlugin")}",
                    "plugin:kcml.bootstrap:compileTarget=jvm"
                )
            }
            updateMergedArguments()
        }
        runWriteAction {
            manager.createModifiableModel().apply {
                addFacet(kotlinFacet)
                commit()
            }
        }
        project.service<SyntaxExtensionService>()
            .loadRuntime(configured, SyntaxConfiguration.fromModule(configured))
            .get(15, TimeUnit.SECONDS)
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    }
}