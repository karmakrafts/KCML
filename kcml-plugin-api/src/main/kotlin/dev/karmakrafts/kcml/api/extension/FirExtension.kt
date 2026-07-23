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

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.ExperimentalTopLevelDeclarationsGenerationApi
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

interface FirExtension : Extension {
    @ExperimentalTopLevelDeclarationsGenerationApi
    fun generateTopLevelClassLikeDeclaration( // @formatter:off
        session: FirSession,
        classId: ClassId
    ): FirClassLikeSymbol<*>? = null // @formatter:on

    fun generateNestedClassLikeDeclaration( // @formatter:off
        session: FirSession,
        owner: FirClassSymbol<*>,
        name: Name,
        context: NestedClassGenerationContext
    ): FirClassLikeSymbol<*>? = null // @formatter:on

    fun generateFunctions( // @formatter:off
        session: FirSession,
        callableId: CallableId,
        context: MemberGenerationContext?
    ): List<FirNamedFunctionSymbol> = emptyList() // @formatter:on

    fun generateProperties( // @formatter:off
        session: FirSession,
        callableId: CallableId,
        context: MemberGenerationContext?
    ): List<FirPropertySymbol> = emptyList() // @formatter:on

    fun generateConstructors( // @formatter:off
        session: FirSession,
        context: MemberGenerationContext
    ): List<FirConstructorSymbol> = emptyList() // @formatter:on

    fun hasPackage( // @formatter:off
        session: FirSession,
        packageFqName: FqName
    ): Boolean = false // @formatter:on

    fun getCallableNamesForClass( // @formatter:off
        session: FirSession,
        classSymbol: FirClassSymbol<*>,
        context: MemberGenerationContext
    ): Set<Name> = emptySet() // @formatter:on

    fun getNestedClassifiersNames( // @formatter:off
        session: FirSession,
        classSymbol: FirClassSymbol<*>,
        context: NestedClassGenerationContext
    ): Set<Name> = emptySet() // @formatter:on

    @ExperimentalTopLevelDeclarationsGenerationApi
    fun getTopLevelCallableIds(): Set<CallableId> = emptySet()

    @ExperimentalTopLevelDeclarationsGenerationApi
    fun getTopLevelClassIds(): Set<ClassId> = emptySet()
}