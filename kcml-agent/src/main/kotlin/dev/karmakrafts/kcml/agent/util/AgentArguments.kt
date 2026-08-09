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

package dev.karmakrafts.kcml.agent.util

import kotlin.reflect.KClass

@JvmInline
internal value class AgentArguments private constructor(private val values: Map<Argument<*>, Any?>) {
    @ConsistentCopyVisibility
    data class Argument<T : Any> private constructor( // @formatter:off
        val type: KClass<T>,
        val name: String,
        val defaultValue: T?,
        val isRequired: Boolean
    ) { // @formatter:on
        companion object {
            inline fun <reified T : Any> create( // @formatter:off
                name: String,
                defaultValue: T? = null,
                isRequired: Boolean = true
            ): Argument<T> { // @formatter:on
                val type = T::class
                require(type in supportedTypes) { "Type $type is not allowed for agent argument '$name'" }
                return Argument(type, name, defaultValue, isRequired)
            }
        }
    }

    companion object {
        private val supportedTypes: Map<KClass<*>, String.() -> Any?> = mapOf( // @formatter:off
            Byte::class to { toByte() },
            Short::class to { toShort() },
            Int::class to { toInt() },
            Long::class to { toLong() },
            Float::class to { toFloat() },
            Double::class to { toDouble() },
            Boolean::class to { lowercase().toBooleanStrictOrNull() },
            String::class to { this }
        ) // @formatter:on

        fun parse(input: String, vararg args: Argument<*>): AgentArguments {
            val namedArguments = args.associateBy(Argument<*>::name)
            val values = HashMap<Argument<*>, Any?>()
            val buffer = StringBuilder()
            var key: String? = null
            var isString = false
            var isEscaped = false

            fun flushArgument(): Boolean {
                val argument = namedArguments[key]
                key = null
                if (argument == null) {
                    buffer.clear()
                    return false
                }
                val parser = supportedTypes[argument.type]
                if (parser == null) {
                    buffer.clear()
                    return false
                }
                values[argument] = parser(buffer.toString())
                buffer.clear()
                return true
            }

            for (c in input) when (c) {
                '\\' -> when (isEscaped) { // Escaping
                    true -> {
                        buffer.append(c) // When we are in escape mode, and we see a backslash, it is literal
                        isEscaped = false
                    }

                    false -> isEscaped = true // If not, we enter escape mode now
                }

                '"' -> when (isString) { // String literals
                    true if !isEscaped -> { // If we are not escaped and see a quote, finish the string and flush the arg
                        if (!flushArgument()) continue
                        isString = false
                    }

                    true -> {
                        buffer.append(c) // If we are escaped and see a quote, it is literal
                        isEscaped = false
                    }

                    else -> isString = true // If we are outside a string and see a quote, enter string mode
                }

                '=' if !isString -> { // Key-value separator
                    key = buffer.toString()
                    buffer.clear()
                }

                ':' if !isString -> { // Argument separator
                    if (!flushArgument()) continue
                }

                else -> { // Regular characters from keys and values
                    isEscaped = false // For every normal character, we stop escaping right away
                    buffer.append(c)
                }
            }
            if (key != null) flushArgument()
            return AgentArguments(values)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getOrNull(argument: Argument<T>): T? = values[argument] as? T

    operator fun <T : Any> get(argument: Argument<T>): T = requireNotNull(getOrNull(argument)) {
        "Could not get agent argument '${argument.name}'"
    }
}