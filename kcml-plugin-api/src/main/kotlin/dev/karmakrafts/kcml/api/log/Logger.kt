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

package dev.karmakrafts.kcml.api.log

/**
 * Writes named messages to the Kotlin compiler diagnostic output.
 *
 * KCML supplies loggers whose messages are scoped to the active compiler invocation. Their levels
 * map to the compiler's verbose, informational, warning, and error reporting channels, so plugin
 * authors should use this API instead of writing directly to standard output.
 */
interface Logger {
    /** Name included with every message produced by this logger. */
    val name: String

    /**
     * Reports a verbose diagnostic.
     *
     * @param message diagnostic text.
     */
    fun debug(message: String)

    /**
     * Reports an informational diagnostic.
     *
     * @param message diagnostic text.
     */
    fun info(message: String)

    /**
     * Reports a warning diagnostic.
     *
     * @param message diagnostic text.
     */
    fun warn(message: String)

    /**
     * Reports an error diagnostic.
     *
     * @param message diagnostic text.
     */
    fun error(message: String)

    /**
     * Reports a verbose diagnostic with an error description appended to the message.
     *
     * @param message diagnostic text.
     * @param error error associated with the diagnostic.
     */
    fun debug(message: String, error: Throwable) = debug("$message: $error")

    /**
     * Reports an informational diagnostic with an error description appended to the message.
     *
     * @param message diagnostic text.
     * @param error error associated with the diagnostic.
     */
    fun info(message: String, error: Throwable) = info("$message: $error")

    /**
     * Reports a warning diagnostic with an error description appended to the message.
     *
     * @param message diagnostic text.
     * @param error error associated with the diagnostic.
     */
    fun warn(message: String, error: Throwable) = warn("$message: $error")

    /**
     * Reports an error diagnostic with an error description appended to the message.
     *
     * @param message diagnostic text.
     * @param error error associated with the diagnostic.
     */
    fun error(message: String, error: Throwable) = error("$message: $error")
}