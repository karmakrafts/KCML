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

package dev.karmakrafts.kcml.intellij.syntax.runtime

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ex.ToolsImpl
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.TextRange
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import dev.karmakrafts.kcml.api.ExperimentalKcmlApi
import dev.karmakrafts.kcml.api.extension.syntax.InspectionMode
import dev.karmakrafts.kcml.api.extension.syntax.InspectionRule
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory

@OptIn(ExperimentalKcmlApi::class)
class SyntaxInspectionsTest : LightJavaCodeInsightFixtureTestCase() {
    private val savedStates = ArrayList<Triple<ToolsImpl, Boolean, Boolean>>()
    private var savedLevel: HighlightDisplayLevel? = null
    private val inspectionId = "RedundantSemicolon"
    private val profile get() = InspectionProjectProfileManager.getInstance(project).currentProfile

    override fun setUp() {
        super.setUp()
        profile.getInspectionTools(null)
        for (tools in profile.tools) {
            savedStates.add(Triple(tools, tools.isEnabled, tools.defaultState.isEnabled))
            tools.isEnabled = false
        }
        val inspection = Class.forName(
            "org.jetbrains.kotlin.idea.codeInsight.inspections.RedundantSemicolonInspection",
            true,
            KotlinLanguage.INSTANCE.javaClass.classLoader
        ).getDeclaredConstructor().newInstance() as LocalInspectionTool
        myFixture.enableInspections(inspection)
        savedLevel = profile.getErrorLevel(HighlightDisplayKey.find(inspectionId)!!, null)
        profile.setErrorLevel(HighlightDisplayKey.find(inspectionId)!!, HighlightDisplayLevel.WARNING, project)
    }

    override fun tearDown() {
        try {
            savedLevel?.let { profile.setErrorLevel(HighlightDisplayKey.find(inspectionId)!!, it, project) }
            for ([tools, enabled, defaultEnabled] in savedStates) {
                tools.isEnabled = enabled
                tools.defaultState.isEnabled = defaultEnabled
            }
            savedStates.clear()
        }
        finally {
            super.tearDown()
        }
    }

    fun testAllLoweredSubnodeReportsReachTheWholeSourceToken() {
        val source = "fun example() {\n    §\n}"
        val token = tokenRange(source)
        (val problems, val toolIds) = collect(
            source, source.replace("§", "val first = 1;\n    val second = 2;"), listOf(rule(source, token))
        )
        assertEquals(setOf(inspectionId), toolIds)
        assertEquals(2, problems.size)
        assertEquals(listOf(token, token), problems.map { it.range })
        assertEquals(1, problems.map { it.message }.distinct().size)
        assertTrue(problems.all { it.severity == HighlightSeverity.WARNING && it.message.isNotBlank() })
    }

    fun testSuppressRuleKeepsUnrelatedInspections() {
        val source = "fun example() {\n    §\n    val outside = 3;\n}"
        val result = collect(
            source, source.replace("§", "val first = 1;\n    val second = 2;"), listOf(
                rule(source, tokenRange(source), InspectionMode.SUPPRESS)
            )
        )
        assertEquals(1, result.problems.size)
        assertEquals(TextRange(source.lastIndexOf(';'), source.lastIndexOf(';') + 1), result.problems.single().range)
    }

    fun testFirstOverlappingRuleWins() {
        val source = "fun example() {\n    §\n}"
        val lowered = source.replace("§", "val first = 1;")
        val token = tokenRange(source)
        val forward = rule(source, token)
        val suppress = rule(source, TextRange(0, source.length), InspectionMode.SUPPRESS)
        assertEquals(listOf(token), collect(source, lowered, listOf(forward, suppress)).problems.map { it.range })
        assertEmpty(collect(source, lowered, listOf(suppress, forward)).problems)
    }

    fun testOrdinaryProblemsRetainTheirMappedSourceRange() {
        val source = "fn example() {\n    val value = 1;\n}"
        val result = collect(source, source.replaceFirst("fn", "fun"))
        assertEquals(1, result.problems.size)
        val offset = source.indexOf(';')
        assertEquals(TextRange(offset, offset + 1), result.problems.single().range)
    }

    fun testActiveProfileSeverityAndDisabledStateAreRespected() {
        val source = "fun example() {\n    val value = 1;\n}"
        profile.setErrorLevel(HighlightDisplayKey.find(inspectionId)!!, HighlightDisplayLevel.ERROR, project)
        assertEquals(HighlightSeverity.ERROR, collect(source, source).problems.single().severity)
        profile.getTools(inspectionId, project).isEnabled = false
        (val problems, val toolIds) = collect(source, source)
        assertEmpty(problems)
        assertEmpty(toolIds)
    }

    fun testKotlinInspectionSuppressionIsPreserved() {
        val source = "@Suppress(\"RedundantSemicolon\")\nfun example() {\n    val value = 1;\n}"
        (val problems, val toolIds) = collect(source, source)
        assertContainsElements(toolIds, inspectionId)
        assertEmpty(problems)
    }

    private fun tokenRange(source: String): TextRange = source.indexOf('§').let { TextRange(it, it + 1) }

    private fun rule(
        source: String, range: TextRange, mode: InspectionMode = InspectionMode.DESUGARED
    ): InspectionRule = InspectionRule(KtPsiFactory(project).createFile("Inspections.kt", source), mode, range)

    private fun collect(
        sourceText: String, loweredText: String, rules: List<InspectionRule> = emptyList()
    ): SyntaxInspections.Result {
        val source = myFixture.configureByText("Inspections.kt", sourceText) as KtFile
        return runReadActionBlocking {
            val lowered = KtPsiFactory(project).createFile(source.name, loweredText)
            lowered.originalFile = source
            var result: SyntaxInspections.Result? = null
            ProgressManager.getInstance().executeProcessUnderProgress({
                result = SyntaxInspections.collect(source, lowered, SyntaxOffsetMap(sourceText, loweredText), rules)
            }, EmptyProgressIndicator())
            result!!
        }
    }
}