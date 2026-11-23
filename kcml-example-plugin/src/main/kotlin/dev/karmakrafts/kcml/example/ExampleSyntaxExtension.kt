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

package dev.karmakrafts.kcml.example

import dev.karmakrafts.kcml.api.ExperimentalKcmlApi
import dev.karmakrafts.kcml.api.extension.AbstractExtension
import dev.karmakrafts.kcml.api.extension.ExtensionId
import dev.karmakrafts.kcml.api.extension.SyntaxExtension
import dev.karmakrafts.kcml.api.frontend.SyntaxFrontend
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.com.intellij.lexer.DelegateLexer
import org.jetbrains.kotlin.com.intellij.lexer.Lexer
import org.jetbrains.kotlin.com.intellij.psi.PsiManager
import org.jetbrains.kotlin.com.intellij.psi.SingleRootFileViewProvider
import org.jetbrains.kotlin.com.intellij.psi.tree.IElementType
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * Reserves `fn` as an alias for `fun` and `magic` as an integer expression with the value 42.
 * Backtick-escaped identifiers remain ordinary Kotlin identifiers.
 */
@OptIn(ExperimentalKcmlApi::class)
@ExtensionId("syntax_example")
internal class ExampleSyntaxExtension : AbstractExtension(), SyntaxExtension {
    override fun createLexer(delegate: Lexer, frontend: SyntaxFrontend): Lexer = object : DelegateLexer(delegate) {
        override fun getTokenType(): IElementType? =
            if (delegate.tokenType == KtTokens.IDENTIFIER && delegate.tokenText == "fn") KtTokens.FUN_KEYWORD
            else delegate.tokenType
    }

    override fun parse(context: SyntaxExtension.ParsingContext, frontend: SyntaxFrontend): Boolean {
        if (context.rule != SyntaxExtension.GrammarRule.EXPRESSION) return false
        val builder = context.builder
        if (builder.tokenType != KtTokens.IDENTIFIER || builder.tokenText != "magic") return false
        val marker = builder.mark()
        builder.advanceLexer()
        marker.done(KtNodeTypes.INTEGER_CONSTANT)
        return true
    }

    override fun highlight(file: KtFile, frontend: SyntaxFrontend): List<SyntaxExtension.Highlighting> = buildList {
        for (function in PsiTreeUtil.collectElementsOfType(file, KtNamedFunction::class.java)) {
            val keyword = function.funKeyword ?: continue
            if (keyword.text == "fn") add(SyntaxExtension.Highlighting(keyword.textRange, "KOTLIN_KEYWORD"))
        }
        for (constant in PsiTreeUtil.collectElementsOfType(file, KtConstantExpression::class.java)) {
            if (constant.text == "magic") add(SyntaxExtension.Highlighting(constant.textRange, "KOTLIN_NUMBER"))
        }
    }

    override fun lower(file: KtFile, frontend: SyntaxFrontend): KtFile {
        val replacements = buildList {
            for (function in PsiTreeUtil.collectElementsOfType(file, KtNamedFunction::class.java)) {
                val keyword = function.funKeyword ?: continue
                if (keyword.text == "fn") add(keyword.textRange to "fun")
            }
            for (constant in PsiTreeUtil.collectElementsOfType(file, KtConstantExpression::class.java)) {
                if (constant.text == "magic") add(constant.textRange to "42")
            }
        }
        if (replacements.isEmpty()) return file
        var text = file.text
        for ([range, replacement] in replacements.sortedByDescending { it.first.startOffset }) {
            text = text.replaceRange(range.startOffset, range.endOffset, replacement)
        }
        // Preserve virtual-file identity for common-source grouping and incremental compilation.
        val view = object : SingleRootFileViewProvider(PsiManager.getInstance(file.project), file.virtualFile, true) {
            override fun getContents(): CharSequence = text
        }
        return (checkNotNull(view.getPsi(KotlinLanguage.INSTANCE)) as KtFile).also {
            file.copyUserDataTo(it)
        }
    }
}