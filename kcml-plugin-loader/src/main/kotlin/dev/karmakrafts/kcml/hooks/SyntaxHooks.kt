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

package dev.karmakrafts.kcml.hooks

import dev.karmakrafts.kcml.api.ExperimentalKcmlApi
import dev.karmakrafts.kcml.api.InternalKcmlApi
import dev.karmakrafts.kcml.api.extension.syntax.GrammarRule
import dev.karmakrafts.kcml.plugin.PluginLoaderImpl
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.lang.PsiBuilder
import org.jetbrains.kotlin.com.intellij.lexer.Lexer
import org.jetbrains.kotlin.lexer.KotlinLexer
import org.jetbrains.kotlin.psi.KtFile
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

@OptIn(ExperimentalKcmlApi::class, InternalKcmlApi::class)
@KCMLHookApi
object SyntaxHooks {
    private class Bypass(val parser: Any, val rule: GrammarRule, var pending: Boolean = true)

    private val bypass: ThreadLocal<Bypass> = ThreadLocal()
    private val processingSources: ThreadLocal<Boolean> = ThreadLocal()

    @JvmStatic
    fun lowerSources(environment: KotlinCoreEnvironment): List<KtFile>? {
        if (processingSources.get() == true) return null
        val adapter = PluginLoaderImpl.syntaxAdapterOrNull ?: return null
        processingSources.set(true)
        try {
            // Re-enter the original getter so other source processors still run before lowering.
            return environment.getSourceFiles().map(adapter::lower)
        }
        finally {
            processingSources.remove()
        }
    }

    @JvmStatic
    fun createLexer(): Lexer = PluginLoaderImpl.syntaxAdapterOrNull?.createLexer(KotlinLexer()) ?: KotlinLexer()

    @JvmStatic
    fun parse(
        parser: Any,
        rule: GrammarRule,
        atomic: Boolean = false,
        rollbackIfDefinitelyNotExpression: Boolean = false,
        isScriptTopLevel: Boolean = false
    ): Boolean {
        val request = bypass.get()
        if (request != null && request.pending && request.parser === parser && request.rule == rule) {
            request.pending = false
            return false
        }
        val adapter = PluginLoaderImpl.syntaxAdapterOrNull ?: return false
        val builder = ParserAccess.builder.get(parser) as PsiBuilder
        return adapter.parse(builder, rule) { requestedRule ->
            parseKotlin(
                parser,
                builder,
                requestedRule,
                atomic && requestedRule == rule,
                rollbackIfDefinitelyNotExpression,
                isScriptTopLevel
            )
        }
    }

    private fun parseKotlin(
        parser: Any,
        builder: PsiBuilder,
        rule: GrammarRule,
        atomic: Boolean,
        rollbackIfDefinitelyNotExpression: Boolean,
        isScriptTopLevel: Boolean
    ): Boolean {
        when (rule) {
            GrammarRule.TOP_LEVEL_DECLARATION, GrammarRule.MEMBER_DECLARATION, GrammarRule.LOCAL_DECLARATION, GrammarRule.STATEMENT, GrammarRule.EXPRESSION, GrammarRule.TYPE -> Unit
            else -> throw IllegalArgumentException("Unsupported Kotlin grammar rule '${rule.id}'")
        }
        if (builder.eof()) return false
        val kotlinParser = if (ParserAccess.kotlinParsing.isInstance(parser)) parser
        else ParserAccess.kotlinParser.get(parser)
        val expressionParser = ParserAccess.expressionParser.get(kotlinParser)
        val target = when (rule) {
            GrammarRule.TOP_LEVEL_DECLARATION, GrammarRule.MEMBER_DECLARATION, GrammarRule.TYPE -> kotlinParser
            else -> expressionParser
        }
        val marker = builder.mark()
        val offset = builder.currentOffset
        val previous = bypass.get()
        bypass.set(Bypass(target, rule))
        try {
            val result = when (rule) {
                GrammarRule.TOP_LEVEL_DECLARATION -> ParserAccess.topLevel.invoke(target)
                GrammarRule.MEMBER_DECLARATION -> ParserAccess.member.invoke(target)
                GrammarRule.LOCAL_DECLARATION -> ParserAccess.local.invoke(
                    target, rollbackIfDefinitelyNotExpression, isScriptTopLevel
                )

                GrammarRule.STATEMENT -> ParserAccess.statement.invoke(target, isScriptTopLevel)
                GrammarRule.EXPRESSION -> if (atomic) ParserAccess.atomic.invoke(target)
                else ParserAccess.expression.invoke(target)

                GrammarRule.TYPE -> ParserAccess.type.invoke(target)
                else -> throw IllegalArgumentException("Unsupported Kotlin grammar rule '${rule.id}'")
            }
            if (result == false || builder.currentOffset <= offset) {
                marker.rollbackTo()
                return false
            }
            marker.drop()
            return true
        } catch (exception: InvocationTargetException) {
            throw exception.targetException
        }
        finally {
            if (previous == null) bypass.remove() else bypass.set(previous)
        }
    }

    private object ParserAccess {
        val kotlinParsing: Class<*> = Class.forName("org.jetbrains.kotlin.parsing.KotlinParsing")
        private val expressionParsing = Class.forName("org.jetbrains.kotlin.parsing.KotlinExpressionParsing")
        val builder: Field = kotlinParsing.superclass.getDeclaredField("myBuilder").apply { isAccessible = true }
        val kotlinParser: Field = expressionParsing.getDeclaredField("myKotlinParsing").apply { isAccessible = true }
        val expressionParser: Field =
            kotlinParsing.getDeclaredField("myExpressionParsing").apply { isAccessible = true }
        val topLevel: Method = kotlinParsing.getDeclaredMethod("parseTopLevelDeclaration").apply { isAccessible = true }
        val member: Method = kotlinParsing.getDeclaredMethod("parseMemberDeclaration").apply { isAccessible = true }
        val type: Method = kotlinParsing.getDeclaredMethod("parseTypeRef").apply { isAccessible = true }
        val local: Method = expressionParsing.getDeclaredMethod(
            "parseLocalDeclaration", Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType
        ).apply { isAccessible = true }
        val statement: Method = expressionParsing.getDeclaredMethod(
            "parseStatement", Boolean::class.javaPrimitiveType
        ).apply { isAccessible = true }
        val expression: Method = expressionParsing.getDeclaredMethod("parseExpression").apply { isAccessible = true }
        val atomic: Method = expressionParsing.getDeclaredMethod("parseAtomicExpression").apply { isAccessible = true }
    }
}