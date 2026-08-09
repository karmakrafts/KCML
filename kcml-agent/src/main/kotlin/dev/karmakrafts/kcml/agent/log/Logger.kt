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

package dev.karmakrafts.kcml.agent.log

internal interface Logger {
    fun debug(message: () -> Any?)
    fun info(message: () -> Any?)
    fun warn(message: () -> Any?)
    fun error(message: () -> Any?)
}

internal inline fun Logger.debug(error: Throwable, crossinline message: () -> Any?) {
    debug { "${message()}: ${error.stackTraceToString()}" }
}

internal inline fun Logger.info(error: Throwable, crossinline message: () -> Any?) {
    info { "${message()}: ${error.stackTraceToString()}" }
}

internal inline fun Logger.warn(error: Throwable, crossinline message: () -> Any?) {
    warn { "${message()}: ${error.stackTraceToString()}" }
}

internal inline fun Logger.error(error: Throwable, crossinline message: () -> Any?) {
    error { "${message()}: ${error.stackTraceToString()}" }
}