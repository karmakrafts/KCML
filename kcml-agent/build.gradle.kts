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
    alias(libs.plugins.shadow)
    java
}

configureJava(libs.versions.java)
val shadeImplementation = configurations.create("shadeImplementation")

configurations {
    implementation {
        extendsFrom(shadeImplementation)
    }
}

dependencies {
    shadeImplementation(libs.ow2.asm.core)
    shadeImplementation(libs.ow2.asm.tree)
    shadeImplementation(libs.annotations)
}

tasks {
    shadowJar {
        configurations = setOf(shadeImplementation)
        entryCompression = ZipEntryCompression.STORED // Don't need compression with Jar-in-Jar
        archiveClassifier = ""
        relocate("org.objectweb.asm", "${rootProject.group}.shaded.org.objectweb.asm")
        relocate("org.jetbrains.annotations", "${rootProject.group}.shaded.org.jetbrains.annotations")
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