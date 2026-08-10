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

package dev.karmakrafts.kcml.agent.asm

import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.ParameterNode

internal fun MethodNode.restoreParameters() {
    val argumentTypes = Type.getArgumentTypes(desc)
    if (parameters?.size == argumentTypes.size) return // If all parameters are present, don't try to restore them
    var localIndex = if (access and Opcodes.ACC_STATIC == 0) 1 else 0
    parameters = argumentTypes.mapTo(mutableListOf()) { argumentType ->
        // @formatter:off
        val localVariable = localVariables
            ?.asSequence()
            ?.filter { variable ->
                variable.index == localIndex && variable.desc == argumentType.descriptor
            }
            ?.minByOrNull { variable -> instructions.indexOf(variable.start) }
        // @formatter:on
        localIndex += argumentType.size
        ParameterNode(localVariable?.name, 0)
    }
}

internal fun MethodNode.hasVisibleAnnotation(type: Type): Boolean = visibleAnnotations?.any { annotationNode ->
    annotationNode.desc == type.descriptor
} == true

internal fun MethodNode.hasInvisibleAnnotation(type: Type): Boolean = invisibleAnnotations?.any { annotationNode ->
    annotationNode.desc == type.descriptor
} == true

internal fun MethodNode.hasAnnotation(type: Type): Boolean = hasInvisibleAnnotation(type) || hasVisibleAnnotation(type)

internal fun MethodNode.findVisibleAnnotation(type: Type): AnnotationNode? = visibleAnnotations?.find { annotation ->
    annotation.desc == type.descriptor
}

internal fun MethodNode.getVisibleAnnotation(type: Type): AnnotationNode = requireNotNull(findVisibleAnnotation(type)) {
    "Method $name$desc does not have a visible annotation of type $type"
}

internal fun MethodNode.findInvisibleAnnotation(type: Type): AnnotationNode? =
    invisibleAnnotations?.find { annotation ->
        annotation.desc == type.descriptor
    }

internal fun MethodNode.getInvisibleAnnotation(type: Type): AnnotationNode =
    requireNotNull(findInvisibleAnnotation(type)) {
        "Method $name$desc does not have an invisible annotation of type $type"
    }

internal fun MethodNode.getAnnotation(type: Type): AnnotationNode =
    findVisibleAnnotation(type) ?: findInvisibleAnnotation(type)
    ?: error("Method $name$desc does not have an annotation of type $type")

internal fun MethodNode.findInvisibleParameterAnnotation(index: Int, type: Type): AnnotationNode? {
    return if (index == -1) null
    else invisibleParameterAnnotations?.getOrNull(index)?.find { annotation ->
        annotation.desc == type.descriptor
    }
}