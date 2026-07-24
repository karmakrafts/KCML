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

package dev.karmakrafts.kcml.example

import dev.karmakrafts.kcml.api.extension.AbstractExtension
import dev.karmakrafts.kcml.api.extension.ExtensionId
import dev.karmakrafts.kcml.api.extension.FirExtension
import dev.karmakrafts.kcml.api.frontend.Frontend
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.name.CallableId

@ExtensionId("fir_example") // example:fir_example
internal class ExampleFirExtension : AbstractExtension(), FirExtension {
    override fun generateFunctions( // @formatter:off
        frontend: Frontend,
        callableId: CallableId,
        context: MemberGenerationContext?
    ): List<FirNamedFunctionSymbol> { // @formatter:on
        frontend.logger.info("Generating top level functions in FIR")
        return emptyList()
    }

    override fun getTopLevelCallableIds(frontend: Frontend): Set<CallableId> {
        frontend.logger.info("Requesting generated top level callable IDs for FIR")
        return setOf(ExampleNames.exampleFunction)
    }
}