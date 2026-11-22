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

package dev.karmakrafts.kcml.api.frontend

import dev.karmakrafts.kcml.api.ExperimentalKcmlApi
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.MessageCollectorAccess
import org.jetbrains.kotlin.config.messageCollector

/**
 * Provides the compiler context for syntax extensions before a FIR session exists.
 *
 * Syntax extensions can inspect compiler configuration and report diagnostics without depending
 * on FIR symbol or type services.
 */
@ExperimentalKcmlApi
public interface SyntaxFrontend : Frontend {
    /** Compiler configuration for the active Kotlin compilation. */
    public val config: CompilerConfiguration

    /** Collects compiler diagnostics emitted by KCML syntax extensions. */
    @OptIn(MessageCollectorAccess::class)
    public val messageCollector: MessageCollector
        get() = config.messageCollector
}