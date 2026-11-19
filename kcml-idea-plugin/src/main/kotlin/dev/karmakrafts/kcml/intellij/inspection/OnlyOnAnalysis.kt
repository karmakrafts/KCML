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

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.util.Key
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.kotlin.psi.KtFile
import java.util.concurrent.atomic.AtomicBoolean

internal object OnlyOnAnalysis {
    class State<T : Any> {
        @Volatile
        var result: T? = null
            private set
        val scheduled = AtomicBoolean()

        @Synchronized
        fun compute(action: () -> T): T {
            val cached = result
            if (cached != null) return cached
            val computed = action()
            result = computed
            return computed
        }
    }

    fun <T : Any> key(name: String): Key<CachedValue<State<T>>> = Key.create(name)

    private fun <T : Any> state(file: KtFile, key: Key<CachedValue<State<T>>>): State<T> =
        CachedValuesManager.getManager(file.project).getCachedValue(file, key, {
            CachedValueProvider.Result.create(
                State<T>(),
                PsiModificationTracker.MODIFICATION_COUNT,
                ProjectRootModificationTracker.getInstance(file.project),
                DumbService.getInstance(file.project).modificationTracker
            )
        }, false)

    fun <T : Any> lookup(file: KtFile, key: Key<CachedValue<State<T>>>, compute: () -> T): T? {
        val project = file.project
        if (project.isDisposed || !file.isValid) return null
        val state = state(file, key)
        val cached = state.result
        if (cached != null) return cached
        if (!ApplicationManager.getApplication().isDispatchThread && !DumbService.isDumb(project)) {
            return state.compute(compute)
        }
        if (state.scheduled.compareAndSet(false, true)) {
            ReadAction.nonBlocking<T> { state.compute(compute) }
                .inSmartMode(project)
                .expireWith(project)
                .expireWhen { !file.isValid }
                .finishOnUiThread(ModalityState.any()) {
                    if (file.isValid && state(file, key) === state) {
                        DaemonCodeAnalyzer.getInstance(project).restart(file, this)
                    }
                }
                .submit(AppExecutorUtil.getAppExecutorService())
                .onProcessed { state.scheduled.set(false) }
        }
        return null
    }
}