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

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector

/**
 * Reports an informational diagnostic through Kotlin's compiler message collector.
 *
 * @param message diagnostic text for the compiler output.
 */
fun MessageCollector.verbose(message: String) = report(CompilerMessageSeverity.LOGGING, message)

/**
 * Reports an informational diagnostic through Kotlin's compiler message collector.
 *
 * @param message diagnostic text for the compiler output.
 */
fun MessageCollector.info(message: String) = report(CompilerMessageSeverity.INFO, message)

/**
 * Reports a warning diagnostic through Kotlin's compiler message collector.
 *
 * @param message diagnostic text for the compiler output.
 */
fun MessageCollector.warn(message: String) = report(CompilerMessageSeverity.WARNING, message)

/**
 * Reports an error diagnostic through Kotlin's compiler message collector.
 *
 * @param message diagnostic text for the compiler output.
 */
fun MessageCollector.error(message: String) = report(CompilerMessageSeverity.ERROR, message)

/**
 * Reports an exception diagnostic through Kotlin's compiler message collector.
 *
 * @param message context that explains the failure.
 * @param error exception whose stack trace is appended to the diagnostic.
 */
fun MessageCollector.error(message: String, error: Throwable) {
    report(CompilerMessageSeverity.EXCEPTION, "$message: ${error.stackTraceToString()}")
}