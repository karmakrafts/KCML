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
import dev.karmakrafts.conventions.kotlin.withAndroidLibrary
import dev.karmakrafts.conventions.kotlin.withBrowser
import dev.karmakrafts.conventions.kotlin.withJvm
import dev.karmakrafts.conventions.kotlin.withNative
import dev.karmakrafts.conventions.kotlin.withNodeJs
import dev.karmakrafts.conventions.kotlin.withWasmWasi
import dev.karmakrafts.conventions.kotlin.withWasmtime
import dev.karmakrafts.conventions.kotlin.withWeb
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    id("dev.karmakrafts.kcml.kcml-gradle-plugin")
}

kcml {
    agentLogging = true
}

kotlin {
    defaultCompilerOptions()
    withAndroidLibrary(group.toString())
    withJvm()
    withNative()
    withWeb {
        withBrowser()
        withNodeJs()
    }
    withWasmWasi {
        @OptIn(ExperimentalWasmDsl::class) //
        withWasmtime()
    }
    applyDefaultHierarchyTemplate()
    sourceSets {
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
        named("wasmJsTest") {
            kotlin.srcDir("src/wasmTest/kotlin")
        }
        named("wasmWasiTest") {
            kotlin.srcDir("src/wasmTest/kotlin")
        }
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