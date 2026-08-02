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

import dev.karmakrafts.kcml.agent.asm.Types.KCML;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.HashMap;
import java.util.Objects;

public final class BlockBuilder {
    private final HashMap<String, LabelNode> labels = new HashMap<>();
    private final InsnList instructions;
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

    public void label(final String name) {
        instructions.add(getOrCreateLabel(name));
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
        instructions.add(new VarInsnNode(Opcodes.ILOAD, ASMUtils.findLocal(Objects.requireNonNull(context), name)));
    }

    public void lload(final int index) {
        instructions.add(new VarInsnNode(Opcodes.LLOAD, index));
    }

    public void lload(final String name) {
        instructions.add(new VarInsnNode(Opcodes.LLOAD, ASMUtils.findLocal(Objects.requireNonNull(context), name)));
    }

    public void fload(final int index) {
        instructions.add(new VarInsnNode(Opcodes.FLOAD, index));
    }

    public void fload(final String name) {
        instructions.add(new VarInsnNode(Opcodes.FLOAD, ASMUtils.findLocal(Objects.requireNonNull(context), name)));
    }

    public void dload(final int index) {
        instructions.add(new VarInsnNode(Opcodes.DLOAD, index));
    }

    public void dload(final String name) {
        instructions.add(new VarInsnNode(Opcodes.DLOAD, ASMUtils.findLocal(Objects.requireNonNull(context), name)));
    }

    public void aload(final int index) {
        instructions.add(new VarInsnNode(Opcodes.ALOAD, index));
    }

    public void aload(final String name) {
        instructions.add(new VarInsnNode(Opcodes.ALOAD, ASMUtils.findLocal(Objects.requireNonNull(context), name)));
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

    public void dup2() {
        instruction(Opcodes.DUP2);
    }

    public void pop() {
        instruction(Opcodes.POP);
    }

    public void pop2() {
        instruction(Opcodes.POP2);
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

    public void invokestatic(final Type owner, final String name, final Type returnType, final Type... paramTypes) {
        invoke(Opcodes.INVOKESTATIC, owner, false, name, returnType, paramTypes);
    }

    public void invokeinterface(final Type owner, final String name, final Type returnType, final Type... paramTypes) {
        invoke(Opcodes.INVOKEINTERFACE, owner, true, name, returnType, paramTypes);
    }

    public void invokehook(final String name, final Type returnType, final Type... paramTypes) {
        invokestatic(KCML.KCML_HOOKS, name, returnType, paramTypes);
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

    public InsnList getInstructions() {
        return instructions;
    }
}
