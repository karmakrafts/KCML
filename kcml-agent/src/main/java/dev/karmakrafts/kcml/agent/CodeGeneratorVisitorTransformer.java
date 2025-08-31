/*
 * Copyright 2025 Karma Krafts & associates
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

package dev.karmakrafts.kcml.agent;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

/**
 * Hook for generating custom LLVM bitcode intrinsics from regular plugin code.
 */
final class CodeGeneratorVisitorTransformer extends AbstractClassTransformer {
    CodeGeneratorVisitorTransformer(final MonitorClient client, final Logger logger) {
        super(client, logger);
    }

    @Override
    protected boolean shouldTransform(final String className) {
        return className.equals("org/jetbrains/kotlin/backend/konan/llvm/CodeGeneratorVisitor");
    }

    @Override
    protected void transform(final ClassNode classNode) {
        // @formatter:off
        final var method = classNode.methods.stream()
            .filter(m -> m.name.equals("evaluateFunctionCall"))
            .findFirst()
            .orElseThrow();
        // @formatter:on

        final var instructions = method.instructions;
        // @formatter:off
        final var needle = ASMUtils.stream(instructions)
            .filter(ASMUtils.firstLocalStore(method, "function"))
            .findFirst()
            .orElseThrow();
        // @formatter:on

        final var injection = new InsnList();
        final var tailLabel = new LabelNode();

        // Load the call target and obtain the origin instance
        injection.add(new VarInsnNode(Opcodes.ALOAD, ASMUtils.findLocal(method, "function")));
        injection.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
            ASMTypes.IR_SIMPLE_FUNCTION.getInternalName(),
            "getOrigin",
            Type.getMethodDescriptor(ASMTypes.IR_DECLARATION_ORIGIN)));
        final var originIndex = method.maxLocals++;
        injection.add(new InsnNode(Opcodes.DUP));
        injection.add(new VarInsnNode(Opcodes.ASTORE, originIndex));

        // Check the type of the origin using reflection
        injection.add(ASMUtils.reflectiveInstanceof(ASMTypes.NATIVE_IR_DECLARATION_ORIGIN));
        injection.add(new JumpInsnNode(Opcodes.IFEQ, tailLabel));

        // Invoke the underlying intrinsic handler
        injection.add(new VarInsnNode(Opcodes.ALOAD, originIndex)); // instance
        // TODO: pass arguments
        injection.add(ASMUtils.reflectiveCall(method,
            "evaluateCall",
            ASMTypes.C_POINTER,
            ASMTypes.IR_CALL,
            ASMTypes.LIST,
            ASMTypes.C_POINTER));

        injection.add(tailLabel);
        instructions.insert(needle, injection);
    }
}
