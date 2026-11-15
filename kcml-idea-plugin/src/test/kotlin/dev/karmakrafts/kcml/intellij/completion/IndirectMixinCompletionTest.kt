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
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.lang.Language
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.org.objectweb.asm.ClassWriter
import org.jetbrains.org.objectweb.asm.Opcodes
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

class IndirectMixinCompletionTest : LightJavaCodeInsightFixtureTestCase() {
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
        val contributors = CompletionContributor.forLanguage(kotlinLanguage)
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
            com.intellij.psi.JavaPsiFacade.getInstance(project)
                .findClass("completion.Outer.Secret", file.resolveScope)
        )
        val variants = myFixture.complete(CompletionType.SMART).orEmpty()
        val secret = "completion.Outer\$Secret"
        assertTrue(variants.joinToString { it.lookupString }, variants.any { it.lookupString == secret })
        myFixture.lookup.currentItem = variants.first { it.lookupString == secret }
        myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
        assertTrue(file.text, file.text.contains("${quotes}completion.Outer\${'$'}Secret$quotes"))
        assertFalse(file.text.contains("OldSuffix"))
        assertFalse(file.text.contains("import "))
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
            "@dev.karmakrafts.kcml.api.mixin.IndirectMixin(\"completion.Hid<caret>\${value}\") class Example"
        )) {
            myFixture.configureByText("Unrelated.kt", text)
            assertFalse(myFixture.completeBasic().orEmpty().any { it.lookupString == "completion.Hidden" })
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
        val jar = directory.resolve("library.jar")
        JarOutputStream(Files.newOutputStream(jar)).use { output ->
            for (name in listOf("LibraryOuter", "LibraryOuter\$1", "LibraryOuter\$PrivateNested")) {
                val internalName = "completion/library/$name"
                val classWriter = ClassWriter(0)
                classWriter.visit(
                    Opcodes.V21,
                    Opcodes.ACC_PUBLIC or Opcodes.ACC_SUPER or Opcodes.ACC_SYNTHETIC,
                    internalName,
                    null,
                    "java/lang/Object",
                    null
                )
                classWriter.visitEnd()
                output.putNextEntry(JarEntry("$internalName.class"))
                output.write(classWriter.toByteArray())
                output.closeEntry()
            }
        }
        return jar
    }
}