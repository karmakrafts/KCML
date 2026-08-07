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

package dev.karmakrafts.kcml.agent.transformer.wasm;

import dev.karmakrafts.kcml.agent.asm.ASMUtils;
import dev.karmakrafts.kcml.agent.asm.BlockBuilder;
import dev.karmakrafts.kcml.agent.asm.Types.Common;
import dev.karmakrafts.kcml.agent.asm.Types.KCML;
import dev.karmakrafts.kcml.agent.asm.Types.WASM;
import dev.karmakrafts.kcml.agent.log.Logger;
import dev.karmakrafts.kcml.agent.transformer.AbstractClassTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public final class BodyGeneratorTransformer extends AbstractClassTransformer {
    public BodyGeneratorTransformer(final Logger logger) {
        super(logger);
    }

    @Override
    protected boolean shouldTransform(final String className) {
        return className.equals(WASM.BODY_GENERATOR.getInternalName());
    }

    // We need this because at this point the call arguments have been materialized in WASM
    private boolean isGenerateCallTarget(final AbstractInsnNode insn) {
        if (insn.getOpcode() != Opcodes.INVOKESPECIAL) {
            return false;
        }
        final var methodInsn = (MethodInsnNode) insn;
        return methodInsn.name.equals("tryToGenerateIntrinsicCall");
    }

    private boolean transformGenerateCall(final MethodNode methodNode) { // @formatter:off
        return ASMUtils.stream(methodNode.instructions)
            .filter(this::isGenerateCallTarget)
            .findFirst()
            .map(needle -> {
                logger.info("Transforming %s%s (onGenerateCall)", methodNode.name, methodNode.desc);
                final var builder = BlockBuilder.create().withContext(methodNode);
                builder.aload("call");
                builder.loadThis();
                builder.invokestatic(KCML.WASM_HOOKS,
                    false,
                    "onGenerateCall",
                    Type.BOOLEAN_TYPE,
                    Common.IR_FUNCTION_ACCESS_EXPRESSION,
                    WASM.BODY_GENERATOR);
                builder.ifeq("fallthrough");
                builder.vreturn(); // If we handle the intrinsic, we return from the function early
                builder.label("fallthrough");
                // Shift 3 instructions backwards (ALOAD 0) and insert before that so arguments are materialized already
                final var shiftedNeedle = ASMUtils.shift(methodNode.instructions, needle, -3);
                methodNode.instructions.insertBefore(shiftedNeedle, builder.build());
                return true;
            })
            .orElse(false);
    } // @formatter:on

    @Override
    protected boolean transform(final ClassNode classNode) {
        var wasChanged = false;
        for (final var methodNode : classNode.methods) {
            switch (methodNode.name) {
                case "generateCall" -> wasChanged |= transformGenerateCall(methodNode);
            }
        }
        return wasChanged;
    }
}
