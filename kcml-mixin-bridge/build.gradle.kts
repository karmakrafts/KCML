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
import dev.karmakrafts.conventions.dokka.withKotlin
import dev.karmakrafts.conventions.setProjectInfo

plugins {
    java
    alias(libs.plugins.dokka)
    signing
    `maven-publish`
}

configureDokka {
    withKotlin()
}

configureJava(rootProject.libs.versions.java)

java {
    withSourcesJar()
}

publishing {
    setProjectInfo(
        name = "KCML Mixin Bridge",
        description = "Mixin bridge API for the Kotlin Compiler Meta Loader",
        url = "https://git.karmakrafts.dev/kk/kcml"
    )
    publications {
        create<MavenPublication>("bridge") {
            from(components["java"])
        }
    }
}