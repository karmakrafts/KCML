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

import dev.karmakrafts.kcml.agent.log.Logger;
import dev.karmakrafts.kcml.agent.util.ASMTypes;
import dev.karmakrafts.kcml.agent.util.ASMTypes.KCML;
import dev.karmakrafts.kcml.agent.util.ASMUtils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

public final class TopLevelPhasesTransformer extends AbstractClassTransformer {
    public TopLevelPhasesTransformer(final Logger logger) {
        super(logger);
    }

    @Override
    protected boolean shouldTransform(final String className) {
        return className.equals("org/jetbrains/kotlin/backend/konan/driver/phases/TopLevelPhasesKt");
    }

    private boolean transformRunAfterLowerings(final MethodNode methodNode) {
        logger.info(String.format("Transforming %s%s", methodNode.name, methodNode.desc));
        // Create the injection
        final var injection = new InsnList();
        final var generationState = ASMUtils.findLocal(methodNode, "generationState");
        injection.add(new VarInsnNode(Opcodes.ALOAD, generationState));
        injection.add(new MethodInsnNode( // @formatter:off
            Opcodes.INVOKESTATIC,
            KCML.TOP_LEVEL_PHASES_HOOKS.getInternalName(),
            "onRunAfterLowerings",
            Type.getMethodDescriptor(Type.VOID_TYPE, ASMTypes.OBJECT),
            false
        )); // @formatter:on
        final var needle = methodNode.instructions.getFirst();
        methodNode.instructions.insertBefore(needle, injection);
        return true;
    }

    @Override
    protected boolean transform(final ClassNode classNode) {
        var wasChanged = false;
        for (final var methodNode : classNode.methods) {
            switch (methodNode.name) {
                case "runBackend$lambda$0$runAfterLowerings" -> wasChanged |= transformRunAfterLowerings(methodNode);
            }
        }
        return wasChanged;
    }
}
