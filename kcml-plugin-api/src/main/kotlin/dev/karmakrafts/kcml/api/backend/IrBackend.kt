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

package dev.karmakrafts.kcml.api.backend

import org.jetbrains.kotlin.backend.common.extensions.DeclarationFinder
import org.jetbrains.kotlin.ir.IrBuiltIns
import org.jetbrains.kotlin.ir.declarations.IrFile

/** Provides IR-specific services for a Kotlin backend integration. */
interface IrBackend : Backend {
    /** IR built-ins for the current compilation. */
    val irBuiltIns: IrBuiltIns

    /** Declaration finder for built-ins. */
    val builtInsFinder: DeclarationFinder

    /**
     * Returns the declaration finder for a source file.
     *
     * @param source IR source file.
     * @return the source's declaration finder.
     */
    fun getFinderForSource(source: IrFile): DeclarationFinder
}