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

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.util.ThreeState
import dev.karmakrafts.kcml.intellij.testutil.AnnotationFixtureTestCase
import org.jetbrains.jps.model.java.JavaResourceRootType
import java.io.File
import java.nio.file.Path

class PluginIdCompletionTest : AnnotationFixtureTestCase() {
    private var autoCompleteSmart = false

    override fun setUp() {
        super.setUp()
        val settings = CodeInsightSettings.getInstance()
        autoCompleteSmart = settings.AUTOCOMPLETE_ON_SMART_TYPE_COMPLETION
        settings.AUTOCOMPLETE_ON_SMART_TYPE_COMPLETION = false
        addAnnotationSource(
            "dev/karmakrafts/kcml/api/plugin/Plugin.kt", """
                package dev.karmakrafts.kcml.api.plugin
                annotation class Plugin(val id: String)
            """.trimIndent()
        )
    }

    override fun tearDown() {
        try {
            CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_SMART_TYPE_COMPLETION = autoCompleteSmart
        }
        finally {
            super.tearDown()
        }
    }

    fun testSmartAnnotationCompletionUsesJsonId() {
        addMetadata("different-filename", "example")
        configureAnnotation("@Plugin(\"ex<caret>\") class Example")
        assertEquals(setOf("example"), variants())
    }

    fun testSandboxJsonSchemaResourceIsAccessible() {
        // Swift's schema provider is macOS-only, but its sandbox resource must be accessible on every host.
        val plugin = PluginManagerCore.getPlugin(PluginId.getId("com.jetbrains.kmm"))!!
        val jar = plugin.pluginPath.resolve("lib/modules/intellij.swift.core.jar")
        assertTrue(
            "Installed plugin roots must be permitted independently of individual classpath entries",
            System.getProperty("vfs.additional-allowed-roots").split(File.pathSeparator).any {
                plugin.pluginPath.toAbsolutePath().normalize().startsWith(Path.of(it).toAbsolutePath().normalize())
            }
        )
        val schema = VirtualFileManager.getInstance().findFileByUrl(
            "jar://${jar.toString().replace('\\', '/')}!/schemas/swift-format.schema.json"
        )
        assertNotNull("JSON schema providers must be able to read installed sandbox resources", schema)
        assertTrue(schema!!.contentsToByteArray().isNotEmpty())
    }

    fun testMetadataLookupOnEdtIsDeferredAndCached() {
        addMetadata("example", "example")
        configureAnnotation("@Plugin(\"<caret>\") class Example")
        assertTrue(ApplicationManager.getApplication().isDispatchThread)
        assertEquals(emptySet<String>(), PluginIds.fromMetadata(myFixture.file))
        waitForIds(setOf("example")) { PluginIds.fromMetadata(myFixture.file) }
        assertSame(PluginIds.fromMetadata(myFixture.file), PluginIds.fromMetadata(myFixture.file))
    }

    fun testAnnotationLookupOnEdtIsDeferredAndCached() {
        addPlugin("Example", "example")
        configureJson("""{"id":"<caret>"}""")
        assertTrue(ApplicationManager.getApplication().isDispatchThread)
        assertEquals(emptySet<String>(), PluginIds.fromAnnotations(myFixture.file))
        waitForIds(setOf("example")) { PluginIds.fromAnnotations(myFixture.file) }
        assertSame(PluginIds.fromAnnotations(myFixture.file), PluginIds.fromAnnotations(myFixture.file))
    }

    fun testMetadataCacheInvalidatesAfterEditAndDeletion() {
        val metadata = addMetadata("example", "example")
        configureAnnotation("@Plugin(\"<caret>\") class Example")
        waitForIds(setOf("example")) { PluginIds.fromMetadata(myFixture.file) }
        WriteCommandAction.runWriteCommandAction(project) {
            val documents = PsiDocumentManager.getInstance(project)
            val document = documents.getDocument(metadata)!!
            document.setText("""{"id":"updated"}""")
            documents.commitDocument(document)
        }
        waitForIds(setOf("updated")) { PluginIds.fromMetadata(myFixture.file) }
        WriteCommandAction.runWriteCommandAction(project) { metadata.delete() }
        assertEquals(emptySet<String>(), PluginIds.fromMetadata(myFixture.file))
    }

    fun testAnnotationCacheInvalidatesAfterEdit() {
        val source = addPlugin("Example", "example")
        configureJson("""{"id":"<caret>"}""")
        waitForIds(setOf("example")) { PluginIds.fromAnnotations(myFixture.file) }
        WriteCommandAction.runWriteCommandAction(project) {
            val documents = PsiDocumentManager.getInstance(project)
            val document = documents.getDocument(source)!!
            document.setText(source.text.replace("\"example\"", "\"updated\""))
            documents.commitDocument(document)
        }
        waitForIds(setOf("updated")) { PluginIds.fromAnnotations(myFixture.file) }
    }

    fun testMetadataCacheInvalidatesAfterResourceRootChange() {
        val metadata = addMetadata("example", "example")
        configureAnnotation("@Plugin(\"<caret>\") class Example")
        waitForIds(setOf("example")) { PluginIds.fromMetadata(myFixture.file) }
        PsiTestUtil.removeSourceRoot(module, metadata.virtualFile.parent.parent)
        assertEquals(emptySet<String>(), PluginIds.fromMetadata(myFixture.file))
        PsiTestUtil.addSourceRoot(module, metadata.virtualFile.parent.parent, JavaResourceRootType.RESOURCE)
        waitForIds(setOf("example")) { PluginIds.fromMetadata(myFixture.file) }
    }

    fun testBasicCompletionSupportsAliasedNamedArgumentAndReplacesSuffix() {
        addMetadata("example", "example")
        configureAnnotation(
            "import dev.karmakrafts.kcml.api.plugin.Plugin as CompilerPlugin\n" + "@CompilerPlugin(id = \"ex<caret>isting\") object Example"
        )
        choose("example", CompletionType.BASIC)
        assertTrue(myFixture.file.text.contains("id = \"example\""))
    }

    fun testRawStringAnnotationCompletion() {
        addMetadata("example", "example")
        configureAnnotation("@Plugin(\"\"\"ex<caret>\"\"\") class Example")
        choose("example")
        assertTrue(myFixture.file.text.contains("@Plugin(\"\"\"example\"\"\")"))
    }

    fun testMetadataIdsAreDistinctAndPrefixFiltered() {
        addMetadata("first", "example")
        addMetadata("second", "example")
        addMetadata("third", "other")
        configureAnnotation("@Plugin(\"ex<caret>\") class Example")
        assertEquals(setOf("example"), variants())
    }

    fun testIgnoresUnrelatedJsonAndNestedIds() {
        addResource("resources", "META-INF/nested.json", """{"nested":{"id":"nested"}}""")
        addResource("resources", "outside.json", """{"id":"outside"}""")
        addResource("resources", "nested/META-INF/deep.json", """{"id":"deep"}""")
        configureAnnotation("@Plugin(\"<caret>\") class Example")
        assertEquals(emptySet<String>(), variants())
    }

    fun testProductionAnnotationDoesNotSeeTestMetadata() {
        addMetadata("production", "production")
        addMetadata("test", "test", true)
        configureAnnotation("@Plugin(\"<caret>\") class Example")
        assertEquals(setOf("production"), variants())
    }

    fun testTestAnnotationSeesProductionAndTestMetadata() {
        addMetadata("production", "production")
        addMetadata("test", "test", true)
        val source = myFixture.addFileToProject(
            "tests/Example.kt", "import dev.karmakrafts.kcml.api.plugin.Plugin\n@Plugin(\"\") class Example"
        )
        PsiTestUtil.addSourceRoot(module, source.virtualFile.parent, true)
        myFixture.configureFromExistingVirtualFile(source.virtualFile)
        myFixture.editor.caretModel.moveToOffset(source.text.indexOf("\"\"") + 1)
        assertEquals(setOf("production", "test"), variants())
    }

    fun testUnrelatedAnnotationHasNoPluginIds() {
        addMetadata("example", "example")
        configureAnnotation("annotation class Other(val id: String)\n@Other(\"<caret>\") class Example")
        assertFalse("example" in variants())
    }

    fun testInterpolatedAnnotationHasNoPluginIds() {
        addMetadata("example", "example")
        configureAnnotation("@Plugin(\"${'$'}variable<caret>\") class Example")
        assertFalse("example" in variants())
    }

    fun testSmartJsonCompletionUsesAnnotationIds() {
        addPlugin("Example", "example")
        addPlugin("Other", "other")
        configureJson("""{"id":"ex<caret>"}""")
        assertEquals(setOf("example"), variants())
    }

    fun testJsonCompletionReplacesSuffixAndPreservesQuotes() {
        addPlugin("Example", "example")
        configureJson("""{"id":"ex<caret>isting","name":"Name"}""")
        choose("example", CompletionType.BASIC)
        assertEquals("""{"id":"example","name":"Name"}""", myFixture.file.text)
    }

    fun testJsonCompletionSupportsAliasedAnnotationAndDeduplicatesIds() {
        myFixture.addFileToProject(
            "Example.kt", """
                import dev.karmakrafts.kcml.api.plugin.Plugin as CompilerPlugin
                @CompilerPlugin(id = "example") object Example
                @CompilerPlugin("example") class Duplicate
            """.trimIndent()
        )
        configureJson("""{"id":"<caret>"}""")
        assertEquals(setOf("example"), variants())
    }

    fun testJsonOtherPropertiesAndNestedIdsAreNotCompleted() {
        addPlugin("Example", "example")
        for (text in listOf("""{"name":"<caret>"}""", """{"nested":{"id":"<caret>"}}""")) {
            configureJson(text)
            assertFalse("example" in variants())
        }
    }

    fun testJsonOutsideMetadataDirectoryIsNotCompleted() {
        addPlugin("Example", "example")
        myFixture.configureByText("unrelated.json", """{"id":"<caret>"}""")
        assertFalse("example" in variants())
    }

    fun testProductionJsonDoesNotSeeTestPlugins() {
        addPlugin("Production", "production")
        val source = addPlugin("TestPlugin", "test", "tests/")
        PsiTestUtil.addSourceRoot(module, source.virtualFile.parent, true)
        configureJson("""{"id":"<caret>"}""")
        assertEquals(setOf("production"), variants())
    }

    fun testTestJsonSeesProductionAndTestPlugins() {
        addPlugin("Production", "production")
        val source = addPlugin("TestPlugin", "test", "tests/")
        PsiTestUtil.addSourceRoot(module, source.virtualFile.parent, true)
        configureJson("""{"id":"<caret>"}""", true)
        assertEquals(setOf("production", "test"), variants())
    }

    fun testAutoPopupEnabledInAnnotationAndJsonId() {
        configureAnnotation("@Plugin(\"<caret>\") class Example")
        assertPopupEnabled()
        configureJson("""{"id":"<caret>"}""")
        assertPopupEnabled()
    }

    fun testAutoPopupDoesNotApplyToUnrelatedJsonProperty() {
        configureJson("""{"name":"<caret>"}""")
        val offset = myFixture.caretOffset
        assertFalse(PluginIdCompletionTypedHandler().shouldShowPopup('a', myFixture.file, offset))
        assertEquals(
            ThreeState.UNSURE, PluginIdCompletionConfidence().shouldSkipAutopopup(
                myFixture.editor, myFixture.file.findElementAt(offset)!!, myFixture.file, offset
            )
        )
    }

    private fun waitForIds(expected: Set<String>, lookup: () -> Set<String>) {
        PlatformTestUtil.waitWithEventsDispatching("Plugin ID background indexing", { lookup() == expected }, 10)
        assertEquals(expected, lookup())
    }

    private fun assertPopupEnabled() {
        val offset = myFixture.caretOffset
        val handler = PluginIdCompletionTypedHandler()
        for (char in "a1_.-:") assertTrue(handler.shouldShowPopup(char, myFixture.file, offset))
        assertFalse(handler.shouldShowPopup(' ', myFixture.file, offset))
        assertEquals(
            ThreeState.NO, PluginIdCompletionConfidence().shouldSkipAutopopup(
                myFixture.editor, myFixture.file.findElementAt(offset)!!, myFixture.file, offset
            )
        )
    }

    private fun addPlugin(name: String, id: String, directory: String = "") = myFixture.addFileToProject(
        "$directory$name.kt", "import dev.karmakrafts.kcml.api.plugin.Plugin\n@Plugin(\"$id\") class $name"
    )

    private fun addMetadata(name: String, id: String, test: Boolean = false) = addResource(
        if (test) "testResources" else "resources", "META-INF/$name.json", """{"id":"$id"}""", test
    )

    private fun addResource(root: String, path: String, text: String, test: Boolean = false): PsiFile {
        val file = myFixture.addFileToProject("$root/$path", text)
        PsiTestUtil.addSourceRoot(
            module,
            myFixture.findFileInTempDir(root),
            if (test) JavaResourceRootType.TEST_RESOURCE else JavaResourceRootType.RESOURCE
        )
        return file
    }

    private fun configureAnnotation(text: String) {
        myFixture.configureByText("Example.kt", "import dev.karmakrafts.kcml.api.plugin.Plugin\n$text")
    }

    private fun configureJson(text: String, test: Boolean = false) {
        val root = if (test) "testResources" else "resources"
        val file =
            myFixture.findFileInTempDir("$root/META-INF/config.json") ?: addMetadata("config", "", test).virtualFile
        myFixture.saveText(file, text.replace("<caret>", ""))
        myFixture.configureFromExistingVirtualFile(file)
        myFixture.editor.caretModel.moveToOffset(text.indexOf("<caret>"))
    }

    private fun variants(type: CompletionType = CompletionType.SMART): Set<String> =
        myFixture.complete(type).orEmpty().map { it.lookupString }.toSet()

    private fun choose(id: String, type: CompletionType = CompletionType.SMART) {
        val items = myFixture.complete(type).orEmpty()
        if (items.isEmpty()) {
            assertTrue(myFixture.file.text.contains(id))
            return
        }
        myFixture.lookup.currentItem = items.single { it.lookupString == id }
        myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
    }
}