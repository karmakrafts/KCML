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
            if (!argChunk.contains("=")) {
                // We know this is a value-less option
                options.put(argChunk, null);
                continue;
            }
            // We know we have a value to parse
            final var pair = argChunk.split("=");
            options.put(pair[0], pair[1]);
        }
        return options;
    }

    public static void agentmain(final String args, final Instrumentation instrumentation) {
        final var options = parseArgs(args);
    }
}
