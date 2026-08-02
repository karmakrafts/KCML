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

package dev.karmakrafts.kcml.agent.transformer;

import dev.karmakrafts.kcml.agent.asm.ASMUtils;
import dev.karmakrafts.kcml.agent.asm.BlockBuilder;
import dev.karmakrafts.kcml.agent.asm.Types;
import dev.karmakrafts.kcml.agent.asm.Types.CInterop;
import dev.karmakrafts.kcml.agent.asm.Types.Common;
import dev.karmakrafts.kcml.agent.log.Logger;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public final class CodeGeneratorVisitorTransformer extends AbstractClassTransformer {
    public CodeGeneratorVisitorTransformer(final Logger logger) {
        super(logger);
    }

    @Override
    protected boolean shouldTransform(final String className) {
        return className.equals("org/jetbrains/kotlin/backend/konan/llvm/CodeGeneratorVisitor");
    }

    private boolean transformEvaluateFunctionCall(final MethodNode methodNode) {
        // @formatter:off
        return ASMUtils.stream(methodNode.instructions)
            .filter(insn -> insn.getOpcode() == Opcodes.NOP) // Target the fallthrough block around L4
            .findFirst()
            .map(needle -> {
                logger.info(String.format("Transforming %s%s", methodNode.name, methodNode.desc));
                final var builder = BlockBuilder.create().withContext(methodNode);
                builder.loadThis();
                builder.load(Common.IR_CALL, "callee");
                builder.load(Types.LIST, "args");
                builder.invokehook("onEvaluateFunctionCall", CInterop.C_POINTER, Types.OBJECT, Common.IR_CALL, Types.LIST);
                builder.dup();
                builder.ifnull("kcml_fallthrough");
                builder.areturn();
                builder.label("kcml_fallthrough");
                builder.pop();
                methodNode.instructions.insert(needle, builder.getInstructions());
                return true;
            })
            .orElse(false);
        // @formatter:on
    }

    @Override
    protected boolean transform(final ClassNode classNode) {
        var wasChanged = false;
        for (final var methodNode : classNode.methods) {
            wasChanged |= switch (methodNode.name) {
                case "evaluateFunctionCall" -> transformEvaluateFunctionCall(methodNode);
                default -> false;
            };
        }
        return wasChanged;
    }
}
