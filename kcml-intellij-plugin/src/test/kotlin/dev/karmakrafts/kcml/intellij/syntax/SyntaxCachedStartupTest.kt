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

package dev.karmakrafts.kcml.intellij.syntax

import com.intellij.facet.FacetManager
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.EmptyModuleType
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import org.jetbrains.kotlin.idea.facet.KotlinFacetType
import java.nio.file.Path

class SyntaxCachedStartupTest : HeavyPlatformTestCase() {
    fun testReopenedProjectWithCachedFacetLoadsExtensions() {
        val exampleJar = Path.of(System.getProperty("kcml.test.examplePlugin")).toAbsolutePath().normalize()
        VfsRootAccess.allowRootAccess(testRootDisposable, exampleJar.parent.toString())
        val projectDir = createTempDirectory().toPath().resolve("cached")
        val manager = ProjectManagerEx.getInstanceEx()
        // The replacement DSL is inline JVM 25 bytecode; this project targets JVM 21.
        @Suppress("DEPRECATION")
        val created = manager.newProject(
            projectDir, OpenProjectTask.build().copy(
                isNewProject = true, runConfigurators = false, useDefaultProjectAsTemplate = false
            )
        )!!
        val moduleName = "cached-module"
        try {
            runWriteAction {
                val model = ModuleManager.getInstance(created).getModifiableModel()
                model.newModule(projectDir.resolve("$moduleName.iml"), EmptyModuleType.EMPTY_MODULE)
                model.commit()
            }
            val module = ModuleManager.getInstance(created).findModuleByName(moduleName)!!
            val facetManager = FacetManager.getInstance(module)
            runWriteAction {
                val facet = facetManager.createFacet(KotlinFacetType.INSTANCE, "Kotlin", null)
                facet.configuration.settings.apply {
                    useProjectSettings = false
                    compilerArguments = K2JVMCompilerArguments().apply {
                        pluginOptions = arrayOf(
                            "plugin:kcml.bootstrap:pluginClasspaths=$exampleJar",
                            "plugin:kcml.bootstrap:compileTarget=jvm"
                        )
                    }
                    updateMergedArguments()
                }
                facetManager.createModifiableModel().apply {
                    addFacet(facet)
                    commit()
                }
            }
            PlatformTestUtil.saveProject(created, true)
        }
        finally {
            assertTrue("Created project must close", manager.closeAndDispose(created))
        }

        val reopened = PlatformTestUtil.loadAndOpenProject(projectDir, testRootDisposable)
        try {
            val module = ModuleManager.getInstance(reopened).findModuleByName(moduleName)!!
            val restored = KotlinFacet.get(module)
            assertNotNull("Kotlin facet must be restored from disk", restored)
            assertNotNull("Restored facet must yield a syntax configuration", SyntaxConfiguration.fromModule(module))

            val activity = ExtensionPointName.create<ProjectActivity>("com.intellij.postStartupActivity")
                .findExtension(SyntaxStartupActivity::class.java)!!
            runBlocking { activity.execute(reopened) }
            val service = reopened.service<SyntaxExtensionService>()
            PlatformTestUtil.waitWithEventsDispatching("Cached syntax extensions load after reopen", {
                service.loadedExtensions().singleOrNull()?.let {
                    it.module == module && it.target == "jvm" && it.extensionIds == listOf("example:syntax_example")
                } == true
            }, 30)
        }
        finally {
            assertTrue("Reopened project must close", manager.closeAndDispose(reopened))
        }
    }
}
