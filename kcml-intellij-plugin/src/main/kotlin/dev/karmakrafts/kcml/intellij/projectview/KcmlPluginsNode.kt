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

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import dev.karmakrafts.kcml.intellij.syntax.SyntaxConfiguration

internal class KcmlPluginsNode(project: Project, settings: ViewSettings) : ProjectViewNode<String>(
    project, "Project classpaths", settings
) {
    override fun contains(file: VirtualFile): Boolean = false

    override fun update(presentation: PresentationData) {
        presentation.presentableText = value
        presentation.setIcon(AllIcons.Nodes.Plugin)
        presentation.tooltip = "KCML plugin classpaths from imported Kotlin compiler configuration"
    }

    override fun getChildren(): Collection<AbstractTreeNode<*>> {
        val project = project ?: return emptyList()
        if (project.isDisposed) return emptyList()
        val configurations = ModuleManager.getInstance(project).modules.sortedBy(Module::getName).mapNotNull { module ->
            SyntaxConfiguration.fromModule(module)?.let { module to it }
        }
        project.service<KcmlPluginTreeUpdater>().watch(configurations.flatMap { it.second.paths }.toSet())
        return configurations.map { [module, configuration] -> KcmlPluginModuleNode(module, configuration, settings) }
    }

    override fun isAlwaysShowPlus(): Boolean = true
}