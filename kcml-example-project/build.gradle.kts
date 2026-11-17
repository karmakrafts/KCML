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

import dev.karmakrafts.conventions.kotlin.defaultCompilerOptions
import dev.karmakrafts.kcml.gradle.customJsTarget
import dev.karmakrafts.kcml.gradle.customJvmTarget
import dev.karmakrafts.kcml.gradle.customNativeTarget
import dev.karmakrafts.kcml.gradle.customWasmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("dev.karmakrafts.kcml.kcml-gradle-plugin")
}

kcml {
    agentLogging = true
}

kotlin {
    defaultCompilerOptions()
    customJvmTarget("exampleJvm")
    customJsTarget("exampleJs") { browser(); nodejs() }
    customWasmTarget("exampleWasm") { browser(); nodejs() }
    customNativeTarget("exampleNative")

    applyDefaultHierarchyTemplate()
    sourceSets {
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
        listOf("Main", "Test").forEach { compilation ->
            named("exampleJs$compilation") { kotlin.srcDir("src/js$compilation/kotlin") }
            named("exampleWasm$compilation") { kotlin.srcDir("src/wasmJs$compilation/kotlin") }
        }
        named("exampleJvmTest") { kotlin.srcDir("src/jvmTest/kotlin") }
        named("exampleWasmTest") { kotlin.srcDir("src/wasmTest/kotlin") }
    }
}

dependencies {
    kcml(projects.kcmlExamplePlugin)
}

configurations.configureEach {
    resolutionStrategy.dependencySubstitution {
        substitute(module("${rootProject.group}:kcml-compiler-plugin")).using(project(":kcml-compiler-plugin"))
    }
}

val pluginJarTask = project(":kcml-example-plugin").tasks.named<Jar>("jar")

tasks {
    named("prepareKotlinIdeaImport") {
        dependsOn(pluginJarTask)
    }
}