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

import dev.karmakrafts.kcml.intellij.testutil.AnnotationFixtureTestCase
import dev.karmakrafts.kcml.intellij.testutil.withAnnotationLibrary

class MixinConstantTableUsageInspectionTest : AnnotationFixtureTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(MixinConstantTableUsageInspection())
    }

    fun testOutsideMixins() = check(
        """
            val topLevel = <warning descr="ConstantTable must only be used in mixin classes">ConstantTable.getInt("value")</warning>
            fun topLevelFunction() = <warning descr="ConstantTable must only be used in mixin classes">ConstantTable.getString("value")</warning>
            class Ordinary {
                val value = <warning descr="ConstantTable must only be used in mixin classes">ConstantTable.getInt("value")</warning>
            }
            object Singleton {
                fun value() = <warning descr="ConstantTable must only be used in mixin classes">ConstantTable.getInt("value")</warning>
            }
        """.trimIndent()
    )

    fun testCallHighlightRanges() {
        myFixture.withAnnotationLibrary("java.io.Serializable", "java.lang.Object") {
            check(
                """
                    import dev.karmakrafts.kcml.api.mixin.ConstantTable.getInt as readInt
                    fun key(): String = "value"
                    val direct = <warning descr="ConstantTable must only be used in mixin classes">ConstantTable.getInt("value")</warning>.toString()
                    val qualified = dev.karmakrafts.kcml.api.mixin.<warning descr="ConstantTable must only be used in mixin classes">ConstantTable.getString(name = "value")</warning>.length
                    val imported = <warning descr="ConstantTable must only be used in mixin classes">readInt(key())</warning>
                    val nested = <warning descr="ConstantTable must only be used in mixin classes">ConstantTable.getString(<warning descr="ConstantTable must only be used in mixin classes">ConstantTable.getString("key")</warning>)</warning>
                    val table = <warning descr="ConstantTable must only be used in mixin classes">ConstantTable</warning>
                    val instance = table.<warning descr="ConstantTable must only be used in mixin classes">getInt("value")</warning>
                """.trimIndent()
            )
        }
    }

    fun testDirectAndIndirectMixins() = check(
        """
            import dev.karmakrafts.kcml.api.mixin.DirectMixin as Mixin
            @Mixin(Any::class) class Direct {
                val value = ConstantTable.getInt("value")
                fun value() = { ConstantTable.getString("value") }
            }
            @IndirectMixin("Target") class Indirect {
                fun value() = ConstantTable.getInt("value")
            }
        """.trimIndent()
    )

    fun testAliasesAndQualifiedReferences() = check(
        """
            import dev.karmakrafts.kcml.api.mixin.ConstantTable as Constants
            typealias Table = <warning descr="ConstantTable must only be used in mixin classes">Constants</warning>
            class Ordinary {
                val first = <warning descr="ConstantTable must only be used in mixin classes">Constants.getInt("value")</warning>
                val second = dev.karmakrafts.kcml.api.mixin.<warning descr="ConstantTable must only be used in mixin classes">ConstantTable.getInt("value")</warning>
                val third = <warning descr="ConstantTable must only be used in mixin classes">Table.getInt("value")</warning>
            }
        """.trimIndent()
    )

    fun testImportedAccessorsAndCallableReferences() = check(
        """
            import dev.karmakrafts.kcml.api.mixin.ConstantTable.getInt
            import dev.karmakrafts.kcml.api.mixin.ConstantTable.getString as readString
            fun value() = <warning descr="ConstantTable must only be used in mixin classes">getInt("value")</warning>
            val string = <warning descr="ConstantTable must only be used in mixin classes">readString("value")</warning>
            val reader = <warning descr="ConstantTable must only be used in mixin classes">ConstantTable</warning>::getInt
            @DirectMixin(Any::class) class Mixin {
                fun value() = getInt("value")
                val reader = ConstantTable::getString
            }
        """.trimIndent()
    )

    fun testNestedAndLocalClasses() = check(
        """
            @DirectMixin(Any::class) class Mixin {
                class Nested {
                    val value = <warning descr="ConstantTable must only be used in mixin classes">ConstantTable.getInt("value")</warning>
                }
                fun example() {
                    fun localFunction() = ConstantTable.getInt("value")
                    class Local {
                        val value = <warning descr="ConstantTable must only be used in mixin classes">ConstantTable.getInt("value")</warning>
                    }
                    val anonymous = object {
                        val value = <warning descr="ConstantTable must only be used in mixin classes">ConstantTable.getInt("value")</warning>
                    }
                }
            }
            class Outer {
                @IndirectMixin("Target") class NestedMixin {
                    val value = ConstantTable.getInt("value")
                }
            }
        """.trimIndent()
    )

    fun testUnrelatedSymbolsAndAnnotations() = check(
        """
            object ConstantTable { fun getInt(name: String): Int = 0 }
            annotation class DirectMixin(val target: String)
            class Ordinary { val value = ConstantTable.getInt("value") }
            @DirectMixin("Target") class NotAMixin {
                val value = dev.karmakrafts.kcml.api.mixin.<warning descr="ConstantTable must only be used in mixin classes">ConstantTable.getInt("value")</warning>
            }
            fun unresolved() = <error descr="[UNRESOLVED_REFERENCE] Unresolved reference 'MissingTable'.">MissingTable</error>.getInt("value")
        """.trimIndent()
    )

    fun testSuppression() = check(
        """
            @Suppress("KCML_MIXIN_CONSTANT_TABLE_USAGE")
            class Suppressed { val value = ConstantTable.getInt("value") }
        """.trimIndent()
    )

    fun testObjectAndInstanceReferences() = check(
        """
            val table = <warning descr="ConstantTable must only be used in mixin classes">ConstantTable</warning>
            val value = table.<warning descr="ConstantTable must only be used in mixin classes">getInt("value")</warning>
            val reader = table::<warning descr="ConstantTable must only be used in mixin classes">getString</warning>
            @DirectMixin(Any::class) class Mixin {
                val table = ConstantTable
                val value = table.getInt("value")
            }
        """.trimIndent()
    )

    fun testCompiledApi() {
        myFixture.withAnnotationLibrary(
            "dev.karmakrafts.kcml.api.mixin.ConstantTable",
            "dev.karmakrafts.kcml.api.mixin.DirectMixin",
            "dev.karmakrafts.kcml.api.mixin.IndirectMixin"
        ) {
            check(
                """
                    import dev.karmakrafts.kcml.api.mixin.ConstantTable.getInt
                    class Ordinary {
                        val value = <warning descr="ConstantTable must only be used in mixin classes">ConstantTable.getInt("value")</warning>
                        val imported = <warning descr="ConstantTable must only be used in mixin classes">getInt("value")</warning>
                        val string = <warning descr="ConstantTable must only be used in mixin classes">ConstantTable.getString("value")</warning>
                        val byte = <warning descr="ConstantTable must only be used in mixin classes">ConstantTable.getByte("value")</warning>
                        val short = <warning descr="ConstantTable must only be used in mixin classes">ConstantTable.getShort("value")</warning>
                        val long = <warning descr="ConstantTable must only be used in mixin classes">ConstantTable.getLong("value")</warning>
                        val float = <warning descr="ConstantTable must only be used in mixin classes">ConstantTable.getFloat("value")</warning>
                        val double = <warning descr="ConstantTable must only be used in mixin classes">ConstantTable.getDouble("value")</warning>
                        val boolean = <warning descr="ConstantTable must only be used in mixin classes">ConstantTable.getBoolean("value")</warning>
                    }
                    @DirectMixin(Any::class) class Direct { val value = ConstantTable.getInt("value") }
                    @IndirectMixin("Target") class Indirect { val value = getInt("value") }
                """.trimIndent(), false
            )
        }
    }

    private fun check(text: String, sourceApi: Boolean = true) {
        if (sourceApi) {
            addAnnotationSource(
                "dev/karmakrafts/kcml/api/mixin/Mixins.kt", """
                    package dev.karmakrafts.kcml.api.mixin
                    object ConstantTable {
                        fun getInt(name: String): Int = 0
                        fun getString(name: String): String = ""
                    }
                    annotation class DirectMixin(val target: kotlin.reflect.KClass<*>)
                    annotation class IndirectMixin(val target: String)
                """.trimIndent()
            )
        }
        myFixture.configureByText("Example.kt", "package sample\nimport dev.karmakrafts.kcml.api.mixin.*\n$text")
        myFixture.checkHighlighting(true, false, false)
    }
}