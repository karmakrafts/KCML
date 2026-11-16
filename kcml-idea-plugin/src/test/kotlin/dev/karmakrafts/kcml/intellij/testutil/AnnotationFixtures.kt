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

package dev.karmakrafts.kcml.intellij.testutil

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

internal class AnnotationLibrary(vararg classNames: String) : AutoCloseable {
    private val directory = Files.createTempDirectory(Path.of("build"), "kcml-annotation-library-")
    private val jar = directory.resolve("annotations.jar")
    private val url = "jar://${jar.toAbsolutePath()}!/"

    init {
        try {
            JarOutputStream(Files.newOutputStream(jar)).use { output ->
                for (name in classNames) {
                    val classFile = "${name.replace('.', '/')}.class"
                    output.putNextEntry(JarEntry(classFile))
                    javaClass.classLoader.getResourceAsStream(classFile)!!.use { it.copyTo(output) }
                    output.closeEntry()
                }
            }
        } catch (exception: Throwable) {
            close()
            throw exception
        }
    }

    fun attachTo(module: Module) {
        WriteCommandAction.runWriteCommandAction(module.project) {
            ModuleRootModificationUtil.addModuleLibrary(module, url)
        }
    }

    fun detachFrom(module: Module) {
        WriteCommandAction.runWriteCommandAction(module.project) {
            ModuleRootModificationUtil.updateModel(module) { model ->
                val table = model.moduleLibraryTable
                table.libraries.filter { url in it.getUrls(OrderRootType.CLASSES) }.forEach(table::removeLibrary)
            }
        }
    }

    override fun close() {
        directory.toFile().deleteRecursively()
    }
}

internal fun JavaCodeInsightTestFixture.withAnnotationLibrary(vararg classNames: String, action: () -> Unit) {
    AnnotationLibrary(*classNames).use { library ->
        try {
            library.attachTo(module)
            action()
        }
        finally {
            library.detachFrom(module)
        }
    }
}

abstract class AnnotationFixtureTestCase : LightJavaCodeInsightFixtureTestCase() {
    private val annotationSources = mutableListOf<PsiFile>()

    protected fun addAnnotationSource(path: String, text: String) {
        annotationSources += myFixture.addFileToProject(path, text)
    }

    override fun tearDown() {
        try {
            WriteCommandAction.runWriteCommandAction(project) {
                annotationSources.filter { it.isValid }.forEach { it.delete() }
            }
        }
        finally {
            annotationSources.clear()
            super.tearDown()
        }
    }
}