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
import dev.karmakrafts.kcml.api.extension.syntax.Completion
import dev.karmakrafts.kcml.api.extension.syntax.Inspection
import dev.karmakrafts.kcml.api.extension.syntax.InspectionMode
import dev.karmakrafts.kcml.api.extension.syntax.InspectionRule
import dev.karmakrafts.kcml.api.extension.syntax.InspectionSeverity
import dev.karmakrafts.kcml.api.extension.syntax.SyntaxExtension
import dev.karmakrafts.kcml.api.frontend.SyntaxFrontend
import dev.karmakrafts.kcml.api.plugin.CompilerPlugin
import dev.karmakrafts.kcml.api.plugin.InitializationContext
import dev.karmakrafts.kcml.api.plugin.Plugin
import dev.karmakrafts.kcml.util.kcmlCompileTarget
import org.jetbrains.kotlin.CoreEnvironmentDeprecation
import org.jetbrains.kotlin.cli.extensionsStorage
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.setupIdeaStandaloneExecution
import org.jetbrains.kotlin.com.intellij.lexer.Lexer
import org.jetbrains.kotlin.com.intellij.openapi.application.ApplicationManager
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar.ExtensionStorage
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfiguration.Internals
import org.jetbrains.kotlin.lexer.KotlinLexer
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
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
import kotlin.test.assertNull
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

    override fun complete(file: KtFile, offset: Int, smart: Boolean, frontend: SyntaxFrontend): List<Completion>? {
        assertEquals("ide_syntax_test", frontend.pluginId)
        assertTrue(frontend.loader.getPlugin(frontend.pluginId) is IdeSyntaxTestPlugin)
        assertEquals("val value = 0", file.text)
        if (offset == 0) return null
        assertEquals(file.textLength, offset)
        return if (smart) emptyList() else listOf(Completion("custom", "custom()", "()", "Unit", file.declarations.single()))
    }

    override fun inspectionRules(file: KtFile, frontend: SyntaxFrontend): List<InspectionRule> {
        assertEquals("ide_syntax_test", frontend.pluginId)
        assertTrue(frontend.loader.getPlugin(frontend.pluginId) is IdeSyntaxTestPlugin)
        assertEquals("val value = 0", file.text)
        val node = file.declarations.single() as KtProperty
        return listOf(InspectionRule(node.nameIdentifier!!), InspectionRule(node.initializer!!, InspectionMode.SUPPRESS))
    }

    override fun inspect(file: KtFile, frontend: SyntaxFrontend): List<Inspection> {
        assertEquals("ide_syntax_test", frontend.pluginId)
        assertTrue(frontend.loader.getPlugin(frontend.pluginId) is IdeSyntaxTestPlugin)
        assertEquals("val value = 0", file.text)
        val node = file.declarations.single() as KtProperty
        return listOf(
            Inspection(node.nameIdentifier!!, "Prefer foo", InspectionSeverity.ERROR),
            Inspection(node.initializer!!, "Prefer one")
        )
    }
}

@OptIn(
    Internals::class,
    InternalKcmlApi::class,
    ExperimentalKcmlApi::class,
    CoreEnvironmentDeprecation::class,
    ExperimentalCompilerApi::class
)
class IdeSyntaxSessionTest {
    @Test
    fun `IDE sessions restore compiler state across threads and close their classloader`() {
        val jar = Files.createTempFile("kcml-ide-syntax", ".jar")
        val resource = IdeSyntaxTestPlugin::class.java.name.replace('.', '/') + ".class"
        val disposable = Disposer.newDisposable()
        setupIdeaStandaloneExecution()
        try {
            val environment = KotlinCoreEnvironment.createForProduction(
                disposable,
                CompilerConfiguration().apply { extensionsStorage = ExtensionStorage() },
                EnvironmentConfigFiles.JVM_CONFIG_FILES
            )
            val file = KtPsiFactory(environment.project).createFile("val value = 0")
            val node = file.declarations.single() as KtProperty
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
                    assertNull(session.complete(file, 0, false))
                    assertEquals(emptyList(), session.complete(file, file.textLength, true))
                    assertEquals(
                        listOf(Completion("custom", "custom()", "()", "Unit", node)),
                        session.complete(file, file.textLength, false)
                    )
                    assertEquals(
                        listOf(
                            InspectionRule(node.nameIdentifier!!),
                            InspectionRule(node.initializer!!, InspectionMode.SUPPRESS)
                        ),
                        session.inspectionRules(file)
                    )
                    assertEquals(
                        listOf(
                            Inspection(node.nameIdentifier!!, "Prefer foo", InspectionSeverity.ERROR),
                            Inspection(node.initializer!!, "Prefer one")
                        ),
                        session.inspect(file)
                    )
                    val ranges = listOf(TextRange(4, 9), TextRange(12, 13))
                    assertEquals(ranges, session.inspectionRules(file).map { it.range })
                    assertEquals(ranges, session.inspect(file).map { it.range })
                    assertSame(node, session.complete(file, file.textLength, false)!!.single().element)
                    assertSame(node.nameIdentifier, session.inspectionRules(file).first().element)
                    assertSame<org.jetbrains.kotlin.com.intellij.psi.PsiElement?>(node.initializer, session.inspect(file).last().element)
                    assertEquals("val value = 0", file.text)
                    assertEquals(workerPlugins, PluginLoaderImpl.allPlugins())
                    assertEquals(workerExtensionIds, PluginLoaderImpl.syntaxAdapterOrNull?.extensionIds.orEmpty())
                }.get(10, TimeUnit.SECONDS)
                PluginLoaderImpl.loadForIde(config, emptyList()).use { empty ->
                    assertTrue(!empty.hasExtensions)
                    assertEquals(emptyList(), empty.extensionIds)
                    assertNull(empty.complete(file, 0, false))
                    assertNull(empty.complete(file, file.textLength, true))
                    assertEquals(emptyList(), empty.inspectionRules(file))
                    assertEquals(emptyList(), empty.inspect(file))
                    assertEquals(expectedExtensionIds, session.extensionIds)
                    session.createLexer(KotlinLexer())
                    assertEquals(2, session.inspectionRules(file).size)
                    assertEquals(2, session.inspect(file).size)
                    assertEquals(emptyList(), empty.inspectionRules(file))
                    assertEquals(emptyList(), empty.inspect(file))
                    assertEquals(emptyList(), empty.extensionIds)
                }
                assertEquals(expectedExtensionIds, session.extensionIds)
            }
            assertTrue(closed)
            assertFailsWith<IllegalStateException> { session.createLexer(KotlinLexer()) }
            assertFailsWith<IllegalStateException> { session.complete(file, 0, false) }
            assertFailsWith<IllegalStateException> { session.inspectionRules(file) }
            assertFailsWith<IllegalStateException> { session.inspect(file) }
            assertFailsWith<IllegalStateException> { session.extensionIds }
            assertEquals(originalPlugins, PluginLoaderImpl.allPlugins())
            assertEquals(originalExtensionIds, PluginLoaderImpl.syntaxAdapterOrNull?.extensionIds.orEmpty())
        }
        finally {
            Files.deleteIfExists(jar)
            ApplicationManager.getApplication().runWriteAction { Disposer.dispose(disposable) }
        }
    }

    @Test
    fun `concurrent non-empty IDE session loads are isolated`() {
        val workerCount = 4
        val resource = IdeSyntaxTestPlugin::class.java.name.replace('.', '/') + ".class"
        val jars = List(workerCount) { Files.createTempFile("kcml-ide-syntax-concurrent", ".jar") }
        val closedFlags = List(workerCount) { AtomicBoolean(false) }
        val executor = Executors.newFixedThreadPool(workerCount)
        val preparationExecutor = Executors.newFixedThreadPool(2)
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
                    val session = PluginLoaderImpl.loadForIde(
                        config, listOf(jar.toUri().toURL()), emptyList(), { urls, parent ->
                            object : URLClassLoader(urls, parent) {
                                override fun close() {
                                    closedFlags[index].set(true)
                                    super.close()
                                }
                            }
                        }, preparationExecutor
                    )
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
            sessions.forEach { session ->
                try {
                    session.close()
                } catch(_: Throwable) {}
            }
            executor.shutdownNow()
            preparationExecutor.shutdownNow()
            jars.forEach(Files::deleteIfExists)
        }
    }
}