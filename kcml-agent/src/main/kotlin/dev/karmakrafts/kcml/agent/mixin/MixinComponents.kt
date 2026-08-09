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

package dev.karmakrafts.kcml.agent.mixin

import dev.karmakrafts.kcml.agent.asm.Types
import dev.karmakrafts.kcml.agent.asm.getAnnotation
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode

internal object MixinComponents {
    private typealias MethodComponentFactory = ( // @formatter:off
        mixinClass: ClassNode,
        mixinMethod: MethodNode,
        annotation: AnnotationNode
    ) -> MixinComponent // @formatter:on

    private val methodComponentFactories: HashMap<Type, MethodComponentFactory> = HashMap()

    fun registerMethodComponent(type: Type, factory: MethodComponentFactory) {
        require(type !in methodComponentFactories) {
            "Mixin component for annotation $type is already registered"
        }
        methodComponentFactories[type] = factory
    }

    init {
        registerMethodComponent(Types.Mixin.inject, InjectComponent::fromAnnotation)
    }

    fun findMixinComponentType(method: MethodNode): Type? { // @formatter:off
        return method.visibleAnnotations
            .map { annotation -> Type.getType(annotation.desc) }
            .find { type -> type in methodComponentFactories }
    } // @formatter:on

    fun tryCreateComponent( // @formatter:off
        type: Type,
        mixinClass: ClassNode,
        mixinMethod: MethodNode
    ): MixinComponent? { // @formatter:on
        val annotation = mixinMethod.getAnnotation(type)
        return methodComponentFactories[type]?.invoke(mixinClass, mixinMethod, annotation)
    }
}