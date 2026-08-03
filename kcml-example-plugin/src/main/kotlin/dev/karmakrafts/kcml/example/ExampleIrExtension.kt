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

import dev.karmakrafts.kcml.api.backend.IrBackend
import dev.karmakrafts.kcml.api.extension.AbstractExtension
import dev.karmakrafts.kcml.api.extension.ExtensionId
import dev.karmakrafts.kcml.api.extension.IrExtension
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment

@ExtensionId("ir_example") // example:ir_example
internal class ExampleIrExtension : AbstractExtension(), IrExtension {
    override fun process(module: IrModuleFragment, backend: IrBackend) {
        backend.logger.info("Processing current IR module fragment for target ${backend.compileTarget.name}")
    }
}