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

/**
 * Open identifier for a grammar entry point at which syntax can be replaced or extended.
 *
 * The well-known Kotlin rules are conveniences, not an exhaustive set. Host adapters may support
 * additional identifiers; constructing a rule does not require every host to support it.
 *
 * @param id nonblank, namespaced identifier shared by extensions and their host adapter.
 */
@ExperimentalKcmlApi
public data class GrammarRule(public val id: String) {
    init {
        require(id.isNotBlank()) { "Grammar rule ID must not be blank" }
    }

    public companion object {
        /** A declaration in a file, after its package and import directives. */
        public val TOP_LEVEL_DECLARATION: GrammarRule = GrammarRule("kotlin.top_level_declaration")

        /** A declaration in a class or object body. */
        public val MEMBER_DECLARATION: GrammarRule = GrammarRule("kotlin.member_declaration")

        /** A declaration in a local scope. */
        public val LOCAL_DECLARATION: GrammarRule = GrammarRule("kotlin.local_declaration")

        /** A statement in a block, including statements that are not expressions. */
        public val STATEMENT: GrammarRule = GrammarRule("kotlin.statement")

        /** An expression, including a replacement for an existing Kotlin expression form. */
        public val EXPRESSION: GrammarRule = GrammarRule("kotlin.expression")

        /** A type reference. */
        public val TYPE: GrammarRule = GrammarRule("kotlin.type")
    }
}