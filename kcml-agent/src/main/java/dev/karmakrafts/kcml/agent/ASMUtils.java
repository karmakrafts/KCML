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

import java.util.List;
import java.util.Objects;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

final class ASMUtils {
    public static Stream<AbstractInsnNode> stream(final InsnList list) {
        return StreamSupport.stream(Spliterators.spliterator(list.iterator(), list.size(), 0), false);
    }

    public static FieldInsnNode loadObjectClass(final Type type) {
        return new FieldInsnNode(Opcodes.GETSTATIC, type.getInternalName(), "INSTANCE", type.getDescriptor());
    }

    public static InsnList instantiate(final Type type,
                                       final List<Type> constructorParams,
                                       final Consumer<InsnList> constructorCallback) {
        final var insn = new InsnList();
        insn.add(new TypeInsnNode(Opcodes.NEW, type.getInternalName()));
        insn.add(new InsnNode(Opcodes.DUP));
        constructorCallback.accept(insn);
        insn.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
            type.getInternalName(),
            "<init>",
            Type.getMethodDescriptor(Type.VOID_TYPE, constructorParams.toArray(Type[]::new))));
        return insn;
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
