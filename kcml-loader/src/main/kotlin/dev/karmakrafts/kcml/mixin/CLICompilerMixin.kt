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
import dev.karmakrafts.kcml.api.mixin.Mixin
import dev.karmakrafts.kcml.api.mixin.ThisAware
import dev.karmakrafts.kcml.hooks.CommonHooks
import dev.karmakrafts.kcml.hooks.KCMLHookApi
import org.jetbrains.kotlin.cli.common.CLICompiler
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.config.Services
import java.net.URL
import kotlin.io.path.Path

@OptIn(KCMLHookApi::class)
@Suppress("UNUSED")
@DirectMixin(CLICompiler::class)
internal class CLICompilerMixin<A : CommonCompilerArguments> : Mixin, ThisAware<CLICompiler<A>> {
    @Inject("execImpl")
    fun execImpl( // @formatter:off
        @Capture messageCollector: MessageCollector,
        @Capture services: Services,
        @Capture arguments: A
    ) { // @formatter:on
        val classLoader = getThis()::class.java.classLoader
        val classLoaderType = classLoader::class.java
        val addURLMethod = classLoaderType.getMethod("addURL", URL::class.java)
        val loaderUrl = Path(constantTable.getString("loader_path")!!).toUri().toURL()
        addURLMethod.isAccessible = true
        addURLMethod.invoke(classLoader, loaderUrl)
        addURLMethod.isAccessible = false
        CommonHooks.onExecImpl(arguments)
    }
}