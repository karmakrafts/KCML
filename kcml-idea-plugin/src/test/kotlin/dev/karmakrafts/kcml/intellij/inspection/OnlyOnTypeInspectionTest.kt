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

package dev.karmakrafts.kcml.intellij.inspection

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.PlatformTestUtil
import dev.karmakrafts.kcml.intellij.testutil.InspectionFixtureTestCase
import dev.karmakrafts.kcml.intellij.testutil.collectProblems
import dev.karmakrafts.kcml.intellij.testutil.withAnnotationLibrary
import org.jetbrains.kotlin.psi.KtTypeReference

class OnlyOnTypeInspectionTest : InspectionFixtureTestCase(OnlyOnTypeInspection()) {
    override fun setUp() {
        super.setUp()
        addAnnotationSource(
            "dev/karmakrafts/kcml/api/target/Targets.kt", """
                package dev.karmakrafts.kcml.api.target
                interface CompileTarget
                object JvmCompileTarget : CompileTarget
                @Retention(AnnotationRetention.BINARY)
                @Target(
                    AnnotationTarget.CLASS,
                    AnnotationTarget.FUNCTION,
                    AnnotationTarget.PROPERTY,
                    AnnotationTarget.PROPERTY_GETTER,
                    AnnotationTarget.PROPERTY_SETTER,
                    AnnotationTarget.FIELD,
                    AnnotationTarget.TYPEALIAS,
                    AnnotationTarget.TYPE
                )
                annotation class OnlyOn(vararg val targets: kotlin.reflect.KClass<out CompileTarget>)
            """.trimIndent()
        )
    }

    fun testEdtTypeValidationIsDeferredAndInvalidatedByAliasEdit() {
        val alias = myFixture.addFileToProject("sample/Alias.kt", "package sample\ntypealias Alias = String")
        myFixture.configureByText(
            "Example.kt", """
            package sample
            import dev.karmakrafts.kcml.api.target.*
            fun use(value: @OnlyOn(JvmCompileTarget::class) Alias) = Unit
        """.trimIndent()
        )
        fun problems() = myFixture.collectProblems<KtTypeReference>(OnlyOnTypeInspection()).size
        assertTrue(ApplicationManager.getApplication().isDispatchThread)
        assertEquals(0, problems())
        PlatformTestUtil.waitWithEventsDispatching("OnlyOn background type validation", { problems() == 1 }, 10)
        assertEquals(1, problems())
        WriteCommandAction.runWriteCommandAction(project) {
            val documents = PsiDocumentManager.getInstance(project)
            val document = documents.getDocument(alias)!!
            document.text = "package sample\ntypealias Alias = () -> Unit"
            documents.commitDocument(document)
        }
        assertEquals(0, problems())
        myFixture.checkHighlighting(true, false, false)
        assertEquals(0, problems())
    }

    fun testNonFunctionTypes() = check(
        """
            val text: <error descr="@OnlyOn is only allowed on function types">@OnlyOn(JvmCompileTarget::class)</error> String = ""
            fun value(input: <error descr="@OnlyOn is only allowed on function types">@OnlyOn(JvmCompileTarget::class)</error> Int):
                <error descr="@OnlyOn is only allowed on function types">@OnlyOn(JvmCompileTarget::class)</error> String = input.toString()
            val nullable: <error descr="@OnlyOn is only allowed on function types">@OnlyOn(JvmCompileTarget::class)</error> String? = null
            val parenthesized: <error descr="@OnlyOn is only allowed on function types">@OnlyOn(JvmCompileTarget::class)</error> (String) = ""
        """.trimIndent()
    )

    fun testFunctionTypes() = check(
        """
            fun consume(
                ordinary: @OnlyOn(JvmCompileTarget::class) () -> Unit,
                parameterized: @OnlyOn(JvmCompileTarget::class) (Int) -> String,
                extension: @OnlyOn(JvmCompileTarget::class) String.() -> Unit,
                suspending: @OnlyOn(JvmCompileTarget::class) suspend () -> Unit,
                nullable: @OnlyOn(JvmCompileTarget::class) (() -> Unit)?,
                parenthesized: @OnlyOn(JvmCompileTarget::class) ((() -> Unit)),
                inner: (@OnlyOn(JvmCompileTarget::class) () -> Unit)?
            ) = Unit
        """.trimIndent()
    )

    fun testNestedTypesAreCheckedIndependently() = check(
        """
            fun consume(
                container: <error descr="@OnlyOn is only allowed on function types">@OnlyOn(JvmCompileTarget::class)</error> List<@OnlyOn(JvmCompileTarget::class) () -> Unit>,
                argument: List<<error descr="@OnlyOn is only allowed on function types">@OnlyOn(JvmCompileTarget::class)</error> String>,
                parameter: @OnlyOn(JvmCompileTarget::class) (<error descr="@OnlyOn is only allowed on function types">@OnlyOn(JvmCompileTarget::class)</error> String) -> Unit,
                result: @OnlyOn(JvmCompileTarget::class) () -> <error descr="@OnlyOn is only allowed on function types">@OnlyOn(JvmCompileTarget::class)</error> String,
                receiver: (<error descr="@OnlyOn is only allowed on function types">@OnlyOn(JvmCompileTarget::class)</error> String).() -> Unit
            ) = Unit
        """.trimIndent()
    )

    fun testQualifiedAndImportedAnnotationAliases() = check(
        """
            import dev.karmakrafts.kcml.api.target.OnlyOn as TargetOnly
            fun consume(
                first: <error descr="@OnlyOn is only allowed on function types">@TargetOnly(JvmCompileTarget::class)</error> String,
                second: <error descr="@OnlyOn is only allowed on function types">@dev.karmakrafts.kcml.api.target.OnlyOn(JvmCompileTarget::class)</error> Int,
                closure: @TargetOnly(JvmCompileTarget::class) () -> Unit
            ) = Unit
        """.trimIndent()
    )

    fun testAnnotationTypeAliases() = check(
        """
            typealias TargetOnly = OnlyOn
            typealias OtherTargetOnly = TargetOnly
            fun consume(
                value: <error descr="@OnlyOn is only allowed on function types">@OtherTargetOnly(JvmCompileTarget::class)</error> String,
                closure: @OtherTargetOnly(JvmCompileTarget::class) () -> Unit
            ) = Unit
        """.trimIndent()
    )

    fun testFunctionTypeAliases() = check(
        """
            typealias Closure = () -> Unit
            typealias NullableClosure = Closure?
            typealias Text = String
            fun consume(
                closure: @OnlyOn(JvmCompileTarget::class) Closure,
                nullable: @OnlyOn(JvmCompileTarget::class) NullableClosure,
                text: <error descr="@OnlyOn is only allowed on function types">@OnlyOn(JvmCompileTarget::class)</error> Text
            ) = Unit
        """.trimIndent()
    )

    fun testDeclarationAnnotationsAreAllowed() = check(
        """
            @OnlyOn(JvmCompileTarget::class) class Example
            @OnlyOn(JvmCompileTarget::class) fun value() = Unit
            @OnlyOn(JvmCompileTarget::class) val property: String = ""
            @get:OnlyOn(JvmCompileTarget::class)
            @set:OnlyOn(JvmCompileTarget::class)
            @field:OnlyOn(JvmCompileTarget::class)
            var mutable: String = ""
            @OnlyOn(JvmCompileTarget::class) typealias Text = String
            typealias InvalidText = <error descr="@OnlyOn is only allowed on function types">@OnlyOn(JvmCompileTarget::class)</error> String
        """.trimIndent()
    )

    fun testUnrelatedAnnotationIsIgnored() = check(
        """
            @Target(AnnotationTarget.TYPE)
            annotation class OnlyOn
            fun consume(value: @OnlyOn String) = Unit
        """.trimIndent()
    )

    fun testSuppression() = check(
        """
            @Suppress("KCML_ONLY_ON_TYPE")
            fun consume(value: @OnlyOn(JvmCompileTarget::class) String) = Unit
        """.trimIndent()
    )

    fun testInspectionIdentifiers() {
        val inspection = OnlyOnTypeInspection()
        assertEquals("KCML_ONLY_ON_TYPE", inspection.getID())
        assertEquals("KcmlOnlyOnType", inspection.getShortName())
    }

    private fun check(text: String) {
        myFixture.withAnnotationLibrary("java.io.Serializable", "java.lang.Object") {
            checkKotlinHighlighting(text, "package sample\nimport dev.karmakrafts.kcml.api.target.*")
        }
    }
}