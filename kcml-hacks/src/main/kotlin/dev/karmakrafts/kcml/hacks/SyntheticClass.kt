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

import org.objectweb.asm.ClassWriter

/**
 * Defines a runtime class using [builder] to generate its bytecode.
 *
 * Each instance defines a separate class in a child of [superType]'s class loader. No existing class is modified.
 * The builder must visit the class header using [name] and emit all required members, including constructors.
 * The writer computes maximum stack and local variable sizes and is ended after the builder returns.
 *
 * @param name binary name of the generated class, using dots as package separators.
 * @param superType class or interface to which the generated class must be assignable.
 * @param builder callback configuring the class header and members with ASM.
 */
@DiscouragedKcmlApi
public open class SyntheticClass<T : Any>(
    name: String,
    superType: Class<T>,
    builder: ClassWriter.() -> Unit
) {
    private class Loader(parent: ClassLoader?) : ClassLoader(parent) {
        fun define(name: String, bytes: ByteArray): Class<*> = defineClass(name, bytes, 0, bytes.size)
    }

    /** The generated runtime class. */
    public val type: Class<out T>

    init {
        val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
        writer.builder()
        writer.visitEnd()
        type = Loader(superType.classLoader).define(name, writer.toByteArray()).asSubclass(superType)
    }
}