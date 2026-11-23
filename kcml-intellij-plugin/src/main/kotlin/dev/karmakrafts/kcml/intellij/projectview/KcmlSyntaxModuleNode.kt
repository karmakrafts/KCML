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
import dev.karmakrafts.kcml.intellij.syntax.LoadedSyntaxExtensions

internal class KcmlSyntaxModuleNode(
    project: Project, extensions: LoadedSyntaxExtensions, settings: ViewSettings
) : ProjectViewNode<LoadedSyntaxExtensions>(project, extensions, settings) {
    override fun contains(file: VirtualFile): Boolean = false

    override fun update(presentation: PresentationData) {
        presentation.presentableText = value.module.name
        presentation.locationString = value.target
        presentation.setIcon(AllIcons.Nodes.Module)
    }

    override fun getChildren(): Collection<AbstractTreeNode<*>> =
        value.extensionIds.map { KcmlSyntaxExtensionNode(value.module.project, it, settings) }
}