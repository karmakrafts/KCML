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

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.LookupElementRenderer
import com.intellij.ide.IconLayerProvider
import com.intellij.ide.IconProvider
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiJavaFile
import com.intellij.ui.DeferredIcon
import com.intellij.ui.IconManager
import com.intellij.ui.LayeredIcon
import com.intellij.ui.icons.CompositeIcon
import com.intellij.ui.icons.CoreIconManager
import dev.karmakrafts.kcml.intellij.completion.CompileTargetCompletionContributor
import dev.karmakrafts.kcml.intellij.testutil.AnnotationFixtureTestCase
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import javax.swing.Icon

class CompileTargetIconProviderTest : AnnotationFixtureTestCase() {
    private val provider = CompileTargetIconProvider()

    override fun setUp() {
        super.setUp()
        addAnnotationSource(
            "dev/karmakrafts/kcml/api/target/Targets.kt", """
                package dev.karmakrafts.kcml.api.target
                sealed interface CompileTarget
                interface JvmCompileTarget : CompileTarget
                interface JsCompileTarget : CompileTarget
                interface WasmCompileTarget : CompileTarget
                interface NativeCompileTarget : CompileTarget
            """.trimIndent()
        )
    }

    fun testAllCompileTargetsReplaceClassAndFileIcons() {
        for (target in listOf("Jvm", "Js", "Wasm", "Native")) {
            val file = kotlinFile("class Example : ${target}CompileTarget")
            val declaration = file.declarations.single() as KtClassOrObject
            assertKotlinTargetIcon(provider.getIcon(file, 0))
            assertKotlinTargetIcon(provider.getIcon(declaration, 0))
            assertKotlinTargetIcon(provider.getIcon(declaration.toLightClass()!!, 0))
        }
    }

    fun testInheritedTargetAndAlias() {
        val file = kotlinFile(
            """
            import dev.karmakrafts.kcml.api.target.JvmCompileTarget as Target
            open class Base : Target
            class Example : Base()
        """.trimIndent()
        )
        assertKotlinTargetIcon(provider.getIcon(file.declarations.last() as KtClassOrObject, 0))
    }

    fun testBaseCompileTargetAndPlatformInterfaces() {
        for (name in listOf(
            "CompileTarget", "JvmCompileTarget", "JsCompileTarget", "WasmCompileTarget", "NativeCompileTarget"
        )) {
            val declaration =
                myFixture.findClass("dev.karmakrafts.kcml.api.target.$name").navigationElement as KtClassOrObject
            assertKotlinTargetIcon(provider.getIcon(declaration, 0))
            assertKotlinTargetIcon(provider.getIcon(declaration.toLightClass()!!, 0))
            assertKotlinTargetIcon(provider.getIcon(declaration.containingFile, 0))
        }
    }

    fun testRecursiveSubtypesOfBaseCompileTarget() {
        val file = kotlinFile(
            """
            interface Direct : CompileTarget
            interface Indirect : Direct
            abstract class Base : Indirect
            open class Intermediate : Base()
            class Example : Intermediate()
            object Singleton : Intermediate()
        """.trimIndent()
        )
        assertKotlinTargetIcon(provider.getIcon(file, 0))
        file.declarations.filterIsInstance<KtClassOrObject>().forEach {
            assertKotlinTargetIcon(provider.getIcon(it, 0))
            assertKotlinTargetIcon(provider.getIcon(it.toLightClass()!!, 0))
        }
    }

    fun testRecursiveJavaSubtypesOfBaseCompileTarget() {
        val file = myFixture.configureByText(
            "Example.java", """
                import dev.karmakrafts.kcml.api.target.CompileTarget;
                interface Direct extends CompileTarget {}
                interface Indirect extends Direct {}
                abstract class Base implements Indirect {}
                class Example extends Base {}
            """.trimIndent()
        ) as PsiJavaFile
        assertSame(Icons.target, provider.getIcon(file, 0))
        file.classes.forEach { assertSame(Icons.target, provider.getIcon(it, 0)) }
    }

    fun testObjectAndMultipleDeclarations() {
        val file = kotlinFile("class Ordinary\nobject Example : JsCompileTarget")
        assertKotlinTargetIcon(provider.getIcon(file, 0))
        assertNull(provider.getIcon(file.declarations.first() as KtClassOrObject, 0))
        assertKotlinTargetIcon(provider.getIcon(file.declarations.last() as KtClassOrObject, 0))
    }

    fun testUnrelatedTypesAndInterfacesKeepOriginalIcon() {
        for (text in listOf(
            "class Example",
            "interface JvmCompileTarget\nclass Example : JvmCompileTarget",
            "interface CompileTarget\ninterface Intermediate : CompileTarget\nclass Example : Intermediate",
            "annotation class Example",
            "fun example() = Unit"
        )) {
            val file = kotlinFile(text)
            assertNull(provider.getIcon(file, 0))
            file.declarations.filterIsInstance<KtClassOrObject>().forEach {
                assertNull(provider.getIcon(it, 0))
            }
        }
    }

    fun testJavaImplementation() {
        val file = myFixture.configureByText(
            "Example.java", """
                import dev.karmakrafts.kcml.api.target.JvmCompileTarget;
                class Example implements JvmCompileTarget {}
            """.trimIndent()
        ) as PsiJavaFile
        assertSame(Icons.target, provider.getIcon(file, 0))
        assertSame(Icons.target, provider.getIcon(file.classes.single(), 0))
        val previousManager = IconManager.getInstance()
        IconManager.activate(CoreIconManager())
        try {
            val classIcon = file.classes.single().getIcon(0)
            assertTrue("Class icon: $classIcon", containsTarget(classIcon))
            val fileIcon = file.getIcon(0)
            assertTrue("File icon: $fileIcon", containsTarget(fileIcon))
        }
        finally {
            IconManager.activate(previousManager)
        }
    }

    fun testRegisteredProviderReplacesBaseIconWithoutLayer() {
        assertFalse(IconLayerProvider.EP_NAME.extensionList.any { it.javaClass == provider.javaClass })
        val file = kotlinFile("class Example : JvmCompileTarget")
        val declaration = file.declarations.single() as KtClassOrObject
        val registered =
            IconProvider.EXTENSION_POINT_NAME.extensionList.filterIsInstance<CompileTargetIconProvider>().single()
        assertKotlinTargetIcon(registered.getIcon(file, 0))
        assertKotlinTargetIcon(registered.getIcon(declaration, Iconable.ICON_FLAG_VISIBILITY))
        val classIcon = declaration.getIcon(Iconable.ICON_FLAG_VISIBILITY)
        assertTrue("Class icon: $classIcon", containsTarget(classIcon))
        val fileIcon = file.getIcon(0)
        assertTrue("File icon: $fileIcon", containsTarget(fileIcon))
    }

    fun testIconContributorRunsBeforeKotlinCompletion() {
        val contributors = CompletionContributor.forLanguage(KotlinLanguage.INSTANCE)
        val iconContributor = contributors.indexOfFirst { it is CompileTargetCompletionContributor }
        val kotlinContributor = contributors.indexOfFirst { it.javaClass.simpleName == "KotlinFirCompletionContributor" }
        assertTrue("Compile target completion contributor must be registered", iconContributor >= 0)
        assertTrue("Kotlin completion contributor must be registered", kotlinContributor >= 0)
        assertTrue(
            "Icon contributor must intercept Kotlin completion results: ${contributors.map { it.javaClass.name }}",
            iconContributor < kotlinContributor
        )
    }

    fun testKotlinCompletionUsesTargetIcons() {
        myFixture.configureByText(
            "Completion.kt", """
            import dev.karmakrafts.kcml.api.target.*
            interface CompletionDirect : CompileTarget
            open class CompletionBase : CompletionDirect
            class CompletionTarget : CompletionBase()
            object CompletionSingleton : CompletionBase()
            class CompletionOrdinary
            fun use(value: Completion<caret>) {}
        """.trimIndent()
        )
        val items = myFixture.complete(CompletionType.BASIC).orEmpty()
        for (name in listOf("CompletionDirect", "CompletionBase", "CompletionTarget", "CompletionSingleton")) {
            val item = items.single { it.lookupString == name }
            assertKotlinTargetIcon(render(item).icon)
        }
        assertFalse(containsTarget(render(items.single { it.lookupString == "CompletionOrdinary" }).icon))
    }

    fun testUnimportedBaseAndPlatformCompletionUsesTargetIcons() {
        myFixture.addFileToProject(
            "other/Targets.kt", """
            package other
            class CompileTargetOther
            class JvmCompileTargetOther
        """.trimIndent()
        )
        myFixture.configureByText("Completion.kt", "fun use(value: CompileTar<caret>) {}")
        val items = myFixture.complete(CompletionType.BASIC, 2).orEmpty()
        assertKotlinTargetIcon(render(items.single { it.lookupString == "CompileTarget" }).icon)
        myFixture.configureByText("Completion.kt", "fun use(value: JvmCompileTar<caret>) {}")
        val platformItems = myFixture.complete(CompletionType.BASIC, 2).orEmpty()
        assertKotlinTargetIcon(render(platformItems.single { it.lookupString == "JvmCompileTarget" }).icon)
    }

    fun testExpressionCompletionPreservesInsertion() {
        myFixture.configureByText(
            "Completion.kt", """
            import dev.karmakrafts.kcml.api.target.*
            class CompletionTarget : JvmCompileTarget
            class CompletionOrdinary
            fun use() { Completion<caret> }
        """.trimIndent()
        )
        val items = myFixture.complete(CompletionType.BASIC).orEmpty()
        val target = items.single { it.lookupString == "CompletionTarget" }
        assertKotlinTargetIcon(render(target).icon)
        myFixture.lookup.currentItem = target
        myFixture.finishLookup('\n')
        myFixture.checkResult(
            """
            import dev.karmakrafts.kcml.api.target.*
            class CompletionTarget : JvmCompileTarget
            class CompletionOrdinary
            fun use() { CompletionTarget<caret> }
        """.trimIndent()
        )
    }

    fun testSmartCompletionUsesTargetIcons() {
        myFixture.configureByText(
            "Completion.kt", """
            import dev.karmakrafts.kcml.api.target.*
            interface CompletionDirect : CompileTarget
            class CompletionTarget : CompletionDirect
            class CompletionOther : CompletionDirect
            fun use(): CompletionDirect = Completion<caret>
        """.trimIndent()
        )
        val items = myFixture.complete(CompletionType.SMART).orEmpty()
        for (name in listOf("CompletionTarget", "CompletionOther")) {
            assertKotlinTargetIcon(render(items.single { it.lookupString == name }).icon)
        }
    }

    fun testJavaCompletionUsesTargetIcons() {
        myFixture.configureByText(
            "Completion.java", """
            import dev.karmakrafts.kcml.api.target.CompileTarget;
            interface CompletionDirect extends CompileTarget {}
            class CompletionBase implements CompletionDirect {}
            class CompletionTarget extends CompletionBase {}
            class CompletionOrdinary {}
            class Usage { Completion<caret> value; }
        """.trimIndent()
        )
        val items = myFixture.complete(CompletionType.BASIC).orEmpty()
        for (name in listOf("CompletionDirect", "CompletionBase", "CompletionTarget")) {
            assertTrue(name, containsTarget(render(items.single { it.lookupString == name }).icon))
        }
        assertFalse(containsTarget(render(items.single { it.lookupString == "CompletionOrdinary" }).icon))
    }

    private fun render(item: LookupElement): LookupElementPresentation {
        val presentation = LookupElementPresentation()
        item.renderElement(presentation)
        @Suppress("UNCHECKED_CAST") val renderer = item.expensiveRenderer as? LookupElementRenderer<LookupElement>
        ProgressManager.getInstance()
            .runProcess({ renderer?.renderElement(item, presentation) }, EmptyProgressIndicator())
        return presentation
    }

    private fun assertKotlinTargetIcon(icon: Icon?) {
        assertTrue("Expected layered Kotlin target icon, got ${icon?.javaClass?.name}: $icon", icon is LayeredIcon)
        val layered = icon as LayeredIcon
        assertSame(Icons.target, layered.getIcon(0))
        assertEquals(16, layered.iconWidth)
        assertEquals(16, layered.iconHeight)
        assertEquals(8, layered.getIcon(1)!!.iconWidth)
        assertEquals(8, layered.getIcon(1)!!.iconHeight)
    }

    private fun containsTarget(icon: Icon?): Boolean = icon == Icons.target || when (icon) {
        is DeferredIcon -> containsTarget(icon.evaluate())
        is CompositeIcon -> (0 until icon.iconCount).any { containsTarget(icon.getIcon(it)) }
        else -> false
    }

    private fun kotlinFile(text: String): KtFile = myFixture.configureByText(
        "Example.kt", "import dev.karmakrafts.kcml.api.target.*\n$text"
    ) as KtFile
}