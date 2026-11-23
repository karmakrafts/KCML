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

package dev.karmakrafts.kcml.api.extension.syntax

import dev.karmakrafts.kcml.api.ExperimentalKcmlApi
import org.jetbrains.kotlin.com.intellij.lang.PsiBuilder

/** Host services available while parsing a single syntax construct. */
@ExperimentalKcmlApi
public interface ParsingContext {
    /** Token stream and tree builder positioned at the start of the candidate construct. */
    public val builder: PsiBuilder

    /** Grammar rule for which the host is requesting a construct. */
    public val rule: GrammarRule

    /**
     * Parses an ordinary Kotlin construct at the builder's current token.
     *
     * Bypasses syntax extensions for this entry call to avoid recursively claiming the same
     * construct. Nested grammar rules may dispatch extensions again. A `false` result must
     * leave the builder unchanged; a `true` result must consume tokens and balance its markers.
     *
     * @param rule grammar rule to invoke, defaulting to this context's [ParsingContext.rule].
     * @return whether the Kotlin parser consumed a construct, including error recovery.
     * @throws IllegalArgumentException if the host adapter does not support [rule].
     */
    public fun parseKotlin(rule: GrammarRule = this.rule): Boolean
}