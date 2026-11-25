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

package dev.karmakrafts.kcml.intellij.syntax.runtime

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector

internal class IdeMessageCollector(private val logger: Logger = logger<SyntaxRuntime>()) : MessageCollector {
    @Volatile
    private var errors: Boolean = false

    override fun clear() {
        errors = false
    }

    override fun hasErrors(): Boolean = errors

    override fun report(
        severity: CompilerMessageSeverity, message: String, location: CompilerMessageSourceLocation?
    ) {
        if (severity.isError) errors = true
        val rendered = if (location == null) message
        else "${location.path}:${location.line}:${location.column}: $message"
        when (severity) {
            CompilerMessageSeverity.EXCEPTION, CompilerMessageSeverity.ERROR -> logger.error(rendered)
            CompilerMessageSeverity.STRONG_WARNING,
            CompilerMessageSeverity.WARNING,
            CompilerMessageSeverity.FIXED_WARNING -> logger.warn(rendered)
            CompilerMessageSeverity.INFO, CompilerMessageSeverity.OUTPUT -> logger.info(rendered)
            CompilerMessageSeverity.LOGGING -> logger.debug(rendered)
        }
    }
}