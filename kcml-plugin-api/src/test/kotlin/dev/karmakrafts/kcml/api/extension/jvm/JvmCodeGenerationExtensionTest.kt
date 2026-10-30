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

package dev.karmakrafts.kcml.api.extension.jvm

import dev.karmakrafts.kcml.api.backend.jvm.JvmCodeGenerator
import dev.karmakrafts.kcml.api.backend.jvm.LateJvmBackend
import dev.karmakrafts.kcml.api.extension.AbstractExtension
import org.jetbrains.kotlin.backend.jvm.codegen.PromisedValue
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImplWithShape
import org.jetbrains.kotlin.ir.expressions.impl.IrErrorExpressionImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrClassSymbolImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrSimpleFunctionSymbolImpl
import org.jetbrains.kotlin.ir.types.impl.IrSimpleTypeImpl
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class JvmCodeGenerationExtensionTest {
    private val type = IrSimpleTypeImpl(IrClassSymbolImpl(), false, emptyList(), emptyList())
    private val expression = IrErrorExpressionImpl(0, 0, type, "before")
    private val call = IrCallImplWithShape(0, 0, type, IrSimpleFunctionSymbolImpl(), 0, 0, 0, false, false)
    private val backend = Proxy.newProxyInstance(
        javaClass.classLoader, arrayOf(LateJvmBackend::class.java)
    ) { _, method, _ -> error("Unexpected backend access: ${method.name}") } as LateJvmBackend
    private val generator = Proxy.newProxyInstance(
        javaClass.classLoader, arrayOf(JvmCodeGenerator::class.java)
    ) { _, method, _ -> error("Unexpected generator access: ${method.name}") } as JvmCodeGenerator

    @Test
    fun `can transform an element and continue normal generation`() {
        val extension = object : AbstractExtension(), JvmCodeGenerationExtension {
            override fun JvmCodeGenerator.process(element: IrElement): PromisedValue? {
                assertSame(expression, element)
                expression.description = "after"
                return null
            }
        }

        assertTrue(extension.shouldProcess(expression, backend))
        assertNull(with(extension) { generator.process(expression) })
        assertEquals("after", expression.description)
    }

    @Test
    fun `intrinsics reject non calls without consulting the call predicate`() {
        var predicateCalls = 0
        val extension: JvmCodeGenerationExtension = object : AbstractExtension(), JvmIntrinsicsExtension {
            override fun shouldProcess(call: IrFunctionAccessExpression, backend: LateJvmBackend): Boolean {
                predicateCalls++
                assertSame(this@JvmCodeGenerationExtensionTest.call, call)
                assertSame(this@JvmCodeGenerationExtensionTest.backend, backend)
                return false
            }

            override fun JvmCodeGenerator.process(call: IrFunctionAccessExpression): PromisedValue {
                error("Unrecognized calls must not be processed")
            }
        }

        assertFalse(extension.shouldProcess(expression, backend))
        assertEquals(0, predicateCalls)
        assertNull(with(extension) { generator.process(expression) })
        assertFalse(extension.shouldProcess(call, backend))
        assertEquals(1, predicateCalls)
    }

    @Test
    fun `late extensions have a no op initialization default`() {
        val extension = object : AbstractExtension(), LateJvmExtension {}
        extension.init(backend)
    }
}