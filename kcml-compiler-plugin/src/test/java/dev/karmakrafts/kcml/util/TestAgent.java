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

package dev.karmakrafts.kcml.util;

import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public final class TestAgent {
    public static void agentmain(final String options, final Instrumentation instrumentation) throws Exception {
        final String args = options.substring(options.indexOf('\n') + 1);
        if (args.equals("fail=true")) {
            throw new IllegalStateException("Requested agent failure");
        }
        if (!args.startsWith("directory=")) {
            return;
        }
        final Path directory = Path.of(args.substring("directory=".length()));
        Files.createFile(directory.resolve("started"));
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!Files.exists(directory.resolve("release"))) {
            if (System.nanoTime() >= deadline) {
                throw new IllegalStateException("Timed out waiting for agent release");
            }
            Thread.sleep(10);
        }
    }
}