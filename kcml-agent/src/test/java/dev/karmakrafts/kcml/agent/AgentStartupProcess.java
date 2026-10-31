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

package dev.karmakrafts.kcml.agent;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.instrument.Instrumentation;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.jar.JarFile;

public final class AgentStartupProcess {
    private static Instrumentation instrumentation;
    private static URLClassLoader compilerLoader;

    public static void premain(final String options, final Instrumentation value) {
        // Capture JVM instrumentation so main can exercise the agent's dynamic-attach entry point.
        instrumentation = value;
    }

    public static void main(final String[] args) throws Throwable {
        // Arguments are agent options, communication port, and optionally a bridge JAR and its replacement.
        final boolean compile = args.length == 3 && args[2].equals("compile");
        final boolean incompatible = args.length > 3 && !Boolean.parseBoolean(args[5]);
        if (compile) {
            compilerLoader = createCompilerLoader();
            for (final String name : new String[]{
                "org.jetbrains.kotlin.cli.common.CLICompiler",
                "org.jetbrains.kotlin.backend.jvm.codegen.ClassCodegen",
                "org.jetbrains.kotlin.backend.jvm.codegen.ExpressionCodegen"
            }) {
                compilerLoader.loadClass(name);
            }
        }
        if (args.length > 3) {
            // Preload a bridge into the bootstrap loader before starting the agent.
            instrumentation.appendToBootstrapClassLoaderSearch(new JarFile(args[2]));
            Class.forName("dev.karmakrafts.kcml.api.mixin.MixinBridge", true, null);
            if (Boolean.parseBoolean(args[4])) {
                // Simulate replacing the JAR on disk while its old bridge class remains loaded in the JVM.
                final Path replacement = Path.of(args[2] + ".replacement");
                Files.copy(Path.of(args[3]), replacement);
                Files.move(replacement, Path.of(args[2]), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        try {
            KCMLAgentBootstrap.agentmain(args[0] + "\ncomm_port=" + args[1] + ":logging=false", instrumentation);
        }
        catch (final IllegalStateException error) {
            // Require ABI diagnostics and restart guidance for an incompatible bridge.
            if (incompatible && error.getMessage().contains("./gradlew --stop") && error.getMessage().contains("ABI")) {
                return;
            }
            throw error;
        }
        if (incompatible) {
            throw new AssertionError("Expected an actionable error for an incompatible bootstrap bridge");
        }
        // A null loader selects the bootstrap loader; false avoids initializing the class during this check.
        final Class<?> source = Class.forName("dev.karmakrafts.kcml.api.mixin.MixinSource", false, null);
        if (source.getClassLoader() != null) {
            throw new AssertionError("Mixin source must be loaded by the bootstrap class loader");
        }
        if (compile) {
            compileTwice();
        }
    }

    private static URLClassLoader createCompilerLoader() throws Exception {
        final String[] entries = System.getProperty("java.class.path").split(java.io.File.pathSeparator);
        final URL[] urls = new URL[entries.length];
        for (int index = 0; index < entries.length; index++) {
            urls[index] = Path.of(entries[index]).toUri().toURL();
        }
        return new URLClassLoader(urls, ClassLoader.getPlatformClassLoader());
    }

    private static void compileTwice() throws Exception {
        final Path directory = Files.createTempDirectory(Path.of("build"), "compiler-reuse-");
        try (final URLClassLoader loader = compilerLoader) {
            final Path source = directory.resolve("Example.kt");
            Files.writeString(source, "fun answer(): Int = run { 42 }\n");
            for (int invocation = 0; invocation < 2; invocation++) {
                final ByteArrayOutputStream output = new ByteArrayOutputStream();
                final Object compiler = loader.loadClass("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler").getConstructor().newInstance();
                final Object result = compiler.getClass().getMethod("exec", PrintStream.class, String[].class).invoke(
                    compiler, new PrintStream(output), new String[]{
                        "-no-stdlib", "-no-reflect", "-classpath", System.getProperty("java.class.path"),
                        "-d", directory.resolve("classes").toString(), source.toString()
                    }
                );
                if (!result.toString().equals("OK")) {
                    throw new AssertionError("Compiler invocation " + invocation + " failed: " + output);
                }
                try (final URLClassLoader generated = new URLClassLoader(
                    new URL[]{directory.resolve("classes").toUri().toURL()}, loader
                )) {
                    final Object answer = generated.loadClass("ExampleKt").getMethod("answer").invoke(null);
                    if (!Integer.valueOf(42).equals(answer)) {
                        throw new AssertionError("Unexpected generated result: " + answer);
                    }
                }
            }
        }
        finally {
            try (final var paths = Files.walk(directory)) {
                for (final Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
    }
}