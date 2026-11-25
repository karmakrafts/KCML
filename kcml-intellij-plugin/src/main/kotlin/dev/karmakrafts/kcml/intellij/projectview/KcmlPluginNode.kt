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
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import dev.karmakrafts.kcml.intellij.icon.Icons
import javax.swing.Icon

internal class KcmlPluginNode( // @formatter:off
    project: Project,
    metadata: KcmlPluginMetadata,
    settings: ViewSettings
) : ProjectViewNode<KcmlPluginMetadata>(project, metadata, settings) { // @formatter:on
    override fun contains(file: VirtualFile): Boolean = file.url == value.configUrl.toExternalForm()

    override fun update(presentation: PresentationData) {
        presentation.presentableText = value.name
        presentation.locationString = value.version
        presentation.setIcon(pluginIcon())
        presentation.tooltip = "${value.id}: ${value.configUrl}"
    }

    private fun pluginIcon(): Icon {
        val url = if (!JBColor.isBright()) value.darkIcon ?: value.lightIcon
        else value.lightIcon ?: value.darkIcon
        if (url == null) return Icons.kcml
        return IconLoader.findIcon(url) ?: Icons.kcml
    }

    override fun getChildren(): Collection<AbstractTreeNode<*>> = emptyList()
}