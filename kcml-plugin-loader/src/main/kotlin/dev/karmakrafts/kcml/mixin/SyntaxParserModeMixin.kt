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

package dev.karmakrafts.kcml.mixin

import dev.karmakrafts.kcml.api.mixin.Capture
import dev.karmakrafts.kcml.api.mixin.IndirectMixin
import dev.karmakrafts.kcml.api.mixin.Inject
import dev.karmakrafts.kcml.api.mixin.ReturnContext
import dev.karmakrafts.kcml.plugin.PluginLoaderImpl
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.ParserMode

@IndirectMixin("org.jetbrains.kotlin.config.CommonConfigurationKeysKt")
internal class SyntaxParserModeMixin {
    @Inject(
        "getParserMode",
        "(Lorg/jetbrains/kotlin/config/CompilerConfiguration;)Lorg/jetbrains/kotlin/config/ParserMode;",
        order = Inject.Order.BEFORE
    )
    fun getParserMode(
        context: ReturnContext<ParserMode>, @Capture(name = $$"$this$parserMode") config: CompilerConfiguration
    ) {
        if (PluginLoaderImpl.prepareSyntaxParsing(config)) context.returnFromTarget(ParserMode.Psi)
    }
}