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
import dev.karmakrafts.kcml.agent.asm.copy
import dev.karmakrafts.kcml.agent.asm.dottedName
import dev.karmakrafts.kcml.agent.asm.findInvisibleParameterAnnotation
import dev.karmakrafts.kcml.agent.asm.getValue
import dev.karmakrafts.kcml.agent.asm.relocateStack
import dev.karmakrafts.kcml.agent.mixin.Capture
import dev.karmakrafts.kcml.agent.mixin.Order
import dev.karmakrafts.kcml.agent.mixin.Slice
import dev.karmakrafts.kcml.agent.mixin.Target
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.signature.SignatureReader
import org.objectweb.asm.signature.SignatureVisitor
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.LocalVariableNode
import org.objectweb.asm.tree.MethodInsnNode
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
) : AbstractMixinComponent() { // @formatter:on
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

    private fun getReturnContextStackIndices(): Set<Int> = buildSet {
        var stackIndex = if (mixinMethod.access and Opcodes.ACC_STATIC == 0) 1 else 0
        for (argumentType in mixinMethodType.argumentTypes) {
            if (argumentType == Types.Mixin.returnContext) add(stackIndex)
            stackIndex += argumentType.size
        }
    }

    private fun getReturnContextType(): Type {
        val signature = checkNotNull(mixinMethod.signature) {
            "ReturnContext parameter in ${mixinMethod.name}${mixinMethod.desc} requires a generic signature"
        }
        var parameterIndex = -1
        var returnContextType: Type? = null
        SignatureReader(signature).accept(object : SignatureVisitor(Opcodes.ASM9) {
            override fun visitParameterType(): SignatureVisitor {
                parameterIndex++
                if (mixinMethodType.argumentTypes[parameterIndex] != Types.Mixin.returnContext) return this
                return object : SignatureVisitor(Opcodes.ASM9) {
                    private var isReturnContext: Boolean = false

                    override fun visitClassType(name: String) {
                        isReturnContext = name == Types.Mixin.returnContext.internalName
                    }

                    override fun visitTypeArgument(wildcard: Char): SignatureVisitor? {
                        if (!isReturnContext || returnContextType != null) return null
                        return object : SignatureVisitor(Opcodes.ASM9) {
                            private var arrayDimensions: Int = 0

                            override fun visitArrayType(): SignatureVisitor {
                                arrayDimensions++
                                return this
                            }

                            override fun visitBaseType(descriptor: Char) {
                                returnContextType = Type.getType("[".repeat(arrayDimensions) + descriptor)
                            }

                            override fun visitClassType(name: String) {
                                returnContextType = Type.getType("[".repeat(arrayDimensions) + "L$name;")
                            }

                            override fun visitTypeVariable(name: String) {
                                error("ReturnContext type variable $name is not supported")
                            }
                        }
                    }
                }
            }
        })
        return checkNotNull(returnContextType) {
            "Could not determine ReturnContext type in ${mixinMethod.name}${mixinMethod.desc}"
        }
    }

    private fun InsnList.processCapturedLocals( // @formatter:off
        context: ComponentContext,
        targetMethod: MethodNode,
        injectionPoint: AbstractInsnNode,
        relocated: HashSet<VarInsnNode>,
        capturedIndices: HashSet<Int>
    ): InsnList { // @formatter:on
        val (_, _, logger) = context
        val captures = buildMap {
            val parameters = mixinMethod.parameters // We know this is non-null from restoring earlier
            var stackIndex = if (mixinMethod.access and Opcodes.ACC_STATIC == 0) 1 else 0
            for (index in parameters.indices) {
                val parameter = parameters[index]
                val annotation = mixinMethod.findInvisibleParameterAnnotation(index, Types.Mixin.capture)
                if (annotation != null) {
                    val capture = Capture.fromAnnotation(annotation)
                    val targetIndex = capture.findStackIndexAt(targetMethod, injectionPoint, order, parameter.name)
                    check(targetIndex != Capture.NOT_FOUND) {
                        "Could not find captured local ${parameter.name} at injection point in target method ${targetMethod.name}${targetMethod.desc}"
                    }
                    this[stackIndex] = targetIndex
                    capturedIndices += stackIndex
                }
                stackIndex += mixinMethodType.argumentTypes[index].size
            }
        }
        if (captures.isEmpty()) return this // If no captures were found, we return early
        logger.info { "Found ${captures.size} capturing parameters, indices are [${captures.entries.joinToString { (key, value) -> "$key -> $value" }}]" }
        for (instruction in this) {
            if (instruction is VarInsnNode) {
                instruction.`var` = captures[instruction.`var`] ?: continue
                relocated += instruction
            }
        }
        return this
    }

    private fun allocateLocals( // @formatter:off
        targetMethod: MethodNode,
        injection: InsnList,
        relocated: Set<VarInsnNode>,
        capturedIndices: Set<Int>,
        copiedLabels: Map<LabelNode, LabelNode>
    ): Int { // @formatter:on
        val targetBase = targetMethod.maxLocals
        val localBase = injection.filterIsInstance<VarInsnNode>()
            .filterNot { instruction -> instruction in relocated }
            .minOfOrNull { instruction -> instruction.`var` } ?: return targetBase
        targetMethod.maxLocals += mixinMethod.maxLocals - localBase
        val targetLocals = targetMethod.localVariables ?: mutableListOf<LocalVariableNode>().also { locals ->
            targetMethod.localVariables = locals
        }
        for (local in mixinMethod.localVariables.orEmpty()) {
            if (local.index < localBase || local.index in capturedIndices) continue
            targetLocals += LocalVariableNode(
                "mixin$${mixinMethod.name}$${local.name}",
                local.desc,
                local.signature,
                checkNotNull(copiedLabels[local.start]) { "Could not find start label for mixin local ${local.name}" },
                checkNotNull(copiedLabels[local.end]) { "Could not find end label for mixin local ${local.name}" },
                targetBase + local.index - localBase
            )
        }
        return targetBase
    }

    private fun InsnList.processReturnFrame(context: ComponentContext): InsnList {
        val (_, _, logger) = context
        logger.info { "Inserting return frame and replacing returns" }
        val returnFrame = LabelNode()
        for (instruction in this) {
            if (instruction.opcode == Opcodes.RETURN) {
                set(instruction, JumpInsnNode(Opcodes.GOTO, returnFrame))
            }
        }
        add(returnFrame)
        return this
    }

    private fun MethodInsnNode.isReturnFromTargetCall(): Boolean { // @formatter:off
        return opcode == Opcodes.INVOKEINTERFACE
            && owner == Types.Mixin.returnContext.internalName
            && name == "returnFromTarget"
            && desc == Type.getMethodDescriptor(Type.VOID_TYPE, Types.any)
    } // @formatter:on

    private fun MethodInsnNode.isParameterNullCheck(): Boolean { // @formatter:off
        return opcode == Opcodes.INVOKESTATIC
            && owner == Types.intrinsics.internalName
            && (name == "checkNotNullParameter" || name == "checkParameterIsNotNull")
            && desc == Type.getMethodDescriptor(Type.VOID_TYPE, Types.any, Types.string)
    } // @formatter:on

    private fun InsnList.removeReturnContextParameterChecks(returnContextIndices: Set<Int>) {
        val checks = filterIsInstance<MethodInsnNode>().filter { instruction -> instruction.isParameterNullCheck() }
        for (check in checks) {
            var parameterName = check.previous
            while (parameterName != null && parameterName.opcode == -1) parameterName = parameterName.previous
            if (parameterName !is LdcInsnNode || parameterName.cst !is String) continue
            var parameter = parameterName.previous
            while (parameter != null && parameter.opcode == -1) parameter = parameter.previous
            if (parameter !is VarInsnNode || parameter.opcode != Opcodes.ALOAD || parameter.`var` !in returnContextIndices) continue
            remove(parameter)
            remove(parameterName)
            remove(check)
        }
    }

    private fun InsnList.removeUnitArgument(call: MethodInsnNode): AbstractInsnNode? {
        var unitValue = call.previous
        while (unitValue != null && unitValue.opcode == -1) unitValue = unitValue.previous
        check( // @formatter:off
            unitValue is FieldInsnNode && unitValue.opcode == Opcodes.GETSTATIC
                && unitValue.owner == Types.unit.internalName
                && unitValue.name == "INSTANCE"
        ) { "ReturnContext<Unit>.returnFromTarget() requires a Unit.INSTANCE argument" } // @formatter:on
        val previous = unitValue.previous
        remove(unitValue)
        return previous
    }

    private fun findReturnContextReceiver( // @formatter:off
        start: AbstractInsnNode?,
        returnContextIndices: Set<Int>,
        allowSyntheticReceiver: Boolean
    ): VarInsnNode { // @formatter:on
        var receiver = start
        while (receiver != null && (receiver !is VarInsnNode // @formatter:off
                || receiver.opcode != Opcodes.ALOAD
                || (!allowSyntheticReceiver && receiver.`var` !in returnContextIndices)
        )
        ) { // @formatter:on
            receiver = receiver.previous
        }
        return checkNotNull(receiver) { "Could not find ReturnContext receiver for returnFromTarget()" }
    }

    private fun InsnList.removeReturnContextReceiver( // @formatter:off
        receiver: VarInsnNode,
        returnContextIndices: Set<Int>
    ) { // @formatter:on
        if (receiver.`var` !in returnContextIndices) {
            // The inline Unit overload aliases its receiver in a synthetic local; remove that alias as well.
            var receiverStore = receiver.previous
            while (receiverStore != null && (receiverStore !is VarInsnNode  // @formatter:off
                    || receiverStore.opcode != Opcodes.ASTORE
                    || receiverStore.`var` != receiver.`var`
            )
            ) { // @formatter:on
                receiverStore = receiverStore.previous
            }
            checkNotNull(receiverStore) { "Could not find synthetic ReturnContext receiver store" }
            var originalReceiver = receiverStore.previous
            while (originalReceiver != null && originalReceiver.opcode == -1) {
                originalReceiver = originalReceiver.previous
            }
            check(
                originalReceiver is VarInsnNode && originalReceiver.opcode == Opcodes.ALOAD && originalReceiver.`var` in returnContextIndices
            ) { "Could not find original ReturnContext receiver load" }
            remove(originalReceiver)
            remove(receiverStore)
        }
        remove(receiver)
    }

    private fun InsnList.processReturnContext(context: ComponentContext): InsnList {
        // If the mixin function doesn't have a ReturnContext parameter, we return early
        if (!hasReturnContext()) return this
        val (_, _, logger) = context
        logger.info { "Inject component has return context, processing references to returnFromTarget()" }
        val returnContextIndices = getReturnContextStackIndices()
        removeReturnContextParameterChecks(returnContextIndices)
        // Only rewrite calls to the erased ReturnContext API; unrelated interface calls must remain untouched.
        val calls = filterIsInstance<MethodInsnNode>().filter { instruction -> instruction.isReturnFromTargetCall() }
        if (calls.isEmpty()) return this
        val returnContextType = getReturnContextType()
        val isUnit = returnContextType == Types.unit
        val returnOpcode = if (isUnit) Opcodes.RETURN else returnContextType.getOpcode(Opcodes.IRETURN)
        for (call in calls) {
            val previous = if (isUnit) {
                // Unit is passed as Unit.INSTANCE, but a JVM void return must leave no value on the operand stack.
                removeUnitArgument(call)
            }
            else call.previous
            val receiver = findReturnContextReceiver(previous, returnContextIndices, isUnit)
            removeReturnContextReceiver(receiver, returnContextIndices)
            set(call, InsnNode(returnOpcode))
        }
        return this
    }

    private fun createInjection( // @formatter:off
        context: ComponentContext,
        targetMethod: MethodNode,
        injectionPoint: AbstractInsnNode
    ): InsnList { // @formatter:on
        val relocated = HashSet<VarInsnNode>()
        val capturedIndices = HashSet<Int>()
        val sourceInstructions = mixinMethod.instructions.toArray()
        val injection = mixinMethod.instructions.copy()
        val copiedInstructions = injection.toArray()
        val copiedLabels = buildMap {
            for (index in sourceInstructions.indices) {
                val source = sourceInstructions[index]
                if (source is LabelNode) put(source, copiedInstructions[index] as LabelNode)
            }
        }
        injection.processCapturedLocals(context, targetMethod, injectionPoint, relocated, capturedIndices)
            .processReturnFrame(context)
            .processReturnContext(context)
        context.otherComponents.filterIsInstance<ThisAwareComponent>()
            .singleOrNull()
            ?.prepareInjection(injection, relocated)
        val targetBase = allocateLocals(targetMethod, injection, relocated, capturedIndices, copiedLabels)
        return injection.relocateStack(targetBase, relocated)
    } // @formatter:on

    private fun injectIntoTarget(context: ComponentContext, targetMethod: MethodNode) {
        val (targetClass, _, logger) = context
        val instructions = targetMethod.instructions
        val needle = with(slice) { target.findWithin(instructions) }
            ?: error("Could not find injection target for ${targetClass.dottedName}.${targetMethod.name}")
        logger.info { "Found injection point in ${targetClass.dottedName}.${targetMethod.name}${targetMethod.desc}" }
        val injection = createInjection(context, targetMethod, needle)
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