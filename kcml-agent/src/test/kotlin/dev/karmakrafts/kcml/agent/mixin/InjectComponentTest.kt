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

package dev.karmakrafts.kcml.agent.mixin

import dev.karmakrafts.kcml.agent.asm.NonLoadingClassWriter
import dev.karmakrafts.kcml.agent.asm.Types
import dev.karmakrafts.kcml.agent.log.NoopLogger
import dev.karmakrafts.kcml.agent.mixin.component.ComponentContext
import dev.karmakrafts.kcml.agent.mixin.component.InjectComponent
import dev.karmakrafts.kcml.agent.mixin.component.MixinComponent
import dev.karmakrafts.kcml.agent.mixin.component.ThisAwareComponent
import dev.karmakrafts.kcml.agent.mixin.source.BuiltinMixinSource
import dev.karmakrafts.kcml.api.mixin.MixinBridge
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.LocalVariableNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.ParameterNode
import org.objectweb.asm.tree.VarInsnNode
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class InjectComponentTest {
    private fun writeClass(classNode: ClassNode): ByteArray {
        val writer = NonLoadingClassWriter(ClassWriter.COMPUTE_FRAMES)
        classNode.accept(writer)
        return writer.toByteArray()
    }

    private fun loadClass(classNode: ClassNode): Class<*> {
        val bytecode = writeClass(classNode)
        return loadClass(bytecode)
    }

    private fun loadClass(bytecode: ByteArray): Class<*> {
        return object : ClassLoader(javaClass.classLoader) {
            fun define(): Class<*> {
                val type = defineClass(null, bytecode, 0, bytecode.size)
                resolveClass(type)
                return type
            }
        }.define()
    }

    private fun createStringCaptureMixin(): MethodNode = MethodNode(
        Opcodes.ACC_STATIC, "capture", "(Ljava/lang/String;)V", null, null
    ).apply {
        parameters = mutableListOf(ParameterNode("captured", 0))
        invisibleParameterAnnotations = arrayOf(mutableListOf(AnnotationNode(Types.Mixin.capture.descriptor)))
        instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
        instructions.add(InsnNode(Opcodes.POP))
        instructions.add(InsnNode(Opcodes.RETURN))
        maxStack = 1
        maxLocals = 1
    }

    private fun createMixinMethod(): MethodNode = MethodNode(0, "inject", "(JLjava/lang/String;)V", null, null).apply {
        parameters = mutableListOf(ParameterNode("wide", 0), ParameterNode("captured", 0))
        invisibleParameterAnnotations = arrayOf(
            mutableListOf(AnnotationNode(Types.Mixin.capture.descriptor)),
            mutableListOf(AnnotationNode(Types.Mixin.capture.descriptor))
        )
        instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
        instructions.add(VarInsnNode(Opcodes.LLOAD, 1))
        instructions.add(VarInsnNode(Opcodes.LSTORE, 1))
        instructions.add(VarInsnNode(Opcodes.ALOAD, 3))
        instructions.add(VarInsnNode(Opcodes.ASTORE, 3))
        instructions.add(InsnNode(Opcodes.RETURN))
    }

    private fun createTargetMethod(includeCaptured: Boolean = true): MethodNode =
        MethodNode(Opcodes.ACC_STATIC, "target", "()V", null, null).apply {
            val start = LabelNode()
            val end = LabelNode()
            instructions.add(start)
            instructions.add(InsnNode(Opcodes.RETURN))
            instructions.add(end)
            localVariables = mutableListOf(LocalVariableNode("wide", "J", null, start, end, 5))
            if (includeCaptured) {
                localVariables.add(LocalVariableNode("captured", "Ljava/lang/String;", null, start, end, 8))
            }
            maxLocals = 9
        }

    private fun createComponent(
        mixinMethod: MethodNode, targetOpcode: Int = Opcodes.RETURN, mixinClass: ClassNode = ClassNode()
    ): InjectComponent = InjectComponent(
        name = "target",
        descriptor = null,
        slice = Slice(),
        target = Target(opcode = targetOpcode),
        order = Order.BEFORE,
        mixinClass = mixinClass,
        mixinMethod = mixinMethod
    )

    private fun createContext(
        targetMethod: MethodNode, otherComponents: List<MixinComponent> = emptyList()
    ): ComponentContext {
        val targetClass = ClassNode().apply {
            name = "example/Target"
            methods = mutableListOf(targetMethod)
        }
        return ComponentContext(targetClass, MixinLoader(NoopLogger), NoopLogger, otherComponents)
    }

    @Test
    fun `inserts remapped copy while preserving original mixin instructions`() {
        val mixinMethod = createMixinMethod()
        val originalInstructions = mixinMethod.instructions.toArray()
        val targetMethod = createTargetMethod()
        val component = createComponent(mixinMethod)

        assertTrue(component.apply(createContext(targetMethod)))

        assertEquals(listOf(0, 1, 1, 3, 3), mixinMethod.instructions.filterIsInstance<VarInsnNode>().map { it.`var` })
        assertEquals(originalInstructions.size, mixinMethod.instructions.size())
        originalInstructions.forEachIndexed { index, instruction ->
            assertSame(instruction, mixinMethod.instructions[index])
        }

        val injectedVariables = targetMethod.instructions.filterIsInstance<VarInsnNode>()
        assertEquals(listOf(9, 5, 5, 8, 8), injectedVariables.map { it.`var` })
        injectedVariables.forEach { injectedInstruction ->
            originalInstructions.forEach { originalInstruction ->
                assertNotSame(originalInstruction, injectedInstruction)
            }
        }
        assertEquals(
            listOf(
            Opcodes.ALOAD,
            Opcodes.LLOAD,
            Opcodes.LSTORE,
            Opcodes.ALOAD,
            Opcodes.ASTORE,
            Opcodes.GOTO,
            Opcodes.RETURN
        ), targetMethod.instructions.filter { it.opcode >= 0 }.map { it.opcode })
    }

    @Test
    fun `allocates injection locals in target method`() {
        val mixinStart = LabelNode()
        val mixinEnd = LabelNode()
        val mixinMethod = MethodNode(0, "mixinFunction", "()V", null, null).apply {
            parameters = mutableListOf()
            instructions.add(mixinStart)
            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(InsnNode(Opcodes.POP))
            instructions.add(InsnNode(Opcodes.LCONST_0))
            instructions.add(VarInsnNode(Opcodes.LSTORE, 1))
            instructions.add(VarInsnNode(Opcodes.LLOAD, 1))
            instructions.add(InsnNode(Opcodes.POP2))
            instructions.add(InsnNode(Opcodes.RETURN))
            instructions.add(mixinEnd)
            localVariables = mutableListOf(
                LocalVariableNode("this", "Lexample/Mixin;", null, mixinStart, mixinEnd, 0),
                LocalVariableNode("wide", "J", null, mixinStart, mixinEnd, 1)
            )
            maxLocals = 3
        }
        val targetStart = LabelNode()
        val targetEnd = LabelNode()
        val targetMethod = MethodNode(Opcodes.ACC_STATIC, "target", "()V", null, null).apply {
            instructions.add(targetStart)
            instructions.add(InsnNode(Opcodes.RETURN))
            instructions.add(targetEnd)
            localVariables = mutableListOf(LocalVariableNode("existing", "I", null, targetStart, targetEnd, 3))
            maxLocals = 4
        }
        val component = createComponent(mixinMethod)

        assertTrue(component.apply(createContext(targetMethod)))

        assertEquals(7, targetMethod.maxLocals)
        assertEquals(listOf(4, 5, 5), targetMethod.instructions.filterIsInstance<VarInsnNode>().map { it.`var` })
        assertEquals(
            listOf("existing", "mixin\$mixinFunction\$this", "mixin\$mixinFunction\$wide"),
            targetMethod.localVariables.map { it.name })
        assertEquals(listOf(3, 4, 5), targetMethod.localVariables.map { it.index })
        assertEquals(listOf("I", "Lexample/Mixin;", "J"), targetMethod.localVariables.map { it.desc })
        targetMethod.localVariables.drop(1).forEach { local ->
            assertTrue(targetMethod.instructions.contains(local.start))
            assertTrue(targetMethod.instructions.contains(local.end))
        }
        assertNotSame(mixinStart, targetMethod.localVariables[1].start)
        assertNotSame(mixinEnd, targetMethod.localVariables[1].end)
    }

    @Test
    fun `allocates injection locals before computing frames`() {
        val parsedTargetClass = ClassNode()
        val targetResource = "org/jetbrains/kotlin/backend/wasm/ir2wasm/BodyGenerator.class"
        val targetBytecode = checkNotNull(javaClass.classLoader.getResourceAsStream(targetResource)).use { stream ->
            stream.readBytes()
        }
        ClassReader(targetBytecode).accept(parsedTargetClass, 0)
        val targetMethod = parsedTargetClass.methods.single { method ->
            method.name == $$"generateExpression$org_jetbrains_kotlin_backend_wasm"
        }
        val initialMaxLocals = targetMethod.maxLocals
        val loaderJar = Path.of(checkNotNull(System.getProperty("kcml.loader.jar")))
        val loader = MixinLoader(NoopLogger)
        loader.load(listOf(loaderJar))
        val mixin = loader.mixins.single { candidate ->
            candidate.mixinClass.name == "dev/karmakrafts/kcml/mixin/wasm/BodyGeneratorMixin"
        }

        assertTrue(mixin.apply(parsedTargetClass))
        assertEquals(initialMaxLocals + 1, targetMethod.maxLocals)

        val hookIndex = targetMethod.instructions.indexOfFirst { instruction ->
            instruction is MethodInsnNode && instruction.owner == "dev/karmakrafts/kcml/hooks/wasm/WASMHooks" && instruction.name == "onGenerateElement"
        }
        val visitorIndex = targetMethod.instructions.indexOfFirst { instruction ->
            instruction is MethodInsnNode && instruction.name == "acceptVoid"
        }
        assertTrue(hookIndex >= 0, "Hook must intercept every expression")
        assertTrue(hookIndex < visitorIndex, "Hook must run before expression generation")

        val writer = NonLoadingClassWriter(ClassWriter.COMPUTE_FRAMES)
        parsedTargetClass.accept(writer)
        assertTrue(writer.toByteArray().isNotEmpty())
    }

    @Test
    fun `captures compiler arguments from typed CLI exec entry point`() {
        val parsedTargetClass = ClassNode()
        val targetResource = "org/jetbrains/kotlin/cli/common/CLICompiler.class"
        val targetBytecode = checkNotNull(javaClass.classLoader.getResourceAsStream(targetResource)).use { stream ->
            stream.readBytes()
        }
        ClassReader(targetBytecode).accept(parsedTargetClass, 0)
        val loaderJar = Path.of(checkNotNull(System.getProperty("kcml.loader.jar")))
        val loader = MixinLoader(NoopLogger)
        loader.load(listOf(loaderJar), loaderJar)
        val mixin = loader.mixins.single { candidate ->
            candidate.mixinClass.name == "dev/karmakrafts/kcml/mixin/CLICompilerMixin"
        }

        val constantTable =
            MixinBridge.INSTANCE.constantTable.computeIfAbsent(BuiltinMixinSource) { ConcurrentHashMap() }
        val previousLoaderPath = constantTable.put("loader_path", loaderJar.toString())
        try {
            mixin.apply(parsedTargetClass)
            assertTrue(mixin.mixinClass.methods.any { method ->
                method.instructions.filterIsInstance<MethodInsnNode>().any { instruction ->
                    instruction.owner == Types.Mixin.constantTable.internalName
                }
            })
            val repeatedTarget = ClassNode()
            ClassReader(targetBytecode).accept(repeatedTarget, 0)
            val nextLoaderPath = loaderJar.resolveSibling("another-loader.jar").toString()
            constantTable["loader_path"] = nextLoaderPath
            assertTrue(mixin.apply(repeatedTarget))
            assertTrue(repeatedTarget.methods.any { method ->
                method.instructions.filterIsInstance<LdcInsnNode>().any { instruction ->
                    instruction.cst == nextLoaderPath
                }
            })
        }
        finally {
            if (previousLoaderPath == null) constantTable.remove("loader_path")
            else constantTable["loader_path"] = previousLoaderPath
        }
        val targetMethod = parsedTargetClass.methods.single { method ->
            method.name == "exec" && method.desc == "(Lorg/jetbrains/kotlin/cli/common/messages/MessageCollector;Lorg/jetbrains/kotlin/config/Services;Lorg/jetbrains/kotlin/cli/common/arguments/CommonCompilerArguments;)Lorg/jetbrains/kotlin/cli/common/ExitCode;"
        }
        val hookCalls = targetMethod.instructions.filterIsInstance<MethodInsnNode>().filter { instruction ->
            instruction.opcode == Opcodes.INVOKESTATIC && instruction.owner == "dev/karmakrafts/kcml/hooks/CommonHooks" && instruction.name == "onExecImpl"
        }
        assertEquals(1, hookCalls.size)
        assertFalse(targetMethod.instructions.filterIsInstance<MethodInsnNode>().any { instruction ->
            instruction.owner == Types.Mixin.constantTable.internalName
        })
        assertTrue(targetMethod.instructions.filterIsInstance<LdcInsnNode>().any { instruction ->
            instruction.cst == loaderJar.toString()
        })
        val transformedClass = loadClass(parsedTargetClass)
        assertEquals(parsedTargetClass.name.replace('/', '.'), transformedClass.name)
    }

    @Test
    fun `captures generation state from llvm backend lambda`() {
        val parsedTargetClass = ClassNode()
        val targetResource = "org/jetbrains/kotlin/backend/konan/driver/phases/TopLevelPhasesKt.class"
        val targetBytecode = checkNotNull(javaClass.classLoader.getResourceAsStream(targetResource)).use { stream ->
            stream.readBytes()
        }
        ClassReader(targetBytecode).accept(parsedTargetClass, 0)
        val loaderJar = Path.of(checkNotNull(System.getProperty("kcml.loader.jar")))
        val loader = MixinLoader(NoopLogger)
        loader.load(listOf(loaderJar))
        val mixin = loader.mixins.single { candidate ->
            candidate.mixinClass.name == "dev/karmakrafts/kcml/mixin/llvm/TopLevelPhasesKtMixin"
        }

        assertTrue(mixin.apply(parsedTargetClass))
    }

    @Test
    fun `removes return context parameter references from llvm injection`() {
        val parsedTargetClass = ClassNode()
        val targetResource = "org/jetbrains/kotlin/backend/konan/llvm/CodeGeneratorVisitor.class"
        val targetBytecode = checkNotNull(javaClass.classLoader.getResourceAsStream(targetResource)).use { stream ->
            stream.readBytes()
        }
        ClassReader(targetBytecode).accept(parsedTargetClass, 0)
        val loaderJar = Path.of(checkNotNull(System.getProperty("kcml.loader.jar")))
        val loader = MixinLoader(NoopLogger)
        loader.load(listOf(loaderJar))
        val mixin = loader.mixins.single { candidate ->
            candidate.mixinClass.name == "dev/karmakrafts/kcml/mixin/llvm/CodeGeneratorVisitorMixin"
        }

        assertTrue(mixin.apply(parsedTargetClass))
        val transformedClass = loadClass(parsedTargetClass)
        assertEquals(parsedTargetClass.name.replace('/', '.'), transformedClass.name)
        val generationMethods = setOf(
            "evaluateExpression",
            "visitModuleFragment",
            "visitFile",
            "visitClass",
            "visitSimpleFunction",
            "visitConstructor",
            "visitAnonymousInitializer",
            "visitProperty",
            "visitField",
            "generateVariable"
        )
        val interceptedMethods = parsedTargetClass.methods.filter { method ->
            method.instructions.filterIsInstance<MethodInsnNode>().any { instruction ->
                instruction.owner == "dev/karmakrafts/kcml/hooks/llvm/LLVMHooks" && instruction.name == "onGenerateElement"
            }
        }
        assertEquals(generationMethods, interceptedMethods.map { it.name }.toSet())
        for (method in interceptedMethods) {
            assertEquals(1, method.instructions.filterIsInstance<MethodInsnNode>().count { instruction ->
                instruction.owner == "dev/karmakrafts/kcml/hooks/llvm/LLVMHooks" && instruction.name == "onGenerateElement"
            })
            assertTrue(method.instructions.none { instruction ->
                instruction is MethodInsnNode && instruction.owner == "dev/karmakrafts/kcml/api/mixin/ReturnContext"
            })
        }
        assertTrue(transformedClass.declaredMethods.any { method -> method.name == "evaluateExpression" })
    }

    @Test
    fun `produces verifiable this aware llvm injection independently`() {
        val parsedTargetClass = ClassNode()
        val targetResource = "org/jetbrains/kotlin/backend/konan/llvm/CodeGeneratorVisitor.class"
        val targetBytecode = checkNotNull(javaClass.classLoader.getResourceAsStream(targetResource)).use { stream ->
            stream.readBytes()
        }
        ClassReader(targetBytecode).accept(parsedTargetClass, 0)
        val loaderJar = Path.of(checkNotNull(System.getProperty("kcml.loader.jar")))
        val loader = MixinLoader(NoopLogger)
        loader.load(listOf(loaderJar))
        val mixin = loader.mixins.single { candidate ->
            candidate.mixinClass.name == "dev/karmakrafts/kcml/mixin/llvm/CodeGeneratorVisitorMixin"
        }
        val inject = mixin.components.filterIsInstance<InjectComponent>().single { component ->
            component.mixinMethod.name == "evaluateExpression"
        }
        val context = ComponentContext(
            parsedTargetClass, loader, NoopLogger, mixin.components - inject
        )

        assertTrue(inject.apply(context))
        val transformedClass = loadClass(parsedTargetClass)
        assertEquals(parsedTargetClass.name.replace('/', '.'), transformedClass.name)
        assertTrue(transformedClass.declaredMethods.any { method -> method.name == "evaluateExpression" })
    }

    @Test
    fun `produces verifiable llvm injection after serialized retransformation`() {
        val targetResource = "org/jetbrains/kotlin/backend/konan/llvm/CodeGeneratorVisitor.class"
        val targetBytecode = checkNotNull(javaClass.classLoader.getResourceAsStream(targetResource)).use { stream ->
            stream.readBytes()
        }
        val loaderJar = Path.of(checkNotNull(System.getProperty("kcml.loader.jar")))
        val loader = MixinLoader(NoopLogger)
        loader.load(listOf(loaderJar))
        val mixin = loader.mixins.single { candidate ->
            candidate.mixinClass.name == "dev/karmakrafts/kcml/mixin/llvm/CodeGeneratorVisitorMixin"
        }
        val firstPass = ClassNode()
        ClassReader(targetBytecode).accept(firstPass, 0)
        assertTrue(mixin.apply(firstPass))
        val secondPass = ClassNode()
        ClassReader(writeClass(firstPass)).accept(secondPass, 0)

        assertTrue(mixin.apply(secondPass))
        val targetMethod = secondPass.methods.single { method ->
            method.name == "evaluateExpression" && method.desc == "(Lorg/jetbrains/kotlin/ir/expressions/IrExpression;Lkotlinx/cinterop/CPointer;)Lkotlinx/cinterop/CPointer;"
        }
        val hookCalls = targetMethod.instructions.filterIsInstance<MethodInsnNode>().filter { instruction ->
            instruction.opcode == Opcodes.INVOKESTATIC && instruction.owner == "dev/karmakrafts/kcml/hooks/llvm/LLVMHooks" && instruction.name == "onGenerateElement"
        }
        assertEquals(2, hookCalls.size)
        for (call in hookCalls) {
            val arguments =
                generateSequence(call.previous) { instruction -> instruction.previous }.filterIsInstance<VarInsnNode>()
                    .take(2)
                    .toList()
                    .reversed()
            assertEquals(listOf(Opcodes.ALOAD, Opcodes.ALOAD), arguments.map { it.opcode })
            assertEquals(listOf(0, 1), arguments.map { it.`var` })
        }
        val transformedClass = loadClass(secondPass)
        assertEquals(secondPass.name.replace('/', '.'), transformedClass.name)
    }

    @Test
    fun `produces verifiable llvm injection through consecutive transformers`() {
        val targetResource = "org/jetbrains/kotlin/backend/konan/llvm/CodeGeneratorVisitor.class"
        val targetBytecode = checkNotNull(javaClass.classLoader.getResourceAsStream(targetResource)).use { stream ->
            stream.readBytes()
        }
        val loaderJar = Path.of(checkNotNull(System.getProperty("kcml.loader.jar")))
        val firstLoader = MixinLoader(NoopLogger).apply { load(listOf(loaderJar)) }
        val secondLoader = MixinLoader(NoopLogger).apply { load(listOf(loaderJar)) }
        val firstTransformer = MixinClassTransformer(NoopLogger, firstLoader, null)
        val secondTransformer = MixinClassTransformer(NoopLogger, secondLoader, null)

        val firstPass =
            firstTransformer.transform(null, null, targetResource.removeSuffix(".class"), null, null, targetBytecode)
        val secondPass =
            secondTransformer.transform(null, null, targetResource.removeSuffix(".class"), null, null, firstPass)
        val transformedClass = loadClass(secondPass)

        assertEquals(targetResource.removeSuffix(".class").replace('/', '.'), transformedClass.name)
    }

    @Test
    fun `produces verifiable wasm generation hooks`() {
        val targets = mapOf(
            "BodyGenerator" to setOf(
                $$"generateExpression$org_jetbrains_kotlin_backend_wasm", "visitVariable", "visitBlockBody"
            ),
            "IrFileToWasmIrGenerator" to setOf(
                "visitClass", "visitFunction", "visitField", "visitProperty", "visitTypeAlias"
            ),
            "WasmModuleFragmentGeneratorKt" to setOf("compileIrFile"),
            "DeclarationGeneratorKt" to setOf("generateConstExpression")
        )
        val loaderJar = Path.of(checkNotNull(System.getProperty("kcml.loader.jar")))
        val loader = MixinLoader(NoopLogger)
        loader.load(listOf(loaderJar))
        for ((target, methods) in targets) {
            val parsedTargetClass = ClassNode()
            val resource = "org/jetbrains/kotlin/backend/wasm/ir2wasm/$target.class"
            checkNotNull(javaClass.classLoader.getResourceAsStream(resource)).use { stream ->
                ClassReader(stream.readBytes()).accept(parsedTargetClass, 0)
            }
            val mixin = loader.mixins.single { candidate ->
                candidate.mixinClass.name == "dev/karmakrafts/kcml/mixin/wasm/${target}Mixin"
            }
            assertTrue(mixin.apply(parsedTargetClass))
            val interceptedMethods = parsedTargetClass.methods.filter { method ->
                method.instructions.filterIsInstance<MethodInsnNode>().any { instruction ->
                    instruction.owner == "dev/karmakrafts/kcml/hooks/wasm/WASMHooks"
                }
            }
            assertEquals(methods, interceptedMethods.map { it.name }.toSet())
            for (method in interceptedMethods) {
                assertEquals(1, method.instructions.filterIsInstance<MethodInsnNode>().count { instruction ->
                    instruction.owner == "dev/karmakrafts/kcml/hooks/wasm/WASMHooks"
                })
                assertTrue(method.instructions.none { instruction ->
                    instruction is MethodInsnNode && instruction.owner == "dev/karmakrafts/kcml/api/mixin/ReturnContext"
                })
            }
            val transformedClass = loadClass(parsedTargetClass)
            assertEquals(parsedTargetClass.name.replace('/', '.'), transformedClass.name)
            assertTrue(transformedClass.declaredMethods.isNotEmpty())
        }
    }

    @Test
    fun `produces verifiable jvm generation hooks`() {
        val loaderJar = Path.of(checkNotNull(System.getProperty("kcml.loader.jar")))
        val loader = MixinLoader(NoopLogger)
        loader.load(listOf(loaderJar), loaderJar)
        for (target in listOf("ClassCodegen", "ExpressionCodegen")) {
            val parsedTargetClass = ClassNode()
            val resource = "org/jetbrains/kotlin/backend/jvm/codegen/$target.class"
            checkNotNull(javaClass.classLoader.getResourceAsStream(resource)).use { stream ->
                ClassReader(stream.readBytes()).accept(parsedTargetClass, 0)
            }
            parsedTargetClass.methods.reverse()
            val mixin = loader.mixins.single { candidate ->
                candidate.mixinClass.name == "dev/karmakrafts/kcml/mixin/jvm/${target}Mixin"
            }
            assertTrue(mixin.apply(parsedTargetClass))
            val interceptedMethods = parsedTargetClass.methods.filter { method ->
                method.instructions.filterIsInstance<MethodInsnNode>().any { instruction ->
                    instruction.owner == "dev/karmakrafts/kcml/hooks/jvm/JVMHooks"
                }
            }
            assertEquals(if (target == "ClassCodegen") 1 else 24, interceptedMethods.size)
            for (method in interceptedMethods) {
                assertFalse(method.access and Opcodes.ACC_BRIDGE != 0)
                assertEquals(1, method.instructions.filterIsInstance<MethodInsnNode>().count { instruction ->
                    instruction.owner == "dev/karmakrafts/kcml/hooks/jvm/JVMHooks"
                })
                assertTrue(method.instructions.none { instruction ->
                    instruction is MethodInsnNode && instruction.owner == "dev/karmakrafts/kcml/api/mixin/ReturnContext"
                })
            }
            val transformedClass = loadClass(parsedTargetClass)
            assertTrue(transformedClass.declaredMethods.isNotEmpty())
        }
    }


    @Test
    fun `does not allocate captured target locals again`() {
        val start = LabelNode()
        val end = LabelNode()
        val mixinMethod = MethodNode(
            Opcodes.ACC_STATIC, "captureAndAllocate", "(Ljava/lang/String;)V", null, null
        ).apply {
            parameters = mutableListOf(ParameterNode("captured", 0))
            invisibleParameterAnnotations = arrayOf(mutableListOf(AnnotationNode(Types.Mixin.capture.descriptor)))
            instructions.add(start)
            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(InsnNode(Opcodes.POP))
            instructions.add(InsnNode(Opcodes.ICONST_0))
            instructions.add(VarInsnNode(Opcodes.ISTORE, 1))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 1))
            instructions.add(InsnNode(Opcodes.POP))
            instructions.add(InsnNode(Opcodes.RETURN))
            instructions.add(end)
            localVariables = mutableListOf(
                LocalVariableNode("captured", "Ljava/lang/String;", null, start, end, 0),
                LocalVariableNode("local", "I", null, start, end, 1)
            )
            maxLocals = 2
        }
        val targetMethod = createTargetMethod()
        val component = createComponent(mixinMethod)

        assertTrue(component.apply(createContext(targetMethod)))

        assertEquals(10, targetMethod.maxLocals)
        assertEquals(listOf(8, 9, 9), targetMethod.instructions.filterIsInstance<VarInsnNode>().map { it.`var` })
        assertEquals(
            listOf("wide", "captured", $$"mixin$captureAndAllocate$local"), targetMethod.localVariables.map { it.name })
        assertEquals(listOf(5, 8, 9), targetMethod.localVariables.map { it.index })
    }

    @Test
    fun `captures the local that is live at the injection point`() {
        val mixinMethod = createStringCaptureMixin()
        val currentStart = LabelNode()
        val currentEnd = LabelNode()
        val futureStart = LabelNode()
        val futureEnd = LabelNode()
        val targetMethod = MethodNode(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "target", "()Ljava/lang/String;", null, null
        ).apply {
            instructions.add(currentStart)
            instructions.add(LdcInsnNode("expected"))
            instructions.add(VarInsnNode(Opcodes.ASTORE, 1))
            instructions.add(VarInsnNode(Opcodes.ALOAD, 1))
            instructions.add(InsnNode(Opcodes.ARETURN))
            instructions.add(currentEnd)
            instructions.add(futureStart)
            instructions.add(InsnNode(Opcodes.ACONST_NULL))
            instructions.add(VarInsnNode(Opcodes.ASTORE, 0))
            instructions.add(InsnNode(Opcodes.ACONST_NULL))
            instructions.add(InsnNode(Opcodes.ARETURN))
            instructions.add(futureEnd)
            localVariables = mutableListOf(
                LocalVariableNode("captured", "Ljava/lang/String;", null, futureStart, futureEnd, 0),
                LocalVariableNode("captured", "Ljava/lang/String;", null, currentStart, currentEnd, 1)
            )
            maxStack = 1
            maxLocals = 2
        }
        val targetClass = ClassNode().apply {
            version = Opcodes.V1_8
            access = Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL
            name = "example/ScopedCaptureTarget"
            superName = "java/lang/Object"
            methods = mutableListOf(targetMethod)
        }
        val component = InjectComponent(
            name = "target",
            descriptor = null,
            slice = Slice(),
            target = Target(opcode = Opcodes.ALOAD, index = 1),
            order = Order.BEFORE,
            mixinClass = ClassNode(),
            mixinMethod = mixinMethod
        )

        assertTrue(component.apply(ComponentContext(targetClass, MixinLoader(NoopLogger), NoopLogger, emptyList())))

        val generatedClass = loadClass(targetClass)
        assertEquals("expected", generatedClass.getMethod("target").invoke(null))
    }

    @Test
    fun `rejects captured local outside the injection point scope`() {
        val localStart = LabelNode()
        val localEnd = LabelNode()
        val targetMethod = MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "target", "()V", null, null).apply {
            instructions.add(localStart)
            instructions.add(LdcInsnNode("expired"))
            instructions.add(VarInsnNode(Opcodes.ASTORE, 0))
            instructions.add(localEnd)
            instructions.add(InsnNode(Opcodes.RETURN))
            localVariables = mutableListOf(
                LocalVariableNode("captured", "Ljava/lang/String;", null, localStart, localEnd, 0)
            )
            maxStack = 1
            maxLocals = 1
        }
        val component = createComponent(createStringCaptureMixin())

        val error = assertFailsWith<IllegalStateException> {
            component.apply(createContext(targetMethod))
        }

        assertTrue(error.message.orEmpty().contains("at injection point"))
    }

    @Test
    fun `redirects all mixin returns to one local return frame`() {
        val mixinMethod = MethodNode(0, "inject", "()V", null, null).apply {
            parameters = mutableListOf()
            instructions.add(InsnNode(Opcodes.RETURN))
            instructions.add(InsnNode(Opcodes.RETURN))
        }
        val targetMethod = createTargetMethod()
        val component = createComponent(mixinMethod)

        assertTrue(component.apply(createContext(targetMethod)))

        val jumps = targetMethod.instructions.filterIsInstance<JumpInsnNode>()
        assertEquals(2, jumps.size)
        assertTrue(jumps.all { it.opcode == Opcodes.GOTO })
        assertSame(jumps.first().label, jumps.last().label)
        assertEquals(Opcodes.RETURN, jumps.first().label.next.opcode)
    }

    @Test
    fun `replaces every this aware call with one target receiver load`() {
        val mixinName = "example/Mixin"
        val mixinMethod = MethodNode(0, "inject", "()V", null, null).apply {
            parameters = mutableListOf()
            repeat(2) {
                instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
                instructions.add(
                    MethodInsnNode(
                        Opcodes.INVOKEVIRTUAL, mixinName, "getThis", "()Ljava/lang/Object;", false
                    )
                )
                instructions.add(InsnNode(Opcodes.POP))
            }
            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(
                MethodInsnNode(
                    Opcodes.INVOKEVIRTUAL, "example/Other", "getThis", "()Ljava/lang/Object;", false
                )
            )
            instructions.add(InsnNode(Opcodes.POP))
            instructions.add(InsnNode(Opcodes.RETURN))
        }
        val mixinClass = ClassNode().apply {
            name = mixinName
            interfaces = mutableListOf(Types.Mixin.thisAware.internalName)
        }
        val targetMethod = MethodNode(0, "target", "()V", null, null).apply {
            instructions.add(InsnNode(Opcodes.RETURN))
            maxLocals = 4
        }
        val component = createComponent(mixinMethod, mixinClass = mixinClass)
        val thisAwareComponent = ThisAwareComponent(mixinClass)

        val context = createContext(targetMethod, listOf(thisAwareComponent))
        assertTrue(component.apply(context))
        assertFalse(thisAwareComponent.apply(context))

        assertEquals(
            listOf(
            Opcodes.ALOAD,
            Opcodes.POP,
            Opcodes.ALOAD,
            Opcodes.POP,
            Opcodes.ALOAD,
            Opcodes.INVOKEVIRTUAL,
            Opcodes.POP,
            Opcodes.GOTO,
            Opcodes.RETURN
        ), targetMethod.instructions.filter { it.opcode >= 0 }.map { it.opcode })
        assertEquals(
            listOf("example/Other"), targetMethod.instructions.filterIsInstance<MethodInsnNode>().map { it.owner })
        assertEquals(listOf(0, 0, 4), targetMethod.instructions.filterIsInstance<VarInsnNode>().map { it.`var` })
    }

    @Test
    fun `leaves this aware calls unchanged for a mixin without the interface`() {
        val mixinName = "example/Mixin"
        val mixinMethod = MethodNode(0, "inject", "()V", null, null).apply {
            parameters = mutableListOf()
            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(
                MethodInsnNode(
                    Opcodes.INVOKEVIRTUAL, mixinName, "getThis", "()Ljava/lang/Object;", false
                )
            )
            instructions.add(InsnNode(Opcodes.POP))
            instructions.add(InsnNode(Opcodes.RETURN))
        }
        val targetMethod = MethodNode(0, "target", "()V", null, null).apply {
            instructions.add(InsnNode(Opcodes.RETURN))
        }
        val component = createComponent(mixinMethod, mixinClass = ClassNode().apply { name = mixinName })

        assertTrue(component.apply(createContext(targetMethod)))

        assertEquals(
            listOf(Opcodes.ALOAD, Opcodes.INVOKEVIRTUAL, Opcodes.POP, Opcodes.GOTO, Opcodes.RETURN),
            targetMethod.instructions.filter { it.opcode >= 0 }.map { it.opcode })
    }

    @Test
    fun `replaces return context call with typed target return`() {
        val mixinMethod = MethodNode(
            0,
            "inject",
            "(${Types.Mixin.returnContext.descriptor})V",
            "(${Types.Mixin.returnContext.descriptor.dropLast(1)}<Ljava/lang/String;>;)V",
            null
        ).apply {
            parameters = mutableListOf(ParameterNode("returnContext", 0))
            instructions.add(VarInsnNode(Opcodes.ALOAD, 1))
            instructions.add(LdcInsnNode("result"))
            instructions.add(
                MethodInsnNode(
                    Opcodes.INVOKEINTERFACE,
                    Types.Mixin.returnContext.internalName,
                    "returnFromTarget",
                    "(Ljava/lang/Object;)V",
                    true
                )
            )
            instructions.add(InsnNode(Opcodes.RETURN))
        }
        val targetMethod = MethodNode(
            Opcodes.ACC_STATIC, "target", "()Ljava/lang/String;", null, null
        ).apply {
            instructions.add(InsnNode(Opcodes.ACONST_NULL))
            instructions.add(InsnNode(Opcodes.ARETURN))
        }
        val component = createComponent(mixinMethod, Opcodes.ARETURN)

        assertTrue(component.apply(createContext(targetMethod)))

        assertEquals(
            listOf(Opcodes.ACONST_NULL, Opcodes.LDC, Opcodes.ARETURN, Opcodes.GOTO, Opcodes.ARETURN),
            targetMethod.instructions.filter { it.opcode >= 0 }.map { it.opcode })
    }

    @Test
    fun `replaces unit return context call with void target return`() {
        val mixinMethod = MethodNode(
            0,
            "inject",
            "(${Types.Mixin.returnContext.descriptor})V",
            "(${Types.Mixin.returnContext.descriptor.dropLast(1)}<Lkotlin/Unit;>;)V",
            null
        ).apply {
            parameters = mutableListOf(ParameterNode("returnContext", 0))
            instructions.add(VarInsnNode(Opcodes.ALOAD, 1))
            instructions.add(VarInsnNode(Opcodes.ASTORE, 2))
            instructions.add(InsnNode(Opcodes.ICONST_0))
            instructions.add(VarInsnNode(Opcodes.ISTORE, 3))
            instructions.add(VarInsnNode(Opcodes.ALOAD, 2))
            instructions.add(FieldInsnNode(Opcodes.GETSTATIC, "kotlin/Unit", "INSTANCE", "Lkotlin/Unit;"))
            instructions.add(
                MethodInsnNode(
                    Opcodes.INVOKEINTERFACE,
                    Types.Mixin.returnContext.internalName,
                    "returnFromTarget",
                    "(Ljava/lang/Object;)V",
                    true
                )
            )
            instructions.add(InsnNode(Opcodes.RETURN))
        }
        val targetMethod = createTargetMethod()
        val component = createComponent(mixinMethod)

        assertTrue(component.apply(createContext(targetMethod)))

        assertEquals(
            listOf(Opcodes.ICONST_0, Opcodes.ISTORE, Opcodes.RETURN, Opcodes.GOTO, Opcodes.RETURN),
            targetMethod.instructions.filter { it.opcode >= 0 }.map { it.opcode })
    }

    @Test
    fun `requires generic signature for return context`() {
        val mixinMethod = MethodNode(0, "inject", "(${Types.Mixin.returnContext.descriptor})V", null, null).apply {
            parameters = mutableListOf(ParameterNode("returnContext", 0))
            instructions.add(VarInsnNode(Opcodes.ALOAD, 1))
            instructions.add(LdcInsnNode("result"))
            instructions.add(
                MethodInsnNode(
                    Opcodes.INVOKEINTERFACE,
                    Types.Mixin.returnContext.internalName,
                    "returnFromTarget",
                    "(Ljava/lang/Object;)V",
                    true
                )
            )
            instructions.add(InsnNode(Opcodes.RETURN))
        }
        val component = createComponent(mixinMethod)

        assertFailsWith<IllegalStateException> {
            component.apply(createContext(createTargetMethod()))
        }
    }

    @Test
    fun `fails when a captured target local cannot be found`() {
        val component = createComponent(createMixinMethod())

        assertFailsWith<IllegalStateException> {
            component.apply(createContext(createTargetMethod(includeCaptured = false)))
        }
    }
}