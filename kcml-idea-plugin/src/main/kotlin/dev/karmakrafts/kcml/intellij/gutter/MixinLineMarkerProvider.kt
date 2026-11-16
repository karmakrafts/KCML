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

package dev.karmakrafts.kcml.intellij.gutter

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.IconLoader
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.util.PsiNavigateUtil
import dev.karmakrafts.kcml.intellij.util.isMixin
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClassLiteralExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtEscapeStringTemplateEntry
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateEntryWithExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import javax.swing.Icon

class MixinLineMarkerProvider : LineMarkerProvider {
    private val icon: Icon = IconLoader.getIcon("/icons/dev/karmakrafts/kcml/intellij/mixin.svg", javaClass)

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

    override fun collectSlowLineMarkers(elements: List<PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) {
        for (element in elements) {
            val declaration = element.parent as? KtClassOrObject ?: continue
            if (declaration.nameIdentifier != element) continue
            val annotations = declaration.annotationEntries.filter { it.isMixin() }
            if (annotations.isEmpty()) continue
            val target = annotations.firstNotNullOfOrNull { it.resolveTarget() }
            result.add(object : LineMarkerInfo<PsiElement>(
                element,
                element.textRange,
                icon,
                { "KCML mixin" },
                target?.let {
                    GutterIconNavigationHandler { _, source ->
                        val current = (source.parent as? KtClassOrObject)?.annotationEntries?.filter { it.isMixin() }
                            ?.firstNotNullOfOrNull { it.resolveTarget() }
                        current?.let { PsiNavigateUtil.navigate(it.navigationElement, true) }
                    }
                },
                GutterIconRenderer.Alignment.LEFT,
                { "KCML mixin" }) {
                override fun createGutterRenderer(): GutterIconRenderer =
                    object : LineMarkerGutterIconRenderer<PsiElement>(this) {
                        override fun looksTheSameAs(renderer: LineMarkerGutterIconRenderer<*>): Boolean =
                            super.looksTheSameAs(renderer) && isNavigateAction == renderer.isNavigateAction
                    }
            })
        }
    }

    private fun KtAnnotationEntry.resolveTarget(): PsiElement? {
        val argument = valueArguments.firstOrNull { it.getArgumentName()?.asName?.identifier == "target" }
            ?: valueArguments.firstOrNull { it.getArgumentName() == null }
        return when (val expression = argument?.getArgumentExpression()) {
            is KtClassLiteralExpression -> {
                val receiver = expression.receiverExpression
                val reference = (receiver as? KtDotQualifiedExpression)?.selectorExpression ?: receiver
                reference?.references?.firstNotNullOfOrNull { it.resolve() }
            }

            is KtStringTemplateExpression -> {
                val name = buildString {
                    for (entry in expression.entries) {
                        append(
                            when (entry) {
                                is KtLiteralStringTemplateEntry -> entry.text
                                is KtEscapeStringTemplateEntry -> entry.unescapedValue
                                is KtStringTemplateEntryWithExpression -> {
                                    val constant = entry.expression as? KtConstantExpression ?: return null
                                    if (constant.text != "'${'$'}'") return null
                                    "$"
                                }

                                else -> return null
                            }
                        )
                    }
                }.replace('/', '.').replace('$', '.')
                KotlinFullClassNameIndex[name, project, resolveScope].firstOrNull()
                    ?: JavaPsiFacade.getInstance(project).findClass(name, resolveScope)
            }

            else -> null
        }
    }
}