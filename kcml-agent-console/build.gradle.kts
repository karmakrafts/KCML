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
    application
}

configureJava(rootProject.libs.versions.java)

java {
    withSourcesJar()
    withJavadocJar() // We don't use Dokka here but we still need a javadoc JAR
}

kotlin {
    defaultCompilerOptions()
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.flatlaf)
}

application {
    mainClass = "${rootProject.group}.agent.console.Main"
    applicationDefaultJvmArgs += "--add-modules"
    applicationDefaultJvmArgs += "jdk.unsupported"
}