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

package dev.karmakrafts.kcml.agent.log;

import org.jetbrains.annotations.Nullable;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;

public final class FileLogger implements Logger {
    private final ArrayList<String> lines = new ArrayList<>();
    private final Path path;

    public FileLogger(final Path path) {
        this.path = path;
    }

    @Override
    public void debug(final @Nullable String message) {
        final var actualMessage = message != null ? message : "null";
        lines.add(String.format("[DEBUG] %s", actualMessage));
    }

    @Override
    public void info(final @Nullable String message) {
        final var actualMessage = message != null ? message : "null";
        lines.add(String.format("[INFO-] %s", actualMessage));
    }

    @Override
    public void warn(final @Nullable String message) {
        final var actualMessage = message != null ? message : "null";
        lines.add(String.format("[WARN-] %s", actualMessage));
    }

    @Override
    public void error(final @Nullable String message) {
        final var actualMessage = message != null ? message : "null";
        lines.add(String.format("[ERROR] %s", actualMessage));
    }

    @Override
    public void close() throws Exception {
        Files.deleteIfExists(path);
        try (final var writer = new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(path,
            StandardOpenOption.CREATE_NEW)))) {
            for (final var line : lines) {
                writer.append(line);
                writer.newLine();
            }
        }
        lines.clear();
    }
}
