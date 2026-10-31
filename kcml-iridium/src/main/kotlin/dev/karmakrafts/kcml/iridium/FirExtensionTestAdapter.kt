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

package dev.karmakrafts.kcml.iridium

import dev.karmakrafts.kcml.api.extension.FirExtension
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.ExperimentalTopLevelDeclarationsGenerationApi
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
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
 * Adapts a KCML [FirExtension] to Kotlin's FIR declaration-generation API for Iridium tests.
 *
 * Every generation and discovery query is forwarded to [extension] with the supplied [frontend].
 *
 * @param session FIR session to which this declaration-generation extension belongs.
 * @property frontend KCML frontend context passed to each extension callback.
 * @property extension KCML extension that supplies generated declarations and discovery results.
 */
public class FirExtensionTestAdapter( // @formatter:off
    session: FirSession,
    public val frontend: TestFrontend,
    public val extension: FirExtension
) : FirDeclarationGenerationExtension(session) { // @formatter:on
    /**
     * Requests constructors from [extension] using [frontend].
     *
     * @param context member-generation context for the class receiving constructors.
     * @return constructor symbols supplied by the KCML extension.
     */
    override fun generateConstructors(context: MemberGenerationContext): List<FirConstructorSymbol> {
        return extension.generateConstructors(frontend, context)
    }

    /**
     * Requests functions from [extension] using [frontend].
     *
     * @param callableId identifier of the requested function.
     * @param context member-generation context, or `null` for a top-level request.
     * @return function symbols supplied by the KCML extension.
     */
    override fun generateFunctions(
        callableId: CallableId, context: MemberGenerationContext?
    ): List<FirNamedFunctionSymbol> {
        return extension.generateFunctions(frontend, callableId, context)
    }

    /**
     * Requests a nested class-like declaration from [extension] using [frontend].
     *
     * @param owner class that owns the requested nested declaration.
     * @param name simple name of the requested declaration.
     * @param context FIR context for nested-class generation.
     * @return the generated symbol, or `null` if the KCML extension does not provide it.
     */
    override fun generateNestedClassLikeDeclaration(
        owner: FirClassSymbol<*>, name: Name, context: NestedClassGenerationContext
    ): FirClassLikeSymbol<*>? {
        return extension.generateNestedClassLikeDeclaration(frontend, owner, name, context)
    }

    /**
     * Requests properties from [extension] using [frontend].
     *
     * @param callableId identifier of the requested property.
     * @param context member-generation context, or `null` for a top-level request.
     * @return property symbols supplied by the KCML extension.
     */
    override fun generateProperties(
        callableId: CallableId, context: MemberGenerationContext?
    ): List<FirPropertySymbol> {
        return extension.generateProperties(frontend, callableId, context)
    }

    /**
     * Requests a top-level class-like declaration from [extension] using [frontend].
     *
     * @param classId identifier of the requested class or type alias.
     * @return the generated symbol, or `null` if the KCML extension does not provide it.
     */
    @ExperimentalTopLevelDeclarationsGenerationApi
    override fun generateTopLevelClassLikeDeclaration(classId: ClassId): FirClassLikeSymbol<*>? {
        return extension.generateTopLevelClassLikeDeclaration(frontend, classId)
    }

    /**
     * Requests callable member names from [extension] using [frontend].
     *
     * @param classSymbol class for which callable names are requested.
     * @param context member-generation context for [classSymbol].
     * @return names of callable members the KCML extension can generate.
     */
    override fun getCallableNamesForClass(
        classSymbol: FirClassSymbol<*>, context: MemberGenerationContext
    ): Set<Name> {
        return extension.getCallableNamesForClass(frontend, classSymbol, context)
    }

    /**
     * Requests nested classifier names from [extension] using [frontend].
     *
     * @param classSymbol class for which nested classifier names are requested.
     * @param context FIR context for nested-class generation.
     * @return names of nested classifiers the KCML extension can generate.
     */
    override fun getNestedClassifiersNames(
        classSymbol: FirClassSymbol<*>, context: NestedClassGenerationContext
    ): Set<Name> {
        return extension.getNestedClassifiersNames(frontend, classSymbol, context)
    }

    /**
     * Requests top-level callable identifiers from [extension] using [frontend].
     *
     * @return identifiers of top-level functions and properties the KCML extension can generate.
     */
    @ExperimentalTopLevelDeclarationsGenerationApi
    override fun getTopLevelCallableIds(): Set<CallableId> = extension.getTopLevelCallableIds(frontend)

    /**
     * Requests top-level classifier identifiers from [extension] using [frontend].
     *
     * @return identifiers of top-level class-like declarations the KCML extension can generate.
     */
    @ExperimentalTopLevelDeclarationsGenerationApi
    override fun getTopLevelClassIds(): Set<ClassId> = extension.getTopLevelClassIds(frontend)

    /**
     * Asks [extension] whether it contributes declarations to a package using [frontend].
     *
     * @param packageFqName fully qualified name of the package queried by FIR.
     * @return `true` when the KCML extension reports that it supplies the package.
     */
    override fun hasPackage(packageFqName: FqName): Boolean {
        return extension.hasPackage(frontend, packageFqName)
    }
}