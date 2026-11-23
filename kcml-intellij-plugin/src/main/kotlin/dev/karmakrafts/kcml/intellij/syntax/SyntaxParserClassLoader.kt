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

package dev.karmakrafts.kcml.intellij.syntax

import com.intellij.psi.tree.TokenSet
import dev.karmakrafts.kcml.intellij.util.TypeNames
import dev.karmakrafts.kcml.internal.org.objectweb.asm.ClassReader
import dev.karmakrafts.kcml.internal.org.objectweb.asm.ClassVisitor
import dev.karmakrafts.kcml.internal.org.objectweb.asm.ClassWriter
import dev.karmakrafts.kcml.internal.org.objectweb.asm.Label
import dev.karmakrafts.kcml.internal.org.objectweb.asm.MethodVisitor
import dev.karmakrafts.kcml.internal.org.objectweb.asm.Opcodes
import dev.karmakrafts.kcml.internal.org.objectweb.asm.Type

// Only private parser copies are instrumented; the IDE's Kotlin classes are never modified.
internal class SyntaxParserClassLoader(parent: ClassLoader) : ClassLoader(parent) {
    companion object {
        private val parsingName: String = TypeNames.KOTLIN_PARSING.replace('.', '/')
        private val grammarRuleName: String = TypeNames.SYNTAX_GRAMMAR_RULE.replace('.', '/')
        private val hooksName: String = TypeNames.SYNTAX_HOOKS.replace('.', '/')

        private val stringToVoid: String = Type.getMethodDescriptor( // @formatter:off
            Type.VOID_TYPE,
            Type.getType(String::class.java)
        ) // @formatter:on

        private val parseTypeRef: String = Type.getMethodDescriptor( // @formatter:off
            Type.VOID_TYPE,
            Type.getType(TokenSet::class.java),
            Type.BOOLEAN_TYPE,
            Type.BOOLEAN_TYPE
        ) // @formatter:on

        private val parseHook: String = Type.getMethodDescriptor( // @formatter:off
            Type.BOOLEAN_TYPE,
            Type.getType(Any::class.java),
            Type.getObjectType(grammarRuleName),
            Type.BOOLEAN_TYPE,
            Type.BOOLEAN_TYPE,
            Type.BOOLEAN_TYPE
        ) // @formatter:on
    }

    override fun loadClass(name: String, resolve: Boolean): Class<*> = synchronized(getClassLoadingLock(name)) {
        val privateClass = name.startsWith(TypeNames.KOTLIN_PARSING_PACKAGE) || name.startsWith(TypeNames.SYNTAX_HOOKS)
        // Share all other classes with the IDE to preserve their type identity.
        if (!privateClass) return super.loadClass(name, resolve)
        // Define each instrumented copy once, under its per-class loading lock.
        val loaded = findLoadedClass(name) ?: loadPrivateClass(name)
        if (resolve) resolveClass(loaded)
        loaded
    }

    private fun loadPrivateClass(name: String): Class<*> {
        val resource = name.replace('.', '/') + ".class"
        val bytes = parent.getResourceAsStream(resource)?.use { input ->
            instrumentClass(name, ClassReader(input))
        } ?: throw ClassNotFoundException(name)
        return defineClass(name, bytes, 0, bytes.size)
    }

    private fun instrumentClass(name: String, reader: ClassReader): ByteArray {
        val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
        reader.accept(object : ClassVisitor(Opcodes.ASM9, writer) {
            override fun visitMethod(
                access: Int, methodName: String, descriptor: String, signature: String?, exceptions: Array<out String>?
            ): MethodVisitor {
                val delegate =
                    nonLazyParserVisitor(super.visitMethod(access, methodName, descriptor, signature, exceptions))
                // Only supported grammar entrypoints receive an extension hook.
                val rule = grammarRule(name, methodName, descriptor) ?: return delegate
                return parseHookVisitor(delegate, methodName, descriptor, rule)
            }
        }, 0)
        return writer.toByteArray()
    }

    private fun nonLazyParserVisitor(delegate: MethodVisitor): MethodVisitor =
        object : MethodVisitor(Opcodes.ASM9, delegate) {
            override fun visitMethodInsn(
                opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean
            ) {
                // Parse bodies eagerly so syntax extensions also see their contents.
                val method = if (owner == parsingName && name == "createForTopLevel") {
                    "createForTopLevelNonLazy"
                }
                else name
                super.visitMethodInsn(opcode, owner, method, descriptor, isInterface)
            }
        }

    private fun grammarRule(name: String, methodName: String, descriptor: String): String? = when (name to methodName) {
        TypeNames.KOTLIN_PARSING to "parseTopLevelDeclaration" -> "top_level_declaration"
        TypeNames.KOTLIN_PARSING to "parseMemberDeclaration" -> "member_declaration"
        TypeNames.KOTLIN_PARSING to "parseTypeRef" -> if (descriptor == parseTypeRef) "type" else null
        TypeNames.KOTLIN_EXPRESSION_PARSING to "parseLocalDeclaration" -> "local_declaration"
        TypeNames.KOTLIN_EXPRESSION_PARSING to "parseStatement" -> "statement"
        TypeNames.KOTLIN_EXPRESSION_PARSING to "parseExpression" -> if (descriptor == stringToVoid) "expression" else null
        TypeNames.KOTLIN_EXPRESSION_PARSING to "parseAtomicExpression" -> "expression"
        else -> null
    }

    private fun parseHookVisitor(
        delegate: MethodVisitor, methodName: String, descriptor: String, rule: String
    ): MethodVisitor = object : MethodVisitor(Opcodes.ASM9, delegate) {
        override fun visitCode() {
            super.visitCode()
            // Pass the parser instance, grammar rule and entrypoint flags to the hook.
            visitVarInsn(Opcodes.ALOAD, 0)
            visitTypeInsn(Opcodes.NEW, grammarRuleName)
            visitInsn(Opcodes.DUP)
            visitLdcInsn("kotlin.$rule")
            visitMethodInsn(Opcodes.INVOKESPECIAL, grammarRuleName, "<init>", stringToVoid, false)
            visitInsn(if (methodName == "parseAtomicExpression") Opcodes.ICONST_1 else Opcodes.ICONST_0)
            if (methodName == "parseLocalDeclaration") visitVarInsn(Opcodes.ILOAD, 1)
            else visitInsn(Opcodes.ICONST_0)
            when (methodName) {
                "parseLocalDeclaration" -> visitVarInsn(Opcodes.ILOAD, 2)
                "parseStatement" -> visitVarInsn(Opcodes.ILOAD, 1)
                else -> visitInsn(Opcodes.ICONST_0)
            }
            visitMethodInsn(Opcodes.INVOKESTATIC, hooksName, "parse", parseHook, false)
            // Return on a successful extension parse; otherwise run the original Kotlin parser.
            val fallback = Label()
            visitJumpInsn(Opcodes.IFEQ, fallback)
            if (descriptor.endsWith("Z")) {
                visitInsn(Opcodes.ICONST_1)
                visitInsn(Opcodes.IRETURN)
            }
            else visitInsn(Opcodes.RETURN)
            visitLabel(fallback)
            visitFrame(Opcodes.F_SAME, 0, null, 0, null)
        }
    }
}