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

package dev.karmakrafts.kcml.hacks

import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.lang.reflect.Constructor
import java.lang.reflect.Modifier

/**
 * Defines a public final subclass with a public constructor forwarding to [superConstructor].
 *
 * Only public superclasses and public or protected constructors are supported; JVM restrictions on inheritance
 * still apply, and abstract methods are not implemented.
 *
 * @param name binary name of the generated class, using dots as package separators.
 * @param superConstructor superclass constructor to invoke.
 * @param trailingNullParameters number of trailing reference parameters supplied as null instead of exposed in
 * the generated constructor; useful for Kotlin's synthetic default constructor marker.
 * @throws IllegalArgumentException if the superclass or constructor is inaccessible, the superclass is final,
 * or the trailing parameter count or types are invalid.
 */
@DiscouragedKcmlApi
public class SyntheticDelegateClass<T : Any>(
    name: String,
    superConstructor: Constructor<T>,
    trailingNullParameters: Int = 0
) : SyntheticClass<T>(name, superConstructor.declaringClass, {
    val superType = superConstructor.declaringClass
    require(Modifier.isPublic(superType.modifiers) && !Modifier.isFinal(superType.modifiers)) {
        "Superclass must be public and non-final: ${superType.name}"
    }
    require(Modifier.isPublic(superConstructor.modifiers) || Modifier.isProtected(superConstructor.modifiers)) {
        "Superclass constructor must be public or protected"
    }
    val parameters = superConstructor.parameterTypes
    require(trailingNullParameters in 0..parameters.size) { "Invalid trailing null parameter count" }
    val exposedParameters = parameters.take(parameters.size - trailingNullParameters).toTypedArray()
    require(parameters.takeLast(trailingNullParameters).none { it.isPrimitive }) {
        "Trailing null parameters must be reference types"
    }

    val className = name.replace('.', '/')
    val superName = Type.getInternalName(superType)
    visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL, className, null, superName, null)
    visitMethod(
        Opcodes.ACC_PUBLIC,
        "<init>",
        Type.getMethodDescriptor(Type.VOID_TYPE, *exposedParameters.map(Type::getType).toTypedArray()),
        null,
        null
    ).apply {
        visitCode()
        visitVarInsn(Opcodes.ALOAD, 0)
        var slot = 1
        for (parameter in exposedParameters) {
            val parameterType = Type.getType(parameter)
            visitVarInsn(parameterType.getOpcode(Opcodes.ILOAD), slot)
            slot += parameterType.size
        }
        repeat(trailingNullParameters) { visitInsn(Opcodes.ACONST_NULL) }
        visitMethodInsn(
            Opcodes.INVOKESPECIAL, superName, "<init>", Type.getConstructorDescriptor(superConstructor), false
        )
        visitInsn(Opcodes.RETURN)
        visitMaxs(0, 0)
        visitEnd()
    }
}) {
    /** The public forwarding constructor, excluding parameters supplied as null. */
    public val constructor: Constructor<out T> = type.getConstructor(
        *superConstructor.parameterTypes.take(superConstructor.parameterCount - trailingNullParameters).toTypedArray()
    )
}