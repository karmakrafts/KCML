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

import dev.karmakrafts.kcml.api.ExperimentalKcmlApi
import dev.karmakrafts.kcml.api.frontend.SyntaxFrontend
import org.jetbrains.kotlin.com.intellij.lang.PsiBuilder
import org.jetbrains.kotlin.com.intellij.lexer.Lexer
import org.jetbrains.kotlin.com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.psi.KtFile

/**
 * Modifies or extends Kotlin syntax before frontend analysis.
 *
 * The contract uses lexing and PSI parsing rather than a FIR session so compiler and IDE adapters
 * can share syntax rules. It covers tokenization, parsing, IDE highlighting of original syntax,
 * and lowering to ordinary Kotlin PSI.
 * The compiler loader installs a PSI adapter when syntax extensions are present; an IDE needs its
 * own adapter.
 * Callbacks receive a [SyntaxFrontend] pre-FIR context with compiler configuration and diagnostics,
 * without depending on a FIR session.
 *
 * Compiler-embeddable IntelliJ types are relocated. An IDE adapter must bridge that boundary rather
 * than assume these types are binary-compatible with the IDE's IntelliJ classes.
 *
 * Callbacks may be invoked repeatedly and concurrently for different files. Implementations must
 * not retain builders or mutable per-file state on the extension instance.
 */
@ExperimentalKcmlApi
public interface SyntaxExtension : Extension {
    /**
     * Supplies the lexer used to tokenize source for parsing.
     *
     * Wrap [delegate] to introduce tokens or reinterpret existing ones, or return it unchanged when
     * only parsing is customized. The returned lexer must preserve source offsets and support the
     * restart and state contract of [Lexer], including incremental lexing in an IDE.
     *
     * @param delegate lexer supplied by the host or the preceding syntax extension.
     * @param frontend pre-FIR syntax context belonging to this extension's owning plugin.
     * @return a fresh lexer for this request; it must not be shared between files.
     */
    public fun createLexer(delegate: Lexer, frontend: SyntaxFrontend): Lexer = delegate

    /**
     * Attempts to parse syntax at the current token, before Kotlin's built-in grammar is tried.
     *
     * This ordering permits both replacing existing Kotlin constructs and adding new ones. Returning
     * `false` declines the construct: the builder, markers, and diagnostics must be unchanged so the
     * host can try the next extension and finally Kotlin's parser. Use a rollback marker for
     * speculative parsing. Returning `true` claims the construct and suppresses that fallback.
     *
     * A successful parse must consume at least one token and close all markers it creates. Once a
     * construct is recognized, malformed or incomplete input should be claimed with builder error
     * nodes and bounded recovery rather than silently falling back to Kotlin's grammar. At end of
     * input, return `false` without changing the builder.
     *
     * @param context builder, grammar rule, and access to the host's Kotlin parser.
     * @param frontend pre-FIR syntax context belonging to this extension's owning plugin.
     * @return `true` if this extension parsed the construct, including a recovered erroneous one.
     */
    public fun parse(context: ParsingContext, frontend: SyntaxFrontend): Boolean = false

    /**
     * Supplies editor highlighting overrides for this extension's original, non-desugared syntax.
     *
     * [file] has been parsed with all active syntax extensions, but has not been [lower]ed. Ranges
     * are absolute offsets in that file's source text, not in the lowered Kotlin. Inspect PSI or
     * tokens rather than matching raw text so comments, strings and escaped identifiers retain
     * their normal highlighting. The file must not be modified.
     *
     * Only IDE hosts invoke this callback. Overrides use color-scheme keys rather than IDE-only
     * editor classes, keeping extensions usable with the compiler-embeddable API. They replace
     * host semantic coloring in their ranges, but do not suppress diagnostics or inspections.
     * Later extensions take precedence for overlapping ranges. Return an empty list to retain
     * the host's highlighting everywhere.
     *
     * @param file parsed source PSI, before any lowering callbacks run.
     * @param frontend pre-FIR syntax context belonging to this extension's owning plugin.
     * @return source ranges and the color-scheme attributes to apply to them.
     */
    public fun highlight(file: KtFile, frontend: SyntaxFrontend): List<Highlighting> = emptyList()

    /**
     * An editor coloring override in original source coordinates.
     *
     * @param range nonempty range within the source file; IDE hosts ignore out-of-bounds ranges.
     * @param textAttributesKey external name of an IDE color-scheme key, such as `KOTLIN_KEYWORD`,
     * `KOTLIN_NUMBER` or `DEFAULT_KEYWORD`. Custom keys must be registered by an IDE integration.
     */
    public data class Highlighting(public val range: TextRange, public val textAttributesKey: String) {
        init {
            require(!range.isEmpty) { "Highlighting range must not be empty" }
            require(textAttributesKey.isNotBlank()) { "Text attributes key must not be blank" }
        }
    }

    /**
     * Lowers modified or extended syntax to PSI understood by Kotlin's frontend.
     *
     * Return [file] unchanged when parsing already produces ordinary Kotlin nodes. Otherwise return
     * a separate Kotlin PSI file, leaving the original source PSI intact for IDE features. The result
     * must contain no syntax owned by this extension, while preserving other extensions' syntax for
     * their lowering callbacks. After all callbacks, only ordinary Kotlin syntax may remain. The
     * compiler adapter retains source provenance through [KtFile.originalFile], but diagnostics
     * use lowered offsets; preserve offsets when source-aligned diagnostics are required. This
     * callback must not perform disk writes or depend on an IDE write action.
     *
     * @param file parsed file, or the result of the preceding syntax extension's lowering.
     * @param frontend pre-FIR syntax context belonging to this extension's owning plugin.
     * @return the file to pass to the next lowering callback and eventually frontend analysis.
     */
    public fun lower(file: KtFile, frontend: SyntaxFrontend): KtFile = file

    /** Host services available while parsing a single syntax construct. */
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

    /**
     * Open identifier for a grammar entry point at which syntax can be replaced or extended.
     *
     * The well-known Kotlin rules are conveniences, not an exhaustive set. Host adapters may support
     * additional identifiers; constructing a rule does not require every host to support it.
     *
     * @param id nonblank, namespaced identifier shared by extensions and their host adapter.
     */
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
}