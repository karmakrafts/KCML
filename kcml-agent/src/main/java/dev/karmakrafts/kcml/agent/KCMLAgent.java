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

import dev.karmakrafts.kcml.agent.log.*;
import dev.karmakrafts.kcml.agent.transformer.TopLevelPhasesTransformer;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class KCMLAgent {
    private static Map<String, String> parseArgs(final String args) {
        if (args.isBlank()) {
            return Collections.emptyMap();
        }
        final var options = new HashMap<String, String>();
        final var argChunks = args.split(":");
        for (final var argChunk : argChunks) {
            final var pair = argChunk.split("=");
            if (pair.length != 2) {
                continue; // We ignore any invalid arguments
            }
            options.put(pair[0], pair[1]);
        }
        return options;
    }

    private static Logger createLogger(final Map<String, String> options) throws IOException {
        final var loggingMode = LoggingMode.byName(options.get("log_mode")).orElse(LoggingMode.NONE);
        Logger logger = NoopLogger.INSTANCE;
        switch (loggingMode) {
            case FILE -> {
                final var logFilePath = options.get("log_file_path");
                if (logFilePath == null) {
                    return logger;
                }
                logger = new FileLogger(Path.of(logFilePath));
            }
            case REMOTE -> {
                final var logServerPort = options.get("log_server_port");
                var port = 9876;
                if (logServerPort != null) {
                    port = Integer.parseInt(logServerPort);
                }
                var moduleName = options.get("module_name");
                if (moduleName == null) {
                    moduleName = "Unknown";
                }
                logger = new RemoteLogger(port, moduleName);
            }
        }
        return logger;
    }

    public static void agentmain(final String args, final Instrumentation instrumentation) {
        final var options = parseArgs(args);
        try {
            final var logger = createLogger(options);
            // This might be delayed because the Kotlin compiler may be hosted within the Gradle daemon or the KCD
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    logger.close();
                }
                catch (Throwable error) {
                    // We can't really do anything here :/
                }
            }));
            logger.info("Initializing KCML compiler agent");
            instrumentation.addTransformer(new TopLevelPhasesTransformer(logger));
        }
        catch (Throwable error) {
            // We can't really do anything here :/
        }
    }
}
