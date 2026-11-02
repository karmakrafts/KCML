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
import dev.karmakrafts.kcml.api.mixin.DirectMixin
import dev.karmakrafts.kcml.api.mixin.Inject
import dev.karmakrafts.kcml.api.mixin.MixinBridge
import dev.karmakrafts.kcml.api.mixin.ThisAware
import dev.karmakrafts.kcml.hooks.CommonHooks
import dev.karmakrafts.kcml.hooks.KCMLHookApi
import org.jetbrains.kotlin.cli.common.CLICompiler
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.config.Services
import java.util.function.Consumer

@OptIn(KCMLHookApi::class)
@Suppress("UNUSED", "UNCHECKED_CAST")
@DirectMixin(CLICompiler::class)
internal class CLICompilerMixin<A : CommonCompilerArguments> : ThisAware<CLICompiler<A>> {
    @Inject(
        "exec",
        "(Lorg/jetbrains/kotlin/cli/common/messages/MessageCollector;Lorg/jetbrains/kotlin/config/Services;Lorg/jetbrains/kotlin/cli/common/arguments/CommonCompilerArguments;)Lorg/jetbrains/kotlin/cli/common/ExitCode;"
    )
    fun exec( // @formatter:off
        @Capture messageCollector: MessageCollector,
        @Capture services: Services,
        @Capture arguments: A
    ) { // @formatter:on
        val classLoader = getThis()::class.java.classLoader
        val injectLoader = MixinBridge.INSTANCE.getConstant("kcml", "inject_loader") as Consumer<ClassLoader>
        injectLoader.accept(classLoader)
        // ======================== KCML loader class loading boundary ========================
        CommonHooks.onExecImpl(getThis(), arguments)
    }
}