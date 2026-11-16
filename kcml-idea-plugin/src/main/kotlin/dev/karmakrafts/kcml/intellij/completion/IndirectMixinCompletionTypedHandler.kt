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

package dev.karmakrafts.kcml.intellij.completion

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

internal class IndirectMixinCompletionTypedHandler : TypedHandlerDelegate() {
    override fun charTyped(charTyped: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if (!shouldShowPopup(charTyped, file, (editor.caretModel.offset - 1).coerceAtLeast(0))) return Result.CONTINUE
        AutoPopupController.getInstance(project).scheduleAutoPopup(editor) { currentFile ->
            shouldShowPopup(charTyped, currentFile, editor.caretModel.offset)
        }
        return Result.CONTINUE
    }

    internal fun shouldShowPopup(charTyped: Char, file: PsiFile, offset: Int): Boolean =
        isCompletionCharacter(charTyped) && file.language.id == "kotlin" && IndirectMixinTargetString.isTargetStringAt(
            file, offset
        )

    private fun isCompletionCharacter(charTyped: Char): Boolean = charTyped.isLetterOrDigit() || charTyped in "._/$\\"
}