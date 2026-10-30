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
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class KCMLNativeLinkTest {
    @Test
    fun `native links invalidate caches when KCML plugins change`() {
        val directory = Files.createTempDirectory(Path.of("build"), "native-link-test-").toAbsolutePath()
        try {
            val project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build()
            project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
            project.pluginManager.apply(KCMLGradlePlugin::class.java)
            val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
            val target = kotlin.linuxX64()
            val otherTarget = kotlin.mingwX64()
            (project as ProjectInternal).evaluate()
            val binary = target.binaries.getTest("DEBUG")
            val link = binary.linkTaskProvider.get()

            val dependencies = link.taskDependencies.getDependencies(link)

            assertTrue(dependencies.any { it.name == "kcmlPrepareNativeCache${binary.name.replaceFirstChar(Char::uppercaseChar)}" })
            val prepareCache = dependencies.filterIsInstance<KCMLPrepareNativeCacheTask>().single()
            assertEquals(
                project.layout.buildDirectory.dir("kotlin-native-ic-cache/${binary.name}").get(),
                prepareCache.cacheDirectory.get()
            )
            val otherLink = otherTarget.binaries.getTest("DEBUG").linkTaskProvider.get()
            assertSame(
                prepareCache,
                otherLink.taskDependencies.getDependencies(otherLink).filterIsInstance<KCMLPrepareNativeCacheTask>().single()
            )
        }
        finally {
            directory.toFile().deleteRecursively()
        }
    }
}