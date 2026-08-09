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

import dev.karmakrafts.kcml.agent.asm.dottedName
import dev.karmakrafts.kcml.agent.log.Logger
import dev.karmakrafts.kcml.agent.log.error
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.TypeInsnNode
import java.lang.instrument.ClassFileTransformer
import java.security.ProtectionDomain

internal class MixinClassTransformer( // @formatter:off
    private val logger: Logger,
    private val loader: MixinLoader
) : ClassFileTransformer { // @formatter:on
    private fun findMixinInstantiation(insn: AbstractInsnNode): Type? = when {
        insn.opcode != Opcodes.NEW -> null
        insn !is TypeInsnNode -> null
        !loader.isMixin(Type.getObjectType(insn.desc)) -> null
        else -> Type.getObjectType(insn.desc)
    }

    private fun checkForMixinInstantiations(classNode: ClassNode) {
        for (method in classNode.methods) {
            for (insn in method.instructions) {
                val mixinType = findMixinInstantiation(insn) ?: continue
                throw MixinRuntimeInstantiationException(
                    "Mixin ${mixinType.dottedName} is being instantiated in ${classNode.dottedName}.${method.name}${method.desc}"
                )
            }
        }
    }

    override fun transform(
        module: Module?,
        loader: ClassLoader?,
        className: String?,
        classBeingRedefined: Class<*>?,
        protectionDomain: ProtectionDomain?,
        classfileBuffer: ByteArray?
    ): ByteArray {
        if (className.isNullOrBlank() || classfileBuffer == null) return ByteArray(0)
        val type = Type.getObjectType(className)
        try {
            if (this.loader.isMixin(type)) {
                // This means a mixin class has been loaded at runtime, which is usually a bad sign, so emit a warning
                logger.warn { "Mixin class ${type.dottedName} was loaded at runtime, this is usually not recommended" }
                return classfileBuffer
            }
            val classReader = ClassReader(classfileBuffer)
            val classNode = ClassNode()
            classReader.accept(classNode, ClassReader.SKIP_FRAMES)
            // Explicit mixin instantiations are completely illegal, so we check for them in every class
            checkForMixinInstantiations(classNode)
            return classfileBuffer // TODO: implement class writing by voting
        } catch (error: MixinRuntimeInstantiationException) {
            throw error // Runtime instantiations are irrecoverable
        } catch (error: Throwable) {
            logger.error(error) { "Could not transform class ${type.dottedName}" }
            return classfileBuffer
        }
    }
}