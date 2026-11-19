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
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.kotlin.psi.KtFile
import java.util.concurrent.atomic.AtomicInteger

class OnlyOnAnalysisTest : LightJavaCodeInsightFixtureTestCase() {
    fun testComputesOnceOffEdtAndReusesResult() {
        val file = myFixture.configureByText("Example.kt", "fun use() = Unit") as KtFile
        val key = OnlyOnAnalysis.key<Any>("kcml.onlyOn.test.cached")
        val calls = AtomicInteger()
        val expected = Any()
        fun lookup() = OnlyOnAnalysis.lookup(file, key) {
            assertFalse(ApplicationManager.getApplication().isDispatchThread)
            calls.incrementAndGet()
            expected
        }
        assertNull(lookup())
        repeat(20) { lookup() }
        PlatformTestUtil.waitWithEventsDispatching("OnlyOn cached result", { lookup() != null }, 10)
        repeat(20) { assertSame(expected, lookup()) }
        assertEquals(1, calls.get())
    }

    fun testInvalidatesAfterDependencyEditAndRootChange() {
        val dependency = myFixture.addFileToProject("Dependency.kt", "class Dependency")
        val file = myFixture.configureByText("Example.kt", "fun use() = Unit") as KtFile
        val key = OnlyOnAnalysis.key<Int>("kcml.onlyOn.test.dependencies")
        val calls = AtomicInteger()
        fun lookup() = OnlyOnAnalysis.lookup(file, key) {
            assertFalse(ApplicationManager.getApplication().isDispatchThread)
            calls.incrementAndGet()
        }
        assertNull(lookup())
        PlatformTestUtil.waitWithEventsDispatching("OnlyOn initial analysis", { lookup() == 1 }, 10)
        WriteCommandAction.runWriteCommandAction(project) {
            val documents = PsiDocumentManager.getInstance(project)
            val document = documents.getDocument(dependency)!!
            document.text = "class UpdatedDependency"
            documents.commitDocument(document)
        }
        assertNull(lookup())
        PlatformTestUtil.waitWithEventsDispatching("OnlyOn dependency invalidation", { lookup() == 2 }, 10)
        PsiTestUtil.addSourceRoot(module, myFixture.tempDirFixture.findOrCreateDir("extraSources"))
        assertNull(lookup())
        PlatformTestUtil.waitWithEventsDispatching("OnlyOn root invalidation", { lookup() == 3 }, 10)
        assertEquals(3, calls.get())
    }
}