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
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.time.Duration;
import java.time.Instant;

public abstract class AbstractClassTransformer implements ClassFileTransformer {
    protected abstract boolean shouldTransform(final String className);

    protected abstract void transform(final ClassNode classNode);

    @Override
    public byte[] transform(final Module module,
                            final ClassLoader loader,
                            final String className,
                            final Class<?> classBeingRedefined,
                            final ProtectionDomain protectionDomain,
                            final byte[] classfileBuffer) {
        if (className == null || className.startsWith("java/") || className.startsWith("jdk/") || className.startsWith(
            "org/objectweb/") || className.startsWith("dev/karmakrafts/kcml/")) {
            return classfileBuffer;
        }
        if (shouldTransform(className)) {
            final var startTime = Instant.now();
            final var reader = new ClassReader(classfileBuffer);
            final var classNode = new ClassNode(Opcodes.ASM5);
            reader.accept(classNode, 0);
            transform(classNode);
            final var writer = new NonLoadingClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            classNode.accept(writer);
            final var time = Duration.between(startTime, Instant.now()).toMillis();
            final var transformedBytes = writer.toByteArray();
            // TODO: reimplement this
            //client.sendClassTransformedPacket(className, loader.getName(), classfileBuffer, transformedBytes);
            return transformedBytes;
        }
        return classfileBuffer;
    }
}
