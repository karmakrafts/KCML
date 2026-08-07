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

import dev.karmakrafts.kcml.agent.asm.ASMUtils;
import dev.karmakrafts.kcml.agent.asm.BlockBuilder;
import dev.karmakrafts.kcml.agent.asm.Types;
import dev.karmakrafts.kcml.agent.asm.Types.Common;
import dev.karmakrafts.kcml.agent.asm.Types.KCML;
import dev.karmakrafts.kcml.agent.log.Logger;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.nio.file.Path;

/**
 * We need to transform CLICompiler to inject the loader JAR into the classpath
 * as early as possible, so compiler workers inherit it properly (for tasks like KotlinJsIrLink and things involving IC).
 */
public final class CLICompilerTransformer extends AbstractClassTransformer {
    private final Path loaderPath;

    public CLICompilerTransformer(final Logger logger, final Path loaderPath) {
        super(logger);
        this.loaderPath = loaderPath;
    }

    @Override
    protected boolean shouldTransform(final String className) {
        return className.equals(Common.CLI_COMPILER.getInternalName());
    }

    private boolean transformExecImplHead(final MethodNode methodNode) { // @formatter:off
        return ASMUtils.stream(methodNode.instructions)
            .findFirst()
            .map(needle -> {
                logger.info("Transforming %s%s (Self-Bootstrap)", methodNode.name, methodNode.desc);
                final var builder = BlockBuilder.create().withContext(methodNode);

                // Get the current app class loader and its UCP
                builder.loadThis();
                builder.invokevirtual(Types.OBJECT, "getClass", Types.CLASS);
                builder.invokevirtual(Types.CLASS, "getClassLoader", Types.CLASS_LOADER);
                builder.dup();
                builder.astore("kcmlClassLoader", Types.CLASS_LOADER);
                builder.invokevirtual(Types.OBJECT, "getClass", Types.CLASS);
                builder.astore("kcmlClassLoaderType", Types.CLASS);

                // Materialize the target URL as a runtime value
                builder.ldc(loaderPath.toAbsolutePath().toString());
                builder.ldc(0);
                builder.anewarray(Types.STRING);
                builder.invokestatic(Types.PATH, true, "of", Types.PATH, Types.STRING, Types.STRING_ARRAY);
                builder.invokeinterface(Types.PATH, "toUri", Types.URI);
                builder.invokevirtual(Types.URI, "toURL", Types.URL);
                builder.astore("kcmlLoaderURL", Types.URL);

                // Create an args array with the URL
                builder.ldc(1);
                builder.anewarray(Types.OBJECT);
                builder.dup();
                builder.astore("kcmlAddURLArgs", Types.OBJECT_ARRAY);
                builder.ldc(0);
                builder.aload("kcmlLoaderURL");
                builder.aastore();

                // Build the param type array for getting the addURL method
                builder.ldc(1);
                builder.anewarray(Types.CLASS);
                builder.dup();
                builder.astore("kcmlAddURLParamTypes", Types.CLASS_ARRAY);
                builder.ldc(0);
                builder.ldc(Types.URL);
                builder.aastore();

                // Retrieve a handle to the target method
                builder.aload("kcmlClassLoaderType");
                builder.ldc("addURL");
                builder.aload("kcmlAddURLParamTypes");
                builder.invokevirtual(Types.CLASS, "getDeclaredMethod", Types.METHOD, Types.STRING, Types.CLASS_ARRAY);
                builder.dup();
                builder.astore("kcmlAddURLMethod", Types.METHOD);
                builder.ldc(true);
                builder.invokevirtual(Types.METHOD, "setAccessible", Type.VOID_TYPE, Type.BOOLEAN_TYPE);

                // Append the loader URL to the classpath
                builder.aload("kcmlAddURLMethod");
                builder.aload("kcmlClassLoader");
                builder.aload("kcmlAddURLArgs");
                builder.invokevirtual(Types.METHOD, "invoke", Types.OBJECT, Types.OBJECT, Types.OBJECT_ARRAY);
                builder.pop(); // Null for void call result

                // Restore the original method and field access
                builder.aload("kcmlAddURLMethod");
                builder.ldc(false);
                builder.invokevirtual(Types.METHOD, "setAccessible", Type.VOID_TYPE, Type.BOOLEAN_TYPE);

                // Invoke callback into CommonHooks
                builder.aload("arguments");
                builder.invokestatic(KCML.COMMON_HOOKS,
                    false,
                    "onExecImpl",
                    Type.VOID_TYPE,
                    Common.COMMON_COMPILER_ARGUMENTS);

                methodNode.instructions.insertBefore(needle, builder.build());
                return true;
            })
            .orElse(false);
    } // @formatter:on

    @Override
    protected boolean transform(final ClassNode classNode) {
        var wasChanged = false;
        for (final var methodNode : classNode.methods) {
            switch (methodNode.name) {
                case "execImpl" -> wasChanged |= transformExecImplHead(methodNode);
            }
        }
        return wasChanged;
    }
}
