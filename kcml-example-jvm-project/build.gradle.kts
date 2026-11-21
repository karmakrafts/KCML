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
import dev.karmakrafts.kcml.gradle.customJvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    id("dev.karmakrafts.kcml.kcml-gradle-plugin")
}

kcml {
    agentLogging = true
    agentPortRangeStart = 12000
    agentPortRangeEnd = 12999
    compileTargetAliases.put("jvm", "exampleJvm")
}

kotlin {
    defaultCompilerOptions()

    customJvmTarget("exampleJvm")

    sourceSets {
        named("exampleJvmMain") { kotlin.srcDir("src/main/kotlin") }
        named("exampleJvmTest") {
            kotlin.srcDir("src/test/kotlin")
            dependencies {
                implementation(kotlin("test-junit", libs.versions.kotlin.get()))
            }
        }
    }
}

dependencies {
    kcml(projects.kcmlExamplePlugin)
    testImplementation(libs.kotlin.test)
}

configurations.configureEach {
    resolutionStrategy.dependencySubstitution {
        substitute(module("${rootProject.group}:kcml-compiler-plugin")).using(project(":kcml-compiler-plugin"))
    }
}