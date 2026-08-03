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

import dev.karmakrafts.conventions.configureJava
import dev.karmakrafts.conventions.kotlin.defaultCompilerOptions

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.shadow)
    signing
}

configureJava(rootProject.libs.versions.java)

java {
    withSourcesJar()
    withJavadocJar() // We don't use Dokka here but we still need a javadoc JAR
}

kotlin {
    defaultCompilerOptions()
}

val shadeImplementation = configurations.create("shadeImplementation") {
    isTransitive = false
}

val shadeApi = configurations.create("shadeApi") {
    isTransitive = false
}

configurations {
    implementation { extendsFrom(shadeImplementation) }
    api { extendsFrom(shadeApi) }
}

dependencies {
    compileOnly(libs.kotlin.compiler.embeddable)
    compileOnly(libs.kotlin.native.compiler.embeddable)

    shadeApi(projects.kcmlPluginApi)
    shadeApi(libs.semver)
    compileOnly(libs.kotlin.reflect)
    shadeImplementation(libs.kotlinx.serialization.core)
    shadeImplementation(libs.kotlinx.serialization.json)
    shadeImplementation(libs.kotlinGraphs)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.iridium)
}

tasks {
    test {
        useJUnitPlatform()
        maxParallelForks = Runtime.getRuntime().availableProcessors()
    }
    jar {
        archiveClassifier = "slim"
    }
    shadowJar {
        configurations = setOf(shadeImplementation, shadeApi)
        archiveClassifier = ""
        relocate("io.github.z4kn4fein.semver", "${rootProject.group}.shaded.io.github.z4kn4fein.semver")
        relocate("io.github.alexandrepiveteau.graphs", "${rootProject.group}.shaded.io.github.alexandrepiveteau.graphs")
        relocate("kotlinx.serialization", "${rootProject.group}.shaded.kotlinx.serialization")
    }
}