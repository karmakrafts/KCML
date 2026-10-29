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

import dev.karmakrafts.kcml.util.EmbeddedJar
import dev.karmakrafts.kcml.util.FileLock
import dev.karmakrafts.kcml.util.KCMLVersion
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.MessageCollectorAccess
import org.jetbrains.kotlin.config.messageCollector
import java.net.URLClassLoader
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.div

internal object KCMLBootstrap {
    private val userDir: Path by lazy { Path(System.getProperty("user.home")) }
    private val kcmlDir: Path by lazy { userDir / ".kcml" }
    val kcmlVersionDir: Path by lazy { (kcmlDir / KCMLVersion.version).also { path -> path.createDirectories() } }
    val loaderPath: Path by lazy { kcmlVersionDir / "loader.jar" }
    val bridgePath: Path by lazy { kcmlVersionDir / "bridge.jar" }
    val lock: Any = Any()

    private fun MessageCollector.log(message: String) =
        report(CompilerMessageSeverity.INFO, "[KCML Bootstrap] $message")

    @OptIn(MessageCollectorAccess::class)
    fun init(configuration: CompilerConfiguration) = synchronized(lock) {
        val lockPath = kcmlVersionDir / "bootstrap.lock"
        val messageCollector = configuration.messageCollector
        messageCollector.log("Acquiring KCML bootstrap lock at $lockPath")
        FileLock(lockPath).use { lock ->
            lock.withLock {
                messageCollector.log("Bootstrapping KCML ${KCMLVersion.version}..")
                EmbeddedJar("/kcml-loader.jar") { messageCollector.log(it) }.unpackIfNeeded(loaderPath)
                EmbeddedJar("/kcml-mixin-bridge.jar") { messageCollector.log(it) }.unpackIfNeeded(bridgePath)
                val compilerClassLoader = configuration::class.java.classLoader
                messageCollector.log("Compiler ClassLoader is $compilerClassLoader")
                try {
                    // Try to resolve the PluginLoaderImpl class from the loader JAR to check if it is already injected
                    messageCollector.log("Checking for loader presence")
                    Class.forName("dev.karmakrafts.kcml.plugin.PluginLoaderImpl", false, compilerClassLoader)
                    messageCollector.log("Found loader on classpath, skipping runtime injection")
                    // If we fall through here, no need to inject anything
                } catch (_: Throwable) {
                    // The fallback method of injecting the loader for early bootstrap for direct compiler invocations
                    messageCollector.log("KCML loader not found on classpath, injecting at runtime")
                    injectLoader(compilerClassLoader, messageCollector)
                }
            }
        }
        messageCollector.log("Releasing KCML bootstrap lock at $lockPath")
    }

    /**
     * This is only for our direct entrypoint via KCMLCommandLineProcessor.
     * Any subsequent tasks that do not use the plugin pipeline will inherit
     * the loader in their compiler CP via a direct injection.
     * See `CLICompilerTransformer` in the agent.
     */
    private fun injectLoader(classLoader: ClassLoader, messageCollector: MessageCollector) {
        try {
            val urlClassLoader = classLoader as? URLClassLoader ?: return
            messageCollector.log("Target ClassLoader is a URLClassLoader, appending loader URL")
            val clClass = urlClassLoader::class.java
            val method = clClass.declaredMethods.first { method -> method.name == "addURL" }
            method.isAccessible = true
            method.invoke(urlClassLoader, loaderPath.toUri().toURL())
            method.isAccessible = false
            messageCollector.log("Appended KCML loader URL to classpath of ClassLoader")
        } catch (error: Throwable) {
            error("Could not inject KCML loader into compiler classpath: ${error.stackTraceToString()}")
        }
    }
}