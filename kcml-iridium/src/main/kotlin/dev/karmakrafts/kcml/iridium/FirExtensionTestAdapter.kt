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

internal class FirExtensionTestAdapter( // @formatter:off
    session: FirSession,
    val frontend: TestFrontend,
    val extension: FirExtension
) : FirDeclarationGenerationExtension(session) { // @formatter:on
    override fun generateConstructors(context: MemberGenerationContext): List<FirConstructorSymbol> {
        return extension.generateConstructors(frontend, context)
    }

    override fun generateFunctions(
        callableId: CallableId, context: MemberGenerationContext?
    ): List<FirNamedFunctionSymbol> {
        return extension.generateFunctions(frontend, callableId, context)
    }

    override fun generateNestedClassLikeDeclaration(
        owner: FirClassSymbol<*>, name: Name, context: NestedClassGenerationContext
    ): FirClassLikeSymbol<*>? {
        return extension.generateNestedClassLikeDeclaration(frontend, owner, name, context)
    }

    override fun generateProperties(
        callableId: CallableId, context: MemberGenerationContext?
    ): List<FirPropertySymbol> {
        return extension.generateProperties(frontend, callableId, context)
    }

    @ExperimentalTopLevelDeclarationsGenerationApi
    override fun generateTopLevelClassLikeDeclaration(classId: ClassId): FirClassLikeSymbol<*>? {
        return extension.generateTopLevelClassLikeDeclaration(frontend, classId)
    }

    override fun getCallableNamesForClass(
        classSymbol: FirClassSymbol<*>, context: MemberGenerationContext
    ): Set<Name> {
        return extension.getCallableNamesForClass(frontend, classSymbol, context)
    }

    override fun getNestedClassifiersNames(
        classSymbol: FirClassSymbol<*>, context: NestedClassGenerationContext
    ): Set<Name> {
        return extension.getNestedClassifiersNames(frontend, classSymbol, context)
    }

    @ExperimentalTopLevelDeclarationsGenerationApi
    override fun getTopLevelCallableIds(): Set<CallableId> = extension.getTopLevelCallableIds(frontend)

    @ExperimentalTopLevelDeclarationsGenerationApi
    override fun getTopLevelClassIds(): Set<ClassId> = extension.getTopLevelClassIds(frontend)

    override fun hasPackage(packageFqName: FqName): Boolean {
        return extension.hasPackage(frontend, packageFqName)
    }
}