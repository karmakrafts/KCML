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

package dev.karmakrafts.kcml.agent.asm

import org.objectweb.asm.tree.AnnotationNode

private inline fun <reified T> Any?.unwrapValue(): T? {
    val outType = T::class.java
    return when { // @formatter:off
        Enum::class.java.isAssignableFrom(outType)
            && this is Array<*>
            && this::class.java.componentType == String::class.java -> {
            @Suppress("UNCHECKED_CAST") // Enum values are encoded as Array<Any>{"<type>", "<constant>"}
            val name = (this as Array<Any>).last() as String
            outType.enumConstants.find { value ->
                (value as Enum<*>).name == name
            }
        }

        else -> this as? T
    }
} // @formatter:on

internal inline fun <reified T> AnnotationNode.getValue(name: String): T? {
    return values?.windowed(2, 2)
        ?.filter { (valueName, _) -> valueName == name }
        ?.map { (_, value) -> value }
        ?.firstOrNull()
        ?.unwrapValue()
}