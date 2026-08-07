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

package dev.karmakrafts.kcml.agent.asm;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;

public final class BlockBuilder {
    private final HashMap<String, LabelNode> labels = new HashMap<>();
    private final InsnList instructions;
    private final HashMap<String, Integer> newLocalIndices = new HashMap<>();
    private final HashSet<String> endedLocals = new HashSet<>();
    private MethodNode context;

    private BlockBuilder(final InsnList instructions) {
        this.instructions = instructions;
    }

    public static BlockBuilder create() {
        return new BlockBuilder(new InsnList());
    }

    public static BlockBuilder from(final InsnList instructions) {
        return new BlockBuilder(instructions);
    }

    public BlockBuilder withContext(final @Nullable MethodNode context) {
        this.context = context;
        return this;
    }

    public LabelNode getOrCreateLabel(final String name) {
        return labels.computeIfAbsent(name, k -> new LabelNode());
    }

    public void beginLocal(final String name) {
        label(String.format("%s_begin", name));
    }

    public void endLocal(final String name) {
        label(String.format("%s_end", name));
        endedLocals.add(name);
    }

    public int getOrCreateLocalIndex(final String name, final Type type) {
        var index = ASMUtils.findLocal(context, name);
        if (index == -1) {
            index = newLocalIndices.computeIfAbsent(name, n -> {
                final var newIndex = context.maxLocals++;
                final var desc = type.getDescriptor();
                final var start = Objects.requireNonNull(getOrCreateLabel(String.format("%s_begin", name)));
                final var end = Objects.requireNonNull(getOrCreateLabel(String.format("%s_end", name)));
                context.localVariables.add(new LocalVariableNode(name, desc, desc, start, end, newIndex));
                // First time a generated local is synthesized we open its scope
                beginLocal(name);
                return newIndex;
            });
        }
        return index;
    }

    private int getLocalIndex(final String name) {
        final var newLocalIndex = newLocalIndices.get(name);
        if (newLocalIndex != null) {
            return newLocalIndex;
        }
        return ASMUtils.findLocal(Objects.requireNonNull(context), name);
    }

    public void label(final String name) {
        final var label = getOrCreateLabel(name);
        if (instructions.contains(label)) {
            throw new IllegalStateException(String.format("Label '%s' has already been inserted", name));
        }
        instructions.add(label);
    }

    public void jump(final int op, final String label) {
        instructions.add(new JumpInsnNode(op, getOrCreateLabel(label)));
    }

    public void ifnull(final String label) {
        jump(Opcodes.IFNULL, label);
    }

    public void ifnonnull(final String label) {
        jump(Opcodes.IFNONNULL, label);
    }

    public void ifeq(final String label) {
        jump(Opcodes.IFEQ, label);
    }

    public void ifne(final String label) {
        jump(Opcodes.IFNE, label);
    }

    public void iload(final int index) {
        instructions.add(new VarInsnNode(Opcodes.ILOAD, index));
    }

    public void iload(final String name) {
        instructions.add(new VarInsnNode(Opcodes.ILOAD, getLocalIndex(name)));
    }

    public void lload(final int index) {
        instructions.add(new VarInsnNode(Opcodes.LLOAD, index));
    }

    public void lload(final String name) {
        instructions.add(new VarInsnNode(Opcodes.LLOAD, getLocalIndex(name)));
    }

    public void fload(final int index) {
        instructions.add(new VarInsnNode(Opcodes.FLOAD, index));
    }

    public void fload(final String name) {
        instructions.add(new VarInsnNode(Opcodes.FLOAD, getLocalIndex(name)));
    }

    public void dload(final int index) {
        instructions.add(new VarInsnNode(Opcodes.DLOAD, index));
    }

    public void dload(final String name) {
        instructions.add(new VarInsnNode(Opcodes.DLOAD, getLocalIndex(name)));
    }

    public void aload(final int index) {
        instructions.add(new VarInsnNode(Opcodes.ALOAD, index));
    }

    public void aload(final String name) {
        instructions.add(new VarInsnNode(Opcodes.ALOAD, getLocalIndex(name)));
    }

    public void astore(final int index) {
        instructions.add(new VarInsnNode(Opcodes.ASTORE, index));
    }

    public void astore(final String name, final Type type) {
        instructions.add(new VarInsnNode(Opcodes.ASTORE, getOrCreateLocalIndex(name, type)));
    }

    public void load(final Type type, final int index) {
        instructions.add(new VarInsnNode(ASMUtils.getLoadOpByType(type), index));
    }

    public void load(final Type type, final String name) {
        instructions.add(ASMUtils.loadLocal(Objects.requireNonNull(context), type, name));
    }

    public void loadThis() {
        aload(0);
    }

    public void instruction(final int op) {
        instructions.add(new InsnNode(op));
    }

    public void dup() {
        instruction(Opcodes.DUP);
    }

    public void dupx1() {
        instruction(Opcodes.DUP_X1);
    }

    public void dupx2() {
        instruction(Opcodes.DUP_X2);
    }

    public void dup2() {
        instruction(Opcodes.DUP2);
    }

    public void pop() {
        instruction(Opcodes.POP);
    }

    public void pop2() {
        instruction(Opcodes.POP2);
    }

    public void checkcast(final Type type) {
        instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, type.getInternalName()));
    }

    public void instanceOf(final Type type) {
        instructions.add(new TypeInsnNode(Opcodes.INSTANCEOF, type.getInternalName()));
    }

    public void anewarray(final Type type) {
        instructions.add(new TypeInsnNode(Opcodes.ANEWARRAY, type.getInternalName()));
    }

    public void aastore() {
        instruction(Opcodes.AASTORE);
    }

    public void swap() {
        instruction(Opcodes.SWAP);
    }

    public void ldc(final Object value) {
        switch (value) {
            case null -> instructions.add(new InsnNode(Opcodes.ACONST_NULL));
            case Boolean boolValue -> instructions.add(new LdcInsnNode(boolValue ? 1 : 0));
            case Byte byteValue -> instructions.add(new IntInsnNode(Opcodes.BIPUSH, byteValue));
            case Short shortValue -> instructions.add(new IntInsnNode(Opcodes.SIPUSH, shortValue));
            default -> instructions.add(new LdcInsnNode(value));
        }
    }

    public void invoke(final int op,
                       final Type owner,
                       final boolean isInterface,
                       final String name,
                       final Type returnType,
                       final Type... paramTypes) {
        final var descriptor = Type.getMethodDescriptor(returnType, paramTypes);
        instructions.add(new MethodInsnNode(op, owner.getInternalName(), name, descriptor, isInterface));
    }

    public void invokespecial(final Type owner, final String name, final Type returnType, final Type... paramTypes) {
        invoke(Opcodes.INVOKESPECIAL, owner, false, name, returnType, paramTypes);
    }

    public void invokevirtual(final Type owner, final String name, final Type returnType, final Type... paramTypes) {
        invoke(Opcodes.INVOKEVIRTUAL, owner, false, name, returnType, paramTypes);
    }

    public void invokestatic(final Type owner,
                             final boolean isInterface,
                             final String name,
                             final Type returnType,
                             final Type... paramTypes) {
        invoke(Opcodes.INVOKESTATIC, owner, isInterface, name, returnType, paramTypes);
    }

    public void invokeinterface(final Type owner, final String name, final Type returnType, final Type... paramTypes) {
        invoke(Opcodes.INVOKEINTERFACE, owner, true, name, returnType, paramTypes);
    }

    public void vreturn() {
        instructions.add(new InsnNode(Opcodes.RETURN));
    }

    public void ireturn() {
        instructions.add(new InsnNode(Opcodes.IRETURN));
    }

    public void lreturn() {
        instructions.add(new InsnNode(Opcodes.LRETURN));
    }

    public void freturn() {
        instructions.add(new InsnNode(Opcodes.FRETURN));
    }

    public void dreturn() {
        instructions.add(new InsnNode(Opcodes.DRETURN));
    }

    public void areturn() {
        instructions.add(new InsnNode(Opcodes.ARETURN));
    }

    public void getstatic(final Type owner, final String name, final Type type) {
        instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, owner.getInternalName(), name, type.getDescriptor()));
    }

    public InsnList build() {
        for (final var local : newLocalIndices.keySet()) {
            if (endedLocals.contains(local)) {
                continue;
            }
            endLocal(local); // Automatically end any locals that are still asymmetric
        }
        return instructions;
    }
}
