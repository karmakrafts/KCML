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

package dev.karmakrafts.kcml.intellij.icon

import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.ui.IconDeferrer
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class PluginConfigIconUpdater(private val project: Project) : Disposable {
    private val files = ConcurrentHashMap.newKeySet<VirtualFile>()
    private val queue = MergingUpdateQueue("KCML plugin config icons", 300, true, null, this).setRestartTimerOnAdd(true)

    init {
        val connection = project.messageBus.connect(this)
        connection.subscribe(PsiModificationTracker.TOPIC, PsiModificationTracker.Listener { scheduleRefresh() })
        connection.subscribe(DumbService.DUMB_MODE, object : DumbService.DumbModeListener {
            override fun exitDumbMode() = scheduleRefresh()
        })
        connection.subscribe(ModuleRootListener.TOPIC, object : ModuleRootListener {
            override fun rootsChanged(event: ModuleRootEvent) = scheduleRefresh()
        })
    }

    fun track(file: VirtualFile) {
        files += file
    }

    private fun scheduleRefresh() {
        if (files.isEmpty()) return
        queue.queue(Update.create(this) {
            if (project.isDisposed || DumbService.isDumb(project)) return@create
            files.removeIf { !it.isValid }
            if (files.isEmpty()) return@create
            IconDeferrer.getInstance().clearCache()
            ProjectView.getInstance(project).refresh()
            val editors = FileEditorManager.getInstance(project)
            files.filter { editors.isFileOpen(it) }.forEach { editors.updateFilePresentation(it) }
        })
    }

    override fun dispose() {
        files.clear()
    }
}