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

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CustomJvmTargetTest {
    @Test
    fun `JVM-only custom targets compile test and package independently`() {
        val directory = Files.createTempDirectory(Path.of("build"), "custom-jvm-execution-").toAbsolutePath()
        try {
            Files.writeString(directory.resolve("settings.gradle.kts"), "rootProject.name = \"custom-jvm-test\"")
            val pluginClasses = Path.of(KCMLExtension::class.java.protectionDomain.codeSource.location.toURI())
            Files.writeString(
                directory.resolve("build.gradle.kts"), """
                import dev.karmakrafts.kcml.gradle.KCMLExtension
                import dev.karmakrafts.kcml.gradle.customJvmTarget
                import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

                buildscript {
                    repositories { mavenCentral() }
                    dependencies {
                        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.5.0-RC")
                        classpath(files("${pluginClasses.toFile().invariantSeparatorsPath}"))
                    }
                }

                apply(plugin = "org.jetbrains.kotlin.jvm")
                repositories { mavenCentral() }
                extensions.create("kcml", KCMLExtension::class.java, project)
                extensions.configure<KotlinJvmProjectExtension> {
                    customJvmTarget("first") {
                        compilations.getByName("test").dependencies { implementation("junit:junit:4.13.2") }
                    }
                    customJvmTarget("second") {
                        compilations.getByName("test").dependencies { implementation("junit:junit:4.13.2") }
                    }
                }
            """.trimIndent()
            )
            fun source(name: String, contents: String) {
                val path = directory.resolve("src/$name")
                Files.createDirectories(path.parent)
                Files.writeString(path, contents)
            }
            source("main/kotlin/Broken.kt", "This source must not be compiled by custom targets")
            listOf("first", "second").forEachIndexed { index, target ->
                source("${target}Main/kotlin/Value.kt", "internal class Value { val value = ${index + 1} }")
                source(
                    "${target}Test/kotlin/ValueTest.kt", """
                    class ValueTest {
                        @org.junit.Test fun value() { org.junit.Assert.assertEquals(${index + 1}, Value().value) }
                    }
                """.trimIndent()
                )
                source("${target}Main/resources/$target.txt", target)
            }
            val result = GradleRunner.create()
                .withProjectDir(directory.toFile())
                .withTestKitDir(directory.resolve("testkit").toFile())
                .withArguments(
                    "firstTest",
                    "secondTest",
                    "firstJar",
                    "secondJar",
                    "--no-configuration-cache",
                    "--console=plain",
                    "--stacktrace",
                    "--max-workers=2",
                    "-Pkotlin.compiler.execution.strategy=in-process"
                )
                .build()
            listOf("first", "second").forEach { target ->
                assertEquals(TaskOutcome.SUCCESS, result.task(":${target}Test")?.outcome)
                assertEquals(TaskOutcome.SUCCESS, result.task(":${target}Jar")?.outcome)
                assertTrue(
                    Files.readString(directory.resolve("build/test-results/${target}Test/TEST-ValueTest.xml"))
                        .contains("tests=\"1\"")
                )
                JarFile(directory.resolve("build/libs/custom-jvm-test-$target.jar").toFile()).use { jar ->
                    assertTrue(jar.getEntry("Value.class") != null)
                    assertTrue(jar.getEntry("$target.txt") != null)
                    assertNull(jar.getEntry(if (target == "first") "second.txt" else "first.txt"))
                }
            }
            assertNull(result.task(":compileKotlin"))
            assertNull(result.task(":test"))
        }
        finally {
            directory.toFile().deleteRecursively()
        }
    }
}