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
                CompletableFuture.runAsync {
                    val workerPlugins = PluginLoaderImpl.allPlugins()
                    val lexer = KotlinLexer()
                    assertSame(lexer, session.createLexer(lexer))
                    assertEquals(workerPlugins, PluginLoaderImpl.allPlugins())
                }.get(10, TimeUnit.SECONDS)
                PluginLoaderImpl.loadForIde(config, emptyList()).use { empty ->
                    assertTrue(!empty.hasExtensions)
                    session.createLexer(KotlinLexer())
                }
            }
            assertTrue(closed)
            assertFailsWith<IllegalStateException> { session.createLexer(KotlinLexer()) }
            assertEquals(originalPlugins, PluginLoaderImpl.allPlugins())
        }
        finally {
            Files.deleteIfExists(jar)
        }
    }
}