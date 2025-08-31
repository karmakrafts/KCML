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

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.time.Duration;
import java.time.Instant;

abstract class AbstractClassTransformer implements ClassFileTransformer {
    protected final MonitorClient client;
    protected final Logger logger;

    protected AbstractClassTransformer(final MonitorClient client, final Logger logger) {
        this.client = client;
        this.logger = logger;
    }

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
            logger.debug("Skipping transformation of class %s", className);
            return classfileBuffer;
        }
        if (shouldTransform(className)) {
            logger.info("Transforming class %s", className);
            final var startTime = Instant.now();
            final var reader = new ClassReader(classfileBuffer);
            final var classNode = new ClassNode(Opcodes.ASM5);
            reader.accept(classNode, 0);
            try {
                transform(classNode);
            }
            catch (Throwable error) {
                client.handleException(error);
            }
            final var writer = new NonLoadingClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            classNode.accept(writer);
            final var time = Duration.between(startTime, Instant.now()).toMillis();
            logger.info("Transformed class %s in %dms", className, time);
            final var transformedBytes = writer.toByteArray();
            client.sendClassTransformedPacket(className, loader.getName(), classfileBuffer, transformedBytes);
            return transformedBytes;
        }
        logger.debug("Skipping transformation of class %s", className);
        return classfileBuffer;
    }
}
