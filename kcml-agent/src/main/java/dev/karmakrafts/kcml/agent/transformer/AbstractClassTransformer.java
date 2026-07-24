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

package dev.karmakrafts.kcml.agent.transformer;

import dev.karmakrafts.kcml.agent.util.NonLoadingClassWriter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.ArrayList;

public abstract class AbstractClassTransformer implements ClassFileTransformer {
    protected static final ArrayList<String> BLACKLIST = new ArrayList<>();

    static {
        BLACKLIST.add("java/");
        BLACKLIST.add("jdk/");
        BLACKLIST.add("org/objectweb/");
        BLACKLIST.add("org/jetbrains/annotations/");
        BLACKLIST.add("dev/karmakrafts/kcml/");
    }

    private static boolean isClassBlacklisted(final @NotNull String className) {
        for (final var prefix : BLACKLIST) {
            if (!className.startsWith(prefix)) {
                continue;
            }
            return true;
        }
        return false;
    }

    protected abstract boolean shouldTransform(final @NotNull String className);

    protected abstract void transform(final @NotNull ClassNode classNode);

    @Override
    public byte[] transform(final @NotNull Module module,
                            final @Nullable ClassLoader loader,
                            final @NotNull String className,
                            final @Nullable Class<?> classBeingRedefined,
                            final @NotNull ProtectionDomain protectionDomain,
                            final byte[] classfileBuffer) {
        if (isClassBlacklisted(className)) {
            return classfileBuffer;
        }
        if (shouldTransform(className)) {
            final var reader = new ClassReader(classfileBuffer);
            final var classNode = new ClassNode(Opcodes.ASM5);
            reader.accept(classNode, 0);
            transform(classNode);
            final var writer = new NonLoadingClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            classNode.accept(writer);
            return writer.toByteArray();
        }
        return classfileBuffer;
    }
}
