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
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import dev.karmakrafts.kcml.intellij.testutil.completionPresentation
import dev.karmakrafts.kcml.intellij.testutil.configureInjectCompletion
import dev.karmakrafts.kcml.intellij.testutil.withAnnotationLibrary
import dev.karmakrafts.kcml.intellij.testutil.writeCompletionClass
import org.jetbrains.org.objectweb.asm.Opcodes
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarOutputStream

class InjectCompletionTest : LightJavaCodeInsightFixtureTestCase() {
    companion object {
        private val DESCRIPTOR = object : LightProjectDescriptor() {
            override fun getSdk(): Sdk =
                JavaSdk.getInstance().createJdk("Inject completion JDK", System.getProperty("java.home"), false)
        }
    }

    private var library: Path? = null

    override fun getProjectDescriptor(): LightProjectDescriptor = DESCRIPTOR

    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject(
            "dev/karmakrafts/kcml/api/mixin/Annotations.kt", """
                package dev.karmakrafts.kcml.api.mixin
                import kotlin.reflect.KClass
                annotation class Inject(val name: String, val descriptor: String = "", val order: Int = 0)
                annotation class DirectMixin(val target: KClass<*>)
                annotation class IndirectMixin(val target: String)
            """.trimIndent()
        )
        myFixture.addClass(
            """
                package completion;
                public class Target extends Parent {
                    public void unique() {}
                    private int hidden(String[] values, int[][] counts) { return 0; }
                    public void overloaded() {}
                    private String overloaded(int value) { return null; }
                    public void overloadOther() {}
                    public static class Nested {}
                    public Nested nested(Nested value) { return value; }
                    public void nestedOverload() {}
                    public Nested nestedOverload(Nested value) { return value; }
                    public <T extends Number> T generic(T value, boolean flag, long... values) { return value; }
                }
            """.trimIndent()
        )
        myFixture.addClass(
            """
                package completion;
                public class Parent {
                    public void inherited() {}
                    private void inheritedPrivate() {}
                    public void overloaded(long value) {}
                    public void unique() {}
                }
            """.trimIndent()
        )
        myFixture.addClass("package completion; public class Unrelated { public void foreignMethod() {} }")
        assertNotNull(
            "The fixture must resolve java.lang.String", JavaPsiFacade.getInstance(project).findClass(
                "java.lang.String", GlobalSearchScope.allScope(project)
            )
        )
    }

    override fun tearDown() {
        try {
            super.tearDown()
        }
        finally {
            library?.parent?.toFile()?.deleteRecursively()
            library = null
        }
    }

    fun testSmartCompletionShowsAllDeclaredOverloadsWithDescriptors() {
        configure("@Inject(\"over<caret>\")")
        val items = complete()
        assertEquals(
            setOf("overloaded()V", "overloaded(I)Ljava/lang/String;", "overloaded(J)V", "overloadOther()V"),
            signatures(items)
        )
    }

    fun testNameCompletionContainsOnlySimpleMethodNamesAndDescriptors() {
        for (type in listOf(CompletionType.BASIC, CompletionType.SMART)) {
            configure("@Inject(name = \"<caret>\")", "@IndirectMixin(\"completion.Target\")")
            val items = complete(type)
            assertTrue("Expected target methods for $type", items.isNotEmpty())
            for (item in items) {
                val rendered = completionPresentation(item)
                assertTrue("Unexpected completion: ${item.lookupString}", item.`object` is InjectTargetMethod)
                val method = item.`object` as InjectTargetMethod
                assertEquals(method.name, item.lookupString)
                assertEquals(method.name, rendered.itemText)
                assertEquals(method.descriptor, rendered.tailText)
                assertNull(rendered.typeText)
                assertFalse(method.name.contains('.'))
                assertFalse(method.name.contains('/'))
            }
        }
    }

    fun testBasicCompletionIncludesPrivateMethodsAndJvmArrayDescriptors() {
        configure("@Inject(\"<caret>\")")
        val signatures = signatures(complete(CompletionType.BASIC))
        assertTrue(signatures.toString(), "hidden([Ljava/lang/String;[[I)I" in signatures)
        assertTrue(
            signatures.toString(), "nested(Lcompletion/Target\$Nested;)Lcompletion/Target\$Nested;" in signatures
        )
        assertTrue("inherited()V" in signatures)
        assertTrue("inheritedPrivate()V" in signatures)
        assertFalse(signatures.any { it.startsWith("foreignMethod") || it.startsWith("<init>") })
    }

    fun testUniqueFunctionInsertsOnlyName() {
        configure("@Inject(\"un<caret>OldSuffix\")")
        select("unique", "()V")
        assertTrue(myFixture.file.text, "@Inject(\"unique\")" in myFixture.file.text)
    }

    fun testCompiledKotlinTarget() {
        myFixture.withAnnotationLibrary("dev.karmakrafts.kcml.api.mixin.Inject") {
            configure("@Inject(\"na<caret>\")", "@IndirectMixin(\"dev.karmakrafts.kcml.api.mixin.Inject\")")
            assertEquals(setOf("name()Ljava/lang/String;"), signatures(complete()))
        }
    }

    fun testInheritedOverloadInsertsDescriptor() {
        configure("@Inject(\"over<caret>\")")
        select("overloaded", "(J)V")
        assertTrue(myFixture.file.text, "@Inject(name = \"overloaded\", descriptor = \"(J)V\")" in myFixture.file.text)
    }

    fun testOverloadInsertsNameAndDescriptor() {
        configure("@Inject(\"over<caret>\", order = 1)")
        select("overloaded", "(I)Ljava/lang/String;")
        assertTrue(
            myFixture.file.text,
            "@Inject(name = \"overloaded\", descriptor = \"(I)Ljava/lang/String;\", order = 1)" in myFixture.file.text
        )
    }

    fun testOverloadNamesPositionalNameWithExistingNamedDescriptor() {
        configure("@Inject(\"over<caret>\", descriptor = \"old\")")
        select("overloaded", "()V")
        assertTrue(myFixture.file.text, "@Inject(name = \"overloaded\", descriptor = \"()V\")" in myFixture.file.text)
    }

    fun testOtherMatchingNamesDoNotCountAsOverloads() {
        configure("@Inject(\"over<caret>\")")
        select("overloadOther", "()V")
        assertTrue(myFixture.file.text, "@Inject(\"overloadOther\")" in myFixture.file.text)
    }

    fun testOverloadReplacesExistingPositionalDescriptor() {
        configure("@Inject(\"over<caret>\", \"old\", order = 1)")
        select("overloaded", "()V")
        assertTrue(myFixture.file.text, "@Inject(\"overloaded\", \"()V\", order = 1)" in myFixture.file.text)
    }

    fun testNamedAliasedAnnotationReplacesExistingDescriptor() {
        configure(
            "@Injection(descriptor = \"old\", name = \"over<caret>\")",
            imports = "import dev.karmakrafts.kcml.api.mixin.Inject as Injection"
        )
        select("overloaded", "(I)Ljava/lang/String;")
        assertTrue(
            myFixture.file.text,
            "@Injection(descriptor = \"(I)Ljava/lang/String;\", name = \"overloaded\")" in myFixture.file.text
        )
    }

    fun testDescriptorCompletionIsRestrictedToSelectedName() {
        configure("@Inject(name = \"overloaded\", descriptor = \"(<caret>\")")
        assertEquals(
            setOf("overloaded()V", "overloaded(I)Ljava/lang/String;", "overloaded(J)V"), signatures(complete())
        )
    }

    fun testDescriptorPrefixAndInsertion() {
        configure("@Inject(\"hidden\", \"([<caret>OldSuffix\")")
        select("hidden", "([Ljava/lang/String;[[I)I")
        assertTrue(myFixture.file.text, "@Inject(\"hidden\", \"([Ljava/lang/String;[[I)I\")" in myFixture.file.text)
    }

    fun testRawStringNameAndSlashSeparatedIndirectTarget() {
        configure("@Inject(name = \"\"\"over<caret>Old\"\"\")", "@IndirectMixin(\"completion/Target\")")
        select("overloaded", "()V")
        assertTrue(myFixture.file.text, "name = \"\"\"overloaded\"\"\", descriptor = \"()V\"" in myFixture.file.text)
    }

    fun testDirectKotlinStringTargetUsesJvmMethods() {
        configure("@Inject(\"<caret>\")", "@IndirectMixin(\"java.lang.String\")")
        val expected = signatures(complete())
        for (target in listOf("String", "kotlin.String", "Text")) {
            configure(
                "@Inject(\"<caret>\")", "@DirectMixin(target = $target::class)", "import kotlin.String as Text"
            )
            val signatures = signatures(complete())
            assertTrue(
                "Missing String methods for $target: $signatures", "substring(I)Ljava/lang/String;" in signatures
            )
            assertTrue("charAt(I)C" in signatures)
            assertTrue("toString()Ljava/lang/String;" in signatures)
            assertFalse(signatures.any { it.startsWith("foreignMethod") || it.startsWith("<init>") })
            assertEquals("Direct and indirect String targets must expose the same JVM methods", expected, signatures)
        }
    }

    fun testDirectKotlinStringOverloadInsertsDescriptor() {
        configure("@Inject(\"sub<caret>\")", "@DirectMixin(String::class)")
        select("substring", "(II)Ljava/lang/String;")
        assertTrue(
            myFixture.file.text,
            "@Inject(name = \"substring\", descriptor = \"(II)Ljava/lang/String;\")" in myFixture.file.text
        )
    }

    fun testKotlinSourceTarget() {
        myFixture.addFileToProject(
            "completion/KotlinTarget.kt", """
                package completion
                class KotlinTarget {
                    private fun hidden(value: Int): Long = 0L
                    fun hidden(): Unit {}
                }
            """.trimIndent()
        )
        for (mixin in listOf(
            "@IndirectMixin(\"completion.KotlinTarget\")", "@DirectMixin(completion.KotlinTarget::class)"
        )) {
            configure("@Inject(\"hid<caret>\")", mixin)
            assertEquals(setOf("hidden(I)J", "hidden()V"), signatures(complete()))
        }
    }

    fun testCompiledTargetIncludesPrivateSyntheticAndBridgeMethods() {
        addCompiledTarget()
        configure("@Inject(\"<caret>\")", "@IndirectMixin(\"completion.CompiledTarget\")")
        val signatures = signatures(complete())
        assertEquals(
            setOf(
                "hidden()V",
                "access\$hidden(Lcompletion/CompiledTarget;)V",
                "value()Ljava/lang/String;",
                "value()Ljava/lang/Object;"
            ), signatures.filter { it.substringBefore('(') in setOf("hidden", "access\$hidden", "value") }.toSet()
        )
        assertTrue("toString()Ljava/lang/String;" in signatures)
        assertFalse(signatures.any { it.startsWith("<init>") || it.startsWith("<clinit>") })
    }

    fun testInheritedCompiledPrivateSyntheticAndBridgeMethods() {
        addCompiledTarget()
        configure("@Inject(\"acc<caret>\")", "@IndirectMixin(\"completion.CompiledChild\")")
        assertEquals(setOf("access\$hidden(Lcompletion/CompiledTarget;)V"), signatures(complete()))
        configure("@Inject(\"hid<caret>\")", "@IndirectMixin(\"completion.CompiledChild\")")
        assertEquals(setOf("hidden()V"), signatures(complete()))
        configure("@Inject(\"val<caret>\")", "@IndirectMixin(\"completion.CompiledChild\")")
        assertEquals(setOf("value()Ljava/lang/String;", "value()Ljava/lang/Object;"), signatures(complete()))
    }

    fun testSyntheticMethodInsertionEscapesDollar() {
        addCompiledTarget()
        configure("@Inject(\"acc<caret>\")", "@IndirectMixin(\"completion.CompiledTarget\")")
        select("access\$hidden", "(Lcompletion/CompiledTarget;)V")
        assertTrue(myFixture.file.text, "@Inject(\"access\\\$hidden\")" in myFixture.file.text)
    }

    fun testSyntheticBridgeCountsAsAnOverload() {
        addCompiledTarget()
        configure("@Inject(\"val<caret>\")", "@IndirectMixin(\"completion.CompiledTarget\")")
        select("value", "()Ljava/lang/Object;")
        assertTrue(
            myFixture.file.text,
            "@Inject(name = \"value\", descriptor = \"()Ljava/lang/Object;\")" in myFixture.file.text
        )
    }

    fun testOverloadDescriptorEscapesNestedClassNames() {
        configure("@Inject(\"nestedOver<caret>\")")
        select("nestedOverload", "(Lcompletion/Target\$Nested;)Lcompletion/Target\$Nested;")
        assertTrue(
            myFixture.file.text,
            "descriptor = \"(Lcompletion/Target\\\$Nested;)Lcompletion/Target\\\$Nested;\"" in myFixture.file.text
        )
    }

    fun testGenericErasureAndVarargsDescriptor() {
        configure("@Inject(\"<caret>\")")
        assertTrue("generic(Ljava/lang/Number;Z[J)Ljava/lang/Number;" in signatures(complete()))
    }

    fun testUniqueCompletionPreservesOtherArguments() {
        configure("@Inject(descriptor = \"()V\", name = \"un<caret>\", order = 1)")
        select("unique", "()V")
        assertTrue(
            myFixture.file.text, "@Inject(descriptor = \"()V\", name = \"unique\", order = 1)" in myFixture.file.text
        )
    }

    fun testNoCompletionForUnrelatedOrInvalidContexts() {
        for (annotation in listOf(
            "@Other(\"over<caret>\")",
            "@Inject(\"target\", order = \"over<caret>\")",
            "@Inject(\"over<caret>\${value}\")",
            "@Inject(\"missing\", \"<caret>\")"
        )) {
            configure(annotation)
            assertTrue(annotation, signatures(complete()).isEmpty())
        }
        configure("@Inject(\"over<caret>\")", "@IndirectMixin(\"missing.Target\")")
        assertTrue(signatures(complete()).isEmpty())
        configure("@Inject(\"over<caret>\")", "")
        assertTrue(signatures(complete()).isEmpty())
    }

    private fun configure(
        annotation: String, mixin: String = "@DirectMixin(completion.Target::class)", imports: String = ""
    ) {
        myFixture.configureInjectCompletion(annotation, mixin, imports)
    }

    private fun complete(type: CompletionType = CompletionType.SMART): List<LookupElement> =
        myFixture.complete(type).orEmpty().toList()

    private fun signatures(items: List<LookupElement>): Set<String> = items.mapNotNull { item ->
        val rendered = completionPresentation(item)
        val descriptor = rendered.tailText ?: return@mapNotNull null
        if (!descriptor.startsWith("(")) return@mapNotNull null
        assertNotNull(rendered.icon)
        "${rendered.itemText}$descriptor"
    }.toSet()

    private fun select(name: String, descriptor: String) {
        val items = complete()
        if (items.isEmpty() && myFixture.lookup == null) return // A single result may be inserted automatically.
        val selected = items.single { item ->
            val presentation = completionPresentation(item)
            presentation.itemText == name && presentation.tailText == descriptor
        }
        myFixture.lookup.currentItem = selected
        myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
    }

    private fun addCompiledTarget() {
        library = Files.createTempDirectory(Path.of("build"), "kcml-inject-completion-").resolve("target.jar")
        VfsRootAccess.allowRootAccess(testRootDisposable, library!!.parent.toAbsolutePath().toString())
        JarOutputStream(Files.newOutputStream(library!!)).use { output ->
            output.writeCompletionClass("completion/CompiledTarget") { writer ->
                writer.visit(
                    Opcodes.V21, Opcodes.ACC_PUBLIC, "completion/CompiledTarget", null, "java/lang/Object", null
                )
                for ((access, name, descriptor) in listOf(
                    Triple(Opcodes.ACC_PRIVATE, "hidden", "()V"), Triple(
                        Opcodes.ACC_STATIC or Opcodes.ACC_SYNTHETIC, "access\$hidden", "(Lcompletion/CompiledTarget;)V"
                    ), Triple(Opcodes.ACC_PUBLIC, "value", "()Ljava/lang/String;"), Triple(
                        Opcodes.ACC_PUBLIC or Opcodes.ACC_BRIDGE or Opcodes.ACC_SYNTHETIC,
                        "value",
                        "()Ljava/lang/Object;"
                    )
                )) writer.visitMethod(access, name, descriptor, null, null).visitEnd()
            }
            output.writeCompletionClass("completion/CompiledChild") { child ->
                child.visit(
                    Opcodes.V21, Opcodes.ACC_PUBLIC, "completion/CompiledChild", null, "completion/CompiledTarget", null
                )
            }
        }
        WriteCommandAction.runWriteCommandAction(project) {
            ModuleRootModificationUtil.addModuleLibrary(myFixture.module, "jar://${library!!.toAbsolutePath()}!/")
        }
    }
}