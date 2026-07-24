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

package dev.karmakrafts.kcml.extension

import dev.karmakrafts.kcml.api.extension.FirExtension
import dev.karmakrafts.kcml.frontend.FrontendImpl
import org.jetbrains.kotlin.config.CompilerConfiguration
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

internal class FirExtensionAdapter( // @formatter:off
    val config: CompilerConfiguration,
    session: FirSession,
    val extensions: List<FirExtension>
) : FirDeclarationGenerationExtension(session) { // @formatter:on
    private val frontend: FrontendImpl = FrontendImpl(session, config)

    override fun generateConstructors(context: MemberGenerationContext): List<FirConstructorSymbol> {
        return extensions.flatMap { extension -> extension.generateConstructors(frontend, context) }
    }

    override fun generateFunctions( // @formatter:off
        callableId: CallableId,
        context: MemberGenerationContext?
    ): List<FirNamedFunctionSymbol> { // @formatter:on
        return extensions.flatMap { extension -> extension.generateFunctions(frontend, callableId, context) }
    }

    override fun generateNestedClassLikeDeclaration( // @formatter:off
        owner: FirClassSymbol<*>,
        name: Name,
        context: NestedClassGenerationContext
    ): FirClassLikeSymbol<*>? { // @formatter:on
        for (extension in extensions) {
            return extension.generateNestedClassLikeDeclaration(frontend, owner, name, context) ?: continue
        }
        return null
    }

    override fun generateProperties( // @formatter:off
        callableId: CallableId,
        context: MemberGenerationContext?
    ): List<FirPropertySymbol> { // @formatter:on
        return extensions.flatMap { extension -> extension.generateProperties(frontend, callableId, context) }
    }

    @ExperimentalTopLevelDeclarationsGenerationApi
    override fun generateTopLevelClassLikeDeclaration(classId: ClassId): FirClassLikeSymbol<*>? {
        for (extension in extensions) {
            return extension.generateTopLevelClassLikeDeclaration(frontend, classId) ?: continue
        }
        return null
    }

    override fun getCallableNamesForClass( // @formatter:off
        classSymbol: FirClassSymbol<*>,
        context: MemberGenerationContext
    ): Set<Name> { // @formatter:on
        return extensions.flatMap { extension -> extension.getCallableNamesForClass(frontend, classSymbol, context) }
            .toSet()
    }

    override fun getNestedClassifiersNames( // @formatter:off
        classSymbol: FirClassSymbol<*>,
        context: NestedClassGenerationContext
    ): Set<Name> { // @formatter:on
        return extensions.flatMap { extension -> extension.getNestedClassifiersNames(frontend, classSymbol, context) }
            .toSet()
    }

    @ExperimentalTopLevelDeclarationsGenerationApi
    override fun getTopLevelCallableIds(): Set<CallableId> {
        return extensions.flatMap { extension -> extension.getTopLevelCallableIds(frontend) }.toSet()
    }

    @ExperimentalTopLevelDeclarationsGenerationApi
    override fun getTopLevelClassIds(): Set<ClassId> {
        return extensions.flatMap { extension -> extension.getTopLevelClassIds(frontend) }.toSet()
    }

    override fun hasPackage(packageFqName: FqName): Boolean {
        return extensions.any { extension -> extension.hasPackage(frontend, packageFqName) }
    }
}