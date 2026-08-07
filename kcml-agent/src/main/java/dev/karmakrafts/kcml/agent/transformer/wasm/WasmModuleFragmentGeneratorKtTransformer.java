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
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public final class WasmModuleFragmentGeneratorKtTransformer extends AbstractClassTransformer {
    public WasmModuleFragmentGeneratorKtTransformer(final Logger logger) {
        super(logger);
    }

    @Override
    protected boolean shouldTransform(final String className) {
        return className.equals(WASM.WASM_MODULE_FRAGMENT_GENERATOR_KT.getInternalName());
    }

    private boolean transformCompileIrFile(final MethodNode methodNode) { // @formatter:off
        return ASMUtils.stream(methodNode.instructions)
            .filter(insn -> insn.getOpcode() == Opcodes.NEW) // First new at the beginning of L1
            .findFirst()
            .map(needle -> {
                logger.info("Transforming %s%s (onCompileIrFiles)", methodNode.name, methodNode.desc);
                final var builder = BlockBuilder.create().withContext(methodNode);
                builder.aload("irFile");
                builder.aload("backendContext");
                builder.aload("typeContext");
                builder.aload("declarationContext");
                builder.aload("linkerDataContext");
                builder.invokestatic(KCML.WASM_HOOKS,
                    false,
                    "onCompileIrFiles",
                    Type.VOID_TYPE,
                    Common.IR_FILE,
                    WASM.WASM_BACKEND_CONTEXT,
                    WASM.WASM_TYPE_CODEGEN_CONTEXT,
                    WASM.WASM_DECLARATION_CODEGEN_CONTEXT,
                    WASM.WASM_LINKER_DATA_CODEGEN_CONTEXT);
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
                case "compileIrFile" -> wasChanged |= transformCompileIrFile(methodNode);
            }
        }
        return wasChanged;
    }
}
