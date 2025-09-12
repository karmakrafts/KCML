import com.mikepenz.aboutlibraries.plugin.AboutLibrariesTask
import dev.karmakrafts.conventions.configureJava
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/*
 * Copyright 2025 Karma Krafts & associates
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

plugins {
    application
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.aboutLibraries.plugin)
    `maven-publish`
}

configureJava(libs.versions.java)

java {
    withSourcesJar()
}

val generatedResources = layout.buildDirectory.dir("generatedResources")

val generateLicenseData = tasks.register<AboutLibrariesTask>("generateLicenseData") {
    group = "build"
    configureOutputFile(generatedResources.map { dir -> dir.file("licenses.json") })
    configure()
}

tasks {
    withType<KotlinCompile>().configureEach {
        dependsOn(generateLicenseData)
    }
    withType<ProcessResources>().configureEach {
        dependsOn(generateLicenseData)
    }
    named<JavaExec>("run") {
        jvmArgs = listOf("-Dkcmlmon.debug=true")
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
    sourceSets {
        val main by getting {
            resources.srcDir(generatedResources)
            dependencies {
                implementation(projects.kcmlMonitorProtocol)
                implementation(libs.flatlaf.core)
                implementation(libs.flatlaf.extras)
                implementation(libs.flatlaf.intellijThemes)
                implementation(libs.miglayout.swing)
                implementation(libs.graphviz)
                implementation(libs.ikonli.core)
                implementation(libs.ikonli.swing)
                implementation(libs.ikonli.materialdesign)
                implementation(libs.kotlinx.serialization.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.aboutLibraries.core)
                implementation(libs.netty.all)
            }
        }
    }
}

application {
    mainClass = "${rootProject.group}.monitor.MainKt"
}