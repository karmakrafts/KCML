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

package dev.karmakrafts.kcml.hooks.llvm

import dev.karmakrafts.kcml.api.plugin.PluginLoader
import dev.karmakrafts.kcml.hooks.ActualType
import dev.karmakrafts.kcml.util.ReflectionUtils
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toLong
import llvm.LLVMBuilderRef

@OptIn(ExperimentalForeignApi::class)
internal data class FunctionGenerationContextView( // @formatter:off
    val builderGetter: () -> LLVMBuilderRef
) { // @formatter:on
    companion object {
        fun fromImpl( // @formatter:off
            @ActualType("FunctionGenerationContext") impl: Any,
            loader: PluginLoader
        ): Result<FunctionGenerationContextView> = runCatching { // @formatter:on
            loader.logger.info("Creating FunctionGenerationContextView")
            FunctionGenerationContextView(
                builderGetter = {
                    val builder = ReflectionUtils.getSuperProperty<Any, LLVMBuilderRef>( // @formatter:off
                        superClassName = "FunctionGenerationContext",
                        name = "builder",
                        instance = impl
                    ) // @formatter:on
                    loader.logger.info("Got function LLVMBuilderRef at 0x${builder.toLong().toHexString()}")
                    builder
                })
        }
    }
}