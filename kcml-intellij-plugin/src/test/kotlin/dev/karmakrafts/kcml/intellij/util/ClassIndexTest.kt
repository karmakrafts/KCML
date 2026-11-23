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

package dev.karmakrafts.kcml.intellij.util

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.org.objectweb.asm.ClassWriter
import org.jetbrains.org.objectweb.asm.Opcodes

class ClassIndexTest : LightJavaCodeInsightFixtureTestCase() {
    fun testSourceMethodChangesAreVisibleImmediately() {
        val target = myFixture.addClass("package cache; public class Target { private void initial() {} }")
        assertTrue(JvmMethod("initial", "()V") in ClassIndex.findMethods(target))
        WriteCommandAction.runWriteCommandAction(project) {
            target.add(JavaPsiFacade.getElementFactory(project).createMethodFromText("public int added() {}", target))
        }
        val methods = ClassIndex.findMethods(target)
        assertTrue(JvmMethod("initial", "()V") in methods)
        assertTrue(JvmMethod("added", "()I") in methods)
    }

    fun testCompiledMetadataIncludesHierarchyPrivateSyntheticAndBridgeMethods() {
        val file = classFile()
        val metadata = ClassIndex.compiledClass(file)!!
        assertEquals("cache.Outer\$Target", metadata.binaryName)
        assertEquals(listOf("cache.Parent", "cache.Interface"), metadata.superNames)
        assertEquals(
            listOf(JvmMethod("hidden", "()V"), JvmMethod("value", "()Ljava/lang/Object;")), metadata.methods
        )
    }

    fun testCompiledMetadataIsSharedAcrossLookupsAndSourceEdits() {
        val file = classFile()
        val initial = ClassIndex.compiledClass(file)
        assertNotNull(initial)
        assertSame(initial, ClassIndex.compiledClass(file))
        myFixture.configureByText("Mixin.kt", "val target = \"<caret>\"")
        WriteCommandAction.runWriteCommandAction(project) {
            myFixture.editor.document.insertString(myFixture.caretOffset, "hidden")
        }
        assertSame(initial, ClassIndex.compiledClass(file))
    }

    fun testCompiledMetadataIsInvalidatedWhenBytecodeChanges() {
        val file = classFile()
        val initial = ClassIndex.compiledClass(file)
        WriteCommandAction.runWriteCommandAction(project) { file.setBinaryContent(bytecode("updated")) }
        val updated = ClassIndex.compiledClass(file)!!
        assertNotSame(initial, updated)
        assertEquals(JvmMethod("updated", "()V"), updated.methods.first())
        assertSame(updated, ClassIndex.compiledClass(file))
    }

    fun testMalformedBytecodeCanBeReplaced() {
        val file = classFile()
        WriteCommandAction.runWriteCommandAction(project) { file.setBinaryContent(byteArrayOf(0)) }
        assertNull(ClassIndex.compiledClass(file))
        WriteCommandAction.runWriteCommandAction(project) { file.setBinaryContent(bytecode("repaired")) }
        assertEquals(JvmMethod("repaired", "()V"), ClassIndex.compiledClass(file)!!.methods.first())
    }

    fun testCanceledLookupDoesNotPoisonMetadataCache() {
        val file = classFile()
        val indicator = EmptyProgressIndicator()
        try {
            ProgressManager.getInstance().runProcess({
                indicator.cancel()
                ClassIndex.compiledClass(file)
            }, indicator)
            fail("Expected canceled lookup to propagate cancellation")
        } catch (_: ProcessCanceledException) {
        }
        assertNotNull(ClassIndex.compiledClass(file))
    }

    private fun classFile(): VirtualFile {
        val file = myFixture.tempDirFixture.createFile("cache/Outer\$Target.class")
        WriteCommandAction.runWriteCommandAction(project) { file.setBinaryContent(bytecode("hidden")) }
        return file
    }

    private fun bytecode(methodName: String): ByteArray {
        val writer = ClassWriter(0)
        writer.visit(
            Opcodes.V21,
            Opcodes.ACC_PUBLIC,
            "cache/Outer\$Target",
            null,
            "cache/Parent",
            arrayOf("cache/Interface")
        )
        writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null).visitEnd()
        writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null).visitEnd()
        writer.visitMethod(Opcodes.ACC_PRIVATE, methodName, "()V", null, null).visitEnd()
        writer.visitMethod(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_SYNTHETIC or Opcodes.ACC_BRIDGE,
            "value",
            "()Ljava/lang/Object;",
            null,
            null
        ).visitEnd()
        writer.visitEnd()
        return writer.toByteArray()
    }
}