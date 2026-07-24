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

import dev.karmakrafts.kcml.agent.log.Logger;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.ArrayList;

public abstract class AbstractClassTransformer implements ClassFileTransformer {
    protected static final ArrayList<String> BLACKLIST = new ArrayList<>();

    static {
        BLACKLIST.add("java/");
        BLACKLIST.add("jdk/");
        BLACKLIST.add("com/sun/");
        BLACKLIST.add("org/objectweb/");
        BLACKLIST.add("dev/karmakrafts/kcml/");
    }

    protected final Logger logger;

    protected AbstractClassTransformer(final Logger logger) {
        this.logger = logger;
    }

    private static boolean isClassBlacklisted(final String className) {
        for (final var prefix : BLACKLIST) {
            if (!className.startsWith(prefix)) {
                continue;
            }
            return true;
        }
        return false;
    }

    protected abstract boolean shouldTransform(final String className);

    protected abstract void transform(final ClassNode classNode);

    @Override
    public byte[] transform(final Module module,
                            final @Nullable ClassLoader loader,
                            final String className,
                            final @Nullable Class<?> classBeingRedefined,
                            final ProtectionDomain protectionDomain,
                            final byte[] classfileBuffer) {
        if (className == null || isClassBlacklisted(className)) {
            return classfileBuffer;
        }
        if (classfileBuffer == null || classfileBuffer.length == 0) {
            return new byte[0];
        }
        if (shouldTransform(className)) {
            logger.info(String.format("Transforming class %s..", className));
            final var reader = new ClassReader(classfileBuffer);
            final var classNode = new ClassNode();
            reader.accept(classNode, 0);
            transform(classNode);
            final var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            classNode.accept(writer);
            return writer.toByteArray();
        }
        return classfileBuffer;
    }
}
