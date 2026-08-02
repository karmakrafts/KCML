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
import dev.karmakrafts.kcml.agent.util.StringUtils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.List;
import java.util.Objects;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class ASMUtils {
    public static Stream<AbstractInsnNode> stream(final InsnList list) {
        return StreamSupport.stream(Spliterators.spliterator(list.iterator(), list.size(), 0), false);
    }

    public static FieldInsnNode loadObjectClass(final Type type) {
        return new FieldInsnNode(Opcodes.GETSTATIC, type.getInternalName(), "INSTANCE", type.getDescriptor());
    }

    public static InsnList getClassIndirect(final Type type) {
        final var instructions = new InsnList();
        instructions.add(new LdcInsnNode(type.getClassName()));
        instructions.add(new MethodInsnNode( // @formatter:off
            Opcodes.INVOKESTATIC,
            Types.CLASS.getInternalName(),
            "forName",
            Type.getMethodDescriptor(Types.CLASS, Types.STRING)
        )); // @formatter:on
        return instructions;
    }

    public static MethodNode createDefaultConstructor() {
        final var descriptor = Type.getMethodDescriptor(Type.VOID_TYPE);
        final var method = new MethodNode( // @formatter:off
            Opcodes.ACC_PUBLIC,
            "<init>",
            descriptor,
            descriptor,
            null
        ); // @formatter:on
        final var body = method.instructions;
        body.add(new VarInsnNode(Opcodes.ALOAD, 0)); // Load this reference
        body.add(new MethodInsnNode( // @formatter:off
            Opcodes.INVOKESPECIAL,
            Types.OBJECT.getInternalName(),
            "<init>",
            Type.getMethodDescriptor(Type.VOID_TYPE),
            false
        )); // @formatter:on
        return method;
    }

    public static InsnList instantiate(final Type type,
                                       final List<Type> constructorParams,
                                       final Consumer<InsnList> constructorCallback) {
        final var instructions = new InsnList();
        instructions.add(new TypeInsnNode(Opcodes.NEW, type.getInternalName()));
        instructions.add(new InsnNode(Opcodes.DUP));
        constructorCallback.accept(instructions);
        instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
            type.getInternalName(),
            "<init>",
            Type.getMethodDescriptor(Type.VOID_TYPE, constructorParams.toArray(Type[]::new))));
        return instructions;
    }

    public static MethodInsnNode getProperty(final Type owner,
                                             final Type type,
                                             final String name,
                                             final boolean isInterface) {
        return new MethodInsnNode( // @formatter:off
            isInterface ? Opcodes.INVOKEINTERFACE : Opcodes.INVOKEVIRTUAL,
            owner.getInternalName(),
            String.format("get%s", StringUtils.capitalize(name)),
            Type.getMethodDescriptor(type),
            isInterface
        ); // @formatter:on
    }

    public static int findLocal(final MethodNode method, final String name) {
        final var locals = Objects.requireNonNull(method.localVariables);
        for (final var local : locals) {
            if (!local.name.equals(name)) {
                continue;
            }
            return local.index;
        }
        throw new IllegalArgumentException(String.format("Could not find local '%s' in method %s", name, method.name));
    }

    public static VarInsnNode loadThis() {
        return new VarInsnNode(Opcodes.ALOAD, 0);
    }

    public static VarInsnNode loadLocal(final MethodNode method, final Type type, final String name) {
        return new VarInsnNode(getLoadOpByType(type), findLocal(method, name));
    }

    public static VarInsnNode storeLocal(final MethodNode method, final Type type, final String name) {
        return new VarInsnNode(getStoreOpByType(type), findLocal(method, name));
    }

    public static int getStoreOpByType(final Type type) {
        if (type.equals(Type.BYTE_TYPE) || type.equals(Type.SHORT_TYPE) || type.equals(Type.INT_TYPE)) {
            return Opcodes.ISTORE;
        }
        else if (type.equals(Type.LONG_TYPE)) {
            return Opcodes.LSTORE;
        }
        else if (type.equals(Type.FLOAT_TYPE)) {
            return Opcodes.FSTORE;
        }
        else if (type.equals(Type.DOUBLE_TYPE)) {
            return Opcodes.DSTORE;
        }
        return Opcodes.ASTORE;
    }

    public static int getLoadOpByType(final Type type) {
        if (type.equals(Type.BYTE_TYPE) || type.equals(Type.SHORT_TYPE) || type.equals(Type.INT_TYPE)) {
            return Opcodes.ILOAD;
        }
        else if (type.equals(Type.LONG_TYPE)) {
            return Opcodes.LLOAD;
        }
        else if (type.equals(Type.FLOAT_TYPE)) {
            return Opcodes.FLOAD;
        }
        else if (type.equals(Type.DOUBLE_TYPE)) {
            return Opcodes.DLOAD;
        }
        return Opcodes.ALOAD;
    }

    public static MethodInsnNode invokeHook(final String name, final Type returnType, final Type... paramTypes) {
        return new MethodInsnNode(Opcodes.INVOKESTATIC,
            KCML.KCML_HOOKS.getInternalName(),
            name,
            Type.getMethodDescriptor(returnType, paramTypes),
            false);
    }

    public static Predicate<AbstractInsnNode> firstLocalStore(final int index) {
        return insn -> insn instanceof VarInsnNode varInsn && varInsn.var == index;
    }

    public static Predicate<AbstractInsnNode> firstLocalStore(final MethodNode method, final String name) {
        return firstLocalStore(findLocal(method, name));
    }
}
