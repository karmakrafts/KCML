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
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.intelliJPlatform)
    signing
}

configureJava(rootProject.libs.versions.java)

val hostPlatform = run {
    val hostOs = OperatingSystem.current()
    val os = when {
        hostOs.isLinux -> "linux"
        hostOs.isMacOsX -> "mac"
        hostOs.isWindows -> "windows"
        else -> error("Unsupported plugin host OS: $hostOs")
    }
    val hostArch = providers.systemProperty("os.arch").get()
    val arch = when (hostArch.lowercase()) {
        "amd64", "x86_64" -> "x86_64"
        "aarch64", "arm64" -> "arm64"
        else -> error("Unsupported plugin host architecture: $hostArch")
    }
    "$os-$arch"
}

val syntaxTestPlugins = configurations.create("syntaxTestPlugins") {
    isCanBeConsumed = false
}

repositories {
    mavenCentral()
    mavenLocal()
    maven("https://central.sonatype.com/repository/maven-snapshots")
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation(project(path = ":kcml-plugin-loader", configuration = "ideaRuntimeElements"))
    testImplementation(libs.kotlin.test)
    syntaxTestPlugins(projects.kcmlExamplePlugin)

    intellijPlatform {
        intellijIdea(libs.versions.intelliJVersion)
        bundledPlugin("com.intellij.java")
        bundledPlugin("com.intellij.modules.json")
        bundledPlugin("org.jetbrains.kotlin")
        plugin("org.jetbrains.android", "${libs.versions.intelliJAndroid.get()}-$hostPlatform")
        plugin("com.android.tools.design", libs.versions.intelliJAndroidDesignTools.get())
        plugin("org.jetbrains.kotlin-toolchain", libs.versions.intelliJKotlinToolchain.get())
        plugin("com.jetbrains.kmm", libs.versions.intelliJKotlinMultiplatform.get())
        plugin("androidx.compose.plugins.idea", libs.versions.intelliJJetpackCompose.get())
        plugin("com.intellij.nativeDebug", "${libs.versions.intelliJNativeDebugging.get()}-$hostPlatform")
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.Plugin.Java)
    }
}

tasks {
    test {
        dependsOn(syntaxTestPlugins)
        inputs.files(syntaxTestPlugins).withPropertyName("syntaxTestPlugins").withNormalizer(ClasspathNormalizer::class)
        systemProperty(
            "kcml.test.examplePlugin",
            syntaxTestPlugins.files.single { it.name.startsWith("kcml-example-plugin-") }.absolutePath
        )
        systemProperty("kcml.test.examplePluginClasspath", syntaxTestPlugins.files.joinToString(";") { it.absolutePath })
        classpath = classpath.filter {
            it.name != "kotlinc.kotlin-compiler-cli.jar" || !it.path.contains("/kmm-plugin/")
        }
        // IDE extensions resolve resources from installed sandbox copies as well as classpath artifacts.
        systemProperty("vfs.additional-allowed-roots", classpath.files
            .map { if (it.isFile) it.parentFile else it }
            .plus(named<PrepareSandboxTask>("prepareTestSandbox").get().sandboxPluginsDirectory.get().asFile)
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