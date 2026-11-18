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

package dev.karmakrafts.kcml.intellij.gutter

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import dev.karmakrafts.kcml.intellij.testutil.AnnotationFixtureTestCase
import dev.karmakrafts.kcml.intellij.testutil.withAnnotationLibrary
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.psi.KtNamedFunction

class InjectImplicitUsageProviderTest : AnnotationFixtureTestCase() {
    private val provider = InjectImplicitUsageProvider()

    fun testAnnotatedMemberFunction() {
        addAnnotation()
        assertFunctionUsage("class Example { @Inject(\"target\") private fun inject() {} }", true)
    }

    fun testAliasedAnnotationOnTopLevelFunction() {
        addAnnotation()
        assertFunctionUsage(
            """
                import dev.karmakrafts.kcml.api.mixin.Inject as Injection
                @Injection("target") fun inject() {}
            """.trimIndent(), true
        )
    }

    fun testQualifiedAnnotation() {
        addAnnotation()
        assertFunctionUsage("@dev.karmakrafts.kcml.api.mixin.Inject(\"target\") fun inject() {}", true)
    }

    fun testCompiledKotlinAnnotation() {
        myFixture.withAnnotationLibrary("dev.karmakrafts.kcml.api.mixin.Inject") {
            assertFunctionUsage("@Inject(\"target\") fun inject() {}", true)
        }
    }

    fun testUnannotatedFunction() {
        assertFunctionUsage("fun example() {}", false)
    }

    fun testUnrelatedAnnotationWithSameShortName() {
        assertFunctionUsage("annotation class Inject(val name: String)\n@Inject(\"target\") fun inject() {}", false)
    }

    fun testUnresolvedAnnotation() {
        assertFunctionUsage("@missing.Inject(\"target\") fun inject() {}", false)
    }

    fun testAnnotatedJavaMethodAndOtherElementTypes() {
        addAnnotation()
        val declaration = myFixture.addClass(
            """
                import dev.karmakrafts.kcml.api.mixin.Inject;
                @Inject(name = "target") public class Example {
                    @Inject(name = "target") private int field;
                    @Inject(name = "target") private void inject() {}
                    private void unannotated() {}
                }
            """.trimIndent()
        )
        assertUsage(declaration.findMethodsByName("inject", false).single(), true)
        assertUsage(declaration.findMethodsByName("unannotated", false).single(), false)
        assertUsage(declaration, false)
        assertUsage(declaration.fields.single(), false)
        assertUsage(declaration.nameIdentifier!!, false)
        assertUsage(declaration.containingFile, false)
    }

    private fun addAnnotation() {
        addAnnotationSource(
            "dev/karmakrafts/kcml/api/mixin/Inject.kt", """
                package dev.karmakrafts.kcml.api.mixin
                annotation class Inject(val name: String, val descriptor: String = "")
            """.trimIndent()
        )
    }

    private fun assertFunctionUsage(text: String, expected: Boolean) {
        val file = myFixture.configureByText("Injection.kt", "import dev.karmakrafts.kcml.api.mixin.*\n$text")
        val function = PsiTreeUtil.findChildOfType(file, KtNamedFunction::class.java)!!
        assertUsage(function.toLightMethods().single(), expected)
        assertUsage(function, false)
        assertUsage(function.nameIdentifier!!, false)
        assertUsage(file, false)
    }

    private fun assertUsage(element: PsiElement, expected: Boolean) {
        assertEquals(expected, provider.isImplicitUsage(element))
        assertFalse(provider.isImplicitRead(element))
        assertFalse(provider.isImplicitWrite(element))
    }
}