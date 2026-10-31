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

package dev.karmakrafts.kcml.api.extension.llvm

import dev.karmakrafts.kcml.api.backend.llvm.LateNativeBackend
import dev.karmakrafts.kcml.api.backend.llvm.NativeCodeGenerator
import dev.karmakrafts.kcml.api.extension.AbstractExtension
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.interpretCPointer
import llvm.LLVMValueRef
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.expressions.IrCall
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

@OptIn(ExperimentalForeignApi::class)
class NativeCodeGenerationExtensionTest {
    private val type = IrSimpleTypeImpl(IrClassSymbolImpl(), false, emptyList(), emptyList())
    private val expression = IrErrorExpressionImpl(0, 0, type, "before")
    private val call = IrCallImplWithShape(
        0, 0, type, IrSimpleFunctionSymbolImpl(), 0, 0, 0, hasDispatchReceiver = false, hasExtensionReceiver = false
    )
    private val backend = Proxy.newProxyInstance(
        javaClass.classLoader, arrayOf(LateNativeBackend::class.java)
    ) { _, method, _ -> error("Unexpected backend access: ${method.name}") } as LateNativeBackend

    private fun generator(onEvaluateArgs: (IrElement) -> Unit = {}): NativeCodeGenerator {
        return Proxy.newProxyInstance(
            javaClass.classLoader, arrayOf(NativeCodeGenerator::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getBackend" -> backend
                "evaluateExplicitArgs" -> {
                    onEvaluateArgs(args[0] as IrElement)
                    emptyList<LLVMValueRef>()
                }

                else -> error("Unexpected generator access: ${method.name}")
            }
        } as NativeCodeGenerator
    }

    @Test
    fun `can transform an element and continue normal generation`() {
        val extension = object : AbstractExtension(), NativeCodeGenerationExtension {
            override fun NativeCodeGenerator.process(element: IrElement): LLVMValueRef? {
                assertSame(expression, element)
                expression.description = "after"
                return null
            }
        }

        assertTrue(extension.shouldProcess(expression, backend))
        assertNull(with(extension) { generator().process(expression) })
        assertEquals("after", expression.description)
    }

    @Test
    fun `intrinsics select only recognized calls through the general contract`() {
        var predicateCalls = 0
        val extension: NativeCodeGenerationExtension = object : AbstractExtension(), NativeIntrinsicsExtension {
            override fun shouldProcess(call: IrCall, backend: LateNativeBackend): Boolean {
                predicateCalls++
                assertSame(this@NativeCodeGenerationExtensionTest.call, call)
                assertSame(this@NativeCodeGenerationExtensionTest.backend, backend)
                return false
            }

            override fun NativeCodeGenerator.process(call: IrCall, args: List<LLVMValueRef>): LLVMValueRef {
                error("Unrecognized calls must not be processed")
            }
        }

        assertFalse(extension.shouldProcess(expression, backend))
        assertEquals(0, predicateCalls)
        assertFalse(extension.shouldProcess(call, backend))
        assertEquals(1, predicateCalls)
        assertNull(with(extension) { generator().process(expression) })
    }

    @Test
    fun `intrinsics evaluate arguments once before invoking the existing processor`() {
        val events = ArrayList<String>()
        val result = checkNotNull(interpretCPointer<llvm.LLVMOpaqueValue>(1L))
        val extension: NativeCodeGenerationExtension = object : AbstractExtension(), NativeIntrinsicsExtension {
            override fun shouldProcess(call: IrCall, backend: LateNativeBackend): Boolean = true

            override fun NativeCodeGenerator.process(call: IrCall, args: List<LLVMValueRef>): LLVMValueRef {
                assertSame(this@NativeCodeGenerationExtensionTest.call, call)
                assertTrue(args.isEmpty())
                events += "process"
                return result
            }
        }
        val generator = generator { element ->
            assertSame(call, element)
            events += "arguments"
        }

        assertTrue(extension.shouldProcess(call, backend))
        assertSame(result, with(extension) { generator.process(call) })
        assertEquals(listOf("arguments", "process"), events)
    }
}