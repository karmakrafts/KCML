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

package dev.karmakrafts.kcml.agent.transformer.llvm;

import dev.karmakrafts.kcml.agent.asm.ASMUtils;
import dev.karmakrafts.kcml.agent.asm.BlockBuilder;
import dev.karmakrafts.kcml.agent.asm.Types;
import dev.karmakrafts.kcml.agent.asm.Types.KCML;
import dev.karmakrafts.kcml.agent.asm.Types.Konan;
import dev.karmakrafts.kcml.agent.log.Logger;
import dev.karmakrafts.kcml.agent.transformer.AbstractClassTransformer;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public final class TopLevelPhasesTransformer extends AbstractClassTransformer {
    public TopLevelPhasesTransformer(final Logger logger) {
        super(logger);
    }

    @Override
    protected boolean shouldTransform(final String className) {
        return className.equals(Konan.TOP_LEVEL_PHASES_KT.getInternalName());
    }

    private boolean transformRunAfterLowerings(final MethodNode methodNode) {
        // @formatter:off
        return ASMUtils.stream(methodNode.instructions)
            .findFirst()
            .map(needle -> {
                logger.info("Transforming %s%s (onRunAfterLowerings)", methodNode.name, methodNode.desc);
                final var builder = BlockBuilder.create().withContext(methodNode);
                builder.load(Konan.NATIVE_GENERATION_STATE, "generationState");
                builder.invokestatic(KCML.LLVM_HOOKS, false, "onRunAfterLowerings", Type.VOID_TYPE, Types.OBJECT);
                methodNode.instructions.insertBefore(needle, builder.build());
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
                case "runBackend$lambda$0$runAfterLowerings" -> transformRunAfterLowerings(methodNode);
                default -> false;
            };
        }
        return wasChanged;
    }
}
