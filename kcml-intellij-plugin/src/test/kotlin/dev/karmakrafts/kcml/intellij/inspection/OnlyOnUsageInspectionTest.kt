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
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.PlatformTestUtil
import dev.karmakrafts.kcml.intellij.testutil.InspectionFixtureTestCase
import dev.karmakrafts.kcml.intellij.testutil.withAnnotationLibrary
import org.jetbrains.kotlin.psi.KtSimpleNameExpression

class OnlyOnUsageInspectionTest : InspectionFixtureTestCase(
    TargetSpecificCodeInspection(), MismatchedTargetInspection()
) {

    private fun addApi(compiled: Boolean) {
        val source = """
            package dev.karmakrafts.kcml.api.target
            import kotlin.reflect.KClass
            @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY,
                AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.FIELD,
                AnnotationTarget.TYPEALIAS, AnnotationTarget.TYPE)
            @Retention(AnnotationRetention.BINARY)
            annotation class OnlyOn(vararg val targets: KClass<out CompileTarget>)
            interface CompileTarget
            interface CompileTargetType
            interface JvmCompileTarget : CompileTarget { companion object Type : CompileTargetType }
            interface JsCompileTarget : CompileTarget { companion object Type : CompileTargetType }
            interface NativeCompileTarget : CompileTarget { companion object Type : CompileTargetType }
            interface WasmCompileTarget : CompileTarget { companion object Type : CompileTargetType }
            interface CompileTargetProvider
            fun CompileTargetProvider.whenCompilingFor(vararg types: CompileTargetType, block: () -> Unit) = block()
            fun <R> CompileTargetProvider.whenCompilingFor(default: R, vararg types: CompileTargetType, block: () -> R): R = block()
        """.trimIndent()
        val targetsSource = if (!compiled) source
        else source.replace(Regex("@Target[\\s\\S]*?annotation class OnlyOn[^\\n]*"), "").replace(
            "interface NativeCompileTarget : CompileTarget { companion object Type : CompileTargetType }", ""
        )
        addAnnotationSource("dev/karmakrafts/kcml/api/target/Targets.kt", targetsSource)
        addAnnotationSource(
            "sample/Api.kt", """
            package sample
            import dev.karmakrafts.kcml.api.target.*
            @OnlyOn(JvmCompileTarget::class) fun jvm() = Unit
            @OnlyOn(JsCompileTarget::class) fun js() = Unit
            @OnlyOn(JvmCompileTarget::class, JsCompileTarget::class) fun shared() = Unit
            @OnlyOn(NativeCompileTarget::class) fun native() = Unit
            @OnlyOn(WasmCompileTarget::class) fun wasm() = Unit
        """.trimIndent()
        )
    }

    fun testEdtLookupIsDeferredCachedAndInvalidated() {
        addApi(false)
        myFixture.configureByText(
            "Example.kt", """
            package sample
            import dev.karmakrafts.kcml.api.target.*
            fun use() { jvm() }
        """.trimIndent()
        )
        fun reference() = PsiTreeUtil.findChildrenOfType(myFixture.file, KtSimpleNameExpression::class.java)
            .single { it.text == "jvm" }
        assertTrue(ApplicationManager.getApplication().isDispatchThread)
        assertNull(OnlyOnUsage.problem(reference()))
        PlatformTestUtil.waitWithEventsDispatching("OnlyOn background analysis", {
            OnlyOnUsage.problem(reference()) == OnlyOnUsage.Problem.UNGUARDED
        }, 10)
        assertEquals(OnlyOnUsage.Problem.UNGUARDED, OnlyOnUsage.problem(reference()))
        WriteCommandAction.runWriteCommandAction(project) {
            val document = myFixture.editor.document
            document.insertString(document.text.indexOf("fun use"), "@OnlyOn(JsCompileTarget::class) ")
            PsiDocumentManager.getInstance(project).commitDocument(document)
        }
        assertNull(OnlyOnUsage.problem(reference()))
        PlatformTestUtil.waitWithEventsDispatching("OnlyOn cache invalidation", {
            OnlyOnUsage.problem(reference()) == OnlyOnUsage.Problem.MISMATCHED
        }, 10)
        assertEquals(OnlyOnUsage.Problem.MISMATCHED, OnlyOnUsage.problem(reference()))
    }

    fun testRootAndMatchingScopes() = check(
        """
        fun CompileTargetProvider.use() {
            <warning descr="$WARNING">jvm()</warning>
            whenCompilingFor(JvmCompileTarget) { jvm(); shared(); <error descr="$ERROR">js()</error> }
            whenCompilingFor(JsCompileTarget) { js(); shared(); <error descr="$ERROR">jvm()</error> }
            whenCompilingFor(NativeCompileTarget) { native(); <error descr="$ERROR">wasm()</error> }
            whenCompilingFor(WasmCompileTarget) { wasm(); <error descr="$ERROR">native()</error> }
        }
    """
    )

    fun testMultipleAndNestedTargets() = check(
        """
        fun CompileTargetProvider.use() {
            whenCompilingFor(JvmCompileTarget, JsCompileTarget) {
                shared()
                <error descr="$ERROR">jvm()</error>
                whenCompilingFor(JvmCompileTarget) { jvm(); <error descr="$ERROR">js()</error> }
            }
            whenCompilingFor(JvmCompileTarget) {
                whenCompilingFor(JsCompileTarget) { jvm(); js(); native() }
            }
            whenCompilingFor { jvm(); js() }
        }
    """
    )

    fun testAnnotatedBodies() = check(
        """
        @OnlyOn(JvmCompileTarget::class)
        fun CompileTargetProvider.annotated() {
            jvm()
            <error descr="$ERROR">js()</error>
            whenCompilingFor(JsCompileTarget) { js(); native() }
        }
        @OnlyOn(JvmCompileTarget::class)
        class Annotated {
            val value = jvm()
            fun use() { jvm(); <error descr="$ERROR">js()</error> }
        }
    """
    )

    fun testResolvedCallsAndNamedArguments() = check(
        """
        import dev.karmakrafts.kcml.api.target.whenCompilingFor as onTarget
        fun CompileTargetProvider.use() {
            onTarget(JvmCompileTarget.Type, block = { jvm() })
            onTarget(default = 0, types = arrayOf(JvmCompileTarget), block = { jvm(); 1 })
            whenCompilingFor("not a scope") { <warning descr="$WARNING">jvm()</warning> }
        }
        fun whenCompilingFor(value: String, block: () -> Unit) = block()
    """
    )

    fun testClassesMembersAndAliases() = check(
        """
        @OnlyOn(JvmCompileTarget::class) class Restricted<T> { fun member() = Unit }
        @OnlyOn(JvmCompileTarget::class) typealias Alias = String
        fun outside(value: <warning descr="$WARNING">Restricted<String></warning>) {
            <warning descr="$WARNING">Restricted<String>()</warning>
            value.<warning descr="$WARNING">member()</warning>
            val ref = ::<warning descr="$WARNING">jvm</warning>
            val alias: <warning descr="$WARNING">Alias</warning> = ""
        }
        @OnlyOn(JvmCompileTarget::class)
        fun inside(value: Restricted<String>) { Restricted<String>(); value.member(); val alias: Alias = "" }
    """
    )

    fun testAccessorsAndFields() = check(
        """
        @get:OnlyOn(JvmCompileTarget::class)
        @set:OnlyOn(JsCompileTarget::class)
        var value: Int = 0
        @field:OnlyOn(JvmCompileTarget::class) var fieldValue: Int = 0
        fun CompileTargetProvider.use() {
            val read = <warning descr="$WARNING">value</warning>
            <warning descr="$WARNING">value</warning> = 1
            val field = <warning descr="$WARNING">fieldValue</warning>
            whenCompilingFor(JvmCompileTarget) {
                val read = value
                <error descr="$ERROR">value</error> = 1
                <error descr="$ERROR">value</error>++
                fieldValue = 2
            }
            whenCompilingFor(JsCompileTarget) { value = 2; val read = <error descr="$ERROR">value</error> }
        }
    """
    )

    fun testClosureContextsAndInvocation() = check(
        """
        fun withJvm(block: @OnlyOn(JvmCompileTarget::class) () -> Unit) = Unit
        fun CompileTargetProvider.use() {
            withJvm { jvm(); <error descr="$ERROR">js()</error> }
            val block: @OnlyOn(JvmCompileTarget::class) () -> Unit = { jvm(); <error descr="$ERROR">js()</error> }
            <warning descr="$WARNING">block()</warning>
            whenCompilingFor(JvmCompileTarget) { block() }
            whenCompilingFor(JsCompileTarget) { <error descr="$ERROR">block()</error> }
        }
    """
    )

    fun testSuppressionIsIndependent() = check(
        """
        @Suppress("KCML_TARGET_SPECIFIC_CODE")
        fun CompileTargetProvider.suppressWarning() {
            jvm()
            whenCompilingFor(JsCompileTarget) { <error descr="$ERROR">jvm()</error> }
        }
        @Suppress("KCML_MISMATCHED_TARGET")
        fun CompileTargetProvider.suppressError() {
            <warning descr="$WARNING">jvm()</warning>
            whenCompilingFor(JsCompileTarget) { jvm() }
        }
    """
    )

    fun testSubtypeCompatibility() = check(
        """
        interface CustomTarget : JvmCompileTarget { companion object : CompileTargetType }
        @OnlyOn(CustomTarget::class) fun custom() = Unit
        @OnlyOn(CompileTarget::class) fun anyTarget() = Unit
        @OnlyOn(CustomTarget::class)
        fun use() { jvm(); custom(); anyTarget(); <error descr="$ERROR">js()</error> }
        fun CompileTargetProvider.scopes() {
            whenCompilingFor(CustomTarget) { jvm(); custom(); anyTarget() }
            whenCompilingFor(JvmCompileTarget) { <error descr="$ERROR">custom()</error>; anyTarget() }
        }
    """
    )

    fun testUnknownTargetArgumentsRemainConservative() = check(
        """
        fun CompileTargetProvider.use(target: CompileTargetType) {
            whenCompilingFor(target) { <warning descr="$WARNING">jvm()</warning> }
            whenCompilingFor(JvmCompileTarget, target) { <warning descr="$WARNING">jvm()</warning> }
            val known = JvmCompileTarget
            whenCompilingFor(known) { jvm(); <error descr="$ERROR">js()</error> }
            val knownArray = arrayOf(JvmCompileTarget, JsCompileTarget)
            whenCompilingFor(*knownArray) { shared(); <error descr="$ERROR">jvm()</error> }
        }
    """
    )

    fun testClosureExplicitInvocation() = check(
        """
        fun CompileTargetProvider.use(block: @OnlyOn(JvmCompileTarget::class) () -> Unit) {
            block.<warning descr="$WARNING">invoke()</warning>
            whenCompilingFor(JvmCompileTarget) { block.invoke() }
            whenCompilingFor(JsCompileTarget) { block.<error descr="$ERROR">invoke()</error> }
        }
    """
    )

    fun testAliasedConstructorsAndInheritedMembers() = check(
        """
        class Ordinary
        @OnlyOn(JvmCompileTarget::class) typealias RestrictedAlias = Ordinary
        @OnlyOn(JvmCompileTarget::class) open class Base { fun member() = Unit }
        class Derived : <warning descr="$WARNING">Base</warning>()
        fun CompileTargetProvider.use(value: Derived) {
            <warning descr="$WARNING">RestrictedAlias()</warning>
            value.<warning descr="$WARNING">member()</warning>
            whenCompilingFor(JvmCompileTarget) { RestrictedAlias(); value.member() }
            whenCompilingFor(JsCompileTarget) {
                <error descr="$ERROR">RestrictedAlias()</error>
                value.<error descr="$ERROR">member()</error>
            }
        }
    """
    )

    fun testCompiledAnnotationAndApi() {
        myFixture.withAnnotationLibrary(
            "dev.karmakrafts.kcml.api.target.OnlyOn",
            "dev.karmakrafts.kcml.api.target.NativeCompileTarget",
            "dev.karmakrafts.kcml.api.target.NativeCompileTarget\$Type",
            "dev.karmakrafts.kcml.api.target.NativeCompileTarget\$DefaultImpls",
            "dev.karmakrafts.kcml.api.target.NativeArchitectureConfiguration"
        ) {
            check(
                """
                fun CompileTargetProvider.use(value: NativeCompileTarget) {
                    val root = value.<warning descr="$WARNING">architecture</warning>
                    whenCompilingFor(NativeCompileTarget) { val allowed = value.architecture }
                    whenCompilingFor(JvmCompileTarget) { val denied = value.<error descr="$ERROR">architecture</error> }
                }
            """, true
            )
        }
    }

    private fun check(text: String, compiled: Boolean = false) {
        addApi(compiled)
        myFixture.withAnnotationLibrary("java.io.Serializable", "java.lang.Object") {
            checkKotlinHighlighting(
                text.trimIndent(), "package sample\nimport dev.karmakrafts.kcml.api.target.*"
            )
        }
    }

    companion object {
        private const val WARNING = "API marked @OnlyOn should be used in a matching target context"
        private const val ERROR = "API marked @OnlyOn is not available for all targets in this context"
    }
}