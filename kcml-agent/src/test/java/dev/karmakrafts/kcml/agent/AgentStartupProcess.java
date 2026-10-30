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

import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.jar.JarFile;

public final class AgentStartupProcess {
    private static Instrumentation instrumentation;

    public static void premain(final String options, final Instrumentation value) {
        // Capture JVM instrumentation so main can exercise the agent's dynamic-attach entry point.
        instrumentation = value;
    }

    public static void main(final String[] args) throws Throwable {
        // Arguments are agent options, communication port, and optionally a bridge JAR and its replacement.
        if (args.length > 2) {
            // Preload an incompatible bridge into the bootstrap loader before starting the agent.
            instrumentation.appendToBootstrapClassLoaderSearch(new JarFile(args[2]));
            Class.forName("dev.karmakrafts.kcml.api.mixin.MixinBridge", true, null);
            if (args.length == 4) {
                // Simulate replacing the JAR on disk while its old bridge class remains loaded in the JVM.
                final Path replacement = Path.of(args[2] + ".replacement");
                Files.copy(Path.of(args[3]), replacement);
                Files.move(replacement, Path.of(args[2]), StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                KCMLAgentBootstrap.agentmain(args[0] + "\ncomm_port=" + args[1] + ":logging=false", instrumentation);
            }
            catch (final IllegalStateException error) {
                // Require restart guidance and the expected cause for either a replaced or incompatible bridge.
                final Class<?> expectedCause = args.length == 4 ? ClassNotFoundException.class : NoSuchMethodException.class;
                if (error.getMessage().contains("./gradlew --stop") && expectedCause.isInstance(error.getCause())) {
                    return;
                }
                throw error;
            }
            throw new AssertionError("Expected an actionable error for an incompatible bootstrap bridge");
        }
        // With no preloaded bridge, start normally and verify that the shared API is bootstrap-visible.
        KCMLAgentBootstrap.agentmain(args[0] + "\ncomm_port=" + args[1] + ":logging=false", instrumentation);
        // A null loader selects the bootstrap loader; false avoids initializing the class during this check.
        final Class<?> source = Class.forName("dev.karmakrafts.kcml.api.mixin.MixinSource", false, null);
        if (source.getClassLoader() != null) {
            throw new AssertionError("Mixin source must be loaded by the bootstrap class loader");
        }
    }
}