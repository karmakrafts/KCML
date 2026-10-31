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

package dev.karmakrafts.kcml.api.extension.js

import dev.karmakrafts.kcml.api.backend.js.JsCodeGenerator
import dev.karmakrafts.kcml.api.backend.js.LateJsBackend
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.js.backend.ast.JsNode

/** Intercepts lowered Kotlin IR as it is translated to JavaScript AST nodes. */
public interface JsCodeGenerationExtension : LateJsExtension {
    /**
     * Selects elements to transform or replace using [process].
     *
     * @param element IR element about to be generated.
     * @param backend KCML context exposing the active JavaScript compilation state.
     * @return `true` to invoke [process]; the default selects every element.
     */
    public fun shouldProcess(element: IrElement, backend: LateJsBackend): Boolean = true

    /**
     * Transforms [element] in place or replaces its JavaScript generation.
     *
     * Return `null` to continue dispatch and normal generation after any in-place changes.
     * A non-null node replaces normal generation and stops dispatch. The replacement must
     * match the expected AST kind (expression or statement) and preserve argument evaluation,
     * side effects, and control flow. Generating the intercepted element again recurses.
     *
     * @receiver generator for the active JavaScript translation context.
     * @param element IR element selected by [shouldProcess].
     * @return the replacement AST node, or `null` to continue normal generation.
     */
    public fun JsCodeGenerator.process(element: IrElement): JsNode?
}