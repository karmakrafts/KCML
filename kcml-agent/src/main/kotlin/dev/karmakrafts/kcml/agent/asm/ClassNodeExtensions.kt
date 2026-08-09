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

internal fun ClassNode.hasAnnotation(type: Type): Boolean {
    return visibleAnnotations?.any { annotationNode ->
        annotationNode.desc == type.descriptor
    } == true
}

internal fun ClassNode.getAnnotation(type: Type): AnnotationNode =
    visibleAnnotations.first { annotationNode -> annotationNode.desc == type.descriptor }

internal fun ClassNode.implements(type: Type): Boolean = interfaces.any { name -> name == type.internalName }

internal fun ClassNode.extends(type: Type): Boolean = superName == type.internalName