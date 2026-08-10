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
import dev.karmakrafts.kcml.agent.asm.copy
import dev.karmakrafts.kcml.agent.asm.dottedName
import dev.karmakrafts.kcml.agent.asm.findInvisibleParameterAnnotation
import dev.karmakrafts.kcml.agent.asm.getValue
import dev.karmakrafts.kcml.agent.asm.implements
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.VarInsnNode

/**
 * A mixin component for injection constructed from the `Inject` annotation exposed by the runtime API.
 */
internal data class InjectComponent( // @formatter:off
    val name: String,
    val descriptor: Type?,
    val slice: Slice,
    val target: Target,
    val order: Order,
    val mixinClass: ClassNode,
    val mixinMethod: MethodNode
) : MixinComponent { // @formatter:on
    companion object {
        fun fromAnnotation( // @formatter:off
            mixinClass: ClassNode,
            mixinMethod: MethodNode,
            node: AnnotationNode
        ): InjectComponent = InjectComponent( // @formatter:on
            name = requireNotNull(node.getValue("name")) { "InjectComponent requires name" },
            descriptor = node.getValue<String>("descriptor")?.let(Type::getMethodType),
            slice = node.getValue<AnnotationNode>("slice")?.let(Slice::fromAnnotation) ?: Slice(),
            target = node.getValue<AnnotationNode>("target")?.let(Target::fromAnnotation) ?: Target(),
            order = node.getValue<Order>("order") ?: Order.AFTER,
            mixinClass = mixinClass,
            mixinMethod = mixinMethod
        )
    }

    private val mixinMethodType: Type = Type.getMethodType(mixinMethod.desc)

    private fun hasReturnContext(): Boolean = mixinMethodType.argumentTypes.any { type ->
        type == Types.Mixin.returnContext
    }

    private fun InsnList.processCapturedLocals(context: ComponentContext, targetMethod: MethodNode): InsnList {
        val (_, _, logger) = context
        val captures = buildMap {
            val parameters = mixinMethod.parameters // We know this is non-null from restoring earlier
            var stackIndex = if (mixinMethod.access and Opcodes.ACC_STATIC == 0) 1 else 0
            for (index in parameters.indices) {
                val parameter = parameters[index]
                val annotation = mixinMethod.findInvisibleParameterAnnotation(index, Types.Mixin.capture)
                if (annotation != null) {
                    val capture = Capture.fromAnnotation(annotation)
                    val targetIndex = capture.findStackIndex(targetMethod, parameter.name)
                    check(targetIndex != Capture.NOT_FOUND) {
                        "Could not find captured local ${parameter.name} in target method ${targetMethod.name}${targetMethod.desc}"
                    }
                    this[stackIndex] = targetIndex
                }
                stackIndex += mixinMethodType.argumentTypes[index].size
            }
        }
        if (captures.isEmpty()) return this // If no captures were found, we return early
        logger.info { "Found ${captures.size} capturing parameters, indices are [${captures.entries.joinToString { (key, value) -> "$key -> $value" }}]" }
        for (instruction in this) {
            if (instruction is VarInsnNode) {
                instruction.`var` = captures[instruction.`var`] ?: continue
            }
        }
        return this
    }

    private fun InsnList.processReturnContext(context: ComponentContext): InsnList {
        // If the mixin function doesn't have a ReturnContext parameter, we return early
        if (!hasReturnContext()) return this
        val (_, _, logger) = context
        logger.info { "Inject component has return context, processing references to returnFromTarget()" }
        return this
    }

    private fun InsnList.processThisAware(context: ComponentContext): InsnList {
        // If the target mixin doesn't implement ThisAware, we return early
        if (!mixinClass.implements(Types.Mixin.thisAware)) return this
        val (_, _, logger) = context
        logger.info { "Mixin is this-aware, processing references to getThis()" }
        return this
    }

    // Parameter capturing analysis
    // Replace loads of captured values with their respective target indices
    // Replace all loads & calls to ReturnContext and replace them with target returns
    // Replace all calls to ThisAware with their intrinsic target this load
    // Relocate stack to max index of target method (using relocateStack extension)
    private fun createInjection(context: ComponentContext, targetMethod: MethodNode): InsnList { // @formatter:off
        return mixinMethod.instructions.copy()
            .processCapturedLocals(context, targetMethod)
            .processReturnContext(context)
            .processThisAware(context)
    } // @formatter:on

    private fun injectIntoTarget(context: ComponentContext, targetMethod: MethodNode) {
        val (targetClass, _, logger) = context
        val instructions = targetMethod.instructions
        val needle = with(slice) { target.findWithin(instructions) }
            ?: error("Could not find injection target for ${targetClass.dottedName}.${targetMethod.name}")
        logger.info { "Found injection point in ${targetClass.dottedName}.${targetMethod.name}${targetMethod.desc}" }
        val injection = createInjection(context, targetMethod)
        order.insert(needle, injection, targetMethod.instructions)
    }

    override fun apply(context: ComponentContext): Boolean {
        val (target, _, logger) = context
        // First we need to find the target method
        for (targetMethod in target.methods) when {
            targetMethod.name != name -> continue
            descriptor != null && targetMethod.desc != descriptor.descriptor -> continue
            else -> {
                logger.info { "Applying injection to ${target.dottedName}.${targetMethod.name}${targetMethod.desc}" }
                injectIntoTarget(context, targetMethod)
                return true
            }
        }
        return false
    }
}