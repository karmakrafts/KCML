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

package dev.karmakrafts.kcml

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.messageCollector
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.div

@OptIn(ExperimentalPathApi::class)
internal object KCMLBootstrap {
    private lateinit var messageCollector: MessageCollector
    val tempDirectory: Path = Files.createTempDirectory("kcml")

    fun init(config: CompilerConfiguration) {
        messageCollector = config.messageCollector
    }

    fun cleanup() {
        tempDirectory.deleteRecursively()
    }

    private fun unpackLoaderJar(): Path {
        val targetPath = tempDirectory / "loader.jar"
        this::class.java.getResourceAsStream("/kcml-loader.jar")?.use { stream ->
            Files.copy(stream, targetPath, StandardCopyOption.REPLACE_EXISTING)
        } ?: error("Could not unpack kcml-loader.jar")
        messageCollector.report(CompilerMessageSeverity.INFO, "Unpacked loader JAR to $targetPath")
        return targetPath
    }

    fun injectLoader() {
        try {
            val compilerClassLoader = this::class.java.classLoader.parent as? URLClassLoader ?: return
            val clClass = compilerClassLoader::class.java
            val method = clClass.declaredMethods.first { method -> method.name == "addURL" }
            val url = unpackLoaderJar().toUri().toURL()
            method.isAccessible = true
            method.invoke(compilerClassLoader, url)
            method.isAccessible = false
            messageCollector.report(CompilerMessageSeverity.INFO, "Injected $url into compiler classpath")
        } catch (error: Throwable) {
            error("Could not inject KCML loader into compiler classpath: ${error.stackTraceToString()}")
        }
    }
}