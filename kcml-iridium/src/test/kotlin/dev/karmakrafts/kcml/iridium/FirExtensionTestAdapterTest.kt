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

import dev.karmakrafts.iridium.pipeline.defaultPipelineSpec
import dev.karmakrafts.iridium.runCompilerTest
import dev.karmakrafts.kcml.api.extension.ExtensionDependency
import dev.karmakrafts.kcml.api.extension.FirExtension
import dev.karmakrafts.kcml.api.frontend.Frontend
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.extensions.ExperimentalTopLevelDeclarationsGenerationApi
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.extensions.NestedClassGenerationContext
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalTopLevelDeclarationsGenerationApi::class)
class FirExtensionTestAdapterTest {
    private class AdapterContext(
        val frontend: TestFrontend,
        val owner: FirRegularClassSymbol,
        val constructorSymbol: FirConstructorSymbol,
        val functionSymbol: FirNamedFunctionSymbol,
        val propertySymbol: FirPropertySymbol,
        val nestedSymbol: FirRegularClassSymbol
    ) {
        val session = frontend.session
        val classId = owner.classId
        val name = Name.identifier("generated")
        val callableId = CallableId(classId, name)
        val memberContext = MemberGenerationContext(owner, null)
        val nestedContext = NestedClassGenerationContext(owner, null)
    }

    private fun testAdapter(block: AdapterContext.() -> Unit) = runCompilerTest {
        lateinit var config: CompilerConfiguration
        pipeline {
            defaultPipelineSpec()
            config { config = this }
        }
        source(
            """
            package test
            class Example {
                val generated: Int = 42
                fun generated(): Int = generated
                class Nested
            }
            """.trimIndent()
        )
        compiler shouldNotReport { error() }
        result firMatches {
            val frontend = TestFrontend(element.moduleData.session, config)
            getChild<FirRegularClass> { it.symbol.classId == ClassId.topLevel(FqName("test.Example")) } matches {
                AdapterContext(
                    frontend,
                    element.symbol,
                    getChild<FirConstructor> { it.symbol.callableId.classId == element.symbol.classId }.symbol,
                    getChild<FirNamedFunction> { it.name.asString() == "generated" }.symbol,
                    getChild<FirProperty> { it.name.asString() == "generated" }.symbol,
                    getChild<FirRegularClass> { it.name.asString() == "Nested" }.symbol
                ).block()
            }
        }
    }

    private fun assertArguments(actual: List<Any?>, vararg expected: Any?) {
        assertEquals(expected.size, actual.size)
        actual.zip(expected).forEach { (value, argument) -> assertSame(argument, value) }
    }

    @Test
    fun `Constructor queries preserve frontend context and result`() = testAdapter {
        val result = listOf(constructorSymbol)
        val calls = ArrayList<List<Any?>>()
        val extension = object : FirExtension {
            override val id: String = "test"
            override val dependencies: List<ExtensionDependency> = emptyList()

            override fun generateConstructors(
                frontend: Frontend,
                context: MemberGenerationContext
            ): List<FirConstructorSymbol> {
                calls += listOf(frontend, context)
                return result
            }
        }
        val adapter = FirExtensionTestAdapter(session, frontend, extension)
        assertSame(frontend, adapter.frontend)
        assertSame(extension, adapter.extension)
        assertSame(result, adapter.generateConstructors(memberContext))
        assertArguments(calls.single(), frontend, memberContext)
    }

    @Test
    fun `Function queries preserve both member and top level contexts`() = testAdapter {
        val result = listOf(functionSymbol)
        val calls = ArrayList<List<Any?>>()
        val extension = object : FirExtension {
            override val id: String = "test"
            override val dependencies: List<ExtensionDependency> = emptyList()

            override fun generateFunctions(
                frontend: Frontend, callableId: CallableId, context: MemberGenerationContext?
            ): List<FirNamedFunctionSymbol> {
                calls += listOf(frontend, callableId, context)
                return result
            }
        }
        val adapter = FirExtensionTestAdapter(session, frontend, extension)
        for (context in listOf(memberContext, null)) {
            assertSame(result, adapter.generateFunctions(callableId, context))
            assertArguments(calls.last(), frontend, callableId, context)
        }
        assertEquals(2, calls.size)
    }

    @Test
    fun `Property queries preserve both member and top level contexts`() = testAdapter {
        val result = listOf(propertySymbol)
        val calls = ArrayList<List<Any?>>()
        val extension = object : FirExtension {
            override val id: String = "test"
            override val dependencies: List<ExtensionDependency> = emptyList()

            override fun generateProperties(
                frontend: Frontend, callableId: CallableId, context: MemberGenerationContext?
            ): List<FirPropertySymbol> {
                calls += listOf(frontend, callableId, context)
                return result
            }
        }
        val adapter = FirExtensionTestAdapter(session, frontend, extension)
        for (context in listOf(memberContext, null)) {
            assertSame(result, adapter.generateProperties(callableId, context))
            assertArguments(calls.last(), frontend, callableId, context)
        }
        assertEquals(2, calls.size)
    }

    @Test
    fun `Nested class queries preserve all arguments and nullable results`() = testAdapter {
        val symbol = nestedSymbol
        var result: FirClassLikeSymbol<*>? = symbol
        val calls = ArrayList<List<Any?>>()
        val extension = object : FirExtension {
            override val id: String = "test"
            override val dependencies: List<ExtensionDependency> = emptyList()

            override fun generateNestedClassLikeDeclaration(
                frontend: Frontend, owner: FirClassSymbol<*>, name: Name, context: NestedClassGenerationContext
            ): FirClassLikeSymbol<*>? {
                calls += listOf(frontend, owner, name, context)
                return result
            }
        }
        val adapter = FirExtensionTestAdapter(session, frontend, extension)
        assertSame(symbol, adapter.generateNestedClassLikeDeclaration(owner, name, nestedContext))
        assertArguments(calls.single(), frontend, owner, name, nestedContext)
        result = null
        assertNull(adapter.generateNestedClassLikeDeclaration(owner, name, nestedContext))
        assertEquals(2, calls.size)
    }

    @Test
    fun `Top level class queries preserve the class id and nullable results`() = testAdapter {
        var result: FirClassLikeSymbol<*>? = owner
        val calls = ArrayList<List<Any?>>()
        val extension = object : FirExtension {
            override val id: String = "test"
            override val dependencies: List<ExtensionDependency> = emptyList()

            override fun generateTopLevelClassLikeDeclaration(
                frontend: Frontend,
                classId: ClassId
            ): FirClassLikeSymbol<*>? {
                calls += listOf(frontend, classId)
                return result
            }
        }
        val adapter = FirExtensionTestAdapter(session, frontend, extension)
        assertSame(owner, adapter.generateTopLevelClassLikeDeclaration(classId))
        assertArguments(calls.single(), frontend, classId)
        result = null
        assertNull(adapter.generateTopLevelClassLikeDeclaration(classId))
        assertEquals(2, calls.size)
    }

    @Test
    fun `Class name queries preserve the owner context and returned sets`() = testAdapter {
        val callableNames = setOf(name)
        val classifierNames = setOf(Name.identifier("Nested"))
        val callableCalls = ArrayList<List<Any?>>()
        val classifierCalls = ArrayList<List<Any?>>()
        val extension = object : FirExtension {
            override val id: String = "test"
            override val dependencies: List<ExtensionDependency> = emptyList()

            override fun getCallableNamesForClass(
                frontend: Frontend, classSymbol: FirClassSymbol<*>, context: MemberGenerationContext
            ): Set<Name> {
                callableCalls += listOf(frontend, classSymbol, context)
                return callableNames
            }

            override fun getNestedClassifiersNames(
                frontend: Frontend, classSymbol: FirClassSymbol<*>, context: NestedClassGenerationContext
            ): Set<Name> {
                classifierCalls += listOf(frontend, classSymbol, context)
                return classifierNames
            }
        }
        val adapter = FirExtensionTestAdapter(session, frontend, extension)
        assertSame(callableNames, adapter.getCallableNamesForClass(owner, memberContext))
        assertArguments(callableCalls.single(), frontend, owner, memberContext)
        assertSame(classifierNames, adapter.getNestedClassifiersNames(owner, nestedContext))
        assertArguments(classifierCalls.single(), frontend, owner, nestedContext)
    }

    @Test
    fun `Top level identifier queries forward the frontend and returned sets`() = testAdapter {
        val callableIds = setOf(callableId)
        val classIds = setOf(classId)
        val callableCalls = ArrayList<List<Any?>>()
        val classCalls = ArrayList<List<Any?>>()
        val extension = object : FirExtension {
            override val id: String = "test"
            override val dependencies: List<ExtensionDependency> = emptyList()

            override fun getTopLevelCallableIds(frontend: Frontend): Set<CallableId> {
                callableCalls += listOf(frontend)
                return callableIds
            }

            override fun getTopLevelClassIds(frontend: Frontend): Set<ClassId> {
                classCalls += listOf(frontend)
                return classIds
            }
        }
        val adapter = FirExtensionTestAdapter(session, frontend, extension)
        assertSame(callableIds, adapter.getTopLevelCallableIds())
        assertArguments(callableCalls.single(), frontend)
        assertSame(classIds, adapter.getTopLevelClassIds())
        assertArguments(classCalls.single(), frontend)
    }

    @Test
    fun `Package queries forward the package and both boolean results`() = testAdapter {
        val packageName = FqName("test.generated")
        val calls = ArrayList<List<Any?>>()
        val extension = object : FirExtension {
            override val id: String = "test"
            override val dependencies: List<ExtensionDependency> = emptyList()

            override fun hasPackage(frontend: Frontend, packageFqName: FqName): Boolean {
                calls += listOf(frontend, packageFqName)
                return packageFqName == packageName
            }
        }
        val adapter = FirExtensionTestAdapter(session, frontend, extension)
        assertTrue(adapter.hasPackage(packageName))
        assertArguments(calls.single(), frontend, packageName)
        assertFalse(adapter.hasPackage(FqName.ROOT))
        assertArguments(calls.last(), frontend, FqName.ROOT)
        assertEquals(2, calls.size)
    }

    @Test
    fun `Default extensions contribute no declarations names or packages`() = testAdapter {
        val extension = object : FirExtension {
            override val id: String = "test"
            override val dependencies: List<ExtensionDependency> = emptyList()
        }
        val adapter = FirExtensionTestAdapter(session, frontend, extension)
        assertTrue(adapter.generateConstructors(memberContext).isEmpty())
        for (context in listOf(memberContext, null)) {
            assertTrue(adapter.generateFunctions(callableId, context).isEmpty())
            assertTrue(adapter.generateProperties(callableId, context).isEmpty())
        }
        assertNull(adapter.generateNestedClassLikeDeclaration(owner, name, nestedContext))
        assertNull(adapter.generateTopLevelClassLikeDeclaration(classId))
        assertTrue(adapter.getCallableNamesForClass(owner, memberContext).isEmpty())
        assertTrue(adapter.getNestedClassifiersNames(owner, nestedContext).isEmpty())
        assertTrue(adapter.getTopLevelCallableIds().isEmpty())
        assertTrue(adapter.getTopLevelClassIds().isEmpty())
        assertFalse(adapter.hasPackage(FqName.ROOT))
    }
}