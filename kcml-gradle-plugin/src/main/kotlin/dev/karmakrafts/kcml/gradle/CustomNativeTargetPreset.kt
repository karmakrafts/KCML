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

@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package dev.karmakrafts.kcml.gradle

import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetPreset
import org.jetbrains.kotlin.konan.target.Architecture
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.lang.reflect.Constructor

internal fun KotlinMultiplatformExtension.createCustomNativeTarget(
    name: String, konanTarget: KonanTarget
): KotlinNativeTarget {
    val adapter = project.extensions.getByType(KCMLExtension::class.java).nativeTargetAdapter
    adapter.register(konanTarget)
    return KotlinNativeTargetPreset(name, project, konanTarget).createTargetInternal(name).also {
        it.compilations.all(adapter::configure)
        adapter.configure(it)
        targets.add(it)
    }
}

private object CustomKonanTargetFactory : ClassLoader(KonanTarget::class.java.classLoader) {
    @Suppress("UNCHECKED_CAST")
    val constructor: Constructor<KonanTarget> by lazy {
        val targetName = TypeNames.konanTarget.internalName
        val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
        writer.visit(
            Opcodes.V1_8,
            Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL,
            TypeNames.customKonanTarget.internalName,
            null,
            targetName,
            null
        )
        writer.visitMethod(
            Opcodes.ACC_PUBLIC,
            "<init>",
            Type.getMethodDescriptor(Type.VOID_TYPE, TypeNames.string, TypeNames.family, TypeNames.architecture),
            null,
            null
        ).apply {
            visitCode()
            visitVarInsn(Opcodes.ALOAD, 0)
            visitVarInsn(Opcodes.ALOAD, 1)
            visitVarInsn(Opcodes.ALOAD, 2)
            visitVarInsn(Opcodes.ALOAD, 3)
            visitInsn(Opcodes.ACONST_NULL)
            visitMethodInsn(
                Opcodes.INVOKESPECIAL, targetName, "<init>", Type.getMethodDescriptor(
                    Type.VOID_TYPE,
                    TypeNames.string,
                    TypeNames.family,
                    TypeNames.architecture,
                    TypeNames.defaultConstructorMarker
                ), false
            )
            visitInsn(Opcodes.RETURN)
            visitMaxs(0, 0)
            visitEnd()
        }
        writer.visitEnd()
        val bytes = writer.toByteArray()
        requireNotNull(
            defineClass(TypeNames.customKonanTarget.className, bytes, 0, bytes.size).getConstructor(
                String::class.java, Family::class.java, Architecture::class.java
            ) as Constructor<KonanTarget>
        ) {
            "Could not reflect constructor of custom KonanTarget for KCML"
        }
    }
}

internal fun customKonanTarget(name: String, family: Family, architecture: Architecture): KonanTarget {
    require(name.isNotBlank()) { "Konan target name must not be blank" }
    return CustomKonanTargetFactory.constructor.newInstance(name, family, architecture) as KonanTarget
}