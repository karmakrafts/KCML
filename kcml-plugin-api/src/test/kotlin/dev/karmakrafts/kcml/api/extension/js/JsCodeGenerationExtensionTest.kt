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

package dev.karmakrafts.kcml.api.extension.js

import dev.karmakrafts.kcml.api.backend.BackendType
import dev.karmakrafts.kcml.api.backend.js.JsCodeGenerator
import dev.karmakrafts.kcml.api.backend.js.LateJsBackend
import dev.karmakrafts.kcml.api.extension.AbstractExtension
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.backend.js.utils.JsGenerationContext
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImplWithShape
import org.jetbrains.kotlin.ir.expressions.impl.IrErrorExpressionImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrClassSymbolImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrSimpleFunctionSymbolImpl
import org.jetbrains.kotlin.ir.types.impl.IrSimpleTypeImpl
import org.jetbrains.kotlin.js.backend.ast.JsExpression
import org.jetbrains.kotlin.js.backend.ast.JsIntLiteral
import org.jetbrains.kotlin.js.backend.ast.JsNode
import org.jetbrains.kotlin.js.backend.ast.JsStatement
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class JsCodeGenerationExtensionTest {
    private val type = IrSimpleTypeImpl(IrClassSymbolImpl(), false, emptyList(), emptyList())
    private val expression = IrErrorExpressionImpl(0, 0, type, "before")
    private val call = IrCallImplWithShape(
        0, 0, type, IrSimpleFunctionSymbolImpl(), 0, 0, 0, hasDispatchReceiver = false, hasExtensionReceiver = false
    )
    private val backend = Proxy.newProxyInstance(
        javaClass.classLoader, arrayOf(LateJsBackend::class.java)
    ) { instance, method, args ->
        if (method.isDefault) InvocationHandler.invokeDefault(instance, method, *(args ?: emptyArray()))
        else error("Unexpected backend access: ${method.name}")
    } as LateJsBackend
    private val generator = object : JsCodeGenerator {
        override val backend: LateJsBackend
            get() = this@JsCodeGenerationExtensionTest.backend
        override val context: JsGenerationContext
            get() = error("Unexpected generation context access")

        override fun generateExpression(expression: IrExpression): JsExpression =
            error("Unexpected expression generation")

        override fun generateStatement(statement: IrStatement): JsStatement = error("Unexpected statement generation")
    }

    @Test
    fun `backend identifies the late JavaScript phase`() {
        assertSame(BackendType.LateJs, backend.type)
    }

    @Test
    fun `late extensions have a no op initialization default`() {
        val extension = object : AbstractExtension(), LateJsExtension {}
        extension.init(backend)
    }

    @Test
    fun `can transform an element and continue normal generation`() {
        val extension = object : AbstractExtension(), JsCodeGenerationExtension {
            override fun JsCodeGenerator.process(element: IrElement): JsNode? {
                assertSame(generator, this)
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
    fun `can replace expression or statement generation with an AST node`() {
        for (replacement in listOf<JsNode>(JsIntLiteral(42), JsIntLiteral(42).makeStmt())) {
            val extension = object : AbstractExtension(), JsCodeGenerationExtension {
                override fun JsCodeGenerator.process(element: IrElement): JsNode {
                    assertSame(expression, element)
                    return replacement
                }
            }

            assertSame(replacement, with(extension) { generator.process(expression) })
        }
    }

    @Test
    fun `intrinsics reject non calls without consulting the call predicate`() {
        var predicateCalls = 0
        val extension: JsCodeGenerationExtension = object : AbstractExtension(), JsIntrinsicsExtension {
            override fun shouldProcess(call: IrFunctionAccessExpression, backend: LateJsBackend): Boolean {
                predicateCalls++
                assertSame(this@JsCodeGenerationExtensionTest.call, call)
                assertSame(this@JsCodeGenerationExtensionTest.backend, backend)
                return false
            }

            override fun JsCodeGenerator.process(call: IrFunctionAccessExpression): JsExpression {
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
    fun `intrinsics return the replacement expression through the general contract`() {
        var processedCalls = 0
        val replacement = JsIntLiteral(42)
        val extension: JsCodeGenerationExtension = object : AbstractExtension(), JsIntrinsicsExtension {
            override fun shouldProcess(call: IrFunctionAccessExpression, backend: LateJsBackend): Boolean = true

            override fun JsCodeGenerator.process(call: IrFunctionAccessExpression): JsExpression {
                assertSame(generator, this)
                assertSame(this@JsCodeGenerationExtensionTest.call, call)
                processedCalls++
                return replacement
            }
        }

        assertTrue(extension.shouldProcess(call, backend))
        assertSame(replacement, with(extension) { generator.process(call) })
        assertEquals(1, processedCalls)
    }
}