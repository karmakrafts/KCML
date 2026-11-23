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

package dev.karmakrafts.kcml.plugin

import dev.karmakrafts.kcml.api.ExperimentalKcmlApi
import dev.karmakrafts.kcml.api.InternalKcmlApi
import dev.karmakrafts.kcml.api.extension.AbstractExtension
import dev.karmakrafts.kcml.api.extension.ExtensionId
import dev.karmakrafts.kcml.api.extension.SyntaxExtension
import dev.karmakrafts.kcml.api.frontend.SyntaxFrontend
import dev.karmakrafts.kcml.api.plugin.CompilerPlugin
import dev.karmakrafts.kcml.api.plugin.InitializationContext
import dev.karmakrafts.kcml.api.plugin.Plugin
import dev.karmakrafts.kcml.util.kcmlCompileTarget
import org.jetbrains.kotlin.com.intellij.lexer.Lexer
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfiguration.Internals
import org.jetbrains.kotlin.lexer.KotlinLexer
import java.net.URLClassLoader
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.TimeUnit
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

@Plugin("ide_syntax_test")
class IdeSyntaxTestPlugin : CompilerPlugin {
    override fun init(context: InitializationContext) {
        context.extensionRegistry.register(IdeSyntaxTestExtension())
    }
}

@OptIn(ExperimentalKcmlApi::class)
@ExtensionId("syntax")
private class IdeSyntaxTestExtension : AbstractExtension(), SyntaxExtension {
    override fun createLexer(delegate: Lexer, frontend: SyntaxFrontend): Lexer {
        assertEquals("ide_syntax_test", frontend.pluginId)
        assertTrue(frontend.loader.getPlugin(frontend.pluginId) is IdeSyntaxTestPlugin)
        return delegate
    }
}

@OptIn(Internals::class, InternalKcmlApi::class)
class IdeSyntaxSessionTest {
    @Test
    fun `IDE sessions restore compiler state across threads and close their classloader`() {
        val jar = Files.createTempFile("kcml-ide-syntax", ".jar")
        val resource = IdeSyntaxTestPlugin::class.java.name.replace('.', '/') + ".class"
        try {
            JarOutputStream(Files.newOutputStream(jar)).use { output ->
                output.putNextEntry(JarEntry(resource))
                IdeSyntaxTestPlugin::class.java.classLoader.getResourceAsStream(resource)!!
                    .use { input -> input.copyTo(output) }
                output.closeEntry()
            }
            val originalPlugins = PluginLoaderImpl.allPlugins()
            val originalExtensionIds = PluginLoaderImpl.syntaxAdapterOrNull?.extensionIds.orEmpty()
            val expectedExtensionIds = listOf("ide_syntax_test:syntax")
            var closed = false
            val config = CompilerConfiguration().apply { kcmlCompileTarget = "jvm" }
            val session = PluginLoaderImpl.loadForIde(config, listOf(jar.toUri().toURL())) { urls, parent ->
                object : URLClassLoader(urls, parent) {
                    override fun close() {
                        closed = true
                        super.close()
                    }
                }
            }
            session.use { session ->
                assertEquals(originalPlugins, PluginLoaderImpl.allPlugins())
                assertTrue(session.hasExtensions)
                assertEquals(expectedExtensionIds, session.extensionIds)
                assertEquals(originalExtensionIds, PluginLoaderImpl.syntaxAdapterOrNull?.extensionIds.orEmpty())
                CompletableFuture.runAsync {
                    val workerPlugins = PluginLoaderImpl.allPlugins()
                    val workerExtensionIds = PluginLoaderImpl.syntaxAdapterOrNull?.extensionIds.orEmpty()
                    val lexer = KotlinLexer()
                    assertEquals(expectedExtensionIds, session.extensionIds)
                    assertSame(lexer, session.createLexer(lexer))
                    assertEquals(workerPlugins, PluginLoaderImpl.allPlugins())
                    assertEquals(workerExtensionIds, PluginLoaderImpl.syntaxAdapterOrNull?.extensionIds.orEmpty())
                }.get(10, TimeUnit.SECONDS)
                PluginLoaderImpl.loadForIde(config, emptyList()).use { empty ->
                    assertTrue(!empty.hasExtensions)
                    assertEquals(emptyList(), empty.extensionIds)
                    assertEquals(expectedExtensionIds, session.extensionIds)
                    session.createLexer(KotlinLexer())
                    assertEquals(emptyList(), empty.extensionIds)
                }
                assertEquals(expectedExtensionIds, session.extensionIds)
            }
            assertTrue(closed)
            assertFailsWith<IllegalStateException> { session.createLexer(KotlinLexer()) }
            assertFailsWith<IllegalStateException> { session.extensionIds }
            assertEquals(originalPlugins, PluginLoaderImpl.allPlugins())
            assertEquals(originalExtensionIds, PluginLoaderImpl.syntaxAdapterOrNull?.extensionIds.orEmpty())
        }
        finally {
            Files.deleteIfExists(jar)
        }
    }

    @Test
    fun `concurrent non-empty IDE session loads are isolated`() {
        val workerCount = 4
        val resource = IdeSyntaxTestPlugin::class.java.name.replace('.', '/') + ".class"
        val jars = List(workerCount) { Files.createTempFile("kcml-ide-syntax-concurrent", ".jar") }
        val closedFlags = List(workerCount) { AtomicBoolean(false) }
        val executor = Executors.newFixedThreadPool(workerCount)
        val sessions = ArrayList<IdeSyntaxSession>()
        try {
            for (jar in jars) {
                JarOutputStream(Files.newOutputStream(jar)).use { output ->
                    output.putNextEntry(JarEntry(resource))
                    IdeSyntaxTestPlugin::class.java.classLoader.getResourceAsStream(resource)!!
                        .use { input -> input.copyTo(output) }
                    output.closeEntry()
                }
            }
            val originalPlugins = PluginLoaderImpl.allPlugins()
            val originalExtensionIds = PluginLoaderImpl.syntaxAdapterOrNull?.extensionIds.orEmpty()
            val expectedExtensionIds = listOf("ide_syntax_test:syntax")
            val ready = CountDownLatch(workerCount)
            val start = CountDownLatch(1)
            val futures = jars.mapIndexed { index, jar ->
                executor.submit<IdeSyntaxSession> {
                    val workerPlugins = PluginLoaderImpl.allPlugins()
                    val workerExtensionIds = PluginLoaderImpl.syntaxAdapterOrNull?.extensionIds.orEmpty()
                    val config = CompilerConfiguration().apply { kcmlCompileTarget = "jvm" }
                    ready.countDown()
                    check(start.await(10, TimeUnit.SECONDS)) { "Start signal timed out" }
                    val session = PluginLoaderImpl.loadForIde(config, listOf(jar.toUri().toURL())) { urls, parent ->
                        object : URLClassLoader(urls, parent) {
                            override fun close() {
                                closedFlags[index].set(true)
                                super.close()
                            }
                        }
                    }
                    try {
                        check(session.hasExtensions)
                        check(expectedExtensionIds == session.extensionIds) { "Unexpected IDs ${session.extensionIds}" }
                        check(workerPlugins == PluginLoaderImpl.allPlugins()) { "Worker plugins changed" }
                        check(workerExtensionIds == PluginLoaderImpl.syntaxAdapterOrNull?.extensionIds.orEmpty()) {
                            "Worker syntax adapter changed"
                        }
                        val lexer = KotlinLexer()
                        check(lexer === session.createLexer(lexer))
                        check(workerPlugins == PluginLoaderImpl.allPlugins()) { "Worker plugins changed after use" }
                    }
                    catch (error: Throwable) {
                        session.close()
                        throw error
                    }
                    session
                }
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            start.countDown()
            futures.forEach { sessions += it.get(30, TimeUnit.SECONDS) }
            assertEquals(workerCount, sessions.toSet().size)
            assertEquals(originalPlugins, PluginLoaderImpl.allPlugins())
            assertEquals(originalExtensionIds, PluginLoaderImpl.syntaxAdapterOrNull?.extensionIds.orEmpty())
            for (session in sessions) {
                assertTrue(session.hasExtensions)
                assertEquals(expectedExtensionIds, session.extensionIds)
                val lexer = KotlinLexer()
                assertSame(lexer, session.createLexer(lexer))
            }
            assertTrue(closedFlags.none { it.get() })
            sessions.first().close()
            assertTrue(closedFlags.first().get())
            for (session in sessions.drop(1)) assertEquals(expectedExtensionIds, session.extensionIds)
            sessions.forEach(IdeSyntaxSession::close)
            assertTrue(closedFlags.all { it.get() })
            for (session in sessions) assertFailsWith<IllegalStateException> { session.extensionIds }
            assertEquals(originalPlugins, PluginLoaderImpl.allPlugins())
            assertEquals(originalExtensionIds, PluginLoaderImpl.syntaxAdapterOrNull?.extensionIds.orEmpty())
        }
        finally {
            sessions.forEach { runCatching { it.close() } }
            executor.shutdownNow()
            jars.forEach(Files::deleteIfExists)
        }
    }
}