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

import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import dev.karmakrafts.kcml.intellij.syntax.SyntaxConfiguration
import java.nio.file.Path

internal data class KcmlModulePlugins(val name: String, val paths: List<Path>) {
    companion object {
        fun fromProject(project: Project): List<KcmlModulePlugins> {
            val modules = ModuleManager.getInstance(project).modules.filterNot(Module::isDisposed)
            val configured = modules.sortedBy(Module::getName).mapNotNull { module ->
                SyntaxConfiguration.fromModule(module)?.let { module to it }
            }
            return configured.groupBy { (module, _) ->
                ExternalSystemModulePropertyManager.getInstance(module).getLinkedProjectPath() ?: module
            }.map { (key, configurations) ->
                val name = if (key is String) {
                    modules.filter {
                        val properties = ExternalSystemModulePropertyManager.getInstance(it)
                        properties.getLinkedProjectPath() == key && properties.getExternalModuleType() != "sourceSet"
                    }.minByOrNull { it.name.length }?.name ?: Path.of(key).fileName.toString()
                }
                else (key as Module).name
                KcmlModulePlugins(name, configurations.flatMap { it.second.paths }.distinct())
            }.sortedBy(KcmlModulePlugins::name)
        }
    }
}