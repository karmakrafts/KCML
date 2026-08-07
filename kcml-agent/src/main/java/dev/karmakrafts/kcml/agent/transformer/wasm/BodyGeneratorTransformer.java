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
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public final class BodyGeneratorTransformer extends AbstractClassTransformer {
    public BodyGeneratorTransformer(final Logger logger) {
        super(logger);
    }

    @Override
    protected boolean shouldTransform(final String className) {
        return className.equals(WASM.BODY_GENERATOR.getInternalName());
    }

    private boolean transformGenerateCall(final MethodNode methodNode) { // @formatter:off
        return ASMUtils.stream(methodNode.instructions)
            .findFirst()
            .map(needle -> {
                logger.info("Transforming %s%s (onGenerateCall)", methodNode.name, methodNode.desc);
                final var builder = BlockBuilder.create().withContext(methodNode);
                builder.aload("call");
                builder.loadThis();
                builder.invokestatic(KCML.WASM_HOOKS,
                    false,
                    "onGenerateCall",
                    Type.VOID_TYPE,
                    Common.IR_FUNCTION_ACCESS_EXPRESSION,
                    WASM.BODY_GENERATOR);
                methodNode.instructions.insertBefore(needle, builder.build());
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
