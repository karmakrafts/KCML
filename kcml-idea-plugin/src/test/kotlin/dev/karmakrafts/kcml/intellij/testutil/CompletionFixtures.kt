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

package dev.karmakrafts.kcml.intellij.testutil

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.org.objectweb.asm.ClassWriter
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

fun completionPresentation(item: LookupElement): LookupElementPresentation {
    val presentation = LookupElementPresentation()
    item.renderElement(presentation)
    return presentation
}

fun CodeInsightTestFixture.configureInjectCompletion(annotation: String, mixin: String, imports: String) {
    configureByText(
        "Mixin.kt", """
            import dev.karmakrafts.kcml.api.mixin.*
            $imports
            $mixin
            class Example {
                $annotation
                fun inject() {}
            }
        """.trimIndent()
    )
}

fun JarOutputStream.writeCompletionClass(internalName: String, configure: (ClassWriter) -> Unit) {
    val writer = ClassWriter(0)
    configure(writer)
    writer.visitEnd()
    putNextEntry(JarEntry("$internalName.class"))
    write(writer.toByteArray())
    closeEntry()
}