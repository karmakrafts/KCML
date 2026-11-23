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

import com.intellij.facet.FacetManager
import com.intellij.ide.projectView.TreeStructureProvider
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowEP
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.ui.tree.StructureTreeModel
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.tree.TreeUtil
import dev.karmakrafts.kcml.intellij.syntax.SyntaxExtensionService
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import org.jetbrains.kotlin.idea.facet.KotlinFacetType
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JScrollPane

class KcmlToolWindowTest : LightJavaCodeInsightFixtureTestCase() {
    private var facet: KotlinFacet? = null

    override fun tearDown() {
        try {
            facet?.let { facet ->
                runWriteAction {
                    FacetManager.getInstance(module).createModifiableModel().apply {
                        removeFacet(facet)
                        commit()
                    }
                }
            }
            project.service<SyntaxExtensionService>().refresh()
        }
        finally {
            super.tearDown()
        }
    }

    fun testToolWindowIsRegisteredWithBottomAnchorAndIcon() {
        val registration = ToolWindowEP.EP_NAME.extensionList.single { it.id == "KCML" }
        assertEquals("bottom", registration.anchor)
        assertEquals("/icons/dev/karmakrafts/kcml/intellij/kcml-tool-window.svg", registration.icon)
        assertEquals(
            "dev.karmakrafts.kcml.intellij.projectview.KcmlToolWindowFactory",
            registration.factoryClass
        )
    }

    @Suppress("JetBrainsInternalApiUsage") // Inspect the platform extension point to verify registration.
    fun testCategoryIsNoLongerInjectedIntoProjectView() {
        assertFalse(TreeStructureProvider.EP.getExtensions(project).any {
            it.javaClass.simpleName == "KcmlPluginTreeStructureProvider"
        })
    }

    fun testCategoriesAreNamedAndHaveAHiddenCommonRoot() {
        val structure = KcmlPluginTreeStructure(project)
        val root = structure.rootElement
        val categories = structure.getChildElements(root).map { it as AbstractTreeNode<*> }
        categories.forEach { it.update() }
        assertEquals(listOf("Project classpaths", "Syntax extensions"), categories.map { it.presentation.presentableText })
        categories.forEach {
            assertEmpty(structure.getChildElements(it))
            assertSame(root, structure.getParentElement(it))
        }
        assertNull(structure.getParentElement(root))
        assertEmpty(structure.providers)
        assertTrue(structure.isToBuildChildrenInBackground(root))
    }

    fun testToolWindowContentRefreshesAfterImportAndClasspathCreation() {
        val manager = ToolWindowManager.getInstance(project)
        val toolWindow = manager.registerToolWindow(RegisterToolWindowTask.notClosable("KCML test", ToolWindowAnchor.BOTTOM))
        val directory = Files.createTempDirectory(Path.of("build").toAbsolutePath(), "kcml-tool-window-")
        try {
            KcmlToolWindowFactory().createToolWindowContent(project, toolWindow)
            val content = toolWindow.contentManager.contents.single()
            val tree = (content.component as JScrollPane).viewport.view as Tree
            assertFalse(tree.isRootVisible)
            val model = (tree.model as AsyncTreeModel).model as StructureTreeModel<*>
            var modelDisposed = false
            Disposer.register(model, Disposable { modelDisposed = true })
            PlatformTestUtil.waitWithEventsDispatching("Empty classpath tree", {
                categoryNode<KcmlPluginsNode>(tree)?.let { tree.model.getChildCount(it) == 0 } == true
            }, 5)

            val missing = directory.resolve("not-built.jar")
            val kotlinFacet = configure(listOf(missing), "exampleJvm")
            PlatformTestUtil.waitWithEventsDispatching("Imported classpath tree", {
                categoryNode<KcmlPluginsNode>(tree)?.let {
                    tree.model.getChildCount(it) == 1 &&
                        TreeUtil.getUserObject(tree.model.getChild(it, 0)) is KcmlPluginModuleNode
                } == true
            }, 5)
            val moduleNode = tree.model.getChild(categoryNode<KcmlPluginsNode>(tree), 0)
            assertEquals("exampleJvm", (TreeUtil.getUserObject(moduleNode) as KcmlPluginModuleNode).presentation.locationString)
            PlatformTestUtil.waitWithEventsDispatching("Classpath node", {
                tree.model.getChildCount(moduleNode) == 1 &&
                    TreeUtil.getUserObject(tree.model.getChild(moduleNode, 0)) is KcmlPluginPathNode
            }, 5)
            fun pathNode(): KcmlPluginPathNode? {
                val category = categoryNode<KcmlPluginsNode>(tree) ?: return null
                if (tree.model.getChildCount(category) != 1) return null
                val currentModule = tree.model.getChild(category, 0)
                if (tree.model.getChildCount(currentModule) != 1) return null
                return TreeUtil.getUserObject(tree.model.getChild(currentModule, 0)) as? KcmlPluginPathNode
            }
            assertEquals("Missing: $missing", pathNode()?.presentation?.locationString)
            val virtualDirectory = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(directory)!!
            runWriteAction { virtualDirectory.createChildData(this, missing.fileName.toString()) }
            PlatformTestUtil.waitWithEventsDispatching("Built classpath presentation", {
                pathNode()?.presentation?.locationString == missing.toString()
            }, 5)
            kotlinFacet.configuration.settings.apply {
                compilerArguments = K2JVMCompilerArguments().apply {
                    pluginOptions = arrayOf(
                        "plugin:kcml.bootstrap:pluginClasspaths=$missing",
                        "plugin:kcml.bootstrap:compileTarget=common"
                    )
                }
                updateMergedArguments()
            }
            runWriteAction { FacetManager.getInstance(module).facetConfigurationChanged(kotlinFacet) }
            PlatformTestUtil.waitWithEventsDispatching("Updated compilation target", {
                categoryNode<KcmlPluginsNode>(tree)?.let {
                    tree.model.getChildCount(it) == 1 &&
                        (TreeUtil.getUserObject(tree.model.getChild(it, 0)) as? KcmlPluginModuleNode)
                            ?.presentation?.locationString == "common"
                } == true
            }, 5)
            kotlinFacet.configuration.settings.apply {
                compilerArguments = K2JVMCompilerArguments()
                updateMergedArguments()
            }
            runWriteAction { FacetManager.getInstance(module).facetConfigurationChanged(kotlinFacet) }
            PlatformTestUtil.waitWithEventsDispatching("Removed classpath configuration", {
                categoryNode<KcmlPluginsNode>(tree)?.let { tree.model.getChildCount(it) == 0 } == true
            }, 5)
            assertFalse(modelDisposed)
            toolWindow.contentManager.removeContent(content, true)
            assertTrue(modelDisposed)
        }
        finally {
            toolWindow.remove()
            directory.toFile().deleteRecursively()
        }
    }

    fun testSyntaxCategoryShowsOnlyLoadedExtensionsAndRefreshesWithRuntimeLifecycle() {
        val exampleJar = Path.of(System.getProperty("kcml.test.examplePlugin")).toAbsolutePath().normalize()
        val kotlinFacet = configure(listOf(exampleJar), "exampleJvm")
        val service = project.service<SyntaxExtensionService>()
        service.start()
        val manager = ToolWindowManager.getInstance(project)
        val toolWindow = manager.registerToolWindow(RegisterToolWindowTask.notClosable("KCML syntax test", ToolWindowAnchor.BOTTOM))
        try {
            KcmlToolWindowFactory().createToolWindowContent(project, toolWindow)
            val content = toolWindow.contentManager.contents.single()
            val tree = (content.component as JScrollPane).viewport.view as Tree
            val model = (tree.model as AsyncTreeModel).model as StructureTreeModel<*>
            var modelDisposed = false
            Disposer.register(model, Disposable { modelDisposed = true })

            fun loadedIds(): List<String> {
                val category = categoryNode<KcmlSyntaxExtensionsNode>(tree) ?: return emptyList()
                if (tree.model.getChildCount(category) != 1) return emptyList()
                val moduleNode = tree.model.getChild(category, 0)
                val node = TreeUtil.getUserObject(moduleNode) as? KcmlSyntaxModuleNode ?: return emptyList()
                assertEquals(module.name, node.presentation.presentableText)
                assertEquals("exampleJvm", node.presentation.locationString)
                return (0 until tree.model.getChildCount(moduleNode)).map {
                    val extension = TreeUtil.getUserObject(tree.model.getChild(moduleNode, it))
                        as? KcmlSyntaxExtensionNode ?: return emptyList()
                    extension.presentation.presentableText!!
                }
            }
            fun awaitEmptyCategory() {
                PlatformTestUtil.waitWithEventsDispatching("Unloaded syntax extensions are removed", {
                    categoryNode<KcmlSyntaxExtensionsNode>(tree)?.let { tree.model.getChildCount(it) == 0 } == true
                }, 5)
            }
            PlatformTestUtil.waitWithEventsDispatching("Automatically loaded syntax extensions", {
                loadedIds() == listOf("example:syntax_example")
            }, 15)
            assertEquals(listOf("example:syntax_example"), service.loadedExtensions().single().extensionIds)
            val first = service.runtime(module)!!
            runWriteAction { FacetManager.getInstance(module).facetConfigurationChanged(kotlinFacet) }
            assertSame(first, service.runtime(module))
            service.refresh()
            PlatformTestUtil.waitWithEventsDispatching("Automatically reloaded syntax extensions", {
                service.runtime(module)?.let { it !== first } == true &&
                    loadedIds() == listOf("example:syntax_example")
            }, 15)
            assertNotSame(first, service.runtime(module))
            PlatformTestUtil.waitWithEventsDispatching("Refreshed syntax runtime is closed", {
                first.extensionIds.isEmpty()
            }, 15)
            assertEmpty(first.extensionIds)
            kotlinFacet.configuration.settings.apply {
                compilerArguments = K2JVMCompilerArguments()
                updateMergedArguments()
            }
            runWriteAction { FacetManager.getInstance(module).facetConfigurationChanged(kotlinFacet) }
            awaitEmptyCategory()
            assertEmpty(service.loadedExtensions())

            assertFalse(modelDisposed)
            toolWindow.contentManager.removeContent(content, true)
            assertTrue(modelDisposed)
            service.refresh()
        }
        finally {
            toolWindow.remove()
        }
    }

    fun testRefreshListenerIsRemovedOnDisposal() {
        val updater = project.service<KcmlPluginTreeUpdater>()
        val disposable = Disposer.newDisposable()
        var disposedRefreshes = 0
        updater.addListener(disposable) { disposedRefreshes++ }
        Disposer.dispose(disposable)
        var refreshes = 0
        updater.addListener(testRootDisposable) { refreshes++ }
        configure(listOf(Path.of("missing.jar")), "common")
        PlatformTestUtil.waitWithEventsDispatching("Classpath import refresh", { refreshes > 0 }, 5)
        assertEquals(0, disposedRefreshes)
    }

    fun testImportedProjectPluginClasspathsAndTargets() {
        val exampleJar = Path.of(System.getProperty("kcml.test.examplePlugin")).toAbsolutePath().normalize()
        val classes = Files.createTempDirectory(Path.of("build").toAbsolutePath(), "kcml-tree-")
        val missing = classes.resolve("not-built.jar")
        try {
            val kotlinFacet = configure(listOf(exampleJar, classes, missing, exampleJar), "exampleJvm")
            val structure = KcmlPluginTreeStructure(project)
            val category = structure.getChildElements(structure.rootElement).filterIsInstance<KcmlPluginsNode>().single()
            val moduleNode = structure.getChildElements(category).single() as KcmlPluginModuleNode
            assertSame(category, structure.getParentElement(moduleNode))
            moduleNode.update()
            assertEquals(module.name, moduleNode.presentation.presentableText)
            assertEquals("exampleJvm", moduleNode.presentation.locationString)
            val paths = structure.getChildElements(moduleNode).map { it as KcmlPluginPathNode }
            paths.forEach { assertSame(moduleNode, structure.getParentElement(it)) }
            assertEquals(listOf(exampleJar, classes, missing), paths.map { it.value })
            paths.forEach { it.update() }
            assertEquals(exampleJar.toString(), paths[0].presentation.locationString)
            assertEquals(classes.toString(), paths[1].presentation.locationString)
            assertEquals("Missing: $missing", paths[2].presentation.locationString)
            Files.createFile(missing)
            paths[2].update()
            assertEquals(missing.toString(), paths[2].presentation.locationString)
            kotlinFacet.configuration.settings.apply {
                compilerArguments = K2JVMCompilerArguments().apply {
                    pluginOptions = arrayOf(
                        "plugin:kcml.bootstrap:pluginClasspaths=$classes",
                        "plugin:kcml.bootstrap:compileTarget=common"
                    )
                }
                updateMergedArguments()
            }
            val updated = category.children.single() as KcmlPluginModuleNode
            updated.update()
            assertEquals("common", updated.presentation.locationString)
            assertEquals(listOf(classes), updated.children.map { it.value })
            kotlinFacet.configuration.settings.apply {
                compilerArguments = K2JVMCompilerArguments()
                updateMergedArguments()
            }
            assertEmpty(category.children)
        }
        finally {
            classes.toFile().deleteRecursively()
        }
    }

    private inline fun <reified T> categoryNode(tree: Tree): Any? {
        val root = tree.model.root ?: return null
        return (0 until tree.model.getChildCount(root)).map { tree.model.getChild(root, it) }
            .firstOrNull { TreeUtil.getUserObject(it) is T }
    }

    private fun configure(paths: List<Path>, target: String): KotlinFacet {
        val manager = FacetManager.getInstance(module)
        val kotlinFacet = manager.createFacet(KotlinFacetType.INSTANCE, "Kotlin", null)
        facet = kotlinFacet
        kotlinFacet.configuration.settings.apply {
            useProjectSettings = false
            compilerArguments = K2JVMCompilerArguments().apply {
                pluginOptions = arrayOf(
                    "plugin:kcml.bootstrap:pluginClasspaths=${paths.joinToString(";")}",
                    "plugin:kcml.bootstrap:compileTarget=$target"
                )
            }
            updateMergedArguments()
        }
        runWriteAction {
            manager.createModifiableModel().apply {
                addFacet(kotlinFacet)
                commit()
            }
        }
        return kotlinFacet
    }
}