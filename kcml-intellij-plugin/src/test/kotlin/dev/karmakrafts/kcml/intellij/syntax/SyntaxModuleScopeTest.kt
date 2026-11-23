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

import com.intellij.facet.FacetManager
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import dev.karmakrafts.kcml.api.ExperimentalKcmlApi
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.idea.facet.KotlinFacetType
import org.jetbrains.kotlin.psi.KtFile
import java.nio.file.Path
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalKcmlApi::class)
class SyntaxModuleScopeTest : JavaCodeInsightFixtureTestCase() {
    private lateinit var configured: Module
    private lateinit var plain: Module
    private lateinit var configuredRoot: VirtualFile
    private lateinit var plainRoot: VirtualFile

    override fun setUp() {
        super.setUp()
        val exampleJar = Path.of(System.getProperty("kcml.test.examplePlugin"))
        VfsRootAccess.allowRootAccess(testRootDisposable, exampleJar.parent.toString())
        configured = myFixture.module
        configuredRoot = myFixture.tempDirFixture.findOrCreateDir("configured")
        plainRoot = myFixture.tempDirFixture.findOrCreateDir("plain")
        plain = runWriteAction {
            ModuleManager.getInstance(project).newModule("${plainRoot.path}/plain.iml", "JAVA_MODULE")
        }
        for ((module, root) in listOf(configured to configuredRoot, plain to plainRoot)) {
            PsiTestUtil.addContentRoot(module, root)
            PsiTestUtil.addSourceRoot(module, myFixture.tempDirFixture.findOrCreateDir("${root.name}/src"))
            val manager = FacetManager.getInstance(module)
            runWriteAction {
                val facet = manager.createFacet(KotlinFacetType.INSTANCE, "Kotlin", null)
                facet.configuration.settings.apply {
                    useProjectSettings = false
                    compilerArguments = K2JVMCompilerArguments().apply {
                        if (module == configured) pluginOptions = arrayOf(
                            "plugin:kcml.bootstrap:pluginClasspaths=$exampleJar",
                            "plugin:kcml.bootstrap:compileTarget=jvm"
                        )
                    }
                    updateMergedArguments()
                }
                manager.createModifiableModel().apply {
                    addFacet(facet)
                    commit()
                }
            }
        }
    }

    fun testDependencyOnConfiguredModuleDoesNotEnableExtensions() {
        ModuleRootModificationUtil.addDependency(plain, configured)
        checkModuleIsolation()
    }

    fun testDependencyOfConfiguredModuleDoesNotReceiveExtensions() {
        ModuleRootModificationUtil.addDependency(configured, plain)
        checkModuleIsolation()
    }

    fun testNonSourceContentDoesNotUseExtensions() {
        val source = addFile("configured/Outside.kt")
        assertSame(configured, ModuleUtilCore.findModuleForPsiElement(source))
        assertFalse(ProjectFileIndex.getInstance(project).isInSourceContent(source.virtualFile))
        loadConfiguredRuntime()
        assertNull("Content outside source roots must keep standard Kotlin analysis", analyze(source))
        val errors = PsiTreeUtil.collectElementsOfType(source, PsiErrorElement::class.java)
        assertFalse("Unconfigured source must retain its raw Kotlin parse errors", errors.isEmpty())
        assertTrue("KCML must not suppress non-source parse errors", errors.all {
            SyntaxErrorFilter().shouldHighlightErrorElement(it)
        })
    }

    fun testLibrarySourcesDoNotUseConsumerExtensions() {
        val source = addFile("library/Library.kt")
        ModuleRootModificationUtil.updateModel(configured) { roots ->
            val library = roots.moduleLibraryTable.createLibrary("sources")
            library.modifiableModel.apply {
                addRoot(source.virtualFile.parent, OrderRootType.SOURCES)
                commit()
            }
        }
        assertTrue(ProjectFileIndex.getInstance(project).isInLibrarySource(source.virtualFile))
        loadConfiguredRuntime()
        assertNull("Library sources must not use a consuming module's syntax extensions", analyze(source))
    }

    private fun checkModuleIsolation() {
        val enabled = addFile("configured/src/Enabled.kt")
        val disabled = addFile("plain/src/Disabled.kt")
        assertSame(configured, ModuleUtilCore.findModuleForPsiElement(enabled))
        assertSame(plain, ModuleUtilCore.findModuleForPsiElement(disabled))
        assertNull(SyntaxConfiguration.fromModule(plain))
        loadConfiguredRuntime()
        val result = analyze(enabled)
        assertNotNull("The configured module must apply its syntax extensions", result)
        assertTrue(result!!.diagnostics.toString(), result.diagnostics.none { it.severity == HighlightSeverity.ERROR })
        assertFalse(result.highlighting.isEmpty())
        assertNull("KCML options must not be inherited through module dependencies", analyze(disabled))
        assertNull(project.service<SyntaxExtensionService>().runtime(plain))
        assertHostErrors(disabled)
        val unresolved = addFile("plain/src/Unresolved.kt", "fun ordinary() = magic")
        myFixture.configureFromExistingVirtualFile(unresolved.virtualFile)
        val hostHighlights = myFixture.doHighlighting()
        assertTrue("Raw Kotlin unresolved references must remain visible: $hostHighlights", hostHighlights.any {
            it.severity == HighlightSeverity.ERROR && it.description?.contains("UNRESOLVED_REFERENCE") == true &&
                unresolved.text.substring(it.startOffset, it.endOffset) == "magic"
        })
        myFixture.configureFromExistingVirtualFile(enabled.virtualFile)
        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.toString(), highlights.none { it.severity == HighlightSeverity.ERROR })
    }

    private fun loadConfiguredRuntime() {
        val service = project.service<SyntaxExtensionService>()
        val runtime = service.loadRuntime(configured, SyntaxConfiguration.fromModule(configured)).get(15, TimeUnit.SECONDS)
        assertNotNull(runtime)
        assertEquals(listOf("example:syntax_example"), runtime!!.extensionIds)
    }

    private fun addFile(path: String, text: String = "fn answer() = magic"): KtFile {
        val file = myFixture.tempDirFixture.createFile(path, text)
        return PsiManager.getInstance(project).findFile(file) as KtFile
    }

    private fun analyze(file: KtFile): SyntaxAnalysis.Result? =
        ApplicationManager.getApplication().executeOnPooledThread<SyntaxAnalysis.Result?> {
            runReadActionBlocking { SyntaxAnalysis.result(file) }
        }.get(30, TimeUnit.SECONDS)

    private fun assertHostErrors(file: KtFile) {
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        val highlights = myFixture.doHighlighting()
        assertTrue("Raw Kotlin syntax errors must remain visible: $highlights", highlights.any {
            it.severity == HighlightSeverity.ERROR && it.description?.contains("Expecting") == true
        })
        assertTrue("Raw Kotlin errors on extension tokens must remain visible: $highlights", highlights.any {
            it.severity == HighlightSeverity.ERROR && file.text.substring(it.startOffset, it.endOffset) == "magic"
        })
    }
}