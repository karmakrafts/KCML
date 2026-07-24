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

import dev.karmakrafts.kcml.api.frontend.Frontend
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.extensions.NestedClassGenerationContext
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * Supplies declarations to Kotlin's Frontend IR (FIR) through KCML.
 *
 * KCML forwards Kotlin FIR generation queries to registered implementations. Override only the
 * callbacks for declarations the extension owns; the default implementations report no generated
 * declarations or packages.
 */
interface FirExtension : Extension {
    /**
     * Generates a top-level class-like declaration requested by FIR.
     *
     * @param frontend KCML's view of the active FIR session and compiler configuration.
     * @param classId identifier of the requested class or type alias.
     * @return the generated FIR symbol, or `null` when this extension does not provide [classId].
     */
    fun generateTopLevelClassLikeDeclaration( // @formatter:off
        frontend: Frontend,
        classId: ClassId
    ): FirClassLikeSymbol<*>? = null // @formatter:on

    /**
     * Generates a nested class-like declaration requested from a FIR class owner.
     *
     * @param frontend KCML's view of the active FIR session and compiler configuration.
     * @param owner symbol of the class that owns the requested nested declaration.
     * @param name simple name requested by FIR.
     * @param context FIR context for nested-class generation.
     * @return the generated FIR symbol, or `null` when this extension does not provide [name].
     */
    fun generateNestedClassLikeDeclaration( // @formatter:off
        frontend: Frontend,
        owner: FirClassSymbol<*>,
        name: Name,
        context: NestedClassGenerationContext
    ): FirClassLikeSymbol<*>? = null // @formatter:on

    /**
     * Generates functions matching a callable identifier.
     *
     * @param frontend KCML's view of the active FIR session and compiler configuration.
     * @param callableId identifier of the requested function.
     * @param context member-generation context, or `null` for a top-level request.
     * @return symbols for the functions contributed for [callableId].
     */
    fun generateFunctions( // @formatter:off
        frontend: Frontend,
        callableId: CallableId,
        context: MemberGenerationContext?
    ): List<FirNamedFunctionSymbol> = emptyList() // @formatter:on

    /**
     * Generates properties matching a callable identifier.
     *
     * @param frontend KCML's view of the active FIR session and compiler configuration.
     * @param callableId identifier of the requested property.
     * @param context member-generation context, or `null` for a top-level request.
     * @return symbols for the properties contributed for [callableId].
     */
    fun generateProperties( // @formatter:off
        frontend: Frontend,
        callableId: CallableId,
        context: MemberGenerationContext?
    ): List<FirPropertySymbol> = emptyList() // @formatter:on

    /**
     * Generates constructors for the class represented by a FIR member-generation context.
     *
     * @param frontend KCML's view of the active FIR session and compiler configuration.
     * @param context member-generation context for the class receiving constructors.
     * @return symbols for the constructors contributed by this extension.
     */
    fun generateConstructors( // @formatter:off
        frontend: Frontend,
        context: MemberGenerationContext
    ): List<FirConstructorSymbol> = emptyList() // @formatter:on

    /**
     * Reports whether this extension contributes declarations to a package.
     *
     * @param frontend KCML's view of the active FIR session and compiler configuration.
     * @param packageFqName fully qualified name of the package queried by FIR.
     * @return `true` when the package is supplied by this extension.
     */
    fun hasPackage( // @formatter:off
        frontend: Frontend,
        packageFqName: FqName
    ): Boolean = false // @formatter:on

    /**
     * Lists callable names this extension can generate for a FIR class.
     *
     * FIR uses the returned names to decide which member generation callbacks to invoke.
     *
     * @param frontend KCML's view of the active FIR session and compiler configuration.
     * @param classSymbol class for which member names are requested.
     * @param context member-generation context for [classSymbol].
     * @return names of callable members that may be generated.
     */
    fun getCallableNamesForClass( // @formatter:off
        frontend: Frontend,
        classSymbol: FirClassSymbol<*>,
        context: MemberGenerationContext
    ): Set<Name> = emptySet() // @formatter:on

    /**
     * Lists nested classifier names this extension can generate for a FIR class.
     *
     * @param frontend KCML's view of the active FIR session and compiler configuration.
     * @param classSymbol class for which nested classifier names are requested.
     * @param context FIR context for nested-class generation.
     * @return names of nested classes or type aliases that may be generated.
     */
    fun getNestedClassifiersNames( // @formatter:off
        frontend: Frontend,
        classSymbol: FirClassSymbol<*>,
        context: NestedClassGenerationContext
    ): Set<Name> = emptySet() // @formatter:on

    /**
     * Lists identifiers of top-level callables generated by this extension.
     *
     * @param frontend KCML's view of the active FIR session and compiler configuration.
     * @return identifiers FIR can use to request generated top-level functions and properties.
     */
    fun getTopLevelCallableIds(frontend: Frontend): Set<CallableId> = emptySet()

    /**
     * Lists identifiers of top-level class-like declarations generated by this extension.
     *
     * @param frontend KCML's view of the active FIR session and compiler configuration.
     * @return identifiers FIR can use to request generated top-level classifiers.
     */
    fun getTopLevelClassIds(frontend: Frontend): Set<ClassId> = emptySet()
}