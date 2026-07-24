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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface Logger {
    void debug(final @Nullable String message);

    void info(final @Nullable String message);

    void warn(final @Nullable String message);

    void error(final @Nullable String message);

    default void debug(final @Nullable String message, final @NotNull Throwable error) {
        debug(String.format("%s: %s", message, error));
    }

    default void info(final @Nullable String message, final @NotNull Throwable error) {
        info(String.format("%s: %s", message, error));
    }

    default void warn(final @Nullable String message, final @NotNull Throwable error) {
        warn(String.format("%s: %s", message, error));
    }

    default void error(final @Nullable String message, final @NotNull Throwable error) {
        error(String.format("%s: %s", message, error));
    }
}
