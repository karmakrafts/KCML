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

@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package dev.karmakrafts.kcml.api.util

import dev.karmakrafts.kcml.api.DelicateKcmlApi
import java.lang.invoke.MethodHandles
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.lang.Boolean as JBoolean
import java.lang.Byte as JByte
import java.lang.Character as JCharacter
import java.lang.Double as JDouble
import java.lang.Float as JFloat
import java.lang.Integer as JInteger
import java.lang.Long as JLong
import java.lang.Short as JShort
import java.lang.reflect.Array as ReflectArray

/**
 * Allows creating new enum constants for an already existing enum type at runtime.
 */
@DelicateKcmlApi
public object EnumExtender {
    /**
     * Defines and registers a new constant in enum type [E] with the given [name] and [args].
     *
     * The enum type is initialized before the constant is created. Constructor selection uses the supplied argument
     * count and runtime types, matching boxed values to primitive parameters and allowing `null` only for reference
     * parameters. If several constructors match, the unique most-specific constructor is used; an ambiguous match
     * is rejected. The new constant is added to the runtime values array and the reflection and Kotlin enum-entry
     * caches when present. Existing references or snapshots of enum values and compiled enum dispatch code are not
     * updated.
     *
     * @param E enum type to extend.
     * @param name non-empty name for the new enum constant; it must not already be in use.
     * @param args arguments passed to the enum constructor, excluding the generated name and ordinal parameters.
     * @return the newly created enum constant.
     * @throws IllegalArgumentException if [name] is empty or already used, or no unique matching constructor exists.
     * @throws IllegalAccessException if JVM access restrictions prevent access to the enum constructor.
     */
    public inline fun <reified E : Enum<E>> define( // @formatter:off
        name: String,
        vararg args: Any?
    ): E = defineWithEnumType(E::class.java, name, args)

    @Suppress("DEPRECATION")
    @PublishedApi
    internal fun <E : Enum<E>> defineWithEnumType(
        enumType: Class<E>,
        name: String,
        args: Array<out Any?>
    ): E = synchronized(enumType) {
        require(enumType.isEnum) { "${enumType.name} is not an enum type" }
        require(name.isNotEmpty()) { "Enum constant names must not be empty" }

        // This initializes the enum before reading its generated values field
        checkNotNull(enumType.enumConstants) { "Could not initialize enum type ${enumType.name}" }
        val valuesField = findValuesField(enumType)
        val unsafe = UnsafeUtils.unsafe
        val valuesBase = unsafe.staticFieldBase(valuesField)
        val valuesOffset = unsafe.staticFieldOffset(valuesField)
        val currentValues = unsafe.getObjectVolatile(valuesBase, valuesOffset) as? Array<*>
            ?: error("Could not read enum values for ${enumType.name}")
        require(currentValues.none { (it as Enum<*>).name == name }) {
            "Enum ${enumType.name} already contains a constant named $name"
        }

        val ordinal = Math.addExact(currentValues.maxOfOrNull { (it as Enum<*>).ordinal } ?: -1, 1)
        val constructor = findConstructor(enumType, args)
        val lookup = MethodHandles.privateLookupIn(enumType, MethodHandles.lookup())
        val handle = lookup.unreflectConstructor(constructor)
        @Suppress("UNCHECKED_CAST")
        val constant = handle.invokeWithArguments(listOf(name, ordinal, *args)) as E

        val updatedValues = ReflectArray.newInstance(enumType, currentValues.size + 1)
        System.arraycopy(currentValues, 0, updatedValues, 0, currentValues.size)
        ReflectArray.set(updatedValues, currentValues.size, constant)

        val entriesField = enumType.declaredFields.firstOrNull { field ->
            Modifier.isStatic(field.modifiers) && field.type.name == "kotlin.enums.EnumEntries"
        }
        val entries = entriesField?.let { field -> createEntries(field.type, updatedValues) }

        val enumConstantsField = Class::class.java.getDeclaredField("enumConstants")
        val enumConstantDirectoryField = Class::class.java.getDeclaredField("enumConstantDirectory")
        val enumConstantsOffset = unsafe.objectFieldOffset(enumConstantsField)
        val enumConstantDirectoryOffset = unsafe.objectFieldOffset(enumConstantDirectoryField)
        val entriesBase = entriesField?.let(unsafe::staticFieldBase)
        val entriesOffset = entriesField?.let(unsafe::staticFieldOffset)

        unsafe.putObjectVolatile(valuesBase, valuesOffset, updatedValues)
        unsafe.putObjectVolatile(enumType, enumConstantsOffset, updatedValues)
        unsafe.putObjectVolatile(enumType, enumConstantDirectoryOffset, null)
        if (entries != null && entriesBase != null && entriesOffset != null) {
            unsafe.putObjectVolatile(
                entriesBase,
                entriesOffset,
                entries
            )
        }
        constant
    }

    private fun findValuesField(enumType: Class<*>): Field {
        val fields = enumType.declaredFields.filter { field ->
            Modifier.isStatic(field.modifiers) && field.type.isArray && field.type.componentType == enumType
        }
        return fields.firstOrNull { field -> field.name == $$"$VALUES" || field.name == $$"ENUM$VALUES" }
            ?: fields.singleOrNull()
            ?: error("Could not find the values array for ${enumType.name}")
    }

    private fun findConstructor(enumType: Class<*>, args: Array<out Any?>): Constructor<*> {
        val matching = enumType.declaredConstructors.filter { constructor ->
            val parameterTypes = constructor.parameterTypes
            parameterTypes.size == args.size + 2 &&
                parameterTypes[0] == String::class.java &&
                parameterTypes[1] == Int::class.javaPrimitiveType &&
                args.indices.all { index -> accepts(parameterTypes[index + 2], args[index]) }
        }
        require(matching.isNotEmpty()) {
            "No enum constructor on ${enumType.name} accepts ${args.size} argument(s)"
        }
        val mostSpecific = matching.filter { candidate ->
            matching.all { other -> candidate === other || isMoreSpecific(candidate, other) }
        }
        require(mostSpecific.size == 1) {
            "Ambiguous enum constructor on ${enumType.name} for ${args.size} argument(s)"
        }
        return mostSpecific.single()
    }

    private fun accepts(parameterType: Class<*>, value: Any?): Boolean = when {
        value == null -> !parameterType.isPrimitive
        parameterType.isPrimitive -> boxedType(parameterType).isInstance(value)
        else -> parameterType.isInstance(value)
    }

    private fun isMoreSpecific(candidate: Constructor<*>, other: Constructor<*>): Boolean {
        var strictlyMoreSpecific = false
        for (index in 2 until candidate.parameterCount) {
            val candidateType = boxedType(candidate.parameterTypes[index])
            val otherType = boxedType(other.parameterTypes[index])
            if (candidateType == otherType) continue
            if (!otherType.isAssignableFrom(candidateType)) return false
            strictlyMoreSpecific = true
        }
        return strictlyMoreSpecific
    }

    private fun boxedType(type: Class<*>): Class<*> = when (type) {
        JBoolean.TYPE -> JBoolean::class.java
        JByte.TYPE -> JByte::class.java
        JCharacter.TYPE -> JCharacter::class.java
        JShort.TYPE -> JShort::class.java
        JInteger.TYPE -> JInteger::class.java
        JLong.TYPE -> JLong::class.java
        JFloat.TYPE -> JFloat::class.java
        JDouble.TYPE -> JDouble::class.java
        else -> type
    }

    private fun createEntries(entriesType: Class<*>, values: Any): Any {
        val entriesFactory = Class.forName("kotlin.enums.EnumEntriesKt", true, entriesType.classLoader)
            .methods.first { method ->
                Modifier.isStatic(method.modifiers) && method.name == "enumEntries" &&
                    method.parameterCount == 1 && method.parameterTypes[0].isAssignableFrom(values.javaClass)
            }
        return checkNotNull(entriesFactory.invoke(null, values))
    }
}