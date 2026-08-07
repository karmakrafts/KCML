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
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.div
import kotlin.io.path.exists

@OptIn(ExperimentalPathApi::class)
internal object KCMLBootstrap {
    private lateinit var messageCollector: MessageCollector
    private val initializationLock: Any = Any()
    private val initializationSet: MutableSet<CompilerConfiguration> = Collections.newSetFromMap(IdentityHashMap())
    private var isCleanedUp: Boolean = false
    val tempDirectory: Path = Files.createTempDirectory("kcml")

    lateinit var loaderPath: Path
        private set

    private fun log(message: String) =
        messageCollector.report(CompilerMessageSeverity.INFO, "[KCML Bootstrap] $message")

    fun init(configuration: CompilerConfiguration) = synchronized(initializationLock) {
        if (configuration in initializationSet) return@synchronized
        messageCollector = configuration.messageCollector
        log("Bootstrapping KCML..")
        unpackLoaderJar()
        // This may occur very late when the Gradle/Kotlin daemon is stopped, but we need it available for a long time
        Runtime.getRuntime().addShutdownHook(Thread(::cleanup))
        val compilerClassLoader = configuration::class.java.classLoader
        log("Compiler ClassLoader is $compilerClassLoader")
        try {
            // Try to resolve the PluginLoaderImpl class from the loader JAR to check if it is already injected
            log("Checking for loader presence")
            Class.forName("dev.karmakrafts.kcml.plugin.PluginLoaderImpl", false, compilerClassLoader)
            log("Found loader on classpath, skipping runtime injection")
            // If we fall through here, no need to inject anything
        } catch (_: Throwable) {
            // The fallback method of injecting the loader for early bootstrap for direct compiler invocations
            log("KCML loader not found on classpath, injecting at runtime")
            injectLoader(compilerClassLoader)
        }
        initializationSet += configuration
    }

    private fun cleanup() = synchronized(initializationLock) {
        if (isCleanedUp) return@synchronized
        log("Cleaning up what KCML left behind..")
        tempDirectory.deleteRecursively()
        isCleanedUp = true
    }

    private fun unpackLoaderJar() {
        loaderPath = tempDirectory / "loader.jar"
        if (loaderPath.exists()) return
        log("Unpacking loader JAR to $loaderPath")
        this::class.java.getResourceAsStream("/kcml-loader.jar")?.use { stream ->
            Files.copy(stream, loaderPath, StandardCopyOption.REPLACE_EXISTING)
        } ?: error("Could not unpack kcml-loader.jar")
        log("Unpacked ${Files.size(loaderPath)} bytes to $loaderPath")
    }

    /**
     * This is only for our direct entrypoint via KCMLCommandLineProcessor.
     * Any subsequent tasks that do not use the plugin pipeline will inherit
     * the loader in their compiler CP via a direct injection.
     * See `CLICompilerTransformer` in the agent.
     */
    private fun injectLoader(classLoader: ClassLoader) {
        try {
            val urlClassLoader = classLoader as? URLClassLoader ?: return
            log("Target ClassLoader is a URLClassLoader, appending loader URL")
            val clClass = urlClassLoader::class.java
            val method = clClass.declaredMethods.first { method -> method.name == "addURL" }
            val url = loaderPath.toUri().toURL()
            method.isAccessible = true
            method.invoke(urlClassLoader, url)
            method.isAccessible = false
            log("Appended KCML loader URL to classpath of ClassLoader")
        } catch (error: Throwable) {
            error("Could not inject KCML loader into compiler classpath: ${error.stackTraceToString()}")
        }
    }
}