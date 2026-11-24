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
import dev.karmakrafts.kcml.api.extension.Extension
import dev.karmakrafts.kcml.api.frontend.SyntaxFrontend
import dev.karmakrafts.kcml.api.target.OnlyInIDE
import org.jetbrains.kotlin.com.intellij.lexer.Lexer
import org.jetbrains.kotlin.psi.KtFile

/**
 * Modifies or extends Kotlin syntax before frontend analysis.
 *
 * The contract uses lexing and PSI parsing rather than a FIR session so compiler and IDE adapters
 * can share syntax rules. It covers tokenization, parsing, IDE highlighting of original syntax,
 * optional IDE completion, custom inspections and inspection rules, and lowering to ordinary Kotlin PSI.
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
    @OnlyInIDE
    public fun highlight(file: KtFile, frontend: SyntaxFrontend): List<Highlighting> = emptyList()

    /**
     * Optionally overrides editor completion for this extension's original, non-desugared syntax.
     *
     * [file] has been parsed with all active syntax extensions, but has not been [lower]ed. [offset]
     * is an absolute UTF-16 offset in that original source text, not in the lowered Kotlin. The file must
     * not be modified.
     *
     * IDE hosts automatically invoke this callback when editor completion is requested; compiler
     * hosts do not invoke it. Later extensions are consulted first, and the first non-null result
     * replaces normal Kotlin completion. Return `null` to decline and let the next extension handle
     * the request, or use normal Kotlin completion on desugared source if all extensions decline.
     * An empty list claims the request and suppresses completion entirely.
     *
     * For example, return `listOf(Completion("magic", "magic", typeText = "Int"))` to offer a custom
     * candidate that inserts `magic` and displays `Int` as its type, or use [Completion.forElement]
     * to infer a candidate's name from PSI. Any [Completion.element] must belong to [file]'s original
     * parsed source; synthetic candidates may omit it.
     *
     * @param file parsed source PSI, before any lowering callbacks run.
     * @param offset completion position in the original source text, in UTF-16 code units.
     * @param smart whether the host requested smart rather than basic completion.
     * @param frontend pre-FIR syntax context belonging to this extension's owning plugin.
     * @return replacement completion candidates, or `null` to retain the host's fallback.
     */
    @OnlyInIDE
    public fun complete(file: KtFile, offset: Int, smart: Boolean, frontend: SyntaxFrontend): List<Completion>? = null

    /**
     * Supplies custom editor inspection findings for this extension's original syntax.
     *
     * [file] has been parsed with all active syntax extensions, but has not been [lower]ed. Findings
     * anchor PSI elements belonging to that original parsed source, optionally restricted by
     * [Inspection.rangeInElement]. Their absolute UTF-16 [Inspection.range] is captured at construction,
     * before lowering can change PSI or offsets. The file must not be modified by this callback.
     *
     * IDE hosts automatically invoke this callback during editor analysis; compiler hosts do not
     * invoke it. Every extension's findings are aggregated in registration order, preserving each
     * extension's result order and all duplicates. [inspectionRules] affect only normal Kotlin
     * inspections and never suppress these custom findings. Return an empty list for no findings.
     *
     * For example, return `listOf(Inspection(node.nameIdentifier!!, "Prefer foo", InspectionSeverity.WARNING))`
     * to show a custom warning on a declaration's name in the original source.
     *
     * @param file parsed source PSI, before any lowering callbacks run.
     * @param frontend pre-FIR syntax context belonging to this extension's owning plugin.
     * @return custom inspection findings in original source coordinates.
     */
    @OnlyInIDE
    public fun inspect(file: KtFile, frontend: SyntaxFrontend): List<Inspection> = emptyList()

    /**
     * Supplies editor inspection rules for this extension's original, non-desugared syntax.
     *
     * [file] has been parsed with all active syntax extensions, but has not been [lower]ed. Rules
     * anchor PSI elements belonging to that original parsed source, optionally restricted by
     * [InspectionRule.rangeInElement]. Their absolute UTF-16 [InspectionRule.range] is captured at
     * construction, before lowering can change PSI or offsets. The file must not be modified by this callback.
     *
     * Only IDE hosts invoke this callback. [InspectionMode.DESUGARED] forwards all mapped diagnostics
     * from lowered subnodes to the whole [InspectionRule.range], allowing many reports on one source
     * node, and suppresses raw Kotlin inspection reports overlapping that source span.
     * [InspectionMode.SUPPRESS] suppresses both raw and lowered normal inspection reports overlapping
     * the range. Neither mode suppresses diagnostics outside its source span or custom findings
     * returned by [inspect].
     *
     * Later extensions take precedence for overlapping ranges. Within one extension, the first
     * overlapping rule wins; IDE hosts select the first overlapping rule from the adapter's list,
     * collected in reverse extension order while retaining each extension's rule order. Return an
     * empty list to retain normal desugared inspection forwarding when lowering actually changes
     * the file, or ordinary Kotlin inspections when it does not.
     *
     * For example, return `listOf(InspectionRule(node))` to attach every inspection from the
     * lowered form of `node` to that single source node, or
     * `listOf(InspectionRule(node, InspectionMode.SUPPRESS))` to omit its normal inspections.
     * Compiler diagnostics and unrelated inspection providers are unaffected. Hosts forward local
     * Kotlin inspection findings, but not quick fixes that would edit only temporary lowered PSI.
     *
     * @param file parsed source PSI, before any lowering callbacks run.
     * @param frontend pre-FIR syntax context belonging to this extension's owning plugin.
     * @return source ranges and their inspection handling modes.
     */
    @OnlyInIDE
    public fun inspectionRules(file: KtFile, frontend: SyntaxFrontend): List<InspectionRule> = emptyList()

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
}