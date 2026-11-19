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

package dev.karmakrafts.kcml.intellij.util

import com.intellij.psi.util.PsiTreeUtil
import dev.karmakrafts.kcml.intellij.testutil.AnnotationFixtureTestCase
import org.jetbrains.kotlin.psi.KtAnnotationEntry

class PsiUtilsTest : AnnotationFixtureTestCase() {
    fun testAnnotationNamesResolveThroughImportAndTypeAliases() {
        addAnnotationSource("api/Marker.kt", "package api\nannotation class Marker")
        myFixture.configureByText(
            "Example.kt", """
            package sample
            import api.Marker as Imported
            typealias Alias = Imported
            typealias Chain = Alias
            @Imported class First
            @Alias class Second
            @Chain class Third
            @api.Marker class Fourth
        """.trimIndent()
        )
        val annotations = PsiTreeUtil.findChildrenOfType(myFixture.file, KtAnnotationEntry::class.java)
        assertEquals(4, annotations.size)
        annotations.forEach { assertEquals("api.Marker", it.qualifiedName()) }
    }

    fun testJavaAnnotationName() {
        myFixture.addClass("package api; public @interface Marker {}")
        myFixture.configureByText("Example.kt", "@api.Marker class Example")
        val annotation = PsiTreeUtil.findChildOfType(myFixture.file, KtAnnotationEntry::class.java)!!
        assertEquals("api.Marker", annotation.qualifiedName())
    }

    fun testUnresolvedAndCyclicAliasesHaveNoAnnotationName() {
        myFixture.configureByText(
            "Example.kt", """
            typealias First = Second
            typealias Second = First
            @First class Example
            @Missing class Other
        """.trimIndent()
        )
        val annotations = PsiTreeUtil.findChildrenOfType(myFixture.file, KtAnnotationEntry::class.java)
        assertEquals(2, annotations.size)
        annotations.forEach { assertNull(it.qualifiedName()) }
    }
}