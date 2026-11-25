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
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotated
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationValue
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.session.analyze
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
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCollectionLiteralExpression
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParameter
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

    private data class Target(val symbol: KaClassSymbol) {
        context(session: KaSession)
        fun accepts(other: Target): Boolean = with(session) {
            symbol == other.symbol || other.symbol.isSubClassOf(symbol)
        }
    }

    fun problem(expression: KtSimpleNameExpression): Problem? {
        if (PsiTreeUtil.getParentOfType(expression, KtImportDirective::class.java) != null) return null
        val file = expression.containingFile as? KtFile ?: return null
        return OnlyOnAnalysis.lookup(file, key) { problems(file) }?.get(expression)
    }

    private fun problems(file: KtFile): Map<KtSimpleNameExpression, Problem> = analyze(file) {
        collectProblems(file)
    }

    context(session: KaSession)
    private fun collectProblems(file: KtFile): Map<KtSimpleNameExpression, Problem> =
        buildMap {
            file.accept(object : KtTreeVisitorVoid() {
                override fun visitImportDirective(importDirective: KtImportDirective) = Unit

                override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
                    ProgressManager.checkCanceled()
                    val problem = session.computeProblem(expression)
                    if (problem != null) put(expression, problem)
                    super.visitSimpleNameExpression(expression)
                }
            })
        }

    private fun KaSession.computeProblem(expression: KtSimpleNameExpression): Problem? {
        val symbol = expression.mainReference.resolveToSymbol() ?: return null
        val restrictions = restrictions(symbol).toMutableList()
        if (symbol is KaConstructorSymbol) {
            val abbreviation = (expression.parent as? KtExpression)?.expressionType?.abbreviation?.symbol
            if (abbreviation != null) restrictions += restrictions(abbreviation)
        }
        if (symbol is KaPropertySymbol) {
            val access = accessExpression(expression)
            val assignment = access.parent as? KtBinaryExpression
            val write = assignment?.left == access && assignment.operationToken in KtTokens.ALL_ASSIGNMENTS
            val compound =
                write && assignment.operationToken != KtTokens.EQ || (access.parent as? KtUnaryExpression)?.operationToken in setOf(
                    KtTokens.PLUSPLUS, KtTokens.MINUSMINUS
                )
            if (!write || compound) {
                val getter = symbol.getter
                if (getter != null) restrictions += restrictions(getter)
            }
            if (write || compound) {
                val setter = symbol.setter
                if (setter != null) restrictions += restrictions(setter)
            }
            val backingField = symbol.backingFieldSymbol
            if (backingField != null) restrictions += annotations(backingField)
        }
        if (expression.parent is KtCallExpression && symbol is KaCallableSymbol) {
            val returnType = symbol.returnType as? KaFunctionType
            if (returnType != null) restrictions += annotations(returnType)
            val call = (expression.parent as KtCallExpression).resolveToCall()?.singleFunctionCallOrNull()
            val receiverType = call?.dispatchReceiver?.type as? KaFunctionType
            if (receiverType != null) restrictions += annotations(receiverType)
        }
        if (restrictions.isEmpty()) return null
        val context = context(expression)
        return when {
            context == null -> Problem.UNGUARDED
            context.any { target -> restrictions.any { allowed -> allowed.none { it.accepts(target) } } } -> Problem.MISMATCHED
            else -> null
        }
    }

    fun highlight(expression: KtSimpleNameExpression): PsiElement {
        val parent = expression.parent
        return when {
            parent is KtCallExpression && parent.calleeExpression == expression -> parent
            parent is KtUserType && parent.referenceExpression == expression -> parent
            else -> expression
        }
    }

    private fun accessExpression(expression: KtSimpleNameExpression): KtExpression {
        val parent = expression.parent
        return if (parent is KtQualifiedExpression && parent.selectorExpression == expression) parent else expression
    }

    private fun KaSession.restrictions(
        symbol: KaSymbol, visited: MutableSet<KaSymbol> = mutableSetOf()
    ): List<Set<Target>> {
        if (!visited.add(symbol)) return emptyList()
        val result = mutableListOf<Set<Target>>()
        if (symbol is KaAnnotated) result += annotations(symbol)
        if (symbol is KaTypeAliasSymbol) {
            val expanded = (symbol.expandedType as? KaClassType)?.symbol
            if (expanded != null) result += restrictions(expanded, visited)
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
        is KaAnnotationValue.ClassLiteralValue -> {
            val symbol = (value.type as? KaClassType)?.symbol
            if (symbol == null) emptyList() else listOfNotNull(target(symbol))
        }

        else -> emptyList()
    }

    private fun KaSession.target(symbol: KaSymbol): Target? {
        if (symbol is KaTypeAliasSymbol) {
            val expanded = (symbol.expandedType as? KaClassType)?.symbol ?: return null
            return target(expanded)
        }
        val klass = symbol as? KaClassSymbol ?: return null
        val compileTarget = findClass(ClassId.topLevel(FqName(TypeNames.COMPILE_TARGET))) ?: return null
        if (klass != compileTarget && !klass.isSubClassOf(compileTarget)) {
            val owner = klass.containingDeclaration as? KaClassSymbol ?: return null
            return target(owner)
        }
        return Target(klass)
    }

    private fun KaSession.context(expression: KtExpression): Set<Target>? {
        var result: Set<Target>? = null
        var current: PsiElement? = expression.parent
        while (current != null) {
            val constraints = when (current) {
                is KtLambdaExpression -> lambdaContext(current)
                is KtParameter if current.isFunctionTypeParameter -> emptyList()
                is KtDeclaration -> {
                    val symbol = current.symbol as? KaAnnotated
                    buildList {
                        if (symbol != null) addAll(annotations(symbol))
                        if (symbol is KaPropertySymbol) {
                            val backingField = symbol.backingFieldSymbol
                            if (backingField != null) addAll(annotations(backingField))
                        }
                    }
                }

                else -> emptyList()
            }
            for (constraint in constraints) {
                val previous = result
                result = if (previous == null) constraint else intersect(previous, constraint)
            }
            current = current.parent
        }
        return result
    }

    context(session: KaSession)
    private fun intersect(first: Set<Target>, second: Set<Target>): Set<Target> = buildSet {
        for (left in first) for (right in second) {
            when {
                left.accepts(right) -> add(right)
                right.accepts(left) -> add(left)
            }
        }
    }

    private fun KaSession.lambdaContext(lambda: KtLambdaExpression): List<Set<Target>> {
        val result = mutableListOf<Set<Target>>()
        val expressionType = lambda.expressionType
        if (expressionType != null) result += annotations(expressionType)
        val expectedType = lambda.expectedType
        if (expectedType != null) result += annotations(expectedType)
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
        val symbol = (value.expressionType as? KaClassType)?.symbol
        if (symbol != null) {
            val target = target(symbol)
            if (target != null) return setOf(target)
        }
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
                val initializer = property.initializer ?: return null
                return scopeTargets(initializer, visited)
            }

            else -> return null
        }
        return buildSet {
            for (element in elements) {
                addAll(scopeTargets(element, visited) ?: return null)
            }
        }
    }
}