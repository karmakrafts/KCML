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

package dev.karmakrafts.kcml.extension

import dev.karmakrafts.kcml.api.ExperimentalKcmlApi
import dev.karmakrafts.kcml.api.InternalKcmlApi
import dev.karmakrafts.kcml.api.extension.SyntaxExtension
import dev.karmakrafts.kcml.api.extension.SyntaxExtension.GrammarRule
import dev.karmakrafts.kcml.api.frontend.SyntaxFrontend
import org.jetbrains.kotlin.com.intellij.lang.PsiBuilder
import org.jetbrains.kotlin.com.intellij.lexer.Lexer
import org.jetbrains.kotlin.psi.KtFile

@InternalKcmlApi
@OptIn(ExperimentalKcmlApi::class)
class SyntaxExtensionAdapter( // @formatter:off
    private val extensions: List<Pair<String, SyntaxExtension>>,
    frontendFactory: (String) -> SyntaxFrontend
) { // @formatter:on
    // @formatter:off
    private val frontends: Map<String, SyntaxFrontend> = extensions.map { ext -> ext.first }
        .distinct()
        .associateWith(frontendFactory)
    // @formatter:on

    fun createLexer(delegate: Lexer): Lexer = extensions.fold(delegate) { lexer, [pluginId, extension] ->
        extension.createLexer(lexer, frontends.getValue(pluginId))
    }

    fun parse(builder: PsiBuilder, rule: GrammarRule, parseKotlin: (GrammarRule) -> Boolean): Boolean {
        if (builder.eof()) return false
        for ([pluginId, extension] in extensions) {
            val marker = builder.mark()
            val offset = builder.currentOffset
            val context = object : SyntaxExtension.ParsingContext {
                override val builder: PsiBuilder = builder
                override val rule: GrammarRule = rule
                override fun parseKotlin(rule: GrammarRule): Boolean = parseKotlin(rule)
            }
            if (!extension.parse(context, frontends.getValue(pluginId))) {
                marker.rollbackTo()
                continue
            }
            check(builder.currentOffset > offset) {
                "Syntax extension '$pluginId:${extension.id}' claimed $rule without consuming a token"
            }
            marker.drop()
            return true
        }
        return false
    }

    fun lower(file: KtFile): KtFile = extensions.fold(file) { current, [pluginId, extension] ->
        extension.lower(current, frontends.getValue(pluginId)).also { lowered ->
            if (lowered !== current) {
                lowered.originalFile = file.originalFile
            }
        }
    }
}