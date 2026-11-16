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

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.TypeConversionUtil
import dev.karmakrafts.kcml.intellij.util.TypeNames
import dev.karmakrafts.kcml.intellij.util.asPsiClass
import dev.karmakrafts.kcml.intellij.util.qualifiedName
import dev.karmakrafts.kcml.intellij.util.resolveMixinTarget
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Opcodes
import java.io.IOException

internal data class InjectTargetMethod(val name: String, val descriptor: String)

internal object InjectTargetMethods {
    fun find(annotation: KtAnnotationEntry): List<InjectTargetMethod> {
        ProgressManager.checkCanceled()
        return try {
            if (annotation.qualifiedName() != TypeNames.INJECT) return emptyList()
            val mixin = PsiTreeUtil.getParentOfType(annotation, KtClassOrObject::class.java) ?: return emptyList()
            val target = mixin.annotationEntries.firstNotNullOfOrNull {
                ProgressManager.checkCanceled()
                it.resolveMixinTarget()?.asPsiClass()
            } ?: return emptyList()
            val file = target.containingFile?.virtualFile
            val methods = if (target is PsiCompiledElement || file?.extension == "class") {
                target.compiledMethods()
            }
            else {
                target.methods.mapNotNull {
                    ProgressManager.checkCanceled()
                    if (it.isConstructor) null
                    else it.descriptor()?.let { descriptor ->
                        InjectTargetMethod(it.name, descriptor)
                    }
                }
            }
            methods.distinct()
        } catch (_: IndexNotReadyException) {
            emptyList()
        }
    }

    private fun PsiClass.compiledMethods(): List<InjectTargetMethod> {
        val binaryName = binaryName() ?: return emptyList()
        val containingFile = containingFile?.virtualFile ?: return emptyList()
        val fileName = "${binaryName.substringAfterLast('.')}.class"
        val classFile = if (containingFile.name == fileName) containingFile
        else containingFile.parent?.findChild(fileName) ?: return emptyList()
        return try {
            ProgressManager.checkCanceled()
            val reader = ClassReader(classFile.contentsToByteArray())
            if (reader.className != binaryName.replace('.', '/')) return emptyList()
            buildList {
                reader.accept(object : ClassVisitor(Opcodes.ASM9) {
                    override fun visitMethod(
                        access: Int,
                        name: String,
                        descriptor: String,
                        signature: String?,
                        exceptions: Array<out String>?
                    ): MethodVisitor? {
                        ProgressManager.checkCanceled()
                        if (name != "<init>" && name != "<clinit>") add(InjectTargetMethod(name, descriptor))
                        return null
                    }
                }, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
            }
        } catch (_: IOException) {
            emptyList()
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
    }

    private fun PsiMethod.descriptor(): String? = buildString {
        append('(')
        for (parameter in parameterList.parameters) {
            ProgressManager.checkCanceled()
            append(parameter.type.descriptor() ?: return null)
        }
        append(')')
        append(returnType?.descriptor() ?: return null)
    }

    private fun PsiType.descriptor(): String? {
        ProgressManager.checkCanceled()
        return when (val erased = TypeConversionUtil.erasure(this)) {
            is PsiArrayType -> erased.componentType.descriptor()?.let { "[$it" }
            is PsiClassType -> erased.resolve()?.binaryName()?.let { "L${it.replace('.', '/')};" }
            is PsiPrimitiveType -> when (erased.canonicalText) {
                "void" -> "V"
                "boolean" -> "Z"
                "byte" -> "B"
                "char" -> "C"
                "short" -> "S"
                "int" -> "I"
                "long" -> "J"
                "float" -> "F"
                "double" -> "D"
                else -> null
            }

            else -> null
        }
    }

    private fun PsiClass.binaryName(): String? {
        ProgressManager.checkCanceled()
        val outer = containingClass ?: return qualifiedName
        return outer.binaryName()?.let { "${it}$${name ?: return null}" }
    }
}