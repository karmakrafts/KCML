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

package dev.karmakrafts.kcml.intellij.util

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.builtins.jvm.JavaToKotlinClassMap
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClassLiteralExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

internal fun PsiElement.asPsiClass(): PsiClass? = when (this) {
    is PsiClass -> this
    is KtClassOrObject -> {
        val javaClass = fqName?.let { JavaToKotlinClassMap.mapKotlinToJava(it.toUnsafe()) }
        if (javaClass != null) JavaPsiFacade.getInstance(project).findClass(
            javaClass.asSingleFqName().asString(), resolveScope
        )
        else toLightClass()
    }

    else -> null
}

internal fun KtAnnotationEntry.resolveMixinTarget(): PsiElement? {
    ProgressManager.checkCanceled()
    if (!isMixin()) return null
    return when (val expression = argument("target", 0)?.getArgumentExpression()) {
        is KtClassLiteralExpression -> {
            val receiver = expression.receiverExpression
            val reference = (receiver as? KtDotQualifiedExpression)?.selectorExpression ?: receiver
            reference?.references?.firstNotNullOfOrNull { it.resolve() }
        }

        is KtStringTemplateExpression -> {
            val className = expression.literalValue()?.replace('/', '.') ?: return null
            ClassIndex.findClass(project, resolveScope, className)
        }

        else -> null
    }
}