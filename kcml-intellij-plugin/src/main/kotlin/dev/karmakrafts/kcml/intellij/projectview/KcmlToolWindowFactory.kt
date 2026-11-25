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

import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.ui.tree.StructureTreeModel
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.tree.TreeUtil
import dev.karmakrafts.kcml.intellij.syntax.SyntaxExtensionService

internal class KcmlToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val disposable = Disposer.newDisposable("KCML tree")
        val model = StructureTreeModel(KcmlPluginTreeStructure(project), disposable)
        val renderer = KcmlPluginTreeCellRenderer()
        val tree = Tree(AsyncTreeModel(model, disposable)).apply {
            cellRenderer = renderer
            rowHeight = 0
            isRootVisible = false
            showsRootHandles = true
        }
        renderer.installActions(tree)
        TreeUtil.installActions(tree)
        TreeSpeedSearch.installOn(tree)
        project.service<KcmlPluginTreeUpdater>().addListener(disposable) { model.invalidateAsync() }
        project.service<SyntaxExtensionService>().addListener(disposable) { model.invalidateAsync() }
        val content = ContentFactory.getInstance().createContent(ScrollPaneFactory.createScrollPane(tree), "", false)
        @Suppress("UsePropertyAccessSyntax") //
        content.setDisposer(disposable)
        toolWindow.contentManager.addContent(content)
    }
}