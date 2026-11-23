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
import com.intellij.ide.projectView.ViewSettings
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.idea.facet.KotlinFacetType
import java.nio.file.Files
import java.nio.file.Path

class KcmlModulePluginsTest : JavaCodeInsightFixtureTestCase() {
    fun testPluginsMergeGradleSourceSetsByOwningModuleAndKeepOtherModulesSeparate() {
        val directory = Files.createTempDirectory(Path.of("build").toAbsolutePath(), "kcml-module-plugins-")
        val manager = ModuleManager.getInstance(project)
        val created = ArrayList<Module>()
        val module = myFixture.module
        try {
            val exampleJar = Path.of(System.getProperty("kcml.test.examplePlugin")).toAbsolutePath().normalize()
            val ownerPath = directory.resolve("library").toString()
            runWriteAction {
                ExternalSystemModulePropertyManager.getInstance(module).apply {
                    setExternalId(ProjectSystemId("GRADLE"))
                    setLinkedProjectPath(ownerPath)
                }
                listOf("library.main", "library.test", "other.main").forEach { name ->
                    val child = manager.newModule(directory.resolve("$name.iml"), "JAVA_MODULE")
                    created += child
                    ExternalSystemModulePropertyManager.getInstance(child).apply {
                        setExternalId(ProjectSystemId("GRADLE"))
                        setLinkedProjectPath(if (name.startsWith("other"))
                            directory.resolve("other").toString() else ownerPath)
                        setExternalModuleType("sourceSet")
                    }
                }
            }
            created.forEach { child -> configure(child, exampleJar) }
            val category = KcmlConfiguredPluginsNode(project, ViewSettings.DEFAULT)
            val owners = category.children.map { it as KcmlConfiguredPluginModuleNode }
            owners.forEach { it.update() }
            assertEquals(listOf(module.name, "other").sorted(), owners.map { it.presentation.presentableText })
            owners.forEach { owner ->
                val plugin = owner.children.single() as KcmlPluginNode
                plugin.update()
                assertEquals("Example Plugin", plugin.presentation.presentableText)
            }
        }
        finally {
            runWriteAction { created.forEach(manager::disposeModule) }
            directory.toFile().deleteRecursively()
        }
    }

    private fun configure(module: Module, path: Path) {
        val manager = FacetManager.getInstance(module)
        runWriteAction {
            val facet = manager.createFacet(KotlinFacetType.INSTANCE, "Kotlin", null)
            facet.configuration.settings.apply {
                useProjectSettings = false
                compilerArguments = K2JVMCompilerArguments().apply {
                    pluginOptions = arrayOf("plugin:kcml.bootstrap:pluginClasspaths=$path")
                }
                updateMergedArguments()
            }
            manager.createModifiableModel().apply {
                addFacet(facet)
                commit()
            }
        }
    }
}