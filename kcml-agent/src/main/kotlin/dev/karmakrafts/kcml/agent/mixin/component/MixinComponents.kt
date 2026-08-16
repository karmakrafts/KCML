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

package dev.karmakrafts.kcml.agent.mixin.component

import dev.karmakrafts.kcml.agent.asm.Types
import dev.karmakrafts.kcml.agent.asm.getAnnotation
import dev.karmakrafts.kcml.agent.asm.implements
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

    private typealias ComponentFactory = ( // @formatter:off
        mixinClass: ClassNode
    ) -> MixinComponent // @formatter:on

    private val componentFactories: ArrayList<ComponentFactory> = ArrayList()
    private val methodComponentFactories: HashMap<Type, MethodComponentFactory> = HashMap()
    private val fieldComponentFactories: HashMap<Type, FieldComponentFactory> = HashMap()
    private val interfaceComponentFactories: HashMap<Type, ComponentFactory> = HashMap()

    fun registerComponent(factory: ComponentFactory) {
        require(factory !in componentFactories) { "Mixin component is already registered" }
        componentFactories += factory
    }

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

    fun registerInterfaceComponent(type: Type, factory: ComponentFactory) {
        require(type !in methodComponentFactories) {
            "Mixin interface component for type $type is already registered"
        }
        interfaceComponentFactories[type] = factory
    }

    init {
        logger.info { "Registering mixin component factories" }
        registerInterfaceComponent(Types.Mixin.thisAware, ::ThisAwareComponent)
        registerMethodComponent(Types.Mixin.inject, InjectComponent::fromAnnotation)
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun List<AnnotationNode>.findComponentType(factories: Map<Type, *>): Type? =
        map { annotation -> Type.getType(annotation.desc) }.find { type -> type in factories }

    fun findMethodComponentType(method: MethodNode): Type? { // @formatter:off
        return method.visibleAnnotations?.findComponentType(methodComponentFactories)
            ?: method.invisibleAnnotations?.findComponentType(methodComponentFactories)
    } // @formatter:on

    fun findFieldComponentType(field: FieldNode): Type? { // @formatter:off
        return field.visibleAnnotations?.findComponentType(fieldComponentFactories)
            ?: field.invisibleAnnotations?.findComponentType(fieldComponentFactories)
    } // @formatter:on

    fun createDefaultComponents(mixinClass: ClassNode): List<MixinComponent> =
        componentFactories.map { factory -> factory(mixinClass) }

    fun tryCreateInterfaceComponent(type: Type, mixinClass: ClassNode): MixinComponent? = when {
        !mixinClass.implements(type) -> null
        else -> interfaceComponentFactories[type]?.invoke(mixinClass)
    }

    fun tryCreateMethodComponent( // @formatter:off
        type: Type,
        mixinClass: ClassNode,
        mixinMethod: MethodNode
    ): MixinComponent? { // @formatter:on
        val annotation = mixinMethod.getAnnotation(type)
        val component = methodComponentFactories[type]?.invoke(mixinClass, mixinMethod, annotation) ?: return null
        logger.info { "Created mixin method component $component" }
        return component
    }

    fun tryCreateFieldComponent( // @formatter:off
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