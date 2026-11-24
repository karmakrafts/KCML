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
import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.TreeStructureProvider
import com.intellij.ide.projectView.ViewSettings
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
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeUtil
import dev.karmakrafts.kcml.intellij.icon.Icons
import dev.karmakrafts.kcml.intellij.syntax.SyntaxExtensionService
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import org.jetbrains.kotlin.idea.facet.KotlinFacetType
import java.awt.Component
import java.awt.Container
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JLabel
import javax.swing.JScrollPane
import javax.swing.UIManager
import javax.swing.tree.TreePath

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
            "dev.karmakrafts.kcml.intellij.projectview.KcmlToolWindowFactory", registration.factoryClass
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
        assertEquals(
            listOf("Plugins", "Project classpaths", "Syntax extensions"),
            categories.map { it.presentation.presentableText })
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
        val toolWindow =
            manager.registerToolWindow(RegisterToolWindowTask.notClosable("KCML test", ToolWindowAnchor.BOTTOM))
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
                    tree.model.getChildCount(it) == 1 && TreeUtil.getUserObject(
                        tree.model.getChild(
                            it,
                            0
                        )
                    ) is KcmlPluginModuleNode
                } == true
            }, 5)
            val moduleNode = tree.model.getChild(categoryNode<KcmlPluginsNode>(tree), 0)
            assertEquals(
                "exampleJvm",
                (TreeUtil.getUserObject(moduleNode) as KcmlPluginModuleNode).presentation.locationString
            )
            PlatformTestUtil.waitWithEventsDispatching("Classpath node", {
                tree.model.getChildCount(moduleNode) == 1 && TreeUtil.getUserObject(
                    tree.model.getChild(
                        moduleNode,
                        0
                    )
                ) is KcmlPluginPathNode
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
                        "plugin:kcml.bootstrap:pluginClasspaths=$missing", "plugin:kcml.bootstrap:compileTarget=common"
                    )
                }
                updateMergedArguments()
            }
            runWriteAction { FacetManager.getInstance(module).facetConfigurationChanged(kotlinFacet) }
            PlatformTestUtil.waitWithEventsDispatching("Updated compilation target", {
                categoryNode<KcmlPluginsNode>(tree)?.let {
                    tree.model.getChildCount(it) == 1 && (TreeUtil.getUserObject(
                        tree.model.getChild(
                            it,
                            0
                        )
                    ) as? KcmlPluginModuleNode)?.presentation?.locationString == "common"
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

    fun testSyntaxPluginArtifactsAreAllowedVfsRoots() {
        val allowedRoots = System.getProperty("vfs.additional-allowed-roots")
            .split(File.pathSeparator)
            .filter { it.isNotBlank() }
            .map { File(it).canonicalFile.toPath() }
        val artifacts = System.getProperty("kcml.test.examplePluginClasspath").split(';')
        artifacts.forEach { artifact ->
            val path = Path.of(artifact).toRealPath()
            assertTrue(
                "Syntax plugin artifact is outside allowed VFS roots: $path",
                allowedRoots.any { path.startsWith(it) })
        }
    }

    fun testSyntaxCategoryShowsOnlyLoadedExtensionsAndRefreshesWithRuntimeLifecycle() {
        val exampleJar = Path.of(System.getProperty("kcml.test.examplePlugin")).toAbsolutePath().normalize()
        val kotlinFacet = configure(listOf(exampleJar), "exampleJvm")
        val service = project.service<SyntaxExtensionService>()
        service.start()
        val manager = ToolWindowManager.getInstance(project)
        val toolWindow =
            manager.registerToolWindow(RegisterToolWindowTask.notClosable("KCML syntax test", ToolWindowAnchor.BOTTOM))
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
                    val extension =
                        TreeUtil.getUserObject(tree.model.getChild(moduleNode, it)) as? KcmlSyntaxExtensionNode
                            ?: return emptyList()
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
                service.runtime(module)?.let { it !== first } == true && loadedIds() == listOf("example:syntax_example")
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
            val category =
                structure.getChildElements(structure.rootElement).filterIsInstance<KcmlPluginsNode>().single()
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
                        "plugin:kcml.bootstrap:pluginClasspaths=$classes", "plugin:kcml.bootstrap:compileTarget=common"
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

    fun testPluginsCategoryShowsEmbeddedMetadataWithoutLoadingSyntaxRuntime() {
        val exampleJar = Path.of(System.getProperty("kcml.test.examplePlugin")).toAbsolutePath().normalize()
        configure(listOf(exampleJar, exampleJar), "exampleJvm")
        val structure = KcmlPluginTreeStructure(project)
        val categories = structure.getChildElements(structure.rootElement)
        val category = categories.first() as KcmlConfiguredPluginsNode
        val moduleNode = structure.getChildElements(category).single() as KcmlConfiguredPluginModuleNode
        moduleNode.update()
        assertEquals(module.name, moduleNode.presentation.presentableText)
        val plugin = structure.getChildElements(moduleNode).single() as KcmlPluginNode
        plugin.update()
        assertEquals("Example Plugin", plugin.presentation.presentableText)
        assertEquals(listOf("Karma Krafts"), plugin.value.authors.map { it.name })
        assertEquals("1.0.0", plugin.presentation.locationString)
        assertNotNull(plugin.presentation.getIcon(false))
        assertNotSame(Icons.kcml, plugin.presentation.getIcon(false))
        assertSame(moduleNode, structure.getParentElement(plugin))
        assertEmpty(project.service<SyntaxExtensionService>().loadedExtensions())
    }

    fun testPluginMetadataFallbacksUseIdUnavailableVersionAndColoredKcmlIcon() {
        val directory = Files.createTempDirectory(Path.of("build").toAbsolutePath(), "kcml-metadata-fallback-")
        try {
            Files.createDirectories(directory.resolve("META-INF"))
            Files.writeString(directory.resolve("META-INF/minimal.json"), """{"id":"minimal"}""")
            configure(listOf(directory), "common")
            val category = KcmlConfiguredPluginsNode(project, ViewSettings.DEFAULT)
            val plugin = category.children.single().children.single() as KcmlPluginNode
            plugin.update()
            assertEquals("minimal", plugin.presentation.presentableText)
            assertEquals("<not available>", plugin.presentation.locationString)
            assertSame(Icons.kcml, plugin.presentation.getIcon(false))
        }
        finally {
            directory.toFile().deleteRecursively()
        }
    }

    fun testPluginEntriesHaveBoundedIconsAndStackedText() {
        val directory = Files.createTempDirectory(Path.of("build").toAbsolutePath(), "kcml-plugin-layout-")
        val toolWindow = ToolWindowManager.getInstance(project).registerToolWindow(
            RegisterToolWindowTask.notClosable("KCML layout test", ToolWindowAnchor.BOTTOM)
        )
        val collapsedIcon = UIManager.get("Tree.collapsedIcon")
        val expandedIcon = UIManager.get("Tree.expandedIcon")
        try {
            // IntelliJ paints tree controls without a component; Metal's Ocean icons require one.
            UIManager.put("Tree.collapsedIcon", AllIcons.General.ArrowRight)
            UIManager.put("Tree.expandedIcon", AllIcons.General.ArrowDown)
            val metadata = Files.createDirectories(directory.resolve("META-INF"))
            Files.writeString(
                metadata.resolve("wide.svg"), """
                <svg xmlns="http://www.w3.org/2000/svg" width="256" height="128"><rect width="256" height="128" fill="red"/></svg>
            """.trimIndent()
            )
            Files.writeString(
                metadata.resolve("tall.svg"), """
                <svg xmlns="http://www.w3.org/2000/svg" width="64" height="256"><rect width="64" height="256" fill="blue"/></svg>
            """.trimIndent()
            )
            for (id in listOf("wide", "tall", "fallback")) {
                val authors = if (id == "fallback") "[]" else """[{"name":"Karma Krafts"},{"name":"Contributor"}]"""
                Files.writeString(
                    metadata.resolve("$id.json"), """
                    {"id":"$id","name":"Plugin $id","authors":$authors,"version":"1.2.3","icon":{"light":"$id.svg"}}
                """.trimIndent()
                )
            }
            configure(listOf(directory), "common")
            KcmlToolWindowFactory().createToolWindowContent(project, toolWindow)
            val tree = (toolWindow.contentManager.contents.single().component as JScrollPane).viewport.view as Tree
            assertEquals(0, tree.rowHeight)
            var moduleNode: Any? = null
            PlatformTestUtil.waitWithEventsDispatching("Plugin tree entries", {
                val category = categoryNode<KcmlConfiguredPluginsNode>(tree)
                moduleNode = category?.takeIf { tree.model.getChildCount(it) == 1 }?.let { tree.model.getChild(it, 0) }
                moduleNode?.let { tree.model.getChildCount(it) == 3 } == true
            }, 5)
            tree.expandPath(
                TreePath(
                    arrayOf(
                        tree.model.root,
                        categoryNode<KcmlConfiguredPluginsNode>(tree),
                        moduleNode
                    )
                )
            )
            PlatformTestUtil.waitWithEventsDispatching("Visible plugin rows", { tree.rowCount >= 7 }, 5)
            fun layout(component: Component) {
                if (component is Container) {
                    component.doLayout()
                    component.components.forEach(::layout)
                }
            }

            fun labels(component: Component): List<JLabel> = when (component) {
                is JLabel -> listOf(component)
                is Container -> component.components.flatMap(::labels)
                else -> emptyList()
            }
            for (index in 0 until tree.model.getChildCount(moduleNode)) {
                val cell = tree.cellRenderer.getTreeCellRendererComponent(
                    tree, tree.model.getChild(moduleNode, index), false, false, true, index, false
                )
                val preferredSize = cell.preferredSize
                val textLabels = labels(cell).filter { it.isVisible && it.icon == null }
                assertTrue(
                    "Cell must fit all text lines",
                    preferredSize.height >= textLabels.sumOf { it.preferredSize.height })
                assertTrue(
                    "Cell must fit the longest text line",
                    preferredSize.width >= textLabels.maxOf { it.preferredSize.width })
                val path = TreePath(
                    arrayOf(
                        tree.model.root,
                        categoryNode<KcmlConfiguredPluginsNode>(tree),
                        moduleNode,
                        tree.model.getChild(moduleNode, index)
                    )
                )
                assertEquals(preferredSize.height, tree.getPathBounds(path)!!.height)
            }
            tree.setSize(800, 600)
            tree.addNotify()
            try {
                val plugin = (0 until tree.model.getChildCount(moduleNode)).map { tree.model.getChild(moduleNode, it) }
                    .single { (TreeUtil.getUserObject(it) as? KcmlPluginNode)?.value?.id == "wide" }
                val path = TreePath(
                    arrayOf(
                        tree.model.root,
                        categoryNode<KcmlConfiguredPluginsNode>(tree),
                        moduleNode,
                        plugin
                    )
                )
                val row = tree.getRowForPath(path)
                val bounds = tree.getPathBounds(path)!!
                val image = BufferedImage(tree.width, tree.height, BufferedImage.TYPE_INT_ARGB)
                val graphics = image.createGraphics()
                try {
                    tree.paint(graphics)
                }
                finally {
                    graphics.dispose()
                }
                val cell = tree.cellRenderer.getTreeCellRendererComponent(tree, plugin, false, false, true, row, false)
                // Painting resets the shared renderer's bounds; inspect it at the actual row size.
                cell.setSize(bounds.width, bounds.height)
                layout(cell)
                val authors = labels(cell).single { it.text == "Karma Krafts, Contributor" }
                assertEquals(authors.preferredSize.height, authors.height)
                assertTrue("Author text must fit within the tree row", authors.width >= authors.preferredSize.width)
            }
            finally {
                tree.removeNotify()
            }
            for (pluginMetadata in KcmlPluginMetadata.fromClasspath(listOf(directory))) {
                val plugin = (0 until tree.model.getChildCount(moduleNode)).map { tree.model.getChild(moduleNode, it) }
                    .single { (TreeUtil.getUserObject(it) as? KcmlPluginNode)?.value?.id == pluginMetadata.id }
                for (selected in listOf(false, true)) {
                    val cell = tree.cellRenderer.getTreeCellRendererComponent(
                        tree, plugin, selected, false, true, 0, selected
                    )
                    cell.setSize(cell.preferredSize)
                    layout(cell)
                    val labels = labels(cell)
                    val icon = labels.single { it.icon != null }
                    val name = labels.single { it.text == pluginMetadata.name }
                    val version = labels.single { it.text == pluginMetadata.version }
                    assertEquals(JBUI.scale(40), icon.icon.iconWidth)
                    assertEquals(JBUI.scale(40), icon.icon.iconHeight)
                    val iconBounds = javax.swing.SwingUtilities.convertRectangle(icon.parent, icon.bounds, cell)
                    val nameBounds = javax.swing.SwingUtilities.convertRectangle(name.parent, name.bounds, cell)
                    val versionBounds =
                        javax.swing.SwingUtilities.convertRectangle(version.parent, version.bounds, cell)
                    assertTrue(nameBounds.x >= iconBounds.x + iconBounds.width)
                    assertEquals(nameBounds.x, versionBounds.x)
                    assertTrue(versionBounds.y >= nameBounds.y + nameBounds.height)
                    val authors = name.parent.getComponent(1) as JLabel
                    assertEquals(pluginMetadata.authors.joinToString(", ") { it.name }, authors.text)
                    assertEquals(pluginMetadata.authors.isNotEmpty(), authors.isVisible)
                    if (authors.isVisible) {
                        assertEquals("Karma Krafts, Contributor", authors.text)
                        assertEquals(version.foreground, authors.foreground)
                        val authorBounds =
                            javax.swing.SwingUtilities.convertRectangle(authors.parent, authors.bounds, cell)
                        assertEquals(authors.preferredSize.height, authorBounds.height)
                        assertTrue(authorBounds.width >= authors.preferredSize.width)
                        assertEquals(nameBounds.x, authorBounds.x)
                        assertTrue(authorBounds.y >= nameBounds.y + nameBounds.height)
                        assertTrue(versionBounds.y >= authorBounds.y + authorBounds.height)
                    }
                    assertTrue(cell.height >= JBUI.scale(52))
                    if (pluginMetadata.id != "fallback") {
                        val size = JBUI.scale(40)
                        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
                        val graphics = image.createGraphics()
                        try {
                            icon.icon.paintIcon(tree, graphics, 0, 0)
                        }
                        finally {
                            graphics.dispose()
                        }
                        val paintedPixels = (0 until size).flatMap { x ->
                            (0 until size).filter { y -> image.getRGB(x, y).ushr(24) != 0 }.map { y -> x to y }
                        }
                        assertFalse(paintedPixels.isEmpty())
                        val width = paintedPixels.maxOf { it.first } - paintedPixels.minOf { it.first } + 1
                        val height = paintedPixels.maxOf { it.second } - paintedPixels.minOf { it.second } + 1
                        assertEquals(size, maxOf(width, height))
                        val expectedRatio = if (pluginMetadata.id == "wide") 2.0 else 0.25
                        assertEquals(expectedRatio, width.toDouble() / height, 0.1)
                    }
                }
            }
        }
        finally {
            UIManager.put("Tree.collapsedIcon", collapsedIcon)
            UIManager.put("Tree.expandedIcon", expandedIcon)
            ToolWindowManager.getInstance(project).unregisterToolWindow(toolWindow.id)
            directory.toFile().deleteRecursively()
        }
    }

    fun testPluginsCategoryRefreshesAfterMetadataCreationUpdateAndRemoval() {
        val directory = Files.createTempDirectory(Path.of("build").toAbsolutePath(), "kcml-metadata-refresh-")
        val toolWindow = ToolWindowManager.getInstance(project).registerToolWindow(
            RegisterToolWindowTask.notClosable("KCML metadata test", ToolWindowAnchor.BOTTOM)
        )
        try {
            configure(listOf(directory), "common")
            val virtualDirectory = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(directory)!!
            KcmlToolWindowFactory().createToolWindowContent(project, toolWindow)
            val tree = (toolWindow.contentManager.contents.single().component as JScrollPane).viewport.view as Tree
            fun pluginNode(): KcmlPluginNode? {
                val category = categoryNode<KcmlConfiguredPluginsNode>(tree) ?: return null
                if (tree.model.getChildCount(category) != 1) return null
                val module = tree.model.getChild(category, 0)
                if (tree.model.getChildCount(module) != 1) return null
                return TreeUtil.getUserObject(tree.model.getChild(module, 0)) as? KcmlPluginNode
            }
            PlatformTestUtil.waitWithEventsDispatching("Empty plugins category", {
                categoryNode<KcmlConfiguredPluginsNode>(tree)?.let { tree.model.getChildCount(it) == 0 } == true
            }, 5)
            val metadata = runWriteAction {
                virtualDirectory.createChildDirectory(this, "META-INF").createChildData(this, "dynamic.json").apply {
                    setBinaryContent("""{"id":"dynamic"}""".toByteArray())
                }
            }
            PlatformTestUtil.waitWithEventsDispatching("Created plugin metadata", {
                pluginNode()?.presentation?.presentableText == "dynamic"
            }, 5)
            assertEquals("<not available>", pluginNode()?.presentation?.locationString)
            runWriteAction {
                metadata.setBinaryContent("""{"id":"dynamic","name":"Updated Plugin","version":"2.0.0"}""".toByteArray())
            }
            PlatformTestUtil.waitWithEventsDispatching("Updated plugin metadata", {
                pluginNode()?.presentation?.let {
                    it.presentableText == "Updated Plugin" && it.locationString == "2.0.0"
                } == true
            }, 5)
            runWriteAction { metadata.delete(this) }
            PlatformTestUtil.waitWithEventsDispatching("Removed plugin metadata", {
                categoryNode<KcmlConfiguredPluginsNode>(tree)?.let { tree.model.getChildCount(it) == 0 } == true
            }, 5)
        }
        finally {
            toolWindow.remove()
            directory.toFile().deleteRecursively()
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