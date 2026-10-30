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

package dev.karmakrafts.kcml.api.extension.wasm

import dev.karmakrafts.kcml.api.backend.wasm.LateWasmBackend
import dev.karmakrafts.kcml.api.backend.wasm.WasmCodeGenerator
import dev.karmakrafts.kcml.api.extension.AbstractExtension
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
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WasmCodeGenerationExtensionTest {
    private val type = IrSimpleTypeImpl(IrClassSymbolImpl(), false, emptyList(), emptyList())
    private val expression = IrErrorExpressionImpl(0, 0, type, "before")
    private val call = IrCallImplWithShape(
        0, 0, type, IrSimpleFunctionSymbolImpl(), 0, 0, 0, false, false
    )
    private val backend = Proxy.newProxyInstance(
        javaClass.classLoader, arrayOf(LateWasmBackend::class.java)
    ) { _, method, _ -> error("Unexpected backend access: ${method.name}") } as LateWasmBackend
    private val generator = Proxy.newProxyInstance(
        javaClass.classLoader, arrayOf(WasmCodeGenerator::class.java)
    ) { _, method, _ -> error("Unexpected generator access: ${method.name}") } as WasmCodeGenerator

    @Test
    fun `can transform an element and continue normal generation`() {
        val extension = object : AbstractExtension(), WasmCodeGenerationExtension {
            override fun WasmCodeGenerator.process(element: IrElement): Boolean {
                assertSame(expression, element)
                expression.description = "after"
                return false
            }
        }

        assertTrue(extension.shouldProcess(expression, backend))
        assertFalse(with(extension) { generator.process(expression) })
        assertEquals("after", expression.description)
    }

    @Test
    fun `can suppress normal generation for a non call expression`() {
        val extension = object : AbstractExtension(), WasmCodeGenerationExtension {
            override fun WasmCodeGenerator.process(element: IrElement): Boolean {
                assertSame(expression, element)
                return true
            }
        }

        assertTrue(with(extension) { generator.process(expression) })
    }

    @Test
    fun `intrinsics select only recognized calls through the general contract`() {
        var predicateCalls = 0
        val extension: WasmCodeGenerationExtension = object : AbstractExtension(), WasmIntrinsicsExtension {
            override fun shouldProcess(call: IrFunctionAccessExpression, backend: LateWasmBackend): Boolean {
                predicateCalls++
                assertSame(this@WasmCodeGenerationExtensionTest.call, call)
                assertSame(this@WasmCodeGenerationExtensionTest.backend, backend)
                return false
            }

            override fun WasmCodeGenerator.process(call: IrFunctionAccessExpression) {
                error("Unrecognized calls must not be processed")
            }
        }

        assertFalse(extension.shouldProcess(expression, backend))
        assertEquals(0, predicateCalls)
        assertFalse(extension.shouldProcess(call, backend))
        assertEquals(1, predicateCalls)
        assertFalse(with(extension) { generator.process(expression) })
    }

    @Test
    fun `intrinsics invoke the existing processor once and report handled`() {
        var processedCalls = 0
        val extension: WasmCodeGenerationExtension = object : AbstractExtension(), WasmIntrinsicsExtension {
            override fun shouldProcess(call: IrFunctionAccessExpression, backend: LateWasmBackend): Boolean = true

            override fun WasmCodeGenerator.process(call: IrFunctionAccessExpression) {
                assertSame(this@WasmCodeGenerationExtensionTest.call, call)
                processedCalls++
            }
        }

        assertTrue(extension.shouldProcess(call, backend))
        assertTrue(with(extension) { generator.process(call) })
        assertEquals(1, processedCalls)
    }
}