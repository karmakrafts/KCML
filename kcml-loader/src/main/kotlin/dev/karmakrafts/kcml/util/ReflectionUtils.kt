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

import org.jetbrains.kotlin.util.capitalizeDecapitalize.capitalizeAsciiOnly
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

    private tailrec fun findSuperClass(clazz: Class<*>, name: String): Class<*> {
        val superClass = clazz.superclass
        return when {
            superClass == null -> error("Could not find super class '$name' in hierarchy of $clazz")
            name in superClass.name -> superClass
            else -> findSuperClass(superClass, name)
        }
    }

    inline fun <reified C, reified T> getSuperField(superClassName: String, name: String, instance: Any? = null): T {
        val type = instance?.javaClass ?: C::class.java
        val superType = findSuperClass(type, superClassName)
        val field = superType.getDeclaredField(name)
        val isAccessible = field.modifiers and Modifier.PUBLIC == Modifier.PUBLIC
        if (!isAccessible) field.isAccessible = true
        val value = field.get(instance) as T
        if (!isAccessible) field.isAccessible = false
        return value
    }

    inline fun <reified C, reified T> getDelegateProperty(name: String, instance: Any? = null): T {
        val type = instance?.javaClass ?: C::class.java
        val method = type.declaredMethods.first { method -> method.name == "get${name.capitalizeAsciiOnly()}" }
        val isAccessible = method.modifiers and Modifier.PUBLIC == Modifier.PUBLIC
        if (!isAccessible) method.isAccessible = true
        val value = method.invoke(instance) as T
        if (!isAccessible) method.isAccessible = false
        return value
    }
}