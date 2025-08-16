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

import dev.karmakrafts.conventions.configureJava
import dev.karmakrafts.conventions.setProjectInfo
import java.time.ZonedDateTime

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.dokka)
    `maven-publish`
}

configureJava(rootProject.libs.versions.java)

java {
    withSourcesJar()
}

dependencies {
    compileOnly(libs.kotlin.compiler.embeddable)
    compileOnly(libs.autoService)
    compileOnly(libs.kotlin.reflect)
    implementation(libs.kotlinGraphs)
    implementation(libs.semver)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)
    kapt(libs.autoService)

    testImplementation(libs.kotlin.reflect)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.iridium)
    testImplementation(libs.kotlinGraphs)
    testImplementation(libs.semver)
    testImplementation(libs.kotlinx.serialization.core)
    testImplementation(libs.kotlinx.serialization.json)
}

tasks {
    test {
        useJUnitPlatform()
        maxParallelForks = Runtime.getRuntime().availableProcessors()
    }
}

dokka {
    moduleName = project.name
    pluginsConfiguration {
        html {
            footerMessage = "(c) ${ZonedDateTime.now().year} Karma Krafts & associates"
        }
    }
}

val dokkaJar by tasks.registering(Jar::class) {
    dependsOn(tasks.dokkaGeneratePublicationHtml)
    from(tasks.dokkaGeneratePublicationHtml.flatMap { it.outputDirectory })
    archiveClassifier.set("javadoc")
}

val agentJarTask = project(":kcml-agent").tasks.named("shadowJar")

tasks {
    jar {
        dependsOn(agentJarTask)
        from(agentJarTask) {
            rename { "kcml-agent.jar" }
        }
    }
    System.getProperty("publishDocs.root")?.let { docsDir ->
        register("publishDocs", Copy::class) {
            dependsOn(dokkaJar)
            mustRunAfter(dokkaJar)
            from(zipTree(dokkaJar.get().outputs.files.first()))
            into("$docsDir/${project.name}")
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("compilerPlugin") {
            artifact(dokkaJar)
            from(components["java"])
        }
    }
    setProjectInfo("KCML Compiler Plugin", "Kotlin Compiler Meta Loader for plugin interop")
}