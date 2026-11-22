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

package dev.karmakrafts.kcml.intellij.syntax

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import java.nio.file.Files
import java.nio.file.Path

@Service(Service.Level.PROJECT)
internal class SyntaxExtensionService( // @formatter:off
    private val project: Project
) : SimpleModificationTracker(), Disposable { // @formatter:on
    private data class Stamp( // @formatter:off
        val path: Path,
        val modified: Long,
        val size: Long
    ) // @formatter:on

    private data class Entry(
        val configuration: SyntaxConfiguration,
        val stamps: List<Stamp>,
        val runtime: SyntaxRuntime,
        val watches: Set<LocalFileSystem.WatchRequest>
    ) : AutoCloseable {
        override fun close() {
            try {
                runtime.close()
            }
            finally {
                LocalFileSystem.getInstance().removeWatchedRoots(watches)
            }
        }
    }

    private val sessions = HashMap<Module, Entry>()

    init {
        project.messageBus.connect(this).apply {
            subscribe(ModuleRootListener.TOPIC, object : ModuleRootListener {
                override fun rootsChanged(event: ModuleRootEvent) = refresh()
            })
            subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    val changed = events.map { event -> Path.of(event.path.substringBefore("!/")) }
                    val affected = synchronized(this@SyntaxExtensionService) {
                        sessions.values.any { entry ->
                            entry.configuration.paths.any { root ->
                                changed.any { path ->
                                    path.startsWith(root) || root.startsWith(path)
                                }
                            }
                        }
                    }
                    if (affected) refresh()
                }
            })
        }
    }

    fun runtime(module: Module): SyntaxRuntime? {
        val settings = KotlinFacet.get(module)?.configuration?.settings
        val arguments = settings?.mergedCompilerArguments ?: settings?.compilerArguments
        val configuration = SyntaxConfiguration.fromOptions(arguments?.pluginOptions?.toList().orEmpty(), module.name)
        return runtime(module, configuration)
    }

    @Synchronized
    internal fun runtime(module: Module, configuration: SyntaxConfiguration?): SyntaxRuntime? {
        if (module.isDisposed || project.isDisposed) return null
        if (configuration == null) {
            // Drop the session when KCML is removed from the module configuration.
            removeSession(module)
            return null
        }
        // Reuse the session only while both configuration and plugin files are unchanged.
        val stamps = collectStamps(configuration)
        val old = sessions[module]
        if (old?.configuration == configuration && old.stamps == stamps) return old.runtime
        // Invalidate cached diagnostics before constructing the replacement session.
        sessions.remove(module)?.close()
        incModificationCount()
        val entry = createEntry(configuration, stamps)
        sessions[module] = entry
        // Refresh plugin files asynchronously so subsequent VFS changes trigger reloads.
        refreshPluginFiles(configuration)
        return entry.runtime
    }

    private fun removeSession(module: Module) {
        val removed = sessions.remove(module) ?: return
        removed.close()
        incModificationCount()
    }

    private fun collectStamps(configuration: SyntaxConfiguration): List<Stamp> = configuration.paths.map { path ->
        if (Files.exists(path)) Stamp(path, Files.getLastModifiedTime(path).toMillis(), Files.size(path))
        else Stamp(path, -1, -1)
    }

    private fun createEntry(configuration: SyntaxConfiguration, stamps: List<Stamp>): Entry {
        val runtime = SyntaxRuntime(
            configuration.compilerConfiguration(),
            configuration.paths.map { path -> path.toUri().toURL() },
            configuration.options
        )
        val localFiles = LocalFileSystem.getInstance()
        val roots =
            configuration.paths.map { path -> if (Files.isDirectory(path)) path else path.parent }.map(Path::toString)
        val watches = localFiles.addRootsToWatch(roots, true)
        return Entry(configuration, stamps, runtime, watches)
    }

    private fun refreshPluginFiles(configuration: SyntaxConfiguration) {
        val localFiles = LocalFileSystem.getInstance()
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) configuration.paths.forEach(localFiles::refreshAndFindFileByNioFile)
        }
    }

    fun refresh() {
        synchronized(this) {
            sessions.values.forEach(Entry::close)
            sessions.clear()
            incModificationCount()
        }
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) DaemonCodeAnalyzer.getInstance(project).restart(this)
        }
    }

    @Synchronized
    override fun dispose() {
        sessions.values.forEach(Entry::close)
        sessions.clear()
    }
}