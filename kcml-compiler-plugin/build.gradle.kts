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
import dev.karmakrafts.conventions.setProjectInfo

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.serialization)
    signing
    `maven-publish`
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
    compileOnly(libs.kotlin.compiler.embeddable)
    compileOnly(libs.kotlin.native.compiler.embeddable)
    compileOnly(projects.kcmlLoader) // Injected into compiler CP by this module for runtime
    compileOnly(libs.autoService.annotations)
    compileOnly(libs.kotlin.reflect)
    kapt(libs.autoService.processor)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.iridium)
}

tasks {
    test {
        useJUnitPlatform()
        maxParallelForks = Runtime.getRuntime().availableProcessors()
    }
    jar {
        val agentJarTask = project(":kcml-agent").tasks.named<Jar>("shadowJar")
        val loaderJarTask = project(":kcml-loader").tasks.named<Jar>("shadowJar")
        dependsOn(agentJarTask, loaderJarTask)
        from(agentJarTask) { rename { "kcml-agent.jar" } }
        from(loaderJarTask) { rename { "kcml-loader.jar" } }
    }
}

publishing {
    setProjectInfo("KCML Compiler Plugin", "KCML compiler plugin for bootstrapping the KCML loader")
    publications {
        create<MavenPublication>("plugin") {
            from(components["kotlin"])
        }
    }
}