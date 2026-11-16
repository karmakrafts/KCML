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

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.CompletionAutoPopupTester
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.testFramework.runInEdtAndWait
import dev.karmakrafts.kcml.intellij.testutil.AnnotationLibrary

class InjectCompletionAutoPopupTest : LightJavaCodeInsightFixtureTestCase() {
    companion object {
        private val DESCRIPTOR = object : LightProjectDescriptor() {
            override fun getSdk(): Sdk =
                JavaSdk.getInstance().createJdk("Inject popup JDK", System.getProperty("java.home"), false)
        }
    }

    private lateinit var library: AnnotationLibrary

    override fun getProjectDescriptor(): LightProjectDescriptor = DESCRIPTOR

    override fun runInDispatchThread(): Boolean = false

    override fun setUp() {
        super.setUp()
        library = AnnotationLibrary(
            "dev.karmakrafts.kcml.api.mixin.Inject",
            "dev.karmakrafts.kcml.api.mixin.IndirectMixin",
            "dev.karmakrafts.kcml.api.mixin.DirectMixin"
        )
        runInEdtAndWait {
            library.attachTo(myFixture.module)
            myFixture.addFileToProject(
                "completion/PopupTarget.kt", """
                    package completion
                    class PopupTarget {
                        private fun overloaded() {}
                        fun overloaded(value: Int) {}
                    }
                """.trimIndent()
            )
        }
    }

    override fun tearDown() {
        try {
            super.tearDown()
        }
        finally {
            if (::library.isInitialized) library.close()
        }
    }

    fun testTypingNameShowsOverloadsAndEnterInsertsDescriptor() {
        assertTypingShowsLookup("@Inject(\"<caret>\")", "over", "overloaded", true)
    }

    fun testTypingNameForCompiledKotlinTargetShowsLookup() {
        assertTypingShowsLookup(
            "@Inject(\"<caret>\")", "na", "name", mixin = "@IndirectMixin(\"dev.karmakrafts.kcml.api.mixin.Inject\")"
        )
    }

    fun testTypingNameForDirectTargetShowsLookup() {
        assertTypingShowsLookup(
            "@Inject(\"<caret>\")", "over", "overloaded", mixin = "@DirectMixin(completion.PopupTarget::class)"
        )
    }

    fun testTypingNameForDirectKotlinStringTargetShowsLookup() {
        assertTypingShowsLookup(
            "@Inject(\"<caret>\")", "sub", "substring", mixin = "@DirectMixin(String::class)"
        )
    }

    fun testTypingInUnterminatedNameShowsLookup() {
        assertTypingShowsLookup("@Inject(\"<caret>", "over", "overloaded")
    }

    fun testTypingOpeningQuoteShowsLookup() {
        assertTypingShowsLookup("@Inject(<caret>)", "\"", "overloaded")
    }

    fun testEditingNameShowsLookup() {
        assertTypingShowsLookup("@Inject(\"ov<caret>OldSuffix\")", "er", "overloaded")
    }

    fun testTypingDescriptorShowsLookup() {
        assertTypingShowsLookup("@Inject(\"overloaded\", \"<caret>\")", "(", "()V")
    }

    fun testTypingInRawAliasedNameShowsLookup() {
        assertTypingShowsLookup(
            "@Injection(name = \"\"\"ov<caret>OldSuffix\"\"\")",
            "er",
            "overloaded",
            imports = "import dev.karmakrafts.kcml.api.mixin.Inject as Injection"
        )
    }

    fun testUnrelatedArgumentsAndInterpolatedStringsDoNotShowInjectLookup() {
        for (annotation in listOf(
            "@Other(\"ov<caret>\")", "@Inject(\"overloaded\", order = \"ov<caret>\")", "@Inject(\"ov<caret>\${value}\")"
        )) {
            val tester = CompletionAutoPopupTester(myFixture)
            tester.runWithAutoPopupEnabled {
                runInEdtAndWait { configure(annotation) }
                tester.typeWithPauses("er")
                runInEdtAndWait {
                    assertFalse(annotation, tester.lookup?.items.orEmpty().any { it.lookupString == "overloaded" })
                    tester.lookup?.hide()
                }
            }
        }
    }

    private fun assertTypingShowsLookup(
        annotation: String,
        typed: String,
        expected: String,
        select: Boolean = false,
        imports: String = "",
        mixin: String = "@IndirectMixin(\"completion.PopupTarget\")"
    ) {
        val tester = CompletionAutoPopupTester(myFixture)
        tester.runWithAutoPopupEnabled {
            runInEdtAndWait { configure(annotation, imports, mixin) }
            tester.typeWithPauses(typed)
            runInEdtAndWait {
                val lookup = tester.lookup
                assertNotNull("Typing an Inject target should open completion", lookup)
                val items = lookup!!.items.filter { it.lookupString == expected }
                assertTrue(lookup.items.joinToString { it.lookupString }, items.isNotEmpty())
                assertTrue(
                    lookup.items.joinToString { it.lookupString },
                    lookup.items.all { it.`object` is InjectTargetMethod })
                if (select) {
                    lookup.currentItem = items.first()
                    myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
                    assertTrue(
                        myFixture.file.text, "@Inject(name = \"overloaded\", descriptor = \"(" in myFixture.file.text
                    )
                }
                else lookup.hide()
            }
        }
    }

    private fun configure(
        annotation: String, imports: String = "", mixin: String = "@IndirectMixin(\"completion.PopupTarget\")"
    ) {
        myFixture.configureByText(
            "Mixin.kt", """
                import dev.karmakrafts.kcml.api.mixin.*
                $imports
                $mixin
                class Example {
                    $annotation
                    fun inject() {}
                }
            """.trimIndent()
        )
    }
}