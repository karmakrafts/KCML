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
import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.impl.ProjectViewImpl
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.IconLoader
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.replaceService
import dev.karmakrafts.kcml.intellij.testutil.AnnotationFixtureTestCase
import org.jetbrains.jps.model.java.JavaResourceRootType

class PluginConfigIconProviderTest : AnnotationFixtureTestCase() {
    override fun setUp() {
        super.setUp()
        addAnnotationSource(
            "dev/karmakrafts/kcml/api/plugin/Plugin.kt", """
                package dev.karmakrafts.kcml.api.plugin
                @Target(AnnotationTarget.CLASS)
                annotation class Plugin(val id: String)
            """.trimIndent()
        )
    }

    fun testMatchingPluginUsesKcmlIcon() {
        addPlugin("@Plugin(\"example\") class Example")
        val file = addResource("META-INF/example.json")
        val providers = ExtensionPointName.create<FileIconProvider>("com.intellij.fileIconProvider").extensionList
        val provider = providers.filterIsInstance<PluginConfigIconProvider>().single()
        assertEquals(
            IconLoader.getIcon("/icons/dev/karmakrafts/kcml/intellij/kcml.svg", javaClass),
            provider.getIcon(file.virtualFile, 0, project)
        )
    }

    fun testNamedIdAndImportedAlias() {
        addPlugin("import dev.karmakrafts.kcml.api.plugin.Plugin as CompilerPlugin\n@CompilerPlugin(id = \"example\") object Example")
        assertNotNull(icon("META-INF/example.json"))
    }

    fun testQualifiedAnnotationOnNestedClass() {
        addPlugin("class Outer { @dev.karmakrafts.kcml.api.plugin.Plugin(\"example\") class Example }")
        assertNotNull(icon("META-INF/example.json"))
    }

    fun testMissingOrMismatchedPluginKeepsDefaultIcon() {
        val file = addResource("META-INF/example.json").virtualFile
        val provider = PluginConfigIconProvider()
        assertNull(provider.getIcon(file, 0, project))
        addPlugin("@Plugin(\"other\") class Example")
        assertNull(provider.getIcon(file, 0, project))
    }

    fun testUnrelatedAnnotationKeepsDefaultIcon() {
        addPlugin("annotation class Plugin(val id: String)\n@Plugin(\"example\") class Example", imports = false)
        assertNull(icon("META-INF/example.json"))
    }

    fun testOnlyJsonDirectlyUnderResourceMetaInfGetsIcon() {
        addPlugin("@Plugin(\"example\") class Example")
        assertNull(icon("example.json"))
        assertNull(icon("nested/META-INF/example.json"))
        assertNull(icon("META-INF/nested/example.json"))
        assertNull(icon("META-INF/example.txt"))
        val outside = myFixture.addFileToProject("META-INF/example.json", "{}")
        assertNull(PluginConfigIconProvider().getIcon(outside.virtualFile, 0, project))
    }

    fun testIconRefreshesWhenAnnotationChanges() {
        val source = addPlugin("@Plugin(\"example\") class Example")
        val file = addResource("META-INF/example.json").virtualFile
        val provider = PluginConfigIconProvider()
        assertNotNull(provider.getIcon(file, 0, project))
        WriteCommandAction.runWriteCommandAction(project) { source.delete() }
        assertNull(provider.getIcon(file, 0, project))
        addPlugin("@Plugin(\"example\") class Example")
        assertNotNull(provider.getIcon(file, 0, project))
    }

    fun testAnnotationEditsRefreshDisplayedIcons() {
        var refreshes = 0
        project.replaceService(ProjectView::class.java, object : ProjectViewImpl(project) {
            override fun refresh() {
                refreshes++
            }
        }, testRootDisposable)
        val source = addPlugin("class Example")
        val file = addResource("META-INF/example.json").virtualFile
        val other = addResource("META-INF/other.json").virtualFile
        val provider = PluginConfigIconProvider()
        assertNull(provider.getIcon(file, 0, project))
        assertNull(provider.getIcon(other, 0, project))
        val document = PsiDocumentManager.getInstance(project).getDocument(source)!!

        fun edit(declaration: String, matches: Boolean, matchesOther: Boolean) {
            val before = refreshes
            WriteCommandAction.runWriteCommandAction(project) {
                document.setText("import dev.karmakrafts.kcml.api.plugin.Plugin\n$declaration")
                PsiDocumentManager.getInstance(project).commitDocument(document)
            }
            PlatformTestUtil.waitWithEventsDispatching("Metadata icon presentation refresh", { refreshes > before }, 5)
            assertEquals(matches, provider.getIcon(file, 0, project) != null)
            assertEquals(matchesOther, provider.getIcon(other, 0, project) != null)
        }

        edit("@Plugin(\"example\") class Example", matches = true, matchesOther = false)
        edit("@Plugin(\"other\") class Example", matches = false, matchesOther = true)
        edit("class Example", matches = false, matchesOther = false)
        edit("@Plugin(\"example\") class Example", matches = true, matchesOther = false)
        val before = refreshes
        WriteCommandAction.runWriteCommandAction(project) { source.delete() }
        PlatformTestUtil.waitWithEventsDispatching(
            "Deleted plugin icon presentation refresh",
            { refreshes > before },
            5
        )
        assertNull(provider.getIcon(file, 0, project))
    }

    fun testTestPluginDoesNotChangeProductionResourceIcon() {
        val source = myFixture.addFileToProject(
            "testSources/Example.kt",
            "import dev.karmakrafts.kcml.api.plugin.Plugin\n@Plugin(\"example\") class Example"
        )
        PsiTestUtil.addSourceRoot(module, source.virtualFile.parent, true)
        assertNull(icon("META-INF/example.json"))
        val file = myFixture.addFileToProject("testResources/META-INF/example.json", "{}")
        PsiTestUtil.addSourceRoot(module, file.virtualFile.parent.parent, JavaResourceRootType.TEST_RESOURCE)
        assertNotNull(PluginConfigIconProvider().getIcon(file.virtualFile, 0, project))
    }

    private fun addPlugin(text: String, imports: Boolean = true) = myFixture.addFileToProject(
        "Example.kt", (if (imports) "import dev.karmakrafts.kcml.api.plugin.Plugin\n" else "") + text
    )

    private fun addResource(path: String) = myFixture.addFileToProject("resources/$path", "{}").also {
        PsiTestUtil.addSourceRoot(module, myFixture.findFileInTempDir("resources"), JavaResourceRootType.RESOURCE)
    }

    private fun icon(path: String) = PluginConfigIconProvider().getIcon(addResource(path).virtualFile, 0, project)
}