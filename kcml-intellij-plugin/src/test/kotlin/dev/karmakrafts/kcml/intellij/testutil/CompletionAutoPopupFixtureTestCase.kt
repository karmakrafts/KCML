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

import com.intellij.testFramework.fixtures.CompletionAutoPopupTester
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.testFramework.runInEdtAndWait

abstract class CompletionAutoPopupFixtureTestCase(private vararg val annotationClasses: String) : LightJavaCodeInsightFixtureTestCase() {
    private lateinit var library: AnnotationLibrary

    override fun runInDispatchThread(): Boolean = false

    override fun setUp() {
        super.setUp()
        library = AnnotationLibrary(*annotationClasses)
        runInEdtAndWait {
            library.attachTo(myFixture.module)
            configureAutoPopupProject()
        }
    }

    protected abstract fun configureAutoPopupProject()

    override fun tearDown() {
        try {
            super.tearDown()
        }
        finally {
            if (::library.isInitialized) library.close()
        }
    }

    protected fun withAutoPopup(action: (CompletionAutoPopupTester) -> Unit) {
        val tester = CompletionAutoPopupTester(myFixture)
        tester.runWithAutoPopupEnabled { action(tester) }
    }
}