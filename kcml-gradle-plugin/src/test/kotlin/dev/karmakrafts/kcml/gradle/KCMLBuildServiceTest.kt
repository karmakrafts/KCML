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

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import java.net.BindException
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class KCMLBuildServiceTest {
    @Test
    fun `projects advertise distinct ports reserved by their build services`() {
        val directory = Files.createTempDirectory(Path.of("build"), "agent-services-").toAbsolutePath()
        val services = ArrayList<KCMLBuildService>()
        try {
            val root = ProjectBuilder.builder().withProjectDir(directory.toFile()).build()
            val projects = listOf("first", "second").map { name ->
                ProjectBuilder.builder().withName(name).withParent(root).build().also { project ->
                    project.pluginManager.apply("org.jetbrains.kotlin.jvm")
                    project.pluginManager.apply(KCMLGradlePlugin::class.java)
                }
            }
            val options = projects.map { project ->
                val compilation = project.extensions.getByType(KotlinJvmProjectExtension::class.java)
                    .target.compilations.getByName("main")
                project.plugins.getPlugin(KCMLBootstrapGradlePlugin::class.java).applyToCompilation(compilation).get()
            }
            val ports = options.mapIndexed { index, options ->
                options.single { it.key == "agentCommPort" }.value.toInt().also {
                    services += projects[index].agentService()
                }
            }
            ports.forEach { port ->
                assertFailsWith<BindException>("An advertised agent port must already be reserved") {
                    ServerSocket(port, 100, InetAddress.getLoopbackAddress()).close()
                }
            }
            assertNotEquals(ports[0], ports[1])
            projects.zip(ports).forEach { (project, port) ->
                assertEquals(port, project.agentService().agentCommServer.port)
                assertEquals(port, project.extensions.getByType(KCMLExtension::class.java).agentPort.get())
            }
        }
        finally {
            services.forEach(KCMLBuildService::close)
            directory.toFile().deleteRecursively()
        }
    }

    private fun Project.agentService(): KCMLBuildService =
        gradle.sharedServices.registrations.getByName("${KCMLBuildService.NAME}$path").service.get() as KCMLBuildService
}