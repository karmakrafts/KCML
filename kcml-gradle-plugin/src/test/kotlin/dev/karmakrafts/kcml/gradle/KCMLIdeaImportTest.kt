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

package dev.karmakrafts.kcml.gradle

import org.gradle.api.internal.project.ProjectInternal
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KCMLIdeaImportTest {
    @Test
    fun `JVM IDE import builds development plugins`() = checkImport("org.jetbrains.kotlin.jvm")

    @Test
    fun `multiplatform IDE import builds development plugins`() = checkImport("org.jetbrains.kotlin.multiplatform")

    @Test
    fun `JVM IDE import supports KCML applied before Kotlin`() = checkImport("org.jetbrains.kotlin.jvm", true)

    @Test
    fun `multiplatform IDE import supports KCML applied before Kotlin`() =
        checkImport("org.jetbrains.kotlin.multiplatform", true)

    private fun checkImport(kotlinPlugin: String, kcmlFirst: Boolean = false) {
        val directory = Files.createTempDirectory(Path.of("build"), "idea-import-").toAbsolutePath()
        try {
            val root = ProjectBuilder.builder().withProjectDir(directory.toFile()).build()
            val producer = ProjectBuilder.builder().withName("plugin").withParent(root).build()
            producer.pluginManager.apply("java-library")
            val helper = ProjectBuilder.builder().withName("helper").withParent(root).build()
            helper.pluginManager.apply("java-library")
            producer.dependencies.add("implementation", producer.dependencies.project(mapOf("path" to helper.path)))
            val consumer = ProjectBuilder.builder().withName("consumer").withParent(root).build()
            if (kcmlFirst) consumer.pluginManager.apply(KCMLGradlePlugin::class.java)
            consumer.pluginManager.apply(kotlinPlugin)
            consumer.pluginManager.apply(KCMLGradlePlugin::class.java)
            consumer.extensions.findByType(KotlinMultiplatformExtension::class.java)?.jvm()
            consumer.dependencies.add("kcml", consumer.dependencies.project(mapOf("path" to producer.path)))
            (root as ProjectInternal).evaluate()
            (producer as ProjectInternal).evaluate()
            (helper as ProjectInternal).evaluate()
            (consumer as ProjectInternal).evaluate()
            val prepareImport = consumer.tasks.getByName("prepareKotlinIdeaImport")
            val dependencies = prepareImport.taskDependencies.getDependencies(prepareImport)
            assertTrue(producer.tasks.getByName("jar") in dependencies)
            assertTrue(helper.tasks.getByName("jar") in dependencies)
            assertFalse(consumer.tasks.getByName("kcmlStartAgentServer") in dependencies)
        }
        finally {
            directory.toFile().deleteRecursively()
        }
    }
}