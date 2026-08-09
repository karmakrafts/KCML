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

import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode

internal val ClassNode.dottedName: String
    get() = name.replace('/', '.')

internal fun ClassNode.hasVisibleAnnotation(type: Type): Boolean = visibleAnnotations?.any { annotationNode ->
    annotationNode.desc == type.descriptor
} == true

internal fun ClassNode.hasInvisibleAnnotation(type: Type): Boolean = invisibleAnnotations?.any { annotationNode ->
    annotationNode.desc == type.descriptor
} == true

internal fun ClassNode.hasAnnotation(type: Type): Boolean = hasInvisibleAnnotation(type) || hasVisibleAnnotation(type)

internal fun ClassNode.findVisibleAnnotation(type: Type): AnnotationNode? = visibleAnnotations?.find { annotation ->
    annotation.desc == type.descriptor
}

internal fun ClassNode.getVisibleAnnotation(type: Type): AnnotationNode = requireNotNull(findVisibleAnnotation(type)) {
    "Class $dottedName does not have a visible annotation of type $type"
}

internal fun ClassNode.findInvisibleAnnotation(type: Type): AnnotationNode? = invisibleAnnotations?.find { annotation ->
    annotation.desc == type.descriptor
}

internal fun ClassNode.getInvisibleAnnotation(type: Type): AnnotationNode =
    requireNotNull(findInvisibleAnnotation(type)) {
        "Class $dottedName does not have an invisible annotation of type $type"
    }

internal fun ClassNode.getAnnotation(type: Type): AnnotationNode =
    findVisibleAnnotation(type) ?: findInvisibleAnnotation(type)
    ?: error("Class $dottedName does not have an annotation of type $type")

internal fun ClassNode.implements(type: Type): Boolean = interfaces.any { name -> name == type.internalName }

internal fun ClassNode.extends(type: Type): Boolean = superName == type.internalName