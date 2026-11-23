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

import dev.karmakrafts.kcml.intellij.testutil.InspectionFixtureTestCase
import dev.karmakrafts.kcml.intellij.testutil.withAnnotationLibrary

class PluginDeclarationInspectionTest : InspectionFixtureTestCase(PluginDeclarationInspection()) {
    override fun setUp() {
        super.setUp()
        addAnnotationSource(
            "dev/karmakrafts/kcml/api/plugin/Plugin.kt", """
                package dev.karmakrafts.kcml.api.plugin
                annotation class Plugin(val id: String)
            """.trimIndent()
        )
    }

    fun testAnnotatedClassMustImplementCompilerPlugin() = check(
        """@Plugin("example") class <warning descr="Classes annotated with @Plugin must implement CompilerPlugin">Example</warning>"""
    )

    fun testImplementationMustHaveAnnotation() = check(
        """class <warning descr="CompilerPlugin implementations must be annotated with @Plugin">Example</warning> : CompilerPlugin"""
    )

    fun testValidPluginAndUnrelatedClass() = check(
        """
            @Plugin("example") class Example : CompilerPlugin
            class Unrelated
        """.trimIndent()
    )

    fun testObjects() = check(
        """
            @Plugin("valid") object Valid : CompilerPlugin
            @Plugin("invalid") object <warning descr="Classes annotated with @Plugin must implement CompilerPlugin">Invalid</warning>
            object <warning descr="CompilerPlugin implementations must be annotated with @Plugin">Missing</warning> : CompilerPlugin
        """.trimIndent()
    )

    fun testAliasedImports() = check(
        """
            import dev.karmakrafts.kcml.api.plugin.Plugin as EntryPoint
            import dev.karmakrafts.kcml.api.plugin.CompilerPlugin as PluginInterface
            @EntryPoint("valid") class Valid : PluginInterface
            @EntryPoint("invalid") class <warning descr="Classes annotated with @Plugin must implement CompilerPlugin">Invalid</warning>
            class <warning descr="CompilerPlugin implementations must be annotated with @Plugin">Missing</warning> : PluginInterface
        """.trimIndent()
    )

    fun testFullyQualifiedNamesAndNestedClass() = check(
        """
            class Outer {
                @dev.karmakrafts.kcml.api.plugin.Plugin("valid")
                class Valid : dev.karmakrafts.kcml.api.plugin.CompilerPlugin
                class <warning descr="CompilerPlugin implementations must be annotated with @Plugin">Missing</warning> : dev.karmakrafts.kcml.api.plugin.CompilerPlugin
            }
        """.trimIndent()
    )

    fun testInheritedImplementationDoesNotInheritAnnotation() = check(
        """
            @Plugin("base") open class Base : CompilerPlugin
            @Plugin("valid") class Valid : Base()
            class <warning descr="CompilerPlugin implementations must be annotated with @Plugin">Missing</warning> : Base()
        """.trimIndent()
    )

    fun testSubinterfaceIsNotAPluginClass() = check(
        """
            interface Extension : CompilerPlugin
            @Plugin("valid") class Valid : Extension
            abstract class <warning descr="CompilerPlugin implementations must be annotated with @Plugin">Missing</warning> : Extension
        """.trimIndent()
    )

    fun testTypeAlias() = check(
        """
            typealias Extension = CompilerPlugin
            @Plugin("valid") class Valid : Extension
            class <warning descr="CompilerPlugin implementations must be annotated with @Plugin">Missing</warning> : Extension
        """.trimIndent()
    )

    fun testUnrelatedNamesAreIgnored() = check(
        """
            annotation class Plugin(val id: String)
            interface CompilerPlugin
            @Plugin("example") class Example
            class Other : CompilerPlugin
        """.trimIndent()
    )

    fun testUnrelatedInterfaceDoesNotSatisfyContract() = check(
        """
            interface CompilerPlugin
            @dev.karmakrafts.kcml.api.plugin.Plugin("example")
            class <warning descr="Classes annotated with @Plugin must implement CompilerPlugin">Example</warning> : CompilerPlugin
        """.trimIndent()
    )

    fun testUnrelatedAnnotationDoesNotSatisfyContract() = check(
        """
            annotation class Plugin(val id: String)
            @Plugin("example")
            class <warning descr="CompilerPlugin implementations must be annotated with @Plugin">Example</warning> : dev.karmakrafts.kcml.api.plugin.CompilerPlugin
        """.trimIndent()
    )

    fun testUnresolvedTypesAreIgnored() = check(
        """
            @<error descr="[UNRESOLVED_REFERENCE]">missing</error>.Plugin("example") class Example
            class Other : <error descr="[UNRESOLVED_REFERENCE]">MissingInterface</error>
        """.trimIndent()
    )

    fun testCompiledInterface() {
        myFixture.withAnnotationLibrary("dev.karmakrafts.kcml.api.plugin.CompilerPlugin") {
            check(
                """
                    @Plugin("valid") abstract class Valid : CompilerPlugin
                    abstract class <warning descr="CompilerPlugin implementations must be annotated with @Plugin">Missing</warning> : CompilerPlugin
                """.trimIndent(), false
            )
        }
    }

    private fun check(text: String, sourceInterface: Boolean = true) {
        if (sourceInterface) {
            addAnnotationSource(
                "dev/karmakrafts/kcml/api/plugin/CompilerPlugin.kt", """
                    package dev.karmakrafts.kcml.api.plugin
                    interface CompilerPlugin
                """.trimIndent()
            )
        }
        checkKotlinHighlighting(text, "import dev.karmakrafts.kcml.api.plugin.*")
    }
}