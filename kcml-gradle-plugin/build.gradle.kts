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
import dev.karmakrafts.conventions.dokka.configureDokka
import dev.karmakrafts.conventions.kotlin.defaultCompilerOptions
import dev.karmakrafts.conventions.setProjectInfo
import dev.karmakrafts.conventions.GitLabCI
import dev.karmakrafts.conventions.apache2License
import dev.karmakrafts.conventions.defaultDependencyLocking
import dev.karmakrafts.conventions.setRepository
import dev.karmakrafts.conventions.signPublications
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.div
import kotlin.io.path.writeText

plugins {
    alias(libs.plugins.karmaConventions)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.dokka)
    `java-gradle-plugin`
    `maven-publish`
    signing
}

group = "dev.karmakrafts.kcml"
version = GitLabCI.getDefaultVersion(libs.versions.kcml)
if (GitLabCI.isCI) defaultDependencyLocking()

subprojects {
    group = rootProject.group
    version = rootProject.version
}

configureDokka {
    withKotlin()
    withKotlinGradle()
    withGradle()
}

configureJava(rootProject.libs.versions.java)

dependencies {
    compileOnly(gradleApi())
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.kotlinx.serialization.core)
    compileOnly(libs.kotlinx.serialization.json)
    implementation(projects.kcmlGradleApi)

    testImplementation(libs.kotlin.test)
}

kotlin {
    defaultCompilerOptions()
    sourceSets {
        main {
            resources.srcDir("build/generated")
        }
    }
}

tasks {
    test {
        useJUnitPlatform()
    }
    val createVersionFile = register("createVersionFile") {
        group = "build"
        description = "Generate the version file embedded in the finished plugin JAR"
        doFirst {
            val path = (layout.buildDirectory.asFile.get().toPath() / "generated" / "kcml.version")
            path.deleteIfExists()
            path.parent.createDirectories()
            path.writeText(version.toString())
        }
        outputs.upToDateWhen { false } // Always re-generate this file
    }
    processResources { dependsOn(createVersionFile) }
    compileKotlin { dependsOn(processResources) }
}

gradlePlugin {
    System.getenv("CI_PROJECT_URL")?.let {
        website = it
        vcsUrl = it
    }
    plugins {
        create("gradlePlugin") {
            id = "$group.kcml-gradle-plugin"
            implementationClass = "$group.gradle.KCMLGradlePlugin"
            displayName = "KCML Gradle Plugin"
            description = "Gradle plugin for applying the KCML compiler plugin"
            tags.addAll("kotlin", "native", "interop", "codegen")
        }
    }
}

signing {
    signPublications()
}

publishing {
    apache2License()
    setRepository("github.com", "karmakrafts/kcml")
    with(GitLabCI) { karmaKraftsDefaults() }
    setProjectInfo(
        name = "KCML Gradle Plugin",
        description = "Gradle plugin for the Kotlin Compiler Meta Loader",
        url = "https://git.karmakrafts.dev/kk/kcml"
    )
}