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
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.div
import kotlin.io.path.writeText

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
    sourceSets {
        main {
            resources.srcDir("build/generated")
        }
    }
}

dependencies {
    compileOnly(libs.kotlin.compiler.embeddable)
    compileOnly(libs.kotlin.native.compiler.embeddable)
    compileOnly(projects.kcmlLoader) // Injected into compiler CP at runtime and by injection
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
        val agentShadowJarTask = project(":kcml-agent").tasks.named<Jar>("shadowJar")
        val agentJarTask = project(":kcml-agent").tasks.named<Jar>("jar")
        val loaderShadowJarTask = project(":kcml-loader").tasks.named<Jar>("shadowJar")
        val loaderJarTask = project(":kcml-loader").tasks.named<Jar>("jar")
        dependsOn(agentShadowJarTask, agentJarTask, loaderJarTask, loaderJarTask)
        from(agentShadowJarTask) { rename { "kcml-agent.jar" } }
        from(loaderShadowJarTask) { rename { "kcml-loader.jar" } }
    }
    val version = version.toString()
    val createVersionFile = register("createVersionFile") {
        group = "build"
        description = "Generate the version file embedded in the finished plugin JAR"
        inputs.file(layout.buildDirectory.asFile.get().toPath() / "generated" / "kcml.version")
        inputs.property("version", version)
        doFirst {
            val path = inputs.files.singleFile.toPath()
            path.deleteIfExists()
            path.parent.createDirectories()
            path.writeText(version)
        }
        outputs.upToDateWhen { false } // Always re-generate this file
    }
    processResources {
        dependsOn(createVersionFile, "kaptKotlin")
    }
    compileKotlin { dependsOn(processResources) }
}

publishing {
    setProjectInfo(
        name = "KCML Compiler Plugin",
        description = "KCML compiler plugin for bootstrapping the KCML loader",
        url = "https://git.karmakrafts.dev/kk/kcml"
    )
    publications {
        create<MavenPublication>("compilerPlugin") {
            from(components["kotlin"])
        }
    }
}