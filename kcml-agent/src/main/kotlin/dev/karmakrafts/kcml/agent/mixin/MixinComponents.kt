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
import dev.karmakrafts.kcml.agent.log.Logger
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode

internal class MixinComponents(private val logger: Logger) {
    private typealias MethodComponentFactory = ( // @formatter:off
        mixinClass: ClassNode,
        mixinMethod: MethodNode,
        annotation: AnnotationNode
    ) -> MixinComponent // @formatter:on

    private typealias FieldComponentFactory = ( // @formatter:off
        mixinClass: ClassNode,
        mixinField: FieldNode,
        annotation: AnnotationNode
    ) -> MixinComponent // @formatter:on

    private val methodComponentFactories: HashMap<Type, MethodComponentFactory> = HashMap()
    private val fieldComponentFactories: HashMap<Type, FieldComponentFactory> = HashMap()

    fun registerFieldComponent(type: Type, factory: FieldComponentFactory) {
        require(type !in fieldComponentFactories) {
            "Mixin field component for annotation $type is already registered"
        }
        fieldComponentFactories[type] = factory
    }

    fun registerMethodComponent(type: Type, factory: MethodComponentFactory) {
        require(type !in methodComponentFactories) {
            "Mixin method component for annotation $type is already registered"
        }
        methodComponentFactories[type] = factory
    }

    init {
        logger.info { "Registering mixin component factories" }
        registerMethodComponent(Types.Mixin.inject, InjectComponent::fromAnnotation)
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun List<AnnotationNode>.findComponentType(factories: Map<Type, *>): Type? =
        map { annotation -> Type.getType(annotation.desc) }.find { type -> type in factories }

    fun findMixinComponentType(method: MethodNode): Type? { // @formatter:off
        return method.visibleAnnotations?.findComponentType(methodComponentFactories)
            ?: method.invisibleAnnotations?.findComponentType(methodComponentFactories)
    } // @formatter:on

    fun findMixinComponentType(field: FieldNode): Type? { // @formatter:off
        return field.visibleAnnotations?.findComponentType(fieldComponentFactories)
            ?: field.invisibleAnnotations?.findComponentType(fieldComponentFactories)
    } // @formatter:on

    fun tryCreateComponent( // @formatter:off
        type: Type,
        mixinClass: ClassNode,
        mixinMethod: MethodNode
    ): MixinComponent? { // @formatter:on
        val annotation = mixinMethod.getAnnotation(type)
        val component = methodComponentFactories[type]?.invoke(mixinClass, mixinMethod, annotation) ?: return null
        logger.info { "Created mixin method component $component" }
        return component
    }

    fun tryCreateComponent( // @formatter:off
        type: Type,
        mixinClass: ClassNode,
        mixinField: FieldNode
    ): MixinComponent? { // @formatter:on
        val annotation = mixinField.getAnnotation(type)
        val component = fieldComponentFactories[type]?.invoke(mixinClass, mixinField, annotation)
        logger.info { "Created mixin field component $component" }
        return component
    }
}