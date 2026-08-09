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

plugins {
    alias(libs.plugins.shadow)
    alias(libs.plugins.kotlin.jvm)
    java
    signing
}

configureJava(libs.versions.java)

dependencies {
    implementation(libs.ow2.asm.core)
    implementation(libs.ow2.asm.tree)
    implementation(libs.annotations)

    testImplementation(libs.kotlin.test)
}

tasks {
    test {
        useJUnitPlatform()
    }
    shadowJar {
        archiveClassifier = ""
        addMultiReleaseAttribute = false
        relocationPrefix = "${rootProject.group}.agent.internal"
        enableAutoRelocation = true
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
        manifest {
            attributes["Agent-Class"] = "${rootProject.group}.agent.KCMLAgent"
            attributes["Can-Redefine-Classes"] = true
            attributes["Can-Retransform-Classes"] = true
            attributes["Permissions"] = "all-permissions"
            attributes["Implementation-Title"] = project.name
            attributes["Implementation-Version"] = project.version
            attributes["Implementation-Vendor"] = "Karma Krafts"
        }
    }
}