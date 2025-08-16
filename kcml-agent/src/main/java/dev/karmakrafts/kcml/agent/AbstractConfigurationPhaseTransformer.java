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
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

final class AbstractConfigurationPhaseTransformer extends AbstractClassTransformer {
    @Override
    protected boolean shouldTransform(final String className) {
        return className.equals("org/jetbrains/kotlin/cli/pipeline/AbstractConfigurationPhase");
    }

    private void transformLoadCompilerPlugins(final ClassNode classNode) { // @formatter:off
        final var method = classNode.methods.stream()
            .filter(m -> m.name.equals("loadCompilerPlugins"))
            .findFirst()
            .orElseThrow();
        final var instructions = method.instructions;

        final var descriptor = Type.getMethodDescriptor(
            Type.VOID_TYPE,
            ASMTypes.ARGUMENTS_PIPELINE_ARTIFACT,
            ASMTypes.COMPILER_CONFIGURATION);
        final var injection = new InsnList();
        injection.add(new VarInsnNode(Opcodes.ALOAD, 2)); // Load ArgumentsPipelineArtifact
        injection.add(new VarInsnNode(Opcodes.ALOAD, 3)); // Load CompilerConfiguration
        injection.add(new MethodInsnNode(Opcodes.INVOKESTATIC, ASMTypes.COMPILER_HOOKS.getInternalName(), "onLoadCompilerPlugins", descriptor));

        final var needle = instructions.getFirst();
        instructions.insertBefore(needle, injection);
    } // @formatter:on

    @Override
    protected void transform(final ClassNode classNode) {
        transformLoadCompilerPlugins(classNode);
    }
}
