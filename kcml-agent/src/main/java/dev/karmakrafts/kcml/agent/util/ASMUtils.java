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
     * Stack: [instance|Class, args...] -> [result]
     *
     * @param name       The name of the function.
     * @param returnType The return type of the function.
     * @param paramTypes The parameter types of the function.
     * @return A list of all instructions to reflectively invoke the given function.
     */
    public static InsnList reflectiveCall(final MethodNode caller,
                                          final boolean isStatic,
                                          final String name,
                                          final Type returnType,
                                          final Type... paramTypes) {
        final var instructions = new InsnList();

        // {instance|Class, args...}
        // Save call arguments (reduce args, leave only instance/Class on top of stack)
        instructions.add(new LdcInsnNode(paramTypes.length)); // Number of arguments
        instructions.add(new TypeInsnNode(Opcodes.ANEWARRAY, ASMTypes.OBJECT.getInternalName()));
        // {instance|Class, args..., Object[]}
        final var callArgsIndex = caller.maxLocals++;
        instructions.add(new VarInsnNode(Opcodes.ASTORE, callArgsIndex));
        // {instance|Class, args...}
        for (var i = 0; i < paramTypes.length; i++) {
            instructions.add(new VarInsnNode(Opcodes.ALOAD, callArgsIndex));
            instructions.add(new InsnNode(Opcodes.SWAP)); // current arg, array ref -> array ref, current arg
            instructions.add(new LdcInsnNode(i));
            instructions.add(new InsnNode(Opcodes.SWAP)); // array ref, current arg, index -> array ref, index, current arg
            instructions.add(new InsnNode(Opcodes.AASTORE));
        }
        // {instance|Class}

        // Retrieve instance class and save both instance and class if the call is not static;
        // Otherwise the Class is already on top of the stack now
        var instanceIndex = -1;
        if (!isStatic) {
            instructions.add(new InsnNode(Opcodes.DUP));
            // {instance, instance}
            instanceIndex = caller.maxLocals++;
            instructions.add(new VarInsnNode(Opcodes.ASTORE, instanceIndex));
            // {instance}
            instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                ASMTypes.OBJECT.getInternalName(),
                "getClass",
                Type.getMethodDescriptor(ASMTypes.CLASS)));
            // {Class}
        }
        // {Class}

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
            Type.getMethodDescriptor(ASMTypes.METHOD, ASMTypes.STRING, ASMTypes.CLASS_ARRAY)));
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
        if (instanceIndex != -1) {
            instructions.add(new VarInsnNode(Opcodes.ALOAD, instanceIndex));
        }
        else {
            instructions.add(new InsnNode(Opcodes.ACONST_NULL)); // Static methods don't require an instance
        }
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

    public static InsnList getClassIndirect(final Type type) {
        final var instructions = new InsnList();
        instructions.add(new LdcInsnNode(type.getClassName()));
        instructions.add(new MethodInsnNode( // @formatter:off
            Opcodes.INVOKESTATIC,
            ASMTypes.CLASS.getInternalName(),
            "forName",
            Type.getMethodDescriptor(ASMTypes.CLASS, ASMTypes.STRING)
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
            ASMTypes.OBJECT.getInternalName(),
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

    public static Predicate<AbstractInsnNode> firstLocalStore(final int index) {
        return insn -> insn instanceof VarInsnNode varInsn && varInsn.var == index;
    }

    public static Predicate<AbstractInsnNode> firstLocalStore(final MethodNode method, final String name) {
        return firstLocalStore(findLocal(method, name));
    }
}
