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

import dev.karmakrafts.kcml.agent.proto.A2SHandshake
import dev.karmakrafts.kcml.agent.proto.S2AHandshake
import dev.karmakrafts.kcml.agent.proto.io.AgentPacketHandler
import dev.karmakrafts.kcml.agent.proto.io.AgentPacketReader
import dev.karmakrafts.kcml.agent.proto.io.AgentPacketWriter
import dev.karmakrafts.kcml.api.ExperimentalKcmlApi
import dev.karmakrafts.kcml.api.extension.AbstractExtension
import dev.karmakrafts.kcml.api.extension.ExtensionId
import dev.karmakrafts.kcml.api.extension.SyntaxExtension
import dev.karmakrafts.kcml.api.extension.SyntaxExtension.GrammarRule
import dev.karmakrafts.kcml.api.extension.SyntaxExtension.ParsingContext
import dev.karmakrafts.kcml.api.frontend.SyntaxFrontend
import dev.karmakrafts.kcml.api.plugin.CompilerPlugin
import dev.karmakrafts.kcml.api.plugin.InitializationContext
import dev.karmakrafts.kcml.api.plugin.Plugin
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.com.intellij.lexer.DelegateLexer
import org.jetbrains.kotlin.com.intellij.lexer.Lexer
import org.jetbrains.kotlin.com.intellij.psi.PsiErrorElement
import org.jetbrains.kotlin.com.intellij.psi.tree.IElementType
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

@Plugin("syntax_integration_test")
class SyntaxIntegrationTestPlugin : CompilerPlugin {
    var lexerCount: Int = 0
    var parseCount: Int = 0
    var delegationCount: Int = 0
    var lowerCount: Int = 0
    val rules: MutableSet<String> = linkedSetOf()

    @OptIn(ExperimentalKcmlApi::class)
    override fun init(context: InitializationContext) {
        context.extensionRegistry.register(SyntaxIntegrationTestExtension(this))
    }
}

@OptIn(ExperimentalKcmlApi::class)
@ExtensionId("syntax_integration_test")
private class SyntaxIntegrationTestExtension(private val plugin: SyntaxIntegrationTestPlugin) : AbstractExtension(),
    SyntaxExtension {
    override fun highlight(file: KtFile, frontend: SyntaxFrontend): List<SyntaxExtension.Highlighting> =
        error("Editor highlighting must not run during compilation")

    private fun assertFrontend(frontend: SyntaxFrontend) {
        assertEquals("syntax_integration_test", frontend.pluginId)
        assertSame(plugin, frontend.loader.getPlugin(frontend.pluginId))
        assertTrue(frontend.logger.name.isNotBlank())
        assertNotNull(frontend.config)
        assertNotNull(frontend.messageCollector)
    }

    override fun createLexer(delegate: Lexer, frontend: SyntaxFrontend): Lexer {
        assertFrontend(frontend)
        if (plugin.lexerCount++ == 0) frontend.logger.info("Syntax integration lexer active")
        return object : DelegateLexer(delegate) {
            override fun getTokenType(): IElementType? =
                if (delegate.tokenType == KtTokens.IDENTIFIER && delegate.tokenText == "fn") KtTokens.FUN_KEYWORD
                else delegate.tokenType
        }
    }

    override fun parse(context: ParsingContext, frontend: SyntaxFrontend): Boolean {
        assertFrontend(frontend)
        plugin.rules += context.rule.id
        if (context.rule != GrammarRule.EXPRESSION) return false
        val builder = context.builder
        if (builder.tokenType == KtTokens.LPAR) {
            ++plugin.delegationCount
            return context.parseKotlin()
        }
        if (builder.tokenType != KtTokens.IDENTIFIER || builder.tokenText != "magic") return false
        val marker = builder.mark()
        builder.advanceLexer()
        marker.done(KtNodeTypes.INTEGER_CONSTANT)
        ++plugin.parseCount
        return true
    }

    override fun lower(file: KtFile, frontend: SyntaxFrontend): KtFile {
        assertFrontend(frontend)
        ++plugin.lowerCount
        val errors = PsiTreeUtil.collectElementsOfType(file, PsiErrorElement::class.java)
        assertTrue(errors.isEmpty(), errors.joinToString { it.errorDescription })
        val original = file.text
        val lowered = KtPsiFactory(file.project).createFile(
            file.name, original.replace(Regex("\\bfn\\b"), "fun").replace(Regex("\\bmagic\\b"), "42")
        )
        assertNotSame(file, lowered)
        assertEquals(original, file.text)
        return lowered
    }
}

class SyntaxExtensionIntegrationTest {
    @Test
    fun `syntax extensions compile nested expressions without leaking into the next invocation`() {
        val timeoutMillis = TimeUnit.MINUTES.toMillis(3)
        val directory = Files.createTempDirectory(Path.of("build"), "syntax-integration-").toAbsolutePath()
        try {
            val compilerPlugin = checkNotNull(System.getProperty("kcml.compiler.plugin.jar"))
            val runtimeLoader = directory.resolve("loader.jar")
            val bridge = directory.resolve("bridge.jar")
            ZipFile(compilerPlugin).use { archive ->
                for ([entry, path] in listOf("kcml-loader.jar" to runtimeLoader, "kcml-mixin-bridge.jar" to bridge)) {
                    archive.getInputStream(archive.getEntry(entry)).use { input -> Files.copy(input, path) }
                }
            }
            val plugin = directory.resolve("plugin.jar")
            JarOutputStream(Files.newOutputStream(plugin)).use { archive ->
                val classEntry = SyntaxIntegrationTestPlugin::class.java.name.replace('.', '/') + ".class"
                archive.putNextEntry(JarEntry(classEntry))
                SyntaxIntegrationTestPlugin::class.java.classLoader.getResourceAsStream(classEntry)!!.use { input ->
                    input.copyTo(archive)
                }
                archive.closeEntry()
            }
            ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
                server.soTimeout = timeoutMillis.toInt()
                val handshake = CompletableFuture.runAsync {
                    server.accept().use { socket ->
                        socket.soTimeout = timeoutMillis.toInt()
                        val writer = AgentPacketWriter(socket.getOutputStream())
                        val handler = AgentPacketHandler(A2SHandshake.serializer()) { request ->
                            writer.write(
                                S2AHandshake.serializer(), S2AHandshake(
                                    request.signature, runtimeLoader.toString(), bridge.toString(), listOf(plugin.toString())
                                )
                            )
                        }
                        AgentPacketReader(socket.getInputStream()).read { type -> handler.takeIf { it.type == type } }
                    }
                }
                val log = directory.resolve("compiler.log")
                val classpath =
                    compilerPlugin + File.pathSeparator + checkNotNull(System.getProperty("kcml.test.classpath"))
                val process = ProcessBuilder(
                    Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-Duser.home=${directory.resolve("home")}",
                    "-cp", classpath,
                    SyntaxIntegrationTestProcess::class.java.name,
                    server.localPort.toString(), plugin.toString(), directory.toString(), compilerPlugin
                ).redirectErrorStream(true).redirectOutput(log.toFile()).start()
                try {
                    assertTrue(
                        process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS),
                        "Syntax integration timed out after ${timeoutMillis}ms: ${Files.readString(log)}"
                    )
                    assertEquals(0, process.exitValue(), Files.readString(log))
                    handshake.get(10, TimeUnit.SECONDS)
                }
                finally {
                    process.destroyForcibly().waitFor()
                }
            }
        }
        finally {
            directory.toFile().deleteRecursively()
        }
    }
}

@OptIn(ExperimentalCompilerApi::class, ExperimentalKcmlApi::class)
object SyntaxIntegrationTestProcess {
    @JvmStatic
    fun main(arguments: Array<String>) {
        val directory = Path.of(arguments[2])
        val compiler = K2JVMCompiler()
        fun compile(name: String, text: String, options: List<String> = emptyList()): Path {
            val source = directory.resolve("$name.kt")
            Files.writeString(source, text)
            val classes = directory.resolve(name)
            val output = ByteArrayOutputStream()
            val result = PrintStream(output).use { stream ->
                compiler.exec(
                    stream, *(listOf(
                        "-no-stdlib", "-no-reflect", "-classpath",
                        Path.of(Unit::class.java.protectionDomain.codeSource.location.toURI()).toString(),
                        "-d", classes.toString(), source.toString()
                    ) + options).toTypedArray()
                )
            }
            assertEquals(ExitCode.OK, result, output.toString())
            assertTrue(!output.toString().contains("error:", ignoreCase = true), output.toString())
            return classes
        }

        fun assertAnswer(classes: Path, className: String, expected: Int) {
            URLClassLoader(arrayOf(classes.toUri().toURL()), javaClass.classLoader).use { loader ->
                assertEquals(expected, loader.loadClass(className).getMethod("answer").invoke(null))
            }
        }

        val classes = compile(
            "Syntax", """
                class Holder {
                    val value: Int = (magic)
                    fn member(): Int {
                        fn local(): Int = (magic)
                        return local()
                    }
                }
                fn answer(): Int { return (0 + magic) }
            """.trimIndent(), listOf(
                "-Xplugin=${arguments[3]}",
                "-P", "plugin:kcml.bootstrap:compileTarget=jvm",
                "-P", "plugin:kcml.bootstrap:agentCommPort=${arguments[0]}",
                "-P", "plugin:kcml.bootstrap:pluginClasspaths=${arguments[1]}"
            )
        )
        assertAnswer(classes, "SyntaxKt", 42)
        val loaderClass = Class.forName("dev.karmakrafts.kcml.plugin.PluginLoaderImpl")
        val loader = loaderClass.getField("INSTANCE").get(null)
        val plugin = loaderClass.getMethod("getPlugin", String::class.java)
            .invoke(loader, "syntax_integration_test") as SyntaxIntegrationTestPlugin
        assertTrue(plugin.lexerCount > 0)
        assertTrue(plugin.parseCount > 0)
        assertTrue(plugin.delegationCount > 0)
        assertTrue(plugin.lowerCount > 0)
        assertTrue(plugin.rules.containsAll(listOf(
            GrammarRule.TOP_LEVEL_DECLARATION, GrammarRule.MEMBER_DECLARATION, GrammarRule.LOCAL_DECLARATION,
            GrammarRule.STATEMENT, GrammarRule.EXPRESSION, GrammarRule.TYPE
        ).map { it.id }), plugin.rules.toString())
        val counts = listOf(plugin.lexerCount, plugin.parseCount, plugin.delegationCount, plugin.lowerCount)

        val plainClasses = compile(
            "PlainSyntax", """
                fun magic(): Int = 7
                fun answer(): Int { val fn = magic(); return fn }
            """.trimIndent()
        )
        assertAnswer(plainClasses, "PlainSyntaxKt", 7)
        assertTrue((loaderClass.getMethod("allPlugins").invoke(loader) as Collection<*>).isEmpty())
        assertEquals(counts, listOf(plugin.lexerCount, plugin.parseCount, plugin.delegationCount, plugin.lowerCount))
    }
}