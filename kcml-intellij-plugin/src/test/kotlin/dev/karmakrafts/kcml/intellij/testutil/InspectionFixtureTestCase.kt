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

package dev.karmakrafts.kcml.intellij.testutil

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture

abstract class InspectionFixtureTestCase(
    private vararg val inspections: LocalInspectionTool
) : AnnotationFixtureTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(*inspections)
    }

    protected fun checkKotlinHighlighting(text: String, preamble: String) {
        myFixture.configureByText("Example.kt", "$preamble\n$text")
        myFixture.checkHighlighting(true, false, false)
    }
}

internal inline fun <reified T : PsiElement> JavaCodeInsightTestFixture.collectProblems(
    inspection: LocalInspectionTool, isOnTheFly: Boolean = true
): List<ProblemDescriptor> {
    val holder = ProblemsHolder(InspectionManager.getInstance(project), file, isOnTheFly)
    val visitor = inspection.buildVisitor(holder, isOnTheFly)
    PsiTreeUtil.findChildrenOfType(file, T::class.java).forEach { it.accept(visitor) }
    return holder.results
}