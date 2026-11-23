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

import com.intellij.ide.projectView.TreeStructureProvider
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeStructureBase
import com.intellij.openapi.project.Project

internal class KcmlPluginTreeStructure(project: Project) : AbstractTreeStructureBase(project) {
    private val root: KcmlRootNode = KcmlRootNode(project, ViewSettings.DEFAULT)

    override fun getRootElement(): KcmlRootNode = root
    override fun getProviders(): List<TreeStructureProvider> = emptyList()
    override fun commit() = Unit
    override fun hasSomethingToCommit(): Boolean = false
    override fun isToBuildChildrenInBackground(element: Any): Boolean = true
}