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

package dev.karmakrafts.kcml.agent.util;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.lang.reflect.Modifier;
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

    /**
     * Reflectively calls a function with the given signature.
     * Stack: [instance, args...] -> [result]
     *
     * @param name       The name of the function.
     * @param returnType The return type of the function.
     * @param paramTypes The parameter types of the function.
     * @return A list of all instructions to reflectively invoke the given function.
     */
    public static InsnList reflectiveCall(final MethodNode caller,
                                          final String name,
                                          final Type returnType,
                                          final Type... paramTypes) {
        final var instructions = new InsnList();

        // Save call arguments (reduce args, leave only instance on top of stack)
        instructions.add(new LdcInsnNode(paramTypes.length)); // Number of arguments
        instructions.add(new TypeInsnNode(Opcodes.ANEWARRAY, ASMTypes.OBJECT.getInternalName()));
        final var callArgsIndex = caller.maxLocals++;
        instructions.add(new VarInsnNode(Opcodes.ASTORE, callArgsIndex));
        for (var i = 0; i < paramTypes.length; i++) {
            instructions.add(new VarInsnNode(Opcodes.ALOAD, callArgsIndex));
            instructions.add(new InsnNode(Opcodes.SWAP)); // current arg, array ref -> array ref, current arg
            instructions.add(new LdcInsnNode(i));
            instructions.add(new InsnNode(Opcodes.SWAP)); // array ref, current arg, index -> array ref, index, current arg
            instructions.add(new InsnNode(Opcodes.AASTORE));
        }

        // Retrieve instance class and save both instance and class
        instructions.add(new InsnNode(Opcodes.DUP));
        final var instanceIndex = caller.maxLocals++;
        instructions.add(new VarInsnNode(Opcodes.ASTORE, instanceIndex));
        instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
            ASMTypes.OBJECT.getInternalName(),
            "getClass",
            Type.getMethodDescriptor(ASMTypes.CLASS)));

        // Push method name & create parameter type array
        instructions.add(new LdcInsnNode(name));
        final var paramCount = paramTypes.length;
        instructions.add(new LdcInsnNode(paramCount)); // Number of parameter types
        instructions.add(new TypeInsnNode(Opcodes.ANEWARRAY, ASMTypes.CLASS.getInternalName()));
        for (var i = 0; i < paramCount; i++) {
            instructions.add(new InsnNode(Opcodes.DUP));
            instructions.add(new LdcInsnNode(i));
            instructions.add(new LdcInsnNode(paramTypes[i]));
            instructions.add(new InsnNode(Opcodes.AASTORE));
        }
        // Get the actual method
        instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
            ASMTypes.CLASS.getInternalName(),
            "getDeclaredMethod",
            Type.getMethodDescriptor(ASMTypes.METHOD, ASMTypes.CLASS_ARRAY)));
        final var methodIndex = caller.maxLocals++;
        instructions.add(new VarInsnNode(Opcodes.ASTORE, methodIndex));

        // Make function accessible if required
        final var accessChangedIndex = caller.maxLocals++;
        instructions.add(new LdcInsnNode(0));
        instructions.add(new VarInsnNode(Opcodes.ISTORE, accessChangedIndex));
        instructions.add(new VarInsnNode(Opcodes.ALOAD, methodIndex));
        instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
            ASMTypes.METHOD.getInternalName(),
            "getModifiers",
            Type.getMethodDescriptor(Type.INT_TYPE)));
        instructions.add(new LdcInsnNode(Modifier.PUBLIC));
        instructions.add(new InsnNode(Opcodes.IAND));
        final var alreadyAccessibleLabel = new LabelNode();
        instructions.add(new JumpInsnNode(Opcodes.IFNE, alreadyAccessibleLabel));

        // We know this method is not public, so we make it accessible
        instructions.add(new VarInsnNode(Opcodes.ALOAD, methodIndex));
        instructions.add(new LdcInsnNode(1));
        instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
            ASMTypes.METHOD.getInternalName(),
            "setAccessible",
            Type.getMethodDescriptor(Type.VOID_TYPE, Type.BOOLEAN_TYPE)));
        instructions.add(new LdcInsnNode(1));
        instructions.add(new VarInsnNode(Opcodes.ISTORE, accessChangedIndex));
        instructions.add(alreadyAccessibleLabel);

        // The method is accessible from here on
        instructions.add(new VarInsnNode(Opcodes.ALOAD, methodIndex));
        instructions.add(new VarInsnNode(Opcodes.ALOAD, instanceIndex));
        instructions.add(new VarInsnNode(Opcodes.ALOAD, callArgsIndex));
        instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
            ASMTypes.METHOD.getInternalName(),
            "invoke",
            Type.getMethodDescriptor(ASMTypes.OBJECT, ASMTypes.OBJECT, ASMTypes.OBJECT_ARRAY)));
        final var resultIndex = caller.maxLocals++;
        instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, returnType.getInternalName()));
        instructions.add(new VarInsnNode(Opcodes.ASTORE, resultIndex));

        // Reset caller access if it was changed
        instructions.add(new VarInsnNode(Opcodes.ILOAD, accessChangedIndex));
        final var endLabel = new LabelNode();
        instructions.add(new JumpInsnNode(Opcodes.IFEQ, endLabel));
        instructions.add(new VarInsnNode(Opcodes.ALOAD, methodIndex));
        instructions.add(new LdcInsnNode(0));
        instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
            ASMTypes.METHOD.getInternalName(),
            "setAccessible",
            Type.getMethodDescriptor(Type.VOID_TYPE, Type.BOOLEAN_TYPE)));
        instructions.add(endLabel);

        // Load result onto the stack
        instructions.add(new VarInsnNode(Opcodes.ALOAD, resultIndex));
        return instructions;
    }

    /**
     * Stack: [instance] -> [int]
     *
     * @param type The type to check the topmost reference against.
     * @return A list of all instructions to reflectively check the instance type.
     */
    public static InsnList reflectiveInstanceof(final Type type) {
        final var instructions = new InsnList();
        instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
            ASMTypes.OBJECT.getInternalName(),
            "getClass",
            Type.getMethodDescriptor(ASMTypes.CLASS)));
        instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
            ASMTypes.CLASS.getInternalName(),
            "getName",
            Type.getMethodDescriptor(ASMTypes.STRING)));
        instructions.add(new LdcInsnNode(type.getInternalName().replace('/', '.')));
        instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
            ASMTypes.STRING.getInternalName(),
            "equals",
            Type.getMethodDescriptor(Type.BOOLEAN_TYPE, ASMTypes.OBJECT)));
        return instructions;
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

    public static Predicate<AbstractInsnNode> firstLocalStore(final int index) {
        return insn -> insn instanceof VarInsnNode varInsn && varInsn.var == index;
    }

    public static Predicate<AbstractInsnNode> firstLocalStore(final MethodNode method, final String name) {
        return firstLocalStore(findLocal(method, name));
    }
}
