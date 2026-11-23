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
import com.intellij.facet.Facet
import com.intellij.facet.FacetManager
import com.intellij.facet.FacetManagerListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.concurrency.AppExecutorUtil
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean

@Service(Service.Level.PROJECT)
internal class SyntaxExtensionService( // @formatter:off
    private val project: Project
) : SimpleModificationTracker(), Disposable { // @formatter:on
    private data class Stamp( // @formatter:off
        val path: Path,
        val modified: FileTime?,
        val size: Long
    ) // @formatter:on

    private data class Entry(
        val configuration: SyntaxConfiguration,
        val runtime: SyntaxRuntime,
        val watches: Set<LocalFileSystem.WatchRequest>
    ) : AutoCloseable {
        override fun close() {
            try {
                runtime.close()
            }
            finally {
                // Release file watches even if plugin cleanup fails
                LocalFileSystem.getInstance().removeWatchedRoots(watches)
            }
        }
    }

    private class Request(val configuration: SyntaxConfiguration?) {
        val result = CompletableFuture<SyntaxRuntime?>()

        @Volatile
        var stamps: List<Stamp>? = null
    }

    private val sessions: HashMap<Module, Entry> = HashMap()
    private val requests: HashMap<Module, Request> = HashMap()

    private val loaderExecutor: ExecutorService =
        AppExecutorUtil.createBoundedApplicationPoolExecutor("KCML syntax loading", 4)
    private val listeners: CopyOnWriteArrayList<() -> Unit> = CopyOnWriteArrayList()
    private val restartScheduled: AtomicBoolean = AtomicBoolean()

    @Volatile
    private var disposed: Boolean = false

    @Volatile
    private var started: Boolean = false

    init {
        project.messageBus.connect(this).apply {
            subscribe(ModuleRootListener.TOPIC, object : ModuleRootListener {
                override fun rootsChanged(event: ModuleRootEvent) = refresh()
            })
            subscribe(FacetManager.FACETS_TOPIC, object : FacetManagerListener {
                override fun facetConfigurationChanged(facet: Facet<*>) = refresh(facet)
                override fun facetAdded(facet: Facet<*>) = refresh(facet)
                override fun facetRemoved(facet: Facet<*>) = refresh(facet)
            })
            subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    // Normalize archive paths and defer filesystem comparisons off the VFS event thread
                    val changed = events.map { event -> Path.of(event.path.substringBefore("!/")) }
                    AppExecutorUtil.getAppExecutorService().execute { refreshChangedFiles(changed) }
                }
            })
        }
    }

    fun start() {
        synchronized(this) {
            if (started || disposed || project.isDisposed) return
            started = true
        }
        initializeRuntimes()
    }

    private fun initializeRuntimes() {
        if (!started || project.isDisposed) return
        // Read module configuration under a cancellable read action; loading runs separately
        ReadAction.nonBlocking<Unit> {
            for (module in ModuleManager.getInstance(project).modules) {
                ProgressManager.checkCanceled()
                loadRuntime(module, SyntaxConfiguration.fromModule(module))
            }
        }.expireWith(this).coalesceBy(this).submit(AppExecutorUtil.getAppExecutorService())
    }

    fun addListener(parentDisposable: Disposable, listener: () -> Unit) {
        listeners.add(listener)
        Disposer.register(parentDisposable) { listeners.remove(listener) }
    }

    @Synchronized
    fun loadedExtensions(): List<LoadedSyntaxExtensions> {
        if (disposed || project.isDisposed) return emptyList()
        // Expose cached metadata only, without invoking plugins or triggering loads
        return sessions.entries.filter { !it.key.isDisposed }.sortedBy { it.key.name }.mapNotNull { [module, entry] ->
            val ids = entry.runtime.extensionIds
            if (ids.isEmpty()) null else LoadedSyntaxExtensions(module, entry.configuration.target, ids)
        }
    }

    fun runtime(module: Module): SyntaxRuntime? {
        synchronized(this) {
            if (disposed || module.isDisposed || project.isDisposed) return null
            sessions[module]?.let { return it.runtime }
        }
        // Editor lookups never wait for discovery or runtime initialization
        start()
        return null
    }

    internal fun runtime(module: Module, configuration: SyntaxConfiguration?): SyntaxRuntime? {
        loadRuntime(module, configuration)
        return synchronized(this) { sessions[module]?.runtime }
    }

    internal fun loadRuntime(module: Module, configuration: SyntaxConfiguration?): CompletableFuture<SyntaxRuntime?> {
        val request: Request
        val old: Entry?
        val previous: Request?
        synchronized(this) {
            if (disposed || module.isDisposed || project.isDisposed) return CompletableFuture.completedFuture(null)
            // Share the result for identical configurations, including loads already in progress
            requests[module]?.takeIf { it.configuration == configuration }?.let { return it.result }
            request = Request(configuration)
            previous = requests.put(module, request)
            old = sessions.remove(module)
            if (configuration == null && previous == null && old == null) {
                request.result.complete(null)
                return request.result
            }
            incModificationCount()
            loaderExecutor.execute { load(module, request) }
        }
        // Retire superseded work without waiting for plugin callbacks or session disposal
        previous?.result?.complete(null)
        closeLater(listOfNotNull(old))
        return request.result
    }

    private fun load(module: Module, request: Request) {
        if (!isCurrent(module, request)) {
            request.result.complete(null)
            return
        }
        var entry: Entry? = null
        try {
            val configuration = request.configuration
            if (configuration != null) {
                val stamps = collectStamps(configuration)
                request.stamps = stamps
                if (!isCurrent(module, request)) return
                entry = createEntry(configuration)
                // Establish the VFS baseline before publication; never scan files from the EDT
                refreshPluginFiles(stamps)
            }
            // Publish only if no refresh, replacement request, or disposal superseded this load
            val accepted = synchronized(this) {
                if (!isCurrent(module, request)) false
                else {
                    if (entry != null) sessions[module] = entry
                    incModificationCount()
                    true
                }
            }
            if (accepted) {
                val runtime = entry?.runtime
                // Transfer ownership to sessions before notifying consumers
                entry = null
                changed()
                request.result.complete(runtime)
            }
        } catch (exception: Exception) {
            request.result.completeExceptionally(exception)
            if (exception !is ControlFlowException && exception !is CancellationException) {
                logger<SyntaxExtensionService>().warn("Could not load KCML syntax extensions", exception)
            }
        }
        finally {
            // Unpublished runtimes belong to this task; completion is harmless if already resolved
            entry?.close()
            request.result.complete(null)
        }
    }

    @Synchronized
    private fun isCurrent(module: Module, request: Request): Boolean =
        !disposed && !project.isDisposed && !module.isDisposed && requests[module] === request

    private fun collectStamps(configuration: SyntaxConfiguration): List<Stamp> =
        configuration.watchedPaths.flatMap { path ->
            // Stable ordering makes recursive directory snapshots comparable across VFS events
            if (Files.isDirectory(path)) Files.walk(path).use { files -> files.sorted().map(::stamp).toList() }
            else listOf(stamp(path))
        }

    private fun stamp(path: Path): Stamp =
        if (Files.exists(path)) Stamp(path, Files.getLastModifiedTime(path), Files.size(path))
        else Stamp(path, null, -1)

    private fun createEntry(configuration: SyntaxConfiguration): Entry {
        val runtime = SyntaxRuntime(
            configuration.compilerConfiguration(),
            configuration.paths.map { path -> path.toUri().toURL() },
            configuration.options
        )
        try {
            val localFiles = LocalFileSystem.getInstance()
            // Watch parent directories for JAR replacement as well as recursive directory changes
            val roots = configuration.watchedPaths.map { path -> if (Files.isDirectory(path)) path else path.parent }
                .map(Path::toString)
            val watches = localFiles.addRootsToWatch(roots, true)
            return Entry(configuration, runtime, watches)
        } catch (exception: Throwable) {
            runtime.close()
            throw exception
        }
    }

    private fun refreshPluginFiles(stamps: List<Stamp>) {
        val localFiles = LocalFileSystem.getInstance()
        if (!project.isDisposed) localFiles.refreshNioFiles(stamps.map { it.path }, true, false, null)
    }

    private fun refreshChangedFiles(changed: List<Path>) {
        val snapshot = synchronized(this) { requests.toMap() }
        for ([module, request] in snapshot) {
            val configuration = request.configuration ?: continue
            val stamps = request.stamps ?: continue
            if (configuration.watchedPaths.none { root -> changed.any { it.startsWith(root) || root.startsWith(it) } }) continue
            // Ignore unrelated events and VFS refresh echoes whose on-disk metadata is unchanged
            if (isCurrent(module, request) && stamps != collectStamps(configuration) && isCurrent(module, request)) {
                refresh()
                return
            }
        }
    }

    private fun refresh(facet: Facet<*>) {
        synchronized(this) {
            val request = requests[facet.module]
            if (request == null && !started) return
            // Facet notifications need not reload an unchanged syntax configuration
            if (request?.configuration == SyntaxConfiguration.fromModule(facet.module)) return
        }
        refresh()
    }

    fun refresh() {
        clearSessions()
        initializeRuntimes()
        changed()
    }

    private fun changed() {
        listeners.forEach { it() }
        // Coalesce background load notifications into one queued EDT analysis restart
        if (!restartScheduled.compareAndSet(false, true)) return
        ApplicationManager.getApplication().invokeLater {
            restartScheduled.set(false)
            if (!disposed && !project.isDisposed) DaemonCodeAnalyzer.getInstance(project).restart(this)
        }
    }

    private fun clearSessions() {
        val old: List<Entry>
        val pending: List<Request>
        synchronized(this) {
            old = sessions.values.toList()
            pending = requests.values.toList()
            sessions.clear()
            // Removing request identities prevents in-flight loads from publishing stale runtimes
            requests.clear()
            incModificationCount()
        }
        pending.forEach { it.result.complete(null) }
        closeLater(old)
    }

    private fun closeLater(entries: List<Entry>) {
        if (entries.isEmpty()) return
        // Closing may wait for active plugin processing, so keep it off the caller's thread
        AppExecutorUtil.getAppExecutorService().execute {
            entries.forEach { entry ->
                try {
                    entry.close()
                } catch (exception: Exception) {
                    logger<SyntaxExtensionService>().warn("Could not close KCML syntax runtime", exception)
                }
            }
        }
    }

    override fun dispose() {
        synchronized(this) {
            disposed = true
            started = false
            listeners.clear()
        }
        clearSessions()
        loaderExecutor.shutdown()
    }
}