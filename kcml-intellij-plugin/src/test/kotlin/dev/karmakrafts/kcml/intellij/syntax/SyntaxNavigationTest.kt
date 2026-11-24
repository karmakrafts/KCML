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

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.facet.FacetManager
import com.intellij.lang.documentation.ide.IdeDocumentationTargetProvider
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.platform.backend.documentation.DocumentationData
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.KaSpiExtensionPoint
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import org.jetbrains.kotlin.idea.facet.KotlinFacetType
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

@OptIn(KaExperimentalApi::class, KaSpiExtensionPoint::class)
class SyntaxNavigationTest : JavaCodeInsightFixtureTestCase() {
    private var facet: KotlinFacet? = null
    private lateinit var configured: Module

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

    fun testNavigationHandlerIsRegisteredBeforeKotlin() {
        val handlers = GotoDeclarationHandler.EP_NAME.extensionList
        val syntax = handlers.indexOfFirst { it is SyntaxGotoDeclarationHandler }
        val kotlin = handlers.indexOfFirst { it.javaClass.name.startsWith("org.jetbrains.kotlin.") }
        assertTrue("Syntax navigation must be registered", syntax >= 0)
        assertTrue("Syntax navigation must run before Kotlin", kotlin > syntax)
    }

    fun testOrdinaryKotlinFallsBackWithoutRuntime() {
        myFixture.configureByText("Navigation.kt", "fun target() = 1\nfun usage() = tar<caret>get()")
        assertNull(navigationTargets())
        val source = myFixture.file
        val offset = myFixture.caretOffset
        val target = background { source.findReferenceAt(offset)?.resolve() }
        assertSourceTarget(source, "target", target)
    }

    fun testPartiallyLoweredFileNavigatesToPhysicalDeclarationName() {
        configureSyntax(
            """
            fn generated() = magic
            fun usage() = gen<caret>erated()
        """.trimIndent()
        )
        assertSourceTarget(myFixture.file, "generated", navigationTargets()?.singleOrNull())
    }

    fun testFullyLoweredFileResolvesReferencesMissingFromSourcePsi() {
        configureSyntax(
            """
            fn generated() = magic
            fn usage() = gen<caret>erated()
        """.trimIndent()
        )
        assertSourceTarget(myFixture.file, "generated", navigationTargets()?.singleOrNull())
    }

    fun testHoverDocumentationInPartiallyLoweredFile() {
        configureSyntax(
            """
            /** Documentation from lowered declaration. */
            fn generated() = magic
            fun usage() = gen<caret>erated()
        """.trimIndent()
        )
        val html = documentationHtml()
        assertTrue(html, html.contains("generated"))
        assertTrue(html, html.contains("Documentation from lowered declaration."))
    }

    fun testHoverDocumentationOnLoweredDeclaration() {
        configureSyntax(
            """
            /** Documentation on declaration. */
            fn gen<caret>erated() = magic
            fun unchanged() = 0
        """.trimIndent()
        )
        val html = documentationHtml()
        assertTrue(html, html.contains("generated"))
        assertTrue(html, html.contains("Documentation on declaration."))
    }

    fun testHoverDocumentationOnUnmodifiedLocalWithLoweredInitializer() {
        configureSyntax(
            """
            fun usage() {
                val ans<caret>wer = magic
            }
        """.trimIndent()
        )
        val html = documentationHtml()
        assertTrue(html, html.contains("answer"))
        assertTrue(html, html.contains("Int"))
        assertFalse(html, html.contains("ERROR"))
    }

    fun testHoverDocumentationInsideFullyLoweredFunction() {
        configureSyntax(
            """
            fn usage(value: Int): Int {
                val local = magic
                return lo<caret>cal + value
            }
        """.trimIndent()
        )
        val html = documentationHtml()
        assertTrue(html, html.contains("local"))
        assertTrue(html, html.contains("Int"))
    }

    fun testHoverDocumentationForCrossFileLoweredMember() {
        configureSyntax("package usage\nfn usage() = library.Widget().gen<caret>erated()")
        addSource(
            "Library.kt", """
            package library
            class Widget {
                /** Cross-file documentation. */
                fn generated() = magic
            }
        """.trimIndent()
        )
        val html = documentationHtml()
        assertTrue(html, html.contains("generated"))
        assertTrue(html, html.contains("Cross-file documentation."))
    }

    fun testHoverDocumentationFallsBackForOrdinaryKotlin() {
        myFixture.configureByText("Documentation.kt", "/** Ordinary documentation. */\nfun tar<caret>get() = 1")
        val file = myFixture.file
        val offset = myFixture.caretOffset
        assertTrue(background { SyntaxDocumentationTargetProvider().documentationTargets(file, offset).isEmpty() })
        assertTrue(documentationHtml().contains("Ordinary documentation."))
    }

    fun testHoverDocumentationFallsBackForUnchangedFileAndUnloadedRuntime() {
        configureSyntax("/** Unchanged documentation. */\nfun tar<caret>get() = 1")
        val file = myFixture.file
        val offset = myFixture.caretOffset
        assertTrue(background { SyntaxDocumentationTargetProvider().documentationTargets(file, offset).isEmpty() })
        assertTrue(documentationHtml().contains("Unchanged documentation."))
        project.service<SyntaxExtensionService>().loadRuntime(configured, null).get(15, TimeUnit.SECONDS)
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        assertTrue(background { SyntaxDocumentationTargetProvider().documentationTargets(file, offset).isEmpty() })
        assertTrue(documentationHtml().contains("Unchanged documentation."))
    }

    fun testHoverDocumentationRefreshesAfterSourceEdit() {
        configureSyntax("fn gen<caret>erated() = magic\nfun unchanged() = 0")
        assertTrue(documentationHtml().contains("generated"))
        runWriteAction {
            myFixture.editor.document.setText("/** Updated documentation. */\nfn replacement() = magic\nfun unchanged() = 0")
            PsiDocumentManager.getInstance(project).commitAllDocuments()
        }
        myFixture.editor.caretModel.moveToOffset(myFixture.file.text.indexOf("replacement") + 2)
        val html = documentationHtml()
        assertTrue(html, html.contains("replacement"))
        assertTrue(html, html.contains("Updated documentation."))
    }

    private fun documentationHtml(): String {
        val file = myFixture.file
        val editor = myFixture.editor
        val offset = myFixture.caretOffset
        return background {
            val targets = IdeDocumentationTargetProvider.getInstance(project).documentationTargets(editor, file, offset)
            assertFalse("Expected hover documentation targets", targets.isEmpty())
            val target = targets.first().createPointer().dereference()
            assertNotNull("Documentation target must survive pointer dereference", target)
            val documentation = target!!.computeDocumentation() as? DocumentationData
            assertNotNull("Expected rendered Kotlin documentation", documentation)
            documentation!!.html
        }
    }

    fun testLocalDeclarationInsideLoweredFunction() {
        configureSyntax(
            """
            fn usage(value: Int): Int {
                val local = value
                return lo<caret>cal
            }
        """.trimIndent()
        )
        assertSourceTarget(myFixture.file, "local", navigationTargets()?.singleOrNull())
    }

    fun testParameterInsideLoweredFunction() {
        configureSyntax("fn usage(value: Int) = va<caret>lue")
        assertSourceTarget(myFixture.file, "value", navigationTargets()?.singleOrNull())
    }

    fun testCrossFileGeneratedMemberMapsToItsPhysicalSource() {
        configureSyntax("package usage\nfn usage() = library.Widget().gen<caret>erated()")
        val library = addSource(
            "Library.kt", """
            package library
            class Widget {
                fn generated() = magic
            }
        """.trimIndent()
        )
        assertSourceTarget(library, "generated", navigationTargets()?.singleOrNull())
    }

    fun testCrossFileGeneratedTopLevelFunctionMapsToItsPhysicalSource() {
        configureSyntax("package usage\nimport library.generated\nfn usage() = gen<caret>erated()")
        val library = addSource("Library.kt", "package library\nfn generated() = magic")
        assertSourceTarget(library, "generated", navigationTargets()?.singleOrNull())
    }

    fun testExternalUnchangedKotlinTargetIsPreserved() {
        configureSyntax("fn usage() = ex<caret>ternal()")
        val external = addSource("External.kt", "fun external() = 1")
        assertSourceTarget(external, "external", navigationTargets()?.singleOrNull())
    }

    fun testUnchangedFileAndUnresolvedReferenceKeepKotlinFallback() {
        configureSyntax("fun usage() = mis<caret>sing()")
        assertNull(navigationTargets())
        runWriteAction {
            myFixture.editor.document.setText("fn usage() = missing()")
            PsiDocumentManager.getInstance(project).commitAllDocuments()
        }
        myFixture.editor.caretModel.moveToOffset(myFixture.file.text.indexOf("missing") + 2)
        assertNull(navigationTargets())
    }

    fun testNavigationRefreshesAfterSourceEdit() {
        configureSyntax("fn first() = magic\nfn second() = magic\nfn usage() = fir<caret>st()")
        assertSourceTarget(myFixture.file, "first", navigationTargets()?.singleOrNull())
        runWriteAction {
            myFixture.editor.document.setText("fn first() = magic\nfn second() = magic\nfn usage() = second()")
            PsiDocumentManager.getInstance(project).commitAllDocuments()
        }
        myFixture.editor.caretModel.moveToOffset(myFixture.file.text.lastIndexOf("second") + 2)
        assertSourceTarget(myFixture.file, "second", navigationTargets()?.singleOrNull())
    }

    fun testRuntimeUnloadRestoresKotlinFallback() {
        configureSyntax("fn generated() = magic\nfn usage() = gen<caret>erated()")
        assertSourceTarget(myFixture.file, "generated", navigationTargets()?.singleOrNull())
        project.service<SyntaxExtensionService>().loadRuntime(configured, null).get(15, TimeUnit.SECONDS)
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        assertNull(navigationTargets())
    }

    fun testResolveExtensionNavigationUsesNameRatherThanKeywordOrModifier() {
        myFixture.configureByText("Navigation.kt", "private fun generated() = 1")
        val source = myFixture.file as KtFile
        val lowered = KtPsiFactory(project).createFile("private fun generated() = 42")
        val provider = SyntaxNavigationTargets(source, SyntaxOffsetMap(source.text, lowered.text))
        val target = background {
            analyze(source) {
                with(provider) { contextOf<KaSession>().getNavigationTargets(lowered.declarations.single()).single() }
            }
        }
        assertSourceTarget(source, "generated", target)
    }

    private fun navigationTargets(): Array<PsiElement>? {
        val file = myFixture.file
        val offset = myFixture.caretOffset
        val editor = myFixture.editor
        return background {
            SyntaxGotoDeclarationHandler().getGotoDeclarationTargets(file.findElementAt(offset), offset, editor)
        }
    }

    private fun <T> background(action: () -> T): T = AppExecutorUtil.getAppExecutorService().submit<T> {
        runReadActionBlocking { action() }
    }.get(30, TimeUnit.SECONDS)

    private fun assertSourceTarget(source: PsiFile, name: String, target: PsiElement?) {
        assertNotNull("Expected a navigation target for $name", target)
        assertTrue("Navigation must not expose lowered PSI", target!!.containingFile.isPhysical)
        assertEquals(source.virtualFile, target.containingFile.virtualFile)
        assertEquals(source.text.indexOf(name), target.textOffset)
    }

    private fun addSource(name: String, text: String): PsiFile {
        val file = runWriteAction {
            myFixture.file.virtualFile.parent.createChildData(this, name).also { VfsUtil.saveText(it, text) }
        }
        return PsiManager.getInstance(project).findFile(file)!!
    }

    private fun configureSyntax(text: String) {
        myFixture.configureByText("Navigation.kt", text)
        val source = myFixture.file.text
        val caret = myFixture.caretOffset
        val directory = Files.createTempDirectory(Path.of("build").toAbsolutePath(), "syntax-navigation-")
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
            root.createChildData(this, "Navigation.kt").also { VfsUtil.saveText(it, source) }
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