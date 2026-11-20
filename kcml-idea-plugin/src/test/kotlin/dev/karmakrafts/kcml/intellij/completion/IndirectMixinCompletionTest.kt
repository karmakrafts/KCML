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

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.LookupElementRenderer
import com.intellij.icons.AllIcons
import com.intellij.lang.Language
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import dev.karmakrafts.kcml.intellij.testutil.completionPresentation
import dev.karmakrafts.kcml.intellij.testutil.writeCompletionClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.org.objectweb.asm.Opcodes
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

class IndirectMixinCompletionTest : LightJavaCodeInsightFixtureTestCase() {
    private fun render(item: LookupElement): LookupElementPresentation {
        val presentation = completionPresentation(item)
        assertEquals(AllIcons.Nodes.Class, presentation.icon)
        assertNotNull("Declaration icons should be resolved lazily", item.expensiveRenderer)
        @Suppress("UNCHECKED_CAST") val renderer = item.expensiveRenderer as LookupElementRenderer<LookupElement>
        renderer.renderElement(item, presentation)
        return presentation
    }

    fun testSmartCompletionShowsKotlinDeclarationIcons() {
        addIndirectMixinAnnotation()
        val targets = myFixture.addFileToProject(
            "completion/icons/Targets.kt", """
                package completion.icons

                class TargetClass
                interface TargetInterface
                annotation class TargetAnnotation
                enum class TargetEnum { ENTRY }
                object TargetObject
                class TargetOuter { private class TargetNested }
            """.trimIndent()
        ) as KtFile
        myFixture.configureByText(
            "Mixin.kt", """
                import dev.karmakrafts.kcml.api.mixin.IndirectMixin

                @IndirectMixin("completion.icons.<caret>")
                class Example
            """.trimIndent()
        )

        val variants = myFixture.complete(CompletionType.SMART).orEmpty()
        val declarations = targets.declarations.filterIsInstance<KtClassOrObject>()
        val outer = declarations.single { it.name == "TargetOuter" }
        for (declaration in declarations + outer.declarations.filterIsInstance<KtClassOrObject>()) {
            val name = if (declaration.parent == outer.body) "TargetOuter\$${declaration.name}" else declaration.name
            val item = variants.single { it.lookupString == "completion.icons.$name" }
            val presentation = render(item)
            assertNotNull(item.lookupString, presentation.icon)
            assertEquals(item.lookupString, declaration.getIcon(0), presentation.icon)
        }
    }

    fun testSmartCompletionShowsJavaDeclarationIcons() {
        addIndirectMixinAnnotation()
        myFixture.addClass(
            """
                package completion.icons;
                public class TargetOuter {
                    public interface TargetInterface {}
                    public @interface TargetAnnotation {}
                    public enum TargetEnum { ENTRY }
                    private static class TargetNested {}
                }
            """.trimIndent()
        )
        myFixture.configureByText(
            "Mixin.kt", """
                import dev.karmakrafts.kcml.api.mixin.IndirectMixin

                @IndirectMixin("completion.icons.<caret>")
                class Example
            """.trimIndent()
        )

        val variants = myFixture.complete(CompletionType.SMART).orEmpty()
        val outer = JavaPsiFacade.getInstance(project).findClass(
            "completion.icons.TargetOuter", GlobalSearchScope.projectScope(project)
        )!!
        for (declaration in listOf(outer) + outer.innerClasses) {
            val name = if (declaration == outer) declaration.name else "TargetOuter\$${declaration.name}"
            val item = variants.single { it.lookupString == "completion.icons.$name" }
            val presentation = render(item)
            assertNotNull(item.lookupString, presentation.icon)
            assertEquals(item.lookupString, declaration.getIcon(0), presentation.icon)
        }
    }

    fun testCompletesPrivateSourceClassesForAliasedAnnotationAndSlashPrefix() {
        addIndirectMixinAnnotation()
        myFixture.addFileToProject(
            "completion/targets/Targets.kt", """
                package completion.targets

                private class Hidden

                class Container {
                    private class Secret
                }
            """.trimIndent()
        )
        val file = myFixture.configureByText(
            "Mixin.kt", """
                package completion.mixins

                import dev.karmakrafts.kcml.api.mixin.IndirectMixin as Mixin

                @Mixin("completion/targets/<caret>")
                class Example
            """.trimIndent()
        )

        val kotlinLanguage = checkNotNull(Language.findLanguageByID("kotlin"))
        val contributors =
            CompletionContributor.EP.extensionList.filter { it.language == kotlinLanguage.id }.map { it.instance }
        assertTrue(
            contributors.joinToString { it.javaClass.name },
            contributors.any { it is IndirectMixinCompletionContributor })

        val variants = myFixture.completeBasic().orEmpty()
        assertTrue(
            variants.joinToString { it.lookupString },
            variants.any { it.lookupString == "completion.targets.Container\$Secret" })
        assertTrue(variants.any { it.lookupString == "completion.targets.Hidden" })

        myFixture.lookup.currentItem = variants.first { it.lookupString == "completion.targets.Container\$Secret" }
        myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
        assertTrue(file.text, file.text.contains("\"completion.targets.Container\\\$Secret\""))
    }

    fun testCompletesBasicCompletionForPartialTypeName() {
        addIndirectMixinAnnotation()
        myFixture.addFileToProject(
            "completion/targets/Targets.kt", "package completion.targets; class Hidden; class HiddenOther"
        )
        myFixture.configureByText(
            "Mixin.kt", """
                import dev.karmakrafts.kcml.api.mixin.IndirectMixin

                @IndirectMixin("Hid<caret>")
                class Example
            """.trimIndent()
        )

        val variants = myFixture.completeBasic().orEmpty()
        assertTrue(
            variants.joinToString { it.lookupString },
            variants.any { it.lookupString == "completion.targets.Hidden" })
    }

    fun testSmartCompletionForPartialNestedTypeNameReplacesWholeContent() {
        addIndirectMixinAnnotation()
        myFixture.addFileToProject(
            "completion/Outer.java", """
                package completion;
                public class Outer {
                    private static class Secret {}
                    private static class Second {}
                }
            """.trimIndent()
        )
        val file = myFixture.configureByText(
            "Mixin.kt", """
                import dev.karmakrafts.kcml.api.mixin.IndirectMixin

                @IndirectMixin("Secret<caret>OldSuffix")
                class Example
            """.trimIndent()
        )

        val variants = myFixture.complete(CompletionType.SMART).orEmpty()
        val secret = "completion.Outer\$Secret"
        assertTrue(variants.joinToString { it.lookupString }, variants.any { it.lookupString == secret })
        myFixture.lookup.currentItem = variants.first { it.lookupString == secret }
        myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
        assertTrue(file.text, file.text.contains("@IndirectMixin(\"completion.Outer\\\$Secret\")"))
        assertFalse(file.text.contains("OldSuffix"))
    }

    fun testCompletesProvidedLibraryUsingCompiledAnnotation() {
        assertCompletesProvidedLibrary(false)
    }

    fun testCompletesProvidedLibraryUsingCompiledKotlinAnnotation() {
        assertCompletesProvidedLibrary(true)
    }

    private fun assertCompletesProvidedLibrary(kotlinAnnotation: Boolean) {
        val library = createProvidedLibrary(kotlinAnnotation)
        try {
            WriteCommandAction.runWriteCommandAction(project) {
                ModuleRootModificationUtil.updateModel(myFixture.module) { rootModel ->
                    val moduleLibrary = rootModel.moduleLibraryTable.createLibrary("provided-completion-library")
                    val libraryModel = moduleLibrary.modifiableModel
                    libraryModel.addRoot("jar://${library.toAbsolutePath()}!/", OrderRootType.CLASSES)
                    libraryModel.commit()
                    rootModel.addLibraryEntry(moduleLibrary).scope = DependencyScope.PROVIDED
                }
            }
            val file = myFixture.configureByText(
                "Mixin.kt", """
                    @dev.karmakrafts.kcml.api.mixin.IndirectMixin("<caret>")
                    class Example
                """.trimIndent()
            )
            assertNotNull(
                JavaPsiFacade.getInstance(project).findClass(
                    "dev.karmakrafts.kcml.api.mixin.IndirectMixin", file.resolveScope
                )
            )

            val variants = myFixture.completeBasic().orEmpty()
            assertTrue(
                variants.joinToString { it.lookupString },
                variants.any { it.lookupString == "completion.provided.ProvidedTarget" })
            assertTrue(variants.any { it.lookupString == "completion.provided.ProvidedOuter\$1" })
            assertTrue(variants.any { it.lookupString == "completion.provided.ProvidedOuter\$PrivateNested" })
        }
        finally {
            library.parent.toFile().deleteRecursively()
        }
    }

    fun testCompletesAnonymousLibraryClassFile() {
        addIndirectMixinAnnotation()
        val library = createAnonymousClassLibrary()
        try {
            WriteCommandAction.runWriteCommandAction(project) {
                ModuleRootModificationUtil.addModuleLibrary(
                    myFixture.module, "jar://${library.toAbsolutePath()}!/"
                )
            }
            myFixture.configureByText(
                "Mixin.kt", """
                    import dev.karmakrafts.kcml.api.mixin.IndirectMixin

                    @IndirectMixin("completion/library/<caret>")
                    class Example
                """.trimIndent()
            )

            val variants = myFixture.completeBasic().orEmpty()
            assertTrue(variants.any { it.lookupString == "completion.library.LibraryOuter\$1" })
            assertTrue(variants.any { it.lookupString == "completion.library.LibraryOuter\$PrivateNested" })
            for (item in variants.filter { it.lookupString.startsWith("completion.library.") }) {
                val presentation = completionPresentation(item)
                assertNotNull(item.lookupString, presentation.icon)
            }
        }
        finally {
            library.parent.toFile().deleteRecursively()
        }
    }

    fun testDoesNotCompleteUnrelatedStringsOrPriorityArgument() {
        addIndirectMixinAnnotation()
        myFixture.addFileToProject(
            "completion/targets/Targets.kt", """
                package completion.targets

                class Hidden
            """.trimIndent()
        )
        myFixture.configureByText(
            "Mixin.kt", """
                import dev.karmakrafts.kcml.api.mixin.IndirectMixin

                fun unrelated() = "completion.targets.Hid<caret>"
            """.trimIndent()
        )
        assertFalse(myFixture.completeBasic().orEmpty().any { it.lookupString == "completion.targets.Hidden" })

        myFixture.configureByText(
            "Priority.kt", """
                import dev.karmakrafts.kcml.api.mixin.IndirectMixin

                @IndirectMixin(target = "completion.targets.Hidden", priority = <caret>0)
                class Example
            """.trimIndent()
        )
        assertFalse(myFixture.completeBasic().orEmpty().any { it.lookupString == "completion.targets.Hidden" })
    }

    fun testSmartCompletionInRawNamedTargetReplacesWholeContent() {
        addIndirectMixinAnnotation()
        myFixture.addFileToProject(
            "completion/Outer.java", """
                package completion;
                public class Outer {
                    private static class Secret {}
                    private static class Second {}
                }
            """.trimIndent()
        )
        val quotes = "\"\"\""
        val file = myFixture.configureByText(
            "Mixin.kt", """
                @dev.karmakrafts.kcml.api.mixin.IndirectMixin(
                    priority = 1, target = ${quotes}completion.Out<caret>OldSuffix$quotes
                )
                class Example
            """.trimIndent()
        )
        assertNotNull(
            JavaPsiFacade.getInstance(project).findClass("completion.Outer.Secret", file.resolveScope)
        )
        val variants = myFixture.complete(CompletionType.SMART).orEmpty()
        val secret = "completion.Outer\$Secret"
        assertTrue(variants.joinToString { it.lookupString }, variants.any { it.lookupString == secret })
        myFixture.lookup.currentItem = variants.first { it.lookupString == secret }
        myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
        assertTrue(file.text, file.text.contains("${quotes}completion.Outer${'$'}Secret$quotes"))
        assertFalse(file.text.contains("OldSuffix"))
        assertFalse(file.text.contains("import "))
    }

    fun testRawStringCompletionAfterLiteralDollar() {
        assertRawNestedCompletion("", "Outer${'$'}{'${'$'}'}Se", "$")
    }

    fun testBasicCompletionInRawString() {
        assertRawNestedCompletion("", "Out", "$", CompletionType.BASIC)
    }

    fun testRawStringCompletionAfterUnescapedDollar() {
        assertRawNestedCompletion("", "Outer${'$'}Se", "$")
    }

    fun testBasicCompletionInRawStringAfterUnescapedDollar() {
        assertRawNestedCompletion("", "Outer${'$'}Se", "$", CompletionType.BASIC)
    }

    fun testRawTargetCompletionDoesNotOfferEscapedFilePaths() {
        addIndirectMixinAnnotation()
        myFixture.addFileToProject(
            "completion/Outer${'$'}Secret.kt", "package completion; class Outer { class Secret; class Second }"
        )
        myFixture.configureByText(
            "Mixin.kt",
            "import dev.karmakrafts.kcml.api.mixin.IndirectMixin\n" + "@IndirectMixin(\"\"\"completion/Out<caret>\"\"\") class Example"
        )
        val variants = myFixture.completeBasic().orEmpty()
        val expected = setOf("completion.Outer", "completion.Outer${'$'}Secret", "completion.Outer${'$'}Second")
        myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
        assertFalse(myFixture.file.text, "\\${'$'}" in myFixture.file.text)
        assertFalse(myFixture.file.text, ".kt" in myFixture.file.text)
        assertEquals(variants.joinToString { it.lookupString }, expected, variants.map { it.lookupString }.toSet())
    }

    fun testMultiDollarRawStringCompletion() {
        assertRawNestedCompletion("$$", "Outer${'$'}Se", "$")
    }

    fun testTripleDollarRawStringCompletion() {
        assertRawNestedCompletion("$$$", "Outer${'$'}Se", "$")
    }

    fun testBasicCompletionInMultiDollarRawString() {
        assertRawNestedCompletion("$$", "Out", "$", CompletionType.BASIC)
    }

    fun testEditingExistingMultiDollarStringDoesNotEscapeDollars() {
        assertEditingNestedTarget("$$", "\"", CompletionType.BASIC)
    }

    fun testSmartCompletionInExistingMultiDollarStringDoesNotEscapeDollars() {
        assertEditingNestedTarget("$$$", "\"", CompletionType.SMART)
    }

    fun testEditingExistingRawStringDoesNotEscapeDollars() {
        assertEditingNestedTarget("", "\"\"\"", CompletionType.BASIC)
    }

    fun testEditingExistingMultiDollarRawStringDoesNotEscapeDollars() {
        assertEditingNestedTarget("$$", "\"\"\"", CompletionType.SMART)
    }

    fun testEditingExistingRegularStringStillEscapesDollars() {
        assertEditingNestedTarget("", "\"", CompletionType.BASIC)
    }

    private fun assertEditingNestedTarget(dollars: String, quotes: String, completionType: CompletionType) {
        addIndirectMixinAnnotation()
        myFixture.addFileToProject(
            "completion/Outer.kt", "package completion; class Outer { class Secret; class Second }"
        )
        val escapedDollar = if (dollars.isEmpty() && quotes.length == 1) "\\$" else "$"
        val source =
            "import dev.karmakrafts.kcml.api.mixin.IndirectMixin\n" + "@IndirectMixin(target = $dollars${quotes}completion.Out<caret>er${escapedDollar}Second$quotes) class Example"
        myFixture.configureByText("Mixin.kt", source)
        myFixture.type("e")
        val variants = myFixture.complete(completionType).orEmpty()
        val name = "completion.Outer${'$'}Secret"
        val item = variants.single { it.lookupString == name }
        myFixture.lookup.currentItem = item
        myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
        if (escapedDollar == "$") assertFalse(myFixture.file.text, "\\$" in myFixture.file.text)
        myFixture.checkResult(
            "import dev.karmakrafts.kcml.api.mixin.IndirectMixin\n" + "@IndirectMixin(target = $dollars${quotes}completion.Outer${escapedDollar}Secret<caret>$quotes) class Example"
        )
    }

    private fun assertRawNestedCompletion(
        dollars: String, prefix: String, escapedDollar: String, completionType: CompletionType = CompletionType.SMART
    ) {
        addIndirectMixinAnnotation()
        myFixture.addFileToProject(
            "completion/Outer.kt", "package completion; class Outer { class Secret; object Second }"
        )
        val quotes = "\"\"\""
        val file = myFixture.configureByText(
            "Mixin.kt", """
                import dev.karmakrafts.kcml.api.mixin.IndirectMixin
                @IndirectMixin(target = $dollars${quotes}completion.$prefix<caret>OldSuffix$quotes)
                class Example
            """.trimIndent()
        )
        assertTrue(file.text, IndirectMixinTargetString.isTargetStringAt(file, myFixture.caretOffset))
        assertTrue(file.text, IndirectMixinCompletionTypedHandler().shouldShowPopup('e', file, myFixture.caretOffset))
        val variants = myFixture.complete(completionType).orEmpty()
        val secret = "completion.Outer\$Secret"
        assertTrue(variants.joinToString { it.lookupString }, variants.any { it.lookupString == secret })
        myFixture.lookup.currentItem = variants.first { it.lookupString == secret }
        myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
        assertTrue(
            file.text, file.text.contains("target = $dollars${quotes}completion.Outer${escapedDollar}Secret$quotes)")
        )
        assertFalse(file.text.contains("OldSuffix"))
        val end = file.text.indexOf("Secret") + "Secret".length
        assertEquals(end, myFixture.caretOffset)
        assertEquals(secret, IndirectMixinTargetString.find(file.findElementAt(end)!!, end)?.prefix)
    }

    fun testEmptyStringOffersAllClasses() {
        addIndirectMixinAnnotation()
        myFixture.addFileToProject("completion/First.kt", "package completion; private class First; class Second")
        myFixture.configureByText(
            "Mixin.kt", """
                import dev.karmakrafts.kcml.api.mixin.IndirectMixin
                @IndirectMixin("<caret>") class Example
            """.trimIndent()
        )
        val variants = myFixture.completeBasic().orEmpty().map { it.lookupString }
        assertTrue(variants.contains("completion.First"))
        assertTrue(variants.contains("completion.Second"))
    }

    fun testRejectsUnrelatedAnnotationAndInterpolatedTarget() {
        addIndirectMixinAnnotation()
        myFixture.addFileToProject("completion/Hidden.kt", "package completion; class Hidden; class HiddenOther")
        for (text in listOf(
            "annotation class IndirectMixin(val target: String); @IndirectMixin(\"completion.Hid<caret>\") class Example",
            "@dev.karmakrafts.kcml.api.mixin.IndirectMixin(\"completion.Hid<caret>\${value}\") class Example",
            "@dev.karmakrafts.kcml.api.mixin.IndirectMixin(\"\"\"completion.Hid<caret>\${value}\"\"\") class Example",
            "@dev.karmakrafts.kcml.api.mixin.IndirectMixin(\$\$\"\"\"completion.Hid<caret>\$\${value}\"\"\") class Example"
        )) {
            myFixture.configureByText("Unrelated.kt", text)
            assertFalse(myFixture.completeBasic().orEmpty().any { it.lookupString == "completion.Hidden" })
        }
    }

    fun testAutomaticallyInvokesPopupWhileEditingTargetStringsOnly() {
        addIndirectMixinAnnotation()
        val handler = IndirectMixinCompletionTypedHandler()
        for ((fileName, text) in listOf(
            "Target.kt" to "import dev.karmakrafts.kcml.api.mixin.IndirectMixin\n@IndirectMixin(\"com<caret>pletion.Target\") class Example",
            "RawTarget.kt" to "import dev.karmakrafts.kcml.api.mixin.IndirectMixin\n@IndirectMixin(target = \"\"\"com<caret>pletion.Target\"\"\") class Example"
        )) {
            val file = myFixture.configureByText(fileName, text)
            assertTrue(file.text, IndirectMixinTargetString.isTargetStringAt(file, myFixture.caretOffset))
            assertTrue(file.text, handler.shouldShowPopup('p', file, myFixture.caretOffset))
            assertEquals(
                TypedHandlerDelegate.Result.CONTINUE, handler.charTyped('p', project, myFixture.editor, file)
            )
        }

        for ((fileName, text) in listOf(
            "Unrelated.kt" to "fun unrelated() = \"com<caret>pletion.Target\"",
            "Interpolated.kt" to "import dev.karmakrafts.kcml.api.mixin.IndirectMixin\n@IndirectMixin(\"com<caret>pletion.\$Target\") class Example"
        )) {
            val file = myFixture.configureByText(fileName, text)
            assertFalse(file.text, IndirectMixinTargetString.isTargetStringAt(file, myFixture.caretOffset))
            assertFalse(file.text, handler.shouldShowPopup('p', file, myFixture.caretOffset))
            assertEquals(
                TypedHandlerDelegate.Result.CONTINUE, handler.charTyped('"', project, myFixture.editor, file)
            )
        }
    }

    private fun addIndirectMixinAnnotation() {
        myFixture.addFileToProject(
            "dev/karmakrafts/kcml/api/mixin/IndirectMixin.kt", """
                package dev.karmakrafts.kcml.api.mixin

                @Target(AnnotationTarget.CLASS)
                annotation class IndirectMixin(val target: String, val priority: Int = 0)
            """.trimIndent()
        )
    }

    private fun createAnonymousClassLibrary(): Path {
        val directory = Files.createTempDirectory(Path.of("build"), "kcml-completion-library-")
        VfsRootAccess.allowRootAccess(testRootDisposable, directory.toAbsolutePath().toString())
        val jar = directory.resolve("library.jar")
        JarOutputStream(Files.newOutputStream(jar)).use { output ->
            for (name in listOf("LibraryOuter", "LibraryOuter\$1", "LibraryOuter\$PrivateNested")) {
                val internalName = "completion/library/$name"
                output.writeCompletionClass(internalName) { classWriter ->
                    classWriter.visit(
                        Opcodes.V21,
                        Opcodes.ACC_PUBLIC or Opcodes.ACC_SUPER or Opcodes.ACC_SYNTHETIC,
                        internalName,
                        null,
                        "java/lang/Object",
                        null
                    )
                }
            }
        }
        return jar
    }

    private fun createProvidedLibrary(kotlinAnnotation: Boolean = false): Path {
        val directory = Files.createTempDirectory(Path.of("build"), "kcml-provided-completion-library-")
        VfsRootAccess.allowRootAccess(testRootDisposable, directory.toAbsolutePath().toString())
        val jar = directory.resolve("library.jar")
        JarOutputStream(Files.newOutputStream(jar)).use { output ->
            if (kotlinAnnotation) {
                val classFile = "dev/karmakrafts/kcml/api/mixin/IndirectMixin.class"
                output.putNextEntry(JarEntry(classFile))
                javaClass.classLoader.getResourceAsStream(classFile)!!.use { it.copyTo(output) }
                output.closeEntry()
            }
            else output.writeCompletionClass("dev/karmakrafts/kcml/api/mixin/IndirectMixin") { classWriter ->
                classWriter.visit(
                    Opcodes.V21,
                    Opcodes.ACC_PUBLIC or Opcodes.ACC_INTERFACE or Opcodes.ACC_ABSTRACT or Opcodes.ACC_ANNOTATION,
                    "dev/karmakrafts/kcml/api/mixin/IndirectMixin",
                    null,
                    "java/lang/Object",
                    arrayOf("java/lang/annotation/Annotation")
                )
                classWriter.visitMethod(
                    Opcodes.ACC_PUBLIC or Opcodes.ACC_ABSTRACT, "target", "()Ljava/lang/String;", null, null
                ).visitEnd()
                val priority =
                    classWriter.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_ABSTRACT, "priority", "()I", null, null)
                val defaultValue = priority.visitAnnotationDefault()
                defaultValue.visit(null, 0)
                defaultValue.visitEnd()
                priority.visitEnd()
            }
            output.writeCompletionClass("completion/provided/ProvidedTarget") { classWriter ->
                classWriter.visit(
                    Opcodes.V21,
                    Opcodes.ACC_PUBLIC or Opcodes.ACC_SUPER,
                    "completion/provided/ProvidedTarget",
                    null,
                    "java/lang/Object",
                    null
                )
            }
            output.writeCompletionClass("completion/provided/ProvidedOuter") { classWriter ->
                classWriter.visit(
                    Opcodes.V21,
                    Opcodes.ACC_PUBLIC or Opcodes.ACC_SUPER,
                    "completion/provided/ProvidedOuter",
                    null,
                    "java/lang/Object",
                    null
                )
                classWriter.visitInnerClass(
                    "completion/provided/ProvidedOuter\$PrivateNested",
                    "completion/provided/ProvidedOuter",
                    "PrivateNested",
                    Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC
                )
            }
            output.writeCompletionClass("completion/provided/ProvidedOuter\$1") { classWriter ->
                classWriter.visit(
                    Opcodes.V21,
                    Opcodes.ACC_SUPER or Opcodes.ACC_SYNTHETIC,
                    "completion/provided/ProvidedOuter\$1",
                    null,
                    "java/lang/Object",
                    null
                )
            }
            output.writeCompletionClass("completion/provided/ProvidedOuter\$PrivateNested") { classWriter ->
                classWriter.visit(
                    Opcodes.V21,
                    Opcodes.ACC_SUPER,
                    "completion/provided/ProvidedOuter\$PrivateNested",
                    null,
                    "java/lang/Object",
                    null
                )
                classWriter.visitInnerClass(
                    "completion/provided/ProvidedOuter\$PrivateNested",
                    "completion/provided/ProvidedOuter",
                    "PrivateNested",
                    Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC
                )
            }
        }
        return jar
    }
}