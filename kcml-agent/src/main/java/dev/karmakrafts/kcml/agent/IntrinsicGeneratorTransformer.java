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

final class IntrinsicGeneratorTransformer extends AbstractClassTransformer {
    @Override
    protected boolean shouldTransform(final String className) {
        return className.equals("org/jetbrains.kotlin/backend/konan/llvm/IntrinsicGenerator");
    }

    private boolean isTargetFunction(final MethodNode method) {
        // Select the non-static, non-synthetic method which also captures the extension receiver
        return method.name.equals("evaluateCall") && ((method.access & Opcodes.ACC_PRIVATE) != 0);
    }

    @Override
    protected void transform(final ClassNode classNode) { // @formatter:off
        final var method = classNode.methods.stream().filter(this::isTargetFunction).findFirst().orElseThrow();
        final var injection = new InsnList();
        injection.add(new VarInsnNode(Opcodes.ALOAD, 1)); // Load extension receiver (internal type)
        injection.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
            ASMTypes.FUNCTION_GENERATION_CONTEXT.getInternalName(),
            "getFunction",
            Type.getMethodDescriptor(ASMTypes.LLVM_CALLABLE))); // Get LlvmCallable function from FGC (public)
        injection.add(new VarInsnNode(Opcodes.ALOAD, 2)); // Load callSite
        injection.add(new VarInsnNode(Opcodes.ALOAD, 3)); // Load args
        injection.add(new VarInsnNode(Opcodes.ALOAD, 4)); // Load resultSlot
        injection.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
            ASMTypes.COMPILER_HOOKS.getInternalName(),
            "onEvaluateLLVMIntrinsics",
            Type.getMethodDescriptor(ASMTypes.C_POINTER, ASMTypes.LLVM_CALLABLE, ASMTypes.IR_CALL, ASMTypes.LIST, ASMTypes.C_POINTER)));
        final var instructions = method.instructions;
        instructions.insertBefore(instructions.getFirst(), injection);
    } // @formatter:on
}