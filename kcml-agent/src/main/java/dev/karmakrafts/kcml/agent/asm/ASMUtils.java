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

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class ASMUtils {
    public static Stream<AbstractInsnNode> stream(final InsnList list) {
        return StreamSupport.stream(Spliterators.spliterator(list.iterator(), list.size(), 0), false);
    }

    public static int findLocal(final MethodNode method, final String name) {
        final var locals = method.localVariables;
        if (locals == null) {
            return -1;
        }
        for (final var local : locals) {
            if (!local.name.equals(name)) {
                continue;
            }
            return local.index;
        }
        return -1;
    }

    public static VarInsnNode loadLocal(final MethodNode method, final Type type, final String name) {
        final var index = findLocal(method, name);
        if (index == -1) {
            throw new IllegalStateException(String.format("Could not find local index for local named %s", name));
        }
        return new VarInsnNode(getLoadOpByType(type), index);
    }

    public static VarInsnNode storeLocal(final MethodNode method, final Type type, final String name) {
        final var index = findLocal(method, name);
        if (index == -1) {
            throw new IllegalStateException(String.format("Could not find local index for local named %s", name));
        }
        return new VarInsnNode(getStoreOpByType(type), index);
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
}
