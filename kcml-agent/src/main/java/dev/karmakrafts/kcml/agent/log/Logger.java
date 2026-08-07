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

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

public interface Logger {
    default String formatException(Throwable exception) {
        // @formatter:off
        final var stackTrace = Arrays.stream(exception.getStackTrace())
            .map(StackTraceElement::toString)
            .collect(Collectors.joining("\n"));
        // @formatter:on
        return String.format(Locale.getDefault(), "%s:\n%s", exception.getLocalizedMessage(), stackTrace);
    }

    void debug(final @Nullable String message);

    default void debug(final @Nullable String message, final Object... args) {
        final var actualMessage = message != null ? message : "null";
        debug(String.format(Locale.getDefault(), actualMessage, args));
    }

    default void debug(final @Nullable String message, final Throwable error) {
        debug(String.format("%s: %s", message, formatException(error)));
    }

    void info(final @Nullable String message);

    default void info(final @Nullable String message, final Object... args) {
        final var actualMessage = message != null ? message : "null";
        info(String.format(Locale.getDefault(), actualMessage, args));
    }

    default void info(final @Nullable String message, final Throwable error) {
        info(String.format("%s: %s", message, formatException(error)));
    }

    void warn(final @Nullable String message);

    default void warn(final @Nullable String message, final Object... args) {
        final var actualMessage = message != null ? message : "null";
        warn(String.format(Locale.getDefault(), actualMessage, args));
    }

    default void warn(final @Nullable String message, final Throwable error) {
        warn(String.format("%s: %s", message, formatException(error)));
    }

    void error(final @Nullable String message);

    default void error(final @Nullable String message, final Object... args) {
        final var actualMessage = message != null ? message : "null";
        error(String.format(Locale.getDefault(), actualMessage, args));
    }

    default void error(final @Nullable String message, final Throwable error) {
        error(String.format("%s: %s", message, formatException(error)));
    }
}
