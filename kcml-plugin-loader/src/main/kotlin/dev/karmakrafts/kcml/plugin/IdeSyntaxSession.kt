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
import dev.karmakrafts.kcml.api.extension.SyntaxExtension.GrammarRule
import dev.karmakrafts.kcml.api.extension.SyntaxExtension.Highlighting
import org.jetbrains.kotlin.com.intellij.lang.PsiBuilder
import org.jetbrains.kotlin.com.intellij.lexer.Lexer
import org.jetbrains.kotlin.psi.KtFile

@InternalKcmlApi
@OptIn(ExperimentalKcmlApi::class)
class IdeSyntaxSession internal constructor(private val state: CompilationState) : AutoCloseable {
    private var closed: Boolean = false

    val hasExtensions: Boolean
        get() = invoke { PluginLoaderImpl.syntaxAdapterOrNull != null }

    val extensionIds: List<String>
        get() = invoke { PluginLoaderImpl.syntaxAdapterOrNull?.extensionIds.orEmpty() }

    @Synchronized
    fun <T> invoke(action: CompilationState.() -> T): T {
        check(!closed) { "IDE syntax session has been closed" }
        return PluginLoaderImpl.withState(state, action)
    }

    fun createLexer(delegate: Lexer): Lexer = invoke {
        PluginLoaderImpl.syntaxAdapterOrNull?.createLexer(delegate) ?: delegate
    }

    fun parse(builder: PsiBuilder, rule: GrammarRule, parseKotlin: (GrammarRule) -> Boolean): Boolean = invoke {
        PluginLoaderImpl.syntaxAdapterOrNull?.parse(builder, rule, parseKotlin) ?: false
    }

    fun lower(file: KtFile): KtFile = invoke { PluginLoaderImpl.syntaxAdapterOrNull?.lower(file) ?: file }

    fun highlight(file: KtFile): List<Highlighting> = invoke {
        PluginLoaderImpl.syntaxAdapterOrNull?.highlight(file).orEmpty()
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        state.pluginClassLoader?.close()
        state.pluginClassLoader = null
    }
}