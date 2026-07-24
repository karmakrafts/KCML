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

import dev.karmakrafts.kcml.agent.log.FileLogger;
import dev.karmakrafts.kcml.agent.log.Logger;
import dev.karmakrafts.kcml.agent.log.NoopLogger;
import dev.karmakrafts.kcml.agent.transformer.TopLevelPhasesTransformer;

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

    private static Logger createLogger(final Map<String, String> options) {
        final var logFilePath = options.get("log_file_path");
        Logger logger = NoopLogger.INSTANCE;
        if (logFilePath != null) {
            logger = new FileLogger(Path.of(logFilePath));
        }
        return logger;
    }

    public static void agentmain(final String args, final Instrumentation instrumentation) {
        final var options = parseArgs(args);
        try {
            final var logger = createLogger(options);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    logger.close();
                }
                catch (Throwable error) {
                    // We can't really do anything here :/
                }
            }));
            final var moduleName = options.get("module_name");
            if (moduleName != null) {
                logger.info(String.format("Initializing KCML compiler agent for module '%s'..", moduleName));
            }
            else {
                logger.info("Initializing KCML compiler agent for unknown module..");
            }
            instrumentation.addTransformer(new TopLevelPhasesTransformer(logger));
        }
        catch (Throwable error) {
            // We can't really do anything here :/
        }
    }
}
