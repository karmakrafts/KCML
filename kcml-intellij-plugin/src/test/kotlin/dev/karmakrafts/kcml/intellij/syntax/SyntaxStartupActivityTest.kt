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
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.idea.facet.KotlinFacetType
import java.nio.file.Path

class SyntaxStartupActivityTest : LightJavaCodeInsightFixtureTestCase() {
    private val descriptor = object : LightProjectDescriptor() {}

    override fun getProjectDescriptor(): LightProjectDescriptor = descriptor

    fun testStartupAndImportedConfigurationLoadExtensionsWithoutOpeningSourceFiles() {
        val exampleJar = Path.of(System.getProperty("kcml.test.examplePlugin")).toAbsolutePath().normalize()
        VfsRootAccess.allowRootAccess(testRootDisposable, exampleJar.parent.toString())
        val manager = FacetManager.getInstance(module)
        val facet = runWriteAction {
            manager.createFacet(KotlinFacetType.INSTANCE, "Kotlin", null).also { facet ->
                manager.createModifiableModel().apply {
                    addFacet(facet)
                    commit()
                }
            }
        }
        fun configure(target: String?) {
            runWriteAction {
                facet.configuration.settings.apply {
                    useProjectSettings = false
                    compilerArguments = K2JVMCompilerArguments().apply {
                        if (target != null) pluginOptions = arrayOf(
                            "plugin:kcml.bootstrap:pluginClasspaths=$exampleJar",
                            "plugin:kcml.bootstrap:compileTarget=$target"
                        )
                    }
                    updateMergedArguments()
                }
                manager.facetConfigurationChanged(facet)
            }
        }
        val service = project.service<SyntaxExtensionService>()
        fun awaitExtensions(target: String) {
            PlatformTestUtil.waitWithEventsDispatching("Configured syntax extensions load without an open editor", {
                service.loadedExtensions().singleOrNull()?.let {
                    it.module == module && it.target == target && it.extensionIds == listOf("example:syntax_example")
                } == true
            }, 15)
        }
        try {
            configure("jvm")
            val activity = ExtensionPointName.create<ProjectActivity>("com.intellij.postStartupActivity")
                .findExtension(SyntaxStartupActivity::class.java)!!
            runBlocking { activity.execute(project) }
            awaitExtensions("jvm")

            val first = service.runtime(module)!!
            configure("common")
            awaitExtensions("common")
            PlatformTestUtil.waitWithEventsDispatching("Replaced syntax runtime is closed", {
                first.extensionIds.isEmpty()
            }, 15)
            assertEmpty(first.extensionIds)

            service.refresh()
            awaitExtensions("common")

            configure(null)
            PlatformTestUtil.waitWithEventsDispatching("Removed syntax configuration unloads extensions", {
                service.loadedExtensions().isEmpty()
            }, 15)
            configure("jvm")
            awaitExtensions("jvm")
        }
        finally {
            runWriteAction {
                manager.createModifiableModel().apply {
                    removeFacet(facet)
                    commit()
                }
            }
        }
    }
}