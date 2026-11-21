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
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.intelliJPlatform)
    signing
}

configureJava(rootProject.libs.versions.java)

repositories {
    mavenCentral()
    mavenLocal()
    maven("https://central.sonatype.com/repository/maven-snapshots")
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    testImplementation(libs.kotlin.test)
    testImplementation(projects.kcmlPluginApi) {
        // Fixtures only need the annotations; compiler and serialization dependencies conflict with the IDE.
        isTransitive = false
    }

    intellijPlatform {
        intellijIdea(libs.versions.intelliJVersion)
        bundledPlugin("com.intellij.java")
        bundledPlugin("com.intellij.modules.json")
        bundledPlugin("org.jetbrains.kotlin")
        plugin("org.jetbrains.android", libs.versions.intelliJAndroid.get())
        plugin("com.android.tools.design", libs.versions.intelliJAndroidDesignTools.get())
        plugin("org.jetbrains.kotlin-toolchain", libs.versions.intelliJKotlinToolchain.get())
        plugin("com.jetbrains.kmm", libs.versions.intelliJKotlinMultiplatform.get())
        plugin("androidx.compose.plugins.idea", libs.versions.intelliJJetpackCompose.get())
        plugin("com.intellij.nativeDebug", libs.versions.intelliJNativeDebugging.get())
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.Plugin.Java)
    }
}

tasks {
    test {
        classpath = classpath.filter {
            it.name != "kotlinc.kotlin-compiler-cli.jar" || !it.path.contains("/kmm-plugin/")
        }
        // IDE extensions also resolve runtime artifacts outside the fixture's module libraries.
        systemProperty("vfs.additional-allowed-roots", classpath.files
            .map { if (it.isFile) it.parentFile else it }
            .distinct()
            .joinToString(File.pathSeparator) { it.canonicalPath })
    }

    runIde {
        // Automatically enable native wayland support when the host is using Wayland
        if (System.getenv("XDG_SESSION_TYPE") == "wayland") {
            jvmArgs("-Dawt.toolkit.name=WLToolkit", "-Dsun.java2d.vulkan=true")
        }
    }
}