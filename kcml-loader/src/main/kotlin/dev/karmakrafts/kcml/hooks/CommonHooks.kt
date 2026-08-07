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

package dev.karmakrafts.kcml.hooks

import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import kotlin.concurrent.atomics.AtomicReference

@Suppress("UNUSED")
@KCMLHookApi
object CommonHooks {
    private val _compilerArguments: AtomicReference<CommonCompilerArguments?> = AtomicReference(null)
    val compilerArguments: CommonCompilerArguments
        get() = requireNotNull(_compilerArguments.load()) {
            "Compiler arguments have not been initialized for KCML"
        }

    @JvmStatic
    fun onExecImpl(arguments: CommonCompilerArguments) {
        _compilerArguments.store(arguments)
    }
}