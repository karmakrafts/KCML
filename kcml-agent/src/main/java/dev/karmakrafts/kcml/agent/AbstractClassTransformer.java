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

import java.io.ByteArrayInputStream;
import java.lang.instrument.ClassFileTransformer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.stream.Collectors;

abstract class AbstractClassTransformer implements ClassFileTransformer {
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
            "org/objectweb/")) {
            return classfileBuffer;
        }
        if (shouldTransform(className)) {
            final var reader = new ClassReader(classfileBuffer);
            final var classNode = new ClassNode(Opcodes.ASM5);
            reader.accept(classNode, ClassReader.EXPAND_FRAMES);
            transform(classNode);
            final var writer = new NonLoadingClassWriter(reader, ClassWriter.COMPUTE_FRAMES);
            try {
                classNode.accept(writer);
                try (final var inputStream = new ByteArrayInputStream(classfileBuffer); final var outputStream = Files.newOutputStream(
                    Path.of(String.format("/home/fux/Schreibtisch/%s.class",
                        className.substring(className.lastIndexOf('/') + 1))))) {
                    inputStream.transferTo(outputStream);
                }
            }
            catch (Throwable error) {
                final var text = new StringBuilder(Arrays.stream(error.getStackTrace()).map(StackTraceElement::toString).collect(
                    Collectors.joining("\n")));
                text.insert(0, String.format("%s\n", error));
                try {
                    Files.writeString(Path.of("/home/fux/Schreibtisch/agent_err.txt"), text);
                }
                catch (Throwable ioError) {
                    // ...
                }
            }
            return writer.toByteArray();
        }
        return classfileBuffer;
    }
}
