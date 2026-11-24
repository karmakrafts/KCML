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

import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class KcmlInitializationProgressTest : BasePlatformTestCase() {
    private lateinit var progress: KcmlInitializationProgress

    override fun setUp() {
        super.setUp()
        progress = KcmlInitializationProgress(project)
        Disposer.register(testRootDisposable, progress)
    }

    fun testOverlappingOperationsShareIndeterminateProgress() {
        val indexing = progress.begin("Indexing KCML plugins")!!
        val loading = progress.begin("Loading KCML plugins")!!
        val indicator = indexing.indicator
        assertSame(indicator, loading.indicator)
        assertTrue(indicator.isRunning)
        assertTrue(indicator.isIndeterminate)
        assertEquals("Loading KCML plugins", indicator.text)
        loading.close()
        assertTrue(indicator.isRunning)
        assertEquals("Indexing KCML plugins", indicator.text)
        loading.close()
        assertTrue(indicator.isRunning)
        indexing.close()
        assertFalse(indicator.isRunning)
    }

    fun testNewWorkAfterCompletionGetsFreshProgress() {
        val first = progress.begin("Discovering KCML plugins")!!
        first.close()
        val second = progress.begin("Loading KCML plugins")!!
        assertNotSame(first.indicator, second.indicator)
        first.close()
        assertTrue(second.indicator.isRunning)
        second.close()
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        assertFalse(second.indicator.isRunning)
    }

    fun testDisposalStopsPendingWorkAndRejectsNewWork() {
        val activity = progress.begin("Loading KCML plugins")!!
        Disposer.dispose(progress)
        assertFalse(activity.indicator.isRunning)
        assertNull(progress.begin("Indexing KCML plugins"))
        activity.close()
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        assertFalse(activity.indicator.isRunning)
    }

    fun testFailedOperationClosesProgress() {
        val activity = progress.begin("Loading KCML plugins")!!
        try {
            activity.use { error("Plugin initialization failed") }
        } catch (_: IllegalStateException) {
        }
        assertFalse(activity.indicator.isRunning)
    }
}