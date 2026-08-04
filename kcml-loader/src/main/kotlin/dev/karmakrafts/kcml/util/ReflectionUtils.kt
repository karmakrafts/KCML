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

package dev.karmakrafts.kcml.util

import java.lang.reflect.Modifier

internal object ReflectionUtils {
    inline fun <reified C : Any, reified T> getField(name: String, instance: Any? = null): T {
        val type = instance?.javaClass ?: C::class.java
        val field = type.getDeclaredField(name)
        val isAccessible = field.modifiers and Modifier.PUBLIC == Modifier.PUBLIC
        if (!isAccessible) field.isAccessible = true
        val value = field.get(instance) as T
        if (!isAccessible) field.isAccessible = false
        return value
    }

    private tailrec fun findSuperClass(clazz: Class<*>, name: String): Class<*>? {
        val superClass = clazz.superclass
        return when {
            superClass == null -> null
            name in superClass.name -> superClass
            else -> findSuperClass(superClass, name)
        }
    }

    private fun findSuperInterface(clazz: Class<*>, name: String): Class<*>? {
        return clazz.interfaces.first { iface -> name in iface.name }
    }

    inline fun <reified C, reified T> getSuperField(superClassName: String, name: String, instance: Any? = null): T {
        val type = instance?.javaClass ?: C::class.java
        val superType = findSuperClass(type, superClassName) ?: findSuperInterface(type, superClassName)
        ?: error("Could not find super class '$superClassName' in hierarchy of $type")
        val field = superType.getDeclaredField(name)
        val isAccessible = field.modifiers and Modifier.PUBLIC == Modifier.PUBLIC
        if (!isAccessible) field.isAccessible = true
        val value = field.get(instance) as T
        if (!isAccessible) field.isAccessible = false
        return value
    }

    inline fun <reified C, reified T> getDelegateProperty(name: String, instance: Any? = null): T {
        val type = instance?.javaClass ?: C::class.java
        val method = type.declaredMethods.first { method -> method.name == "get${name.capitalize()}" }
        val isAccessible = method.modifiers and Modifier.PUBLIC == Modifier.PUBLIC
        if (!isAccessible) method.isAccessible = true
        val value = method.invoke(instance) as T
        if (!isAccessible) method.isAccessible = false
        return value
    }

    inline fun <reified C, reified T> getSuperDelegateProperty(
        superClassName: String, name: String, instance: Any? = null
    ): T {
        val type = instance?.javaClass ?: C::class.java
        val superType = findSuperClass(type, superClassName) ?: findSuperInterface(type, superClassName)
        ?: error("Could not find super class '$superClassName' in hierarchy of $type")
        val method = superType.declaredMethods.first { method -> method.name == "get${name.capitalize()}" }
        val isAccessible = method.modifiers and Modifier.PUBLIC == Modifier.PUBLIC
        if (!isAccessible) method.isAccessible = true
        val value = method.invoke(instance) as T
        if (!isAccessible) method.isAccessible = false
        return value
    }
}