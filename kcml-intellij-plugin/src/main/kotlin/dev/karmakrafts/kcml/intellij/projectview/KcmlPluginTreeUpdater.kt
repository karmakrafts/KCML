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

package dev.karmakrafts.kcml.intellij.projectview

import com.intellij.facet.Facet
import com.intellij.facet.FacetManager
import com.intellij.facet.FacetManagerListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList

@Service(Service.Level.PROJECT)
internal class KcmlPluginTreeUpdater(private val project: Project) : Disposable {
    private val updates: MergingUpdateQueue = MergingUpdateQueue("KCML plugin tree", 200, true, null, this)
    private val listeners: CopyOnWriteArrayList<() -> Unit> = CopyOnWriteArrayList()

    @Volatile
    private var paths: Set<Path> = emptySet()
    private var watches: Set<LocalFileSystem.WatchRequest> = emptySet()

    init {
        project.messageBus.connect(this).apply {
            subscribe(ModuleRootListener.TOPIC, object : ModuleRootListener {
                override fun rootsChanged(event: ModuleRootEvent) = refresh()
            })
            subscribe(FacetManager.FACETS_TOPIC, object : FacetManagerListener {
                override fun facetConfigurationChanged(facet: Facet<*>) = refresh()
                override fun facetAdded(facet: Facet<*>) = refresh()
                override fun facetRemoved(facet: Facet<*>) = refresh()
            })
            subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    if (events.any { event ->
                            val changed = Path.of(event.path.substringBefore("!/"))
                            paths.any { it.startsWith(changed) || changed.startsWith(it) }
                        }) refresh()
                }
            })
        }
    }

    fun addListener(parentDisposable: Disposable, listener: () -> Unit) {
        listeners.add(listener)
        Disposer.register(parentDisposable) { listeners.remove(listener) }
    }

    @Synchronized
    fun watch(paths: Set<Path>) {
        if (this.paths == paths || project.isDisposed) return
        this.paths = paths
        val localFiles = LocalFileSystem.getInstance()
        localFiles.removeWatchedRoots(watches)
        val roots = paths.mapNotNull { path ->
            generateSequence(
                if (Files.isDirectory(path)) path else path.parent, Path::getParent
            ).firstOrNull(Files::exists)?.toString()
        }.distinct()
        watches = localFiles.addRootsToWatch(roots, true)
    }

    private fun refresh() {
        updates.queue(Update.create("refresh") {
            if (!project.isDisposed) listeners.forEach { it() }
        })
    }

    @Synchronized
    override fun dispose() {
        LocalFileSystem.getInstance().removeWatchedRoots(watches)
        watches = emptySet()
        paths = emptySet()
        listeners.clear()
    }
}