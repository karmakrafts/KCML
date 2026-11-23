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

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.IconLoader
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import dev.karmakrafts.kcml.intellij.testutil.AnnotationFixtureTestCase
import dev.karmakrafts.kcml.intellij.testutil.assertMarkersByIcon
import dev.karmakrafts.kcml.intellij.testutil.assertNavigatesToFile
import dev.karmakrafts.kcml.intellij.testutil.collectSingleMarker
import dev.karmakrafts.kcml.intellij.testutil.declarationAtCaret
import dev.karmakrafts.kcml.intellij.testutil.firstDescendant
import dev.karmakrafts.kcml.intellij.testutil.navigate
import dev.karmakrafts.kcml.intellij.testutil.replaceTextAndCommit
import dev.karmakrafts.kcml.intellij.testutil.withAnnotationLibrary
import org.jetbrains.kotlin.psi.KtNamedFunction

class InjectLineMarkerProviderTest : AnnotationFixtureTestCase() {

    fun testDirectMixinNavigatesToPrivateJavaMethod() {
        addNavigationAnnotations()
        val target = myFixture.addClass("package example; public class Target { private void target() {} }")
        assertNavigation(
            "@DirectMixin(example.Target::class)", "\"target\"", target.methods.single { !it.isConstructor })
    }

    fun testIndirectMixinNavigatesToKotlinFunction() {
        addNavigationAnnotations()
        val target = myFixture.addFileToProject(
            "example/Target.kt", "package example\nclass Target { private fun target() {} }"
        )
        assertNavigation(
            "@IndirectMixin(\"example/Target\")", "\"target\"", target.firstDescendant<KtNamedFunction>()
        )
    }

    fun testDescriptorSelectsExactOverload() {
        addNavigationAnnotations()
        val target = myFixture.addClass(
            "package example; public class Target { void target() {} int target(int value) { return value; } }"
        )
        assertNavigation(
            "@IndirectMixin(\"example.Target\")",
            "descriptor = \"(I)I\", name = \"target\"",
            target.methods.single { it.parameterList.parametersCount == 1 })
    }

    fun testEmptyDescriptorSelectsFirstOverload() {
        addNavigationAnnotations()
        val target = myFixture.addClass(
            "package example; public class Target { void target() {} void target(int value) {} }"
        )
        assertNavigation(
            "@DirectMixin(example.Target::class)", "\"target\", \"\"", target.methods.first { !it.isConstructor })
    }

    fun testNavigatesToInheritedPrivateMethod() {
        addNavigationAnnotations()
        val parent = myFixture.addClass("package example; public class Parent { private void target() {} }")
        myFixture.addClass("package example; public class Target extends Parent {}")
        assertNavigation(
            "@DirectMixin(example.Target::class)", "\"target\"", parent.methods.single { !it.isConstructor })
    }

    fun testNavigatesToCompiledMethod() {
        myFixture.withAnnotationLibrary(
            "dev.karmakrafts.kcml.api.mixin.Inject", "dev.karmakrafts.kcml.api.mixin.IndirectMixin"
        ) {
            val marker = configureMarker(
                """
                    @IndirectMixin("dev/karmakrafts/kcml/api/mixin/Inject")
                    class Example { @Inject("name", "()Ljava/lang/String;") fun <caret>inject() {} }
                """.trimIndent()
            )
            val target = JavaPsiFacade.getInstance(project).findClass(
                "dev.karmakrafts.kcml.api.mixin.Inject", GlobalSearchScope.allScope(project)
            )!!
            assertNavigation(marker, target.findMethodsByName("name", false).single())
        }
    }

    fun testAliasedAnnotationsNavigateToNestedTarget() {
        addNavigationAnnotations()
        val target = myFixture.addClass(
            "package example; public class Outer { public static class Nested { void target() {} } }"
        )
        val marker = configureMarker(
            """
                import dev.karmakrafts.kcml.api.mixin.Inject as Injection
                import dev.karmakrafts.kcml.api.mixin.IndirectMixin as Mixin
                @Mixin("example/Outer\${'$'}Nested")
                class Example { @Injection("target") fun <caret>inject() {} }
            """.trimIndent()
        )
        assertNavigation(marker, target.innerClasses.single().methods.single { !it.isConstructor })
    }

    fun testUnresolvedInjectionSitesHaveNoClickAction() {
        addNavigationAnnotations()
        myFixture.addClass("package example; public class Target { void target() {} }")
        for (text in listOf(
            "@Inject(\"target\") fun <caret>inject() {}",
            "@IndirectMixin(\"example.Missing\") class Example { @Inject(\"target\") fun <caret>inject() {} }",
            "@DirectMixin(example.Target::class) class Example { @Inject(\"missing\") fun <caret>inject() {} }",
            "@DirectMixin(example.Target::class) class Example { @Inject(\"target\", \"(I)V\") fun <caret>inject() {} }"
        )) {
            val marker = configureMarker(text)
            assertNull(text, marker.navigationHandler)
            assertNull(text, marker.createGutterRenderer()!!.clickAction)
        }
    }

    fun testClickResolvesCurrentInjectionName() {
        addNavigationAnnotations()
        val target = myFixture.addClass(
            "package example; public class Target { void first() {} void second() {} }"
        )
        val marker = configureMarker(
            "@DirectMixin(example.Target::class) class Example { @Inject(\"first\") fun <caret>inject() {} }"
        )
        myFixture.replaceTextAndCommit("first", "second")
        assertNavigation(marker, target.methods.single { it.name == "second" })
    }

    fun testNavigationBecomesAvailableAfterEditingName() {
        addNavigationAnnotations()
        myFixture.addClass("package example; public class Target { void target() {} }")
        val unresolved = configureMarker(
            "@DirectMixin(example.Target::class) class Example { @Inject(\"missing\") fun <caret>inject() {} }"
        ).createGutterRenderer()!!
        myFixture.replaceTextAndCommit("missing", "target")
        val resolved = collectMarker().createGutterRenderer()!!
        assertNotNull(resolved.clickAction)
        assertFalse(unresolved == resolved)
    }

    fun testNavigationDisappearsAfterEditingDescriptor() {
        addNavigationAnnotations()
        myFixture.addClass("package example; public class Target { void target() {} }")
        val marker = configureMarker(
            "@DirectMixin(example.Target::class) class Example { @Inject(\"target\", \"()V\") fun <caret>inject() {} }"
        )
        val resolved = marker.createGutterRenderer()!!
        assertNotNull(resolved.clickAction)
        myFixture.replaceTextAndCommit("()V", "(I)V")
        val unresolved = collectMarker().createGutterRenderer()!!
        assertNull(unresolved.clickAction)
        assertFalse(resolved == unresolved)
        marker.navigate()
        assertTrue(FileEditorManager.getInstance(project).selectedFiles.none { it.name == "Target.java" })
    }

    fun testTopLevelFunction() {
        addAnnotation()
        assertMarkers("@Inject(\"target\") fun <caret>inject() {}", 1)
    }

    fun testMemberFunction() {
        addAnnotation()
        assertMarkers("class Example { @Inject(\"target\") fun <caret>inject() {} }", 1)
    }

    fun testAliasedAnnotation() {
        addAnnotation()
        assertMarkers(
            """
                import dev.karmakrafts.kcml.api.mixin.Inject as Injection
                @Injection("target") fun <caret>inject() {}
            """.trimIndent(), 1
        )
    }

    fun testQualifiedAnnotationOnObjectMember() {
        addAnnotation()
        assertMarkers(
            """
                object Example {
                    @dev.karmakrafts.kcml.api.mixin.Inject("target")
                    fun <caret>inject() {}
                }
            """.trimIndent(), 1
        )
    }

    fun testMultipleFunctions() {
        addAnnotation()
        assertMarkers(
            """
                @Inject("first") fun <caret>first() {}
                fun unannotated() {}
                @Inject("second") fun second() {}
            """.trimIndent(), 2
        )
    }

    fun testUnannotatedFunctionHasNoMarker() {
        assertMarkers("fun <caret>example() {}", 0)
    }

    fun testUnrelatedAnnotationHasNoMarker() {
        assertMarkers(
            """
                annotation class Inject(val name: String)
                @Inject("target") fun <caret>inject() {}
            """.trimIndent(), 0
        )
    }

    fun testUnresolvedAnnotationHasNoMarker() {
        assertMarkers("@missing.Inject(\"target\") fun <caret>inject() {}", 0)
    }

    fun testClassHasNoMarker() {
        addAnnotation()
        assertMarkers("@Inject(\"target\") class <caret>Example", 0)
    }

    fun testObjectHasNoMarker() {
        addAnnotation()
        assertMarkers("@Inject(\"target\") object <caret>Example", 0)
    }

    fun testPropertyHasNoMarker() {
        addAnnotation()
        assertMarkers("@Inject(\"target\") val <caret>example = 0", 0)
    }

    fun testCompiledKotlinAnnotation() {
        myFixture.withAnnotationLibrary("dev.karmakrafts.kcml.api.mixin.Inject") {
            assertMarkers("@Inject(\"target\") fun <caret>inject() {}", 1)
        }
    }

    private fun addAnnotation() {
        addAnnotationSource(
            "dev/karmakrafts/kcml/api/mixin/Inject.kt", """
                package dev.karmakrafts.kcml.api.mixin
                annotation class Inject(val name: String, val descriptor: String = "")
            """.trimIndent()
        )
    }

    private fun addNavigationAnnotations() {
        addAnnotation()
        addAnnotationSource(
            "dev/karmakrafts/kcml/api/mixin/Mixins.kt", """
                package dev.karmakrafts.kcml.api.mixin
                annotation class DirectMixin(val target: kotlin.reflect.KClass<*>)
                annotation class IndirectMixin(val target: String)
            """.trimIndent()
        )
    }

    private fun configureMarker(text: String): LineMarkerInfo<*> {
        myFixture.configureByText("Injection.kt", "import dev.karmakrafts.kcml.api.mixin.*\n$text")
        return collectMarker()
    }

    private fun collectMarker(): LineMarkerInfo<*> =
        InjectLineMarkerProvider().collectSingleMarker(myFixture.declarationAtCaret<KtNamedFunction>())

    private fun assertNavigation(mixin: String, arguments: String, target: PsiElement) {
        assertNavigation(configureMarker("$mixin class Example { @Inject($arguments) fun <caret>inject() {} }"), target)
    }

    private fun assertNavigation(marker: LineMarkerInfo<*>, target: PsiElement) {
        assertNotNull("Injection marker should navigate to its target", marker.navigationHandler)
        assertNotNull(marker.createGutterRenderer()!!.clickAction)
        project.assertNavigatesToFile(marker, target.containingFile)
        val manager = FileEditorManager.getInstance(project)
        assertEquals(target.navigationElement.textOffset, manager.selectedTextEditor!!.caretModel.offset)
    }

    private fun assertMarkers(text: String, expected: Int) {
        myFixture.configureByText("Injection.kt", "import dev.karmakrafts.kcml.api.mixin.*\n$text")
        val icon = IconLoader.getIcon("/icons/dev/karmakrafts/kcml/intellij/injection.svg", javaClass)
        myFixture.assertMarkersByIcon(expected, icon, "KCML plugin")
    }
}