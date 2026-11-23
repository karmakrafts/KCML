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
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Files
import java.nio.file.Path

internal class KcmlPluginPathNode(project: Project, path: Path, settings: ViewSettings) : ProjectViewNode<Path>(
    project,
    path,
    settings
) {
    override fun contains(file: VirtualFile): Boolean = file.path == value.toString()

    override fun update(presentation: PresentationData) {
        val exists = Files.exists(value)
        presentation.presentableText = value.fileName?.toString() ?: value.toString()
        presentation.locationString = if (exists) value.toString() else "Missing: $value"
        presentation.setIcon(if (exists) AllIcons.Nodes.Plugin else AllIcons.General.Warning)
        presentation.tooltip = if (exists) "Configured KCML classpath: $value"
        else "Configured KCML classpath does not exist. Build the plugin project and sync Gradle: $value"
    }

    override fun getChildren(): Collection<AbstractTreeNode<*>> = emptyList()
}