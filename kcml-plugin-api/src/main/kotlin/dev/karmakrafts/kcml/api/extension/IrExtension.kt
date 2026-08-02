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

package dev.karmakrafts.kcml.api.extension

import dev.karmakrafts.kcml.api.backend.IrBackend
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment

/**
 * Processes Kotlin IR through KCML after frontend analysis.
 *
 * KCML invokes this callback for the module fragment at the applicable backend phase, allowing an
 * extension to inspect or transform declarations before target code generation continues.
 */
interface IrExtension : Extension {
    /**
     * Processes the IR module for the current compilation target.
     *
     * @param module Kotlin IR module fragment available for inspection or transformation.
     * @param backend KCML context exposing compiler services for the active target backend.
     */
    fun process(module: IrModuleFragment, backend: IrBackend)
}