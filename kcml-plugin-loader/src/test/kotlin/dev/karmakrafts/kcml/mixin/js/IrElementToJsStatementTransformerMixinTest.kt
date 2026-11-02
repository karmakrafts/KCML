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

package dev.karmakrafts.kcml.mixin.js

import dev.karmakrafts.kcml.api.mixin.Capture
import dev.karmakrafts.kcml.api.mixin.Inject
import org.jetbrains.kotlin.ir.backend.js.transformers.irToJs.IrElementToJsStatementTransformer
import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.tree.ClassNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class IrElementToJsStatementTransformerMixinTest {
    private fun readClass(type: Class<*>): ClassNode {
        val node = ClassNode()
        val resource = "/${type.name.replace('.', '/')}.class"
        assertNotNull(type.getResourceAsStream(resource)).use { stream ->
            ClassReader(stream.readBytes()).accept(node, 0)
        }
        return node
    }

    @Test
    fun `named captures resolve to the original target arguments for every visitor`() {
        val mixin = readClass(IrElementToJsStatementTransformerMixin::class.java)
        val target = readClass(IrElementToJsStatementTransformer::class.java)
        val visitors = mixin.methods.filter { method ->
            method.invisibleAnnotations?.any { it.desc == Type.getDescriptor(Inject::class.java) } == true
        }
        assertEquals(18, visitors.size)
        for (visitor in visitors) {
            val targetMethod = target.methods.single {
                it.name == visitor.name && it.access and Opcodes.ACC_BRIDGE == 0
            }
            val parameterAnnotations = assertNotNull(visitor.invisibleParameterAnnotations)
            for (parameter in 0..1) {
                val capture = parameterAnnotations[parameter].single {
                    it.desc == Type.getDescriptor(Capture::class.java)
                }
                val values = capture.values?.chunked(2)?.associate { it[0] as String to it[1] }.orEmpty()
                assertEquals(
                    Capture.AUTOMATIC_INDEX,
                    values["index"] ?: Capture.AUTOMATIC_INDEX,
                    "${visitor.name}: capture must resolve by name"
                )
                val parameterName = visitor.localVariables.single { it.index == parameter + 1 }.name
                val captureName = (values["name"] as? String)?.ifEmpty { parameterName } ?: parameterName
                val local = targetMethod.localVariables.single { it.name == captureName }
                assertEquals(parameter + 1, local.index, "${visitor.name}: $captureName")
            }
        }
    }
}