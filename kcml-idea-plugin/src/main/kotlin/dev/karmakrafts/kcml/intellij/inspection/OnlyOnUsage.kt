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

package dev.karmakrafts.kcml.intellij.inspection

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import dev.karmakrafts.kcml.intellij.util.TypeNames
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotated
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationValue
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeAliasSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCollectionLiteralExpression
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtUnaryExpression
import org.jetbrains.kotlin.psi.KtUserType

internal object OnlyOnUsage {
    enum class Problem { UNGUARDED, MISMATCHED }

    private val key = OnlyOnAnalysis.key<Map<KtSimpleNameExpression, Problem>>("kcml.onlyOn.usage")

    private data class Target(val name: String, val ancestors: Set<String>) {
        fun accepts(other: Target): Boolean = name in other.ancestors
    }

    fun problem(expression: KtSimpleNameExpression): Problem? {
        if (PsiTreeUtil.getParentOfType(expression, KtImportDirective::class.java) != null) return null
        val file = expression.containingFile as? KtFile ?: return null
        return OnlyOnAnalysis.lookup(file, key) { problems(file) }?.get(expression)
    }

    private fun problems(file: KtFile): Map<KtSimpleNameExpression, Problem> = analyze(file) {
        buildMap {
            file.accept(object : KtTreeVisitorVoid() {
                override fun visitImportDirective(importDirective: KtImportDirective) = Unit

                override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
                    ProgressManager.checkCanceled()
                    computeProblem(expression)?.let { put(expression, it) }
                    super.visitSimpleNameExpression(expression)
                }
            })
        }
    }

    private fun KaSession.computeProblem(expression: KtSimpleNameExpression): Problem? {
        val symbol = expression.mainReference.resolveToSymbol() ?: return null
        val restrictions = restrictions(symbol).toMutableList()
        if (symbol is KaConstructorSymbol) {
            (expression.parent as? KtExpression)?.expressionType?.abbreviation?.symbol?.let {
                restrictions += restrictions(it)
            }
        }
        if (symbol is KaPropertySymbol) {
            val access = accessExpression(expression)
            val assignment = access.parent as? KtBinaryExpression
            val write = assignment?.left == access && assignment.operationToken in KtTokens.ALL_ASSIGNMENTS
            val compound =
                write && assignment.operationToken != KtTokens.EQ || (access.parent as? KtUnaryExpression)?.operationToken in setOf(
                    KtTokens.PLUSPLUS, KtTokens.MINUSMINUS
                )
            if (!write || compound) symbol.getter?.let { restrictions += restrictions(it) }
            if (write || compound) symbol.setter?.let { restrictions += restrictions(it) }
            symbol.backingFieldSymbol?.let { restrictions += annotations(it) }
        }
        if (expression.parent is KtCallExpression && symbol is KaCallableSymbol) {
            (symbol.returnType as? KaFunctionType)?.let { restrictions += annotations(it) }
            val call = (expression.parent as KtCallExpression).resolveToCall()?.singleFunctionCallOrNull()
            (call?.dispatchReceiver?.type as? KaFunctionType)?.let { restrictions += annotations(it) }
        }
        if (restrictions.isEmpty()) return null
        val context = context(expression)
        return when {
            context == null -> Problem.UNGUARDED
            context.any { target -> restrictions.any { allowed -> allowed.none { it.accepts(target) } } } -> Problem.MISMATCHED
            else -> null
        }
    }

    fun highlight(expression: KtSimpleNameExpression): PsiElement =
        (expression.parent as? KtCallExpression)?.takeIf { it.calleeExpression == expression }
            ?: (expression.parent as? KtUserType)?.takeIf { it.referenceExpression == expression } ?: expression

    private fun accessExpression(expression: KtSimpleNameExpression): KtExpression =
        (expression.parent as? KtQualifiedExpression)?.takeIf { it.selectorExpression == expression } ?: expression

    private fun KaSession.restrictions(
        symbol: KaSymbol, visited: MutableSet<KaSymbol> = mutableSetOf()
    ): List<Set<Target>> {
        if (!visited.add(symbol)) return emptyList()
        val result = (symbol as? KaAnnotated)?.let { annotations(it) }.orEmpty().toMutableList()
        if (symbol is KaTypeAliasSymbol) {
            (symbol.expandedType as? KaClassType)?.symbol?.let { result += restrictions(it, visited) }
        }
        val owner = (symbol as? KaDeclarationSymbol)?.containingDeclaration
        if (owner is KaClassSymbol) result += restrictions(owner, visited)
        return result
    }

    private fun KaSession.annotations(owner: KaAnnotated): List<Set<Target>> =
        owner.annotations.filter { it.classId?.asFqNameString() == TypeNames.ONLY_ON }.map { annotation ->
            annotation.arguments.flatMap { targets(it.expression) }.toSet()
        }

    private fun KaSession.targets(value: KaAnnotationValue): List<Target> = when (value) {
        is KaAnnotationValue.ArrayValue -> value.values.flatMap { targets(it) }
        is KaAnnotationValue.ClassLiteralValue -> listOfNotNull((value.type as? KaClassType)?.symbol?.let { target(it) })
        else -> emptyList()
    }

    private fun KaSession.target(symbol: KaSymbol): Target? {
        if (symbol is KaTypeAliasSymbol) return (symbol.expandedType as? KaClassType)?.symbol?.let { target(it) }
        val klass = symbol as? KaClassSymbol ?: return null
        val name = klass.classId?.asFqNameString() ?: return null
        val ancestors = mutableSetOf(name)
        fun collect(current: KaClassSymbol) {
            for (type in current.superTypes.filterIsInstance<KaClassType>()) {
                if (ancestors.add(type.classId.asFqNameString())) (type.symbol as? KaClassSymbol)?.let(::collect)
            }
        }
        collect(klass)
        if (TypeNames.COMPILE_TARGET !in ancestors) {
            return (klass.containingDeclaration as? KaClassSymbol)?.let { target(it) }
        }
        return Target(name, ancestors)
    }

    private fun KaSession.context(expression: KtExpression): Set<Target>? {
        var result: Set<Target>? = null
        var current: PsiElement? = expression.parent
        while (current != null) {
            val constraints = when (current) {
                is KtLambdaExpression -> lambdaContext(current)
                is KtDeclaration -> (current.symbol as? KaAnnotated)?.let { annotations(it) }.orEmpty()
                else -> emptyList()
            }
            for (constraint in constraints) result = result?.let { intersect(it, constraint) } ?: constraint
            current = current.parent
        }
        return result
    }

    private fun intersect(first: Set<Target>, second: Set<Target>): Set<Target> = buildSet {
        for (left in first) for (right in second) {
            when {
                left.accepts(right) -> add(right)
                right.accepts(left) -> add(left)
            }
        }
    }

    private fun KaSession.lambdaContext(lambda: KtLambdaExpression): List<Set<Target>> {
        val result = lambda.expressionType?.let { annotations(it) }.orEmpty().toMutableList()
        lambda.expectedType?.let { result += annotations(it) }
        val call = generateSequence(lambda.parent) { it.parent }.filterIsInstance<KtCallExpression>().firstOrNull()
            ?: return result
        val resolved = call.resolveToCall()?.singleFunctionCallOrNull() ?: return result
        val parameter = resolved.valueArgumentMapping.entries.firstOrNull { (argument, _) ->
            argument == lambda || KtPsiUtil.deparenthesize(argument) == lambda
        }?.value ?: return result
        result += annotations(parameter.returnType)
        val symbol = resolved.signature.symbol
        if (symbol.callableId?.asSingleFqName()
                ?.asString() != "dev.karmakrafts.kcml.api.target.whenCompilingFor" || parameter.name.asString() != "block"
        ) return result
        val types = resolved.valueArgumentMapping.filterValues { it.name.asString() == "types" }.keys
        val targets = mutableSetOf<Target>()
        for (argument in types) targets += scopeTargets(argument, mutableSetOf()) ?: return result
        result += targets
        return result
    }

    private fun KaSession.scopeTargets(expression: KtExpression, visited: MutableSet<PsiElement>): Set<Target>? {
        val value = KtPsiUtil.deparenthesize(expression) ?: return null
        if (!visited.add(value)) return null
        (value.expressionType as? KaClassType)?.symbol?.let { target(it) }?.let { return setOf(it) }
        val elements = when (value) {
            is KtCollectionLiteralExpression -> value.innerExpressions.toList()
            is KtCallExpression -> {
                val call = value.resolveToCall()?.singleFunctionCallOrNull() ?: return null
                when (call.signature.callableId?.asSingleFqName()?.asString()) {
                    "kotlin.emptyArray" -> emptyList()
                    "kotlin.arrayOf" -> value.valueArguments.map { it.getArgumentExpression() ?: return null }
                    else -> return null
                }
            }

            is KtNameReferenceExpression -> {
                val property = value.mainReference.resolveToSymbol()?.psi as? KtProperty ?: return null
                if (property.isVar) return null
                return property.initializer?.let { scopeTargets(it, visited) }
            }

            else -> return null
        }
        return buildSet {
            for (element in elements) addAll(scopeTargets(element, visited) ?: return null)
        }
    }
}