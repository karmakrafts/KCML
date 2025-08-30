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

import java.util.Collections;
import java.util.List;

/**
 * Patch for <a href="https://youtrack.jetbrains.com/issue/KT-58886" target="_blank">KT-58886</a>.
 */
final class KT58886Transformer extends AbstractClassTransformer {
    @Override
    protected boolean shouldTransform(final String className) {
        return className.equals("org/jetbrains/kotlin/fir/pipeline/Fir2KlibMetadataSerializer");
    }

    private void buildPackageDirective(final InsnList injection) {
        injection.add(ASMUtils.instantiate(ASMTypes.FIR_PACKAGE_DIRECTIVE_BUILDER, Collections.emptyList(), args -> {
        }));
        injection.add(new InsnNode(Opcodes.DUP));
        injection.add(new FieldInsnNode(Opcodes.GETSTATIC,
            ASMTypes.FQ_NAME.getInternalName(),
            "ROOT",
            ASMTypes.FQ_NAME.getDescriptor()));
        injection.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
            ASMTypes.FIR_PACKAGE_DIRECTIVE_BUILDER.getInternalName(),
            "setPackageFqName",
            Type.getMethodDescriptor(Type.VOID_TYPE, ASMTypes.FQ_NAME)));
        injection.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
            ASMTypes.FIR_PACKAGE_DIRECTIVE_BUILDER.getInternalName(),
            "build",
            Type.getMethodDescriptor(ASMTypes.FIR_PACKAGE_DIRECTIVE)));
    }

    private void buildKtSourceFile(final InsnList injection, final int fileNameIndex) {
        injection.add(ASMUtils.instantiate(ASMTypes.KT_IN_MEMORY_SOURCE_FILE,
            List.of(ASMTypes.STRING, ASMTypes.STRING, ASMTypes.CHAR_SEQUENCE),
            args -> {
                args.add(new VarInsnNode(Opcodes.ALOAD, fileNameIndex));
                args.add(new InsnNode(Opcodes.DUP));
                args.add(new LdcInsnNode("")); // Empty string for content
            }));
    }

    private void buildFile(final InsnList injection,
                           final MethodNode method,
                           final int sessionIndex,
                           final int fileEntryIndex) {
        injection.add(ASMUtils.instantiate(ASMTypes.FIR_FILE_BUILDER, Collections.emptyList(), args -> {
        }));
        //  > Set the FirModuleData
        injection.add(new InsnNode(Opcodes.DUP));
        injection.add(new VarInsnNode(Opcodes.ALOAD, sessionIndex));
        injection.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
            ASMTypes.FIR_MODULE_DATA_KT.getInternalName(),
            "getModuleData",
            Type.getMethodDescriptor(ASMTypes.FIR_MODULE_DATA, ASMTypes.FIR_SESSION)));
        injection.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
            ASMTypes.FIR_FILE_BUILDER.getInternalName(),
            "setModuleData",
            Type.getMethodDescriptor(Type.VOID_TYPE, ASMTypes.FIR_MODULE_DATA)));
        //  > Set the FirDeclarationOrigin
        injection.add(new InsnNode(Opcodes.DUP));
        injection.add(ASMUtils.loadObjectClass(ASMTypes.FIR_SYNTHETIC_PLUGIN_FILE));
        injection.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
            ASMTypes.FIR_FILE_BUILDER.getInternalName(),
            "setOrigin",
            Type.getMethodDescriptor(Type.VOID_TYPE, ASMTypes.FIR_DECLARATION_ORIGIN)));
        //  > Set the PackageDirective
        injection.add(new InsnNode(Opcodes.DUP));
        buildPackageDirective(injection);
        injection.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
            ASMTypes.FIR_FILE_BUILDER.getInternalName(),
            "setPackageDirective",
            Type.getMethodDescriptor(Type.VOID_TYPE, ASMTypes.FIR_PACKAGE_DIRECTIVE)));
        //  > Set the Name
        injection.add(new InsnNode(Opcodes.DUP));
        injection.add(new VarInsnNode(Opcodes.ALOAD, fileEntryIndex));
        injection.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,
            ASMTypes.IR_FILE_ENTRY.getInternalName(),
            "getName",
            Type.getMethodDescriptor(ASMTypes.STRING)));
        injection.add(new InsnNode(Opcodes.DUP));
        final var fileNameIndex = method.maxLocals++;
        injection.add(new VarInsnNode(Opcodes.ASTORE, fileNameIndex));
        injection.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
            ASMTypes.FIR_FILE_BUILDER.getInternalName(),
            "setName",
            Type.getMethodDescriptor(Type.VOID_TYPE, ASMTypes.STRING)));
        //  > Set the KtSourceFile
        injection.add(new InsnNode(Opcodes.DUP));
        buildKtSourceFile(injection, fileNameIndex);
        injection.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
            ASMTypes.FIR_FILE_BUILDER.getInternalName(),
            "setSourceFile",
            Type.getMethodDescriptor(Type.VOID_TYPE, ASMTypes.KT_SOURCE_FILE)));
        //  > Build the actual FirFile
        injection.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
            ASMTypes.FIR_FILE_BUILDER.getInternalName(),
            "build",
            Type.getMethodDescriptor(ASMTypes.FIR_FILE))); // Build the FirFile instance (key)
    }

    private void loadFilesIterator(final InsnList injection, final MethodNode method) {
        injection.add(new VarInsnNode(Opcodes.ALOAD, ASMUtils.findLocal(method, "fir2IrActualizedResult")));
        injection.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
            ASMTypes.FIR_2_IR_ACTUALIZED_RESULT.getInternalName(),
            "getIrModuleFragment",
            Type.getMethodDescriptor(ASMTypes.IR_MODULE_FRAGMENT))); // Load irModuleFragment through generated getter
        injection.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
            ASMTypes.IR_MODULE_FRAGMENT.getInternalName(),
            "getFiles",
            Type.getMethodDescriptor(ASMTypes.LIST))); // Load files through generated getter
        injection.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,
            ASMTypes.LIST.getInternalName(),
            "iterator",
            Type.getMethodDescriptor(ASMTypes.ITERATOR))); // Get iterator from files list
    }

    private void attachFileMetadata(final InsnList injection, final MethodNode method, final int fileIndex) {
        injection.add(new InsnNode(Opcodes.DUP)); // Duplicate ref to FirFile
        final var firFileIndex = method.maxLocals++;
        injection.add(new VarInsnNode(Opcodes.ASTORE, firFileIndex));
        injection.add(ASMUtils.instantiate(ASMTypes.FIR_FILE_METADATA_SOURCE,
            List.of(ASMTypes.FIR_FILE),
            args -> args.add(new VarInsnNode(Opcodes.ALOAD, firFileIndex))));
        injection.add(new VarInsnNode(Opcodes.ALOAD, fileIndex));
        injection.add(new InsnNode(Opcodes.SWAP)); // MetadataSource, IrFile -> IrFile, MetadataSource
        injection.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
            ASMTypes.IR_FILE.getInternalName(),
            "setMetadata",
            Type.getMethodDescriptor(Type.VOID_TYPE, ASMTypes.METADATA_SOURCE)));
    }

    @Override
    protected void transform(final ClassNode classNode) {
        // @formatter:off
        final var method = classNode.methods.stream()
            .filter(m -> m.name.equals("<init>"))
            .findFirst()
            .orElseThrow();
        // @formatter:on

        final var instructions = method.instructions;
        // @formatter:off
        final var needle = ASMUtils.stream(instructions)
            .filter(ASMUtils.firstLocalStore(15)) // First copy of this-ref
            .findFirst()
            .orElseThrow();
        // @formatter:on

        final var injection = new InsnList();

        // Grab list of IrFiles from analyzed compile output
        loadFilesIterator(injection, method);
        final var iteratorIndex = method.maxLocals++;
        injection.add(new VarInsnNode(Opcodes.ASTORE, iteratorIndex));

        // Loop over files using iterator
        final var loopHead = new LabelNode();
        final var loopTail = new LabelNode();
        injection.add(loopHead);

        // Load iterator instance and check if we have more elements, if not jump to tail
        injection.add(new VarInsnNode(Opcodes.ALOAD, iteratorIndex)); // Load iterator ref
        injection.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,
            ASMTypes.ITERATOR.getInternalName(),
            "hasNext",
            Type.getMethodDescriptor(Type.BOOLEAN_TYPE))); // Determine if we have more elements to process
        injection.add(new JumpInsnNode(Opcodes.IFEQ, loopTail)); // Break out of the loop if we have no more elements

        // Load iterator instance and get element instance, checkcast to IrFile
        injection.add(new VarInsnNode(Opcodes.ALOAD, iteratorIndex)); // Load iterator ref
        injection.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,
            ASMTypes.ITERATOR.getInternalName(),
            "next",
            Type.getMethodDescriptor(ASMTypes.OBJECT))); // Get next element from iterator, mind type erasure
        injection.add(new TypeInsnNode(Opcodes.CHECKCAST, ASMTypes.IR_FILE.getInternalName()));
        injection.add(new InsnNode(Opcodes.DUP));
        final var fileIndex = method.maxLocals++;
        injection.add(new VarInsnNode(Opcodes.ASTORE, fileIndex));

        // Get the IrFileEntry of the current IrFile & check if it is a SyntheticIrFileEntry, jump to head if it's not
        injection.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
            ASMTypes.IR_FILE.getInternalName(),
            "getFileEntry",
            Type.getMethodDescriptor(ASMTypes.IR_FILE_ENTRY))); // Get IrFileEntry so we can check its type
        injection.add(new InsnNode(Opcodes.DUP));
        final var fileEntryIndex = method.maxLocals++;
        injection.add(new VarInsnNode(Opcodes.ASTORE, fileEntryIndex));
        injection.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
            ASMTypes.OBJECT.getInternalName(),
            "getClass",
            Type.getMethodDescriptor(ASMTypes.CLASS)));
        injection.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
            ASMTypes.CLASS.getInternalName(),
            "getName",
            Type.getMethodDescriptor(ASMTypes.STRING)));
        injection.add(new LdcInsnNode(ASMTypes.SYNTHETIC_IR_FILE_ENTRY.getInternalName().replace('/', '.')));
        injection.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
            ASMTypes.OBJECT.getInternalName(),
            "equals",
            Type.getMethodDescriptor(Type.BOOLEAN_TYPE, ASMTypes.OBJECT)));
        injection.add(new JumpInsnNode(Opcodes.IFEQ, loopHead)); // Continue if this entry is not a SyntheticIrFileEntry

        // Get associated compiler output and session
        injection.add(new VarInsnNode(Opcodes.ALOAD, ASMUtils.findLocal(method, "firOutputs")));
        injection.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,
            ASMTypes.LIST.getInternalName(),
            "getFirst",
            Type.getMethodDescriptor(ASMTypes.OBJECT))); // Get first compiler output for stub
        injection.add(new TypeInsnNode(Opcodes.CHECKCAST, ASMTypes.MODULE_COMPILER_ANALYZED_OUTPUT.getInternalName()));
        injection.add(new InsnNode(Opcodes.DUP)); // Duplicate reference to module compiler output for both gets
        final var compilerOutputIndex = method.maxLocals++;
        injection.add(new VarInsnNode(Opcodes.ASTORE, compilerOutputIndex));
        injection.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
            ASMTypes.MODULE_COMPILER_ANALYZED_OUTPUT.getInternalName(),
            "getSession",
            Type.getMethodDescriptor(ASMTypes.FIR_SESSION))); // Get the session
        final var sessionIndex = method.maxLocals++;
        injection.add(new VarInsnNode(Opcodes.ASTORE, sessionIndex));

        // Load scope receiver
        injection.add(new VarInsnNode(Opcodes.ALOAD,
            ASMUtils.findLocal(method, "$this$firFilesAndSessions_u24lambda_u240")));

        // Create fake FirFile using a default FirFileBuilder
        buildFile(injection, method, sessionIndex, fileEntryIndex);
        attachFileMetadata(injection, method, fileIndex);

        // Create the value pair for the map entry
        injection.add(new VarInsnNode(Opcodes.ALOAD, compilerOutputIndex));
        injection.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
            ASMTypes.MODULE_COMPILER_ANALYZED_OUTPUT.getInternalName(),
            "getScopeSession",
            Type.getMethodDescriptor(ASMTypes.SCOPE_SESSION))); // Get scope session
        final var scopeSessionIndex = method.maxLocals++;
        injection.add(new VarInsnNode(Opcodes.ASTORE, scopeSessionIndex));
        injection.add(ASMUtils.instantiate(ASMTypes.PAIR, List.of(ASMTypes.OBJECT, ASMTypes.OBJECT), args -> {
            args.add(new VarInsnNode(Opcodes.ALOAD, sessionIndex));
            args.add(new VarInsnNode(Opcodes.ALOAD, scopeSessionIndex));
        }));

        // Add the map entry
        injection.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,
            ASMTypes.MAP.getInternalName(),
            "put",
            Type.getMethodDescriptor(ASMTypes.OBJECT, ASMTypes.OBJECT, ASMTypes.OBJECT)));
        injection.add(new InsnNode(Opcodes.POP)); // Discard the ref pushed by the last call

        injection.add(new JumpInsnNode(Opcodes.GOTO, loopHead)); // Jump back to loop head for another iteration
        injection.add(loopTail);

        instructions.insertBefore(needle, injection);
    }
}
