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

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.TaskInfo
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.ex.StatusBarEx

@Service(Service.Level.PROJECT)
internal class KcmlInitializationProgress(private val project: Project) : Disposable {
    private val task = object : TaskInfo {
        override fun getTitle(): String = "KCML initialization"
        override fun getCancelText(): String = ""
        override fun getCancelTooltipText(): String = ""
        override fun isCancellable(): Boolean = false
    }

    internal class Activity(
        private val owner: KcmlInitializationProgress,
        val indicator: ProgressIndicator
    ) : AutoCloseable {
        override fun close() = owner.finish(this)
    }

    private val activities = LinkedHashMap<Activity, String>()
    private var indicator: ProgressIndicatorBase? = null
    private var disposed = false

    @Synchronized
    fun begin(text: String): Activity? {
        if (disposed || project.isDisposed) return null
        val progress = indicator ?: ProgressIndicatorBase().also { progress ->
            progress.isIndeterminate = true
            progress.start()
            indicator = progress
            ApplicationManager.getApplication().invokeLater({
                synchronized(this) {
                    if (!disposed && !project.isDisposed && indicator === progress) {
                        (WindowManager.getInstance().getStatusBar(project) as? StatusBarEx)?.addProgress(progress, task)
                    }
                }
            }, ModalityState.any())
        }
        return Activity(this, progress).also {
            activities[it] = text
            progress.text = text
        }
    }

    @Synchronized
    private fun finish(activity: Activity) {
        if (activities.remove(activity) == null) return
        if (activities.isEmpty()) stop()
        else indicator?.text = activities.values.last()
    }

    private fun stop() {
        indicator?.let {
            it.stop()
            it.finish(task)
        }
        indicator = null
    }

    @Synchronized
    override fun dispose() {
        disposed = true
        activities.clear()
        stop()
    }
}