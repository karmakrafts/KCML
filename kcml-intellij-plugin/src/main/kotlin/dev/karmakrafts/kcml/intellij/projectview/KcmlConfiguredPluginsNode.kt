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

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import dev.karmakrafts.kcml.intellij.icon.Icons

internal class KcmlConfiguredPluginsNode(project: Project, settings: ViewSettings) : ProjectViewNode<String>(
    project, "Plugins", settings
) {
    override fun contains(file: VirtualFile): Boolean = false

    override fun update(presentation: PresentationData) {
        presentation.presentableText = value
        presentation.setIcon(Icons.kcml)
        presentation.tooltip = "KCML plugins from embedded JSON metadata, grouped by module"
    }

    override fun getChildren(): Collection<AbstractTreeNode<*>> {
        val project = project ?: return emptyList()
        if (project.isDisposed) return emptyList()
        val modules = KcmlModulePlugins.fromProject(project)
        project.service<KcmlPluginTreeUpdater>().watch(modules.flatMap { it.paths }.toSet())
        return modules.mapNotNull { module ->
            val plugins = KcmlPluginMetadata.fromClasspath(module.paths)
            if (plugins.isEmpty()) null else KcmlConfiguredPluginModuleNode(project, module, plugins, settings)
        }
    }

    override fun isAlwaysShowPlus(): Boolean = true
}