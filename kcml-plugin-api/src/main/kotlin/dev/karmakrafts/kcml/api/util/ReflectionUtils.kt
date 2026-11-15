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

package dev.karmakrafts.kcml.api.util

import dev.karmakrafts.kcml.api.DelicateKcmlApi
import java.lang.reflect.Constructor
import java.lang.reflect.Member
import java.lang.reflect.Modifier
import java.util.Locale.getDefault

// TODO: add documentation
public inline val Member.isPubliclyAccessible: Boolean
    get() = modifiers and Modifier.PUBLIC == Modifier.PUBLIC

/**
 * Provides helpers for reading fields and properties through Java reflection.
 *
 * Lookups are limited to members declared by the selected class or supertype; access to non-public members
 * depends on JVM reflection access restrictions.
 */
@DelicateKcmlApi
public object ReflectionUtils {
    @PublishedApi
    @Suppress("UNCHECKED_CAST")
    internal inline fun <reified T> findConstructor(vararg args: Any?): Constructor<T>? {
        return T::class.java.declaredConstructors.find { constructor ->
            val params = constructor.parameters
            if (params.size != args.size) return@find false
            var matchedCount = 0
            for (paramIndex in params.indices) {
                val argument = args[paramIndex]
                if (argument == null) {
                    matchedCount++
                    continue // Null-args are always a wildcard match
                }
                val paramType = params[paramIndex].type
                if (!paramType.isAssignableFrom(argument::class.java)) continue
                matchedCount++
            }
            matchedCount == params.size
        } as? Constructor<T>
    }

    // TODO: add documentation
    public inline fun <reified T> new(vararg args: Any?): T {
        val constructor = requireNotNull(findConstructor<T>(*args)) {
            "Could not find constructor matching arguments ${args.joinToString()}"
        }
        val isAccessible = constructor.isPubliclyAccessible
        return try {
            if (!isAccessible) constructor.isAccessible = true
            constructor.newInstance(*args)
        }
        finally {
            constructor.isAccessible = isAccessible
        }
    }

    /**
     * Reads the declared field named [name] from [instance], or from [C] when [instance] is `null`.
     *
     * When an instance is supplied, its runtime class is used instead of [C]. Non-public fields are made
     * accessible for the duration of the read when the JVM permits it. The field value is cast to [T].
     *
     * @param C Class used for lookup when [instance] is `null`.
     * @param T Expected type of the field value.
     * @param name Exact name of the declared field to read.
     * @param instance Object whose field is read, or `null` to use [C] as the lookup class.
     * @return The field value cast to [T].
     * @throws NoSuchFieldException If the selected class does not declare a field named [name].
     * @throws IllegalAccessException If the field cannot be accessed.
     * @throws ClassCastException If the field value cannot be cast to [T].
     */
    public inline fun <reified C : Any, reified T> getField(name: String, instance: Any? = null): T {
        val type = instance?.javaClass ?: C::class.java
        val field = type.getDeclaredField(name)
        val isAccessible = field.isPubliclyAccessible
        return try {
            if (!isAccessible) field.isAccessible = true
            field.get(instance) as T
        }
        finally {
            field.isAccessible = isAccessible
        }
    }

    @PublishedApi
    internal tailrec fun findSuperClass(clazz: Class<*>, name: String): Class<*>? {
        val superClass = clazz.superclass
        return when {
            superClass == null -> null
            name in superClass.name -> superClass
            else -> findSuperClass(superClass, name)
        }
    }

    @PublishedApi
    internal fun findSuperInterface(clazz: Class<*>, name: String): Class<*>? {
        return clazz.interfaces.first { iface -> name in iface.name }
    }

    /**
     * Reads a declared field from the first matching superclass or direct interface of the selected class.
     *
     * Superclasses are searched recursively for a class name containing [superClassName]. If none matches,
     * the selected class's direct interfaces are checked for a name containing [superClassName]. The field
     * named [name] must be declared by the resulting type. When [instance] is supplied, its runtime class is
     * used for the search; otherwise [C] is used.
     *
     * @param C Class used as the search root when [instance] is `null`.
     * @param T Expected type of the field value.
     * @param superClassName Substring used to select a superclass or direct interface.
     * @param name Exact name of the field declared by the selected supertype.
     * @param instance Object whose field is read, or `null` to use [C] as the lookup class.
     * @return The field value cast to [T].
     * @throws NoSuchElementException If no matching superclass or direct interface is found.
     * @throws NoSuchFieldException If the selected supertype does not declare a field named [name].
     * @throws IllegalAccessException If the field cannot be accessed.
     * @throws ClassCastException If the field value cannot be cast to [T].
     */
    public inline fun <reified C : Any, reified T> getSuperField(
        superClassName: String, name: String, instance: Any? = null
    ): T {
        val type = instance?.javaClass ?: C::class.java
        val superType = findSuperClass(type, superClassName) ?: findSuperInterface(type, superClassName)
        ?: error("Could not find super class '$superClassName' in hierarchy of $type")
        val field = superType.getDeclaredField(name)
        val isAccessible = field.isPubliclyAccessible
        return try {
            if (!isAccessible) field.isAccessible = true
            field.get(instance) as T
        }
        finally {
            field.isAccessible = isAccessible
        }
    }

    /**
     * Invokes the first declared method matching the Java-style getter name for [name] with no arguments, on
     * [instance] or on [C] when [instance] is `null`.
     *
     * The getter name is formed by prefixing `get` and capitalizing the first character of [name] using the
     * default locale. When an instance is supplied, its runtime class is used instead of [C]. The getter's
     * result is cast to [T].
     *
     * @param C Class used for lookup when [instance] is `null`.
     * @param T Expected type of the getter result.
     * @param name Property name whose getter should be invoked.
     * @param instance Object on which the getter is invoked, or `null` to use [C] as the lookup class.
     * @return The getter result cast to [T].
     * @throws NoSuchElementException If the selected class has no declared method with the computed getter name.
     * @throws IllegalArgumentException If the selected method cannot be invoked with no arguments.
     * @throws IllegalAccessException If the getter cannot be accessed.
     * @throws ClassCastException If the getter result cannot be cast to [T].
     */
    public inline fun <reified C : Any, reified T> getProperty(name: String, instance: Any? = null): T {
        val type = instance?.javaClass ?: C::class.java
        val method = type.declaredMethods.first { method ->
            method.name == "get${
                name.replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(
                        getDefault()
                    )
                    else it.toString()
                }
            }"
        }
        val isAccessible = method.isPubliclyAccessible
        return try {
            if (!isAccessible) method.isAccessible = true
            method.invoke(instance) as T
        }
        finally {
            method.isAccessible = isAccessible
        }
    }

    /**
     * Invokes the first declared method matching the Java-style getter name for [name] with no arguments, on
     * the first matching superclass or direct interface.
     *
     * Superclasses are searched recursively for a class name containing [superClassName]. If none matches,
     * the selected class's direct interfaces are checked for a name containing [superClassName]. The getter
     * must be declared by the resulting type. When [instance] is supplied, its runtime class is used for the
     * search; otherwise [C] is used. The getter name is formed by prefixing `get` and capitalizing the first
     * character of [name] using the default locale.
     *
     * @param C Class used as the search root when [instance] is `null`.
     * @param T Expected type of the getter result.
     * @param superClassName Substring used to select a superclass or direct interface.
     * @param name Property name whose getter should be invoked.
     * @param instance Object on which the getter is invoked, or `null` to use [C] as the lookup class.
     * @return The getter result cast to [T].
     * @throws NoSuchElementException If no matching superclass or direct interface is found, or the selected
     * supertype has no declared method with the computed getter name.
     * @throws IllegalArgumentException If the selected method cannot be invoked with no arguments.
     * @throws IllegalAccessException If the getter cannot be accessed.
     * @throws ClassCastException If the getter result cannot be cast to [T].
     */
    public inline fun <reified C : Any, reified T> getSuperProperty(
        superClassName: String, name: String, instance: Any? = null
    ): T {
        val type = instance?.javaClass ?: C::class.java
        val superType = findSuperClass(type, superClassName) ?: findSuperInterface(type, superClassName)
        ?: error("Could not find super class '$superClassName' in hierarchy of $type")
        val method = superType.declaredMethods.first { method ->
            method.name == "get${
                name.replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(
                        getDefault()
                    )
                    else it.toString()
                }
            }"
        }
        val isAccessible = method.isPubliclyAccessible
        return try {
            if (!isAccessible) method.isAccessible = true
            method.invoke(instance) as T
        }
        finally {
            method.isAccessible = isAccessible
        }
    }
}