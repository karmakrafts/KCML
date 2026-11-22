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

package dev.karmakrafts.kcml.intellij.syntax

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.psi.KtFile

internal class SyntaxHighlightInfoFilter : HighlightInfoFilter {
    @Suppress("JetBrainsInternalApiUsage")
    override fun accept(info: HighlightInfo, file: PsiFile?): Boolean {
        if (file !is KtFile || info.problemGroup === SyntaxProblemGroup || info.isFromInspection) return true
        if (!info.isFromHighlightVisitor || info.severity < HighlightSeverity.WARNING) return true
        return SyntaxAnalysis.result(file) == null
    }
}