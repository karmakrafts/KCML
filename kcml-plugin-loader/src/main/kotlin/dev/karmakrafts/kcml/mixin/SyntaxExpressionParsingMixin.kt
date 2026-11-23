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

package dev.karmakrafts.kcml.mixin

import dev.karmakrafts.kcml.api.ExperimentalKcmlApi
import dev.karmakrafts.kcml.api.extension.syntax.GrammarRule
import dev.karmakrafts.kcml.api.mixin.Capture
import dev.karmakrafts.kcml.api.mixin.IndirectMixin
import dev.karmakrafts.kcml.api.mixin.Inject
import dev.karmakrafts.kcml.api.mixin.ReturnContext
import dev.karmakrafts.kcml.api.mixin.ThisAware
import dev.karmakrafts.kcml.api.mixin.returnFromTarget
import dev.karmakrafts.kcml.hooks.KCMLHookApi
import dev.karmakrafts.kcml.hooks.SyntaxHooks

@OptIn(ExperimentalKcmlApi::class, KCMLHookApi::class)
@IndirectMixin("org.jetbrains.kotlin.parsing.KotlinExpressionParsing")
internal class SyntaxExpressionParsingMixin : ThisAware<Any> {
    @Inject("parseExpression", "(Ljava/lang/String;)V", order = Inject.Order.BEFORE)
    fun parseExpression(context: ReturnContext<Unit>) {
        if (SyntaxHooks.parse(getThis(), GrammarRule.EXPRESSION)) context.returnFromTarget()
    }

    @Inject("parseAtomicExpression", "()Z", order = Inject.Order.BEFORE)
    fun parseAtomicExpression(context: ReturnContext<Boolean>) {
        if (SyntaxHooks.parse(getThis(), GrammarRule.EXPRESSION, atomic = true)) context.returnFromTarget(true)
    }

    @Inject("parseStatement", "(Z)V", order = Inject.Order.BEFORE)
    fun parseStatement(context: ReturnContext<Unit>, @Capture isScriptTopLevel: Boolean) {
        if (SyntaxHooks.parse(getThis(), GrammarRule.STATEMENT, isScriptTopLevel = isScriptTopLevel)) {
            context.returnFromTarget()
        }
    }

    @Inject("parseLocalDeclaration", "(ZZ)Z", order = Inject.Order.BEFORE)
    fun parseLocalDeclaration(
        context: ReturnContext<Boolean>,
        @Capture rollbackIfDefinitelyNotExpression: Boolean,
        @Capture isScriptTopLevel: Boolean
    ) {
        if (SyntaxHooks.parse(
                getThis(),
                GrammarRule.LOCAL_DECLARATION,
                rollbackIfDefinitelyNotExpression = rollbackIfDefinitelyNotExpression,
                isScriptTopLevel = isScriptTopLevel
            )
        ) {
            context.returnFromTarget(true)
        }
    }
}