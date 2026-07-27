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

import dev.karmakrafts.kcml.agent.util.AgentCommClient;
import org.jetbrains.annotations.Nullable;

public final class RemoteLogger implements Logger {
    private final AgentCommClient client;
    private final String moduleName;

    public RemoteLogger(final AgentCommClient client, final String moduleName) {
        this.client = client;
        this.moduleName = moduleName;
    }

    @Override
    public void debug(final @Nullable String message) {
        final var actualMessage = message != null ? message : "null";
        client.log(String.format("[DEBUG][%s] %s", moduleName, actualMessage));
    }

    @Override
    public void info(final @Nullable String message) {
        final var actualMessage = message != null ? message : "null";
        client.log(String.format("[INFO-][%s] %s", moduleName, actualMessage));
    }

    @Override
    public void warn(final @Nullable String message) {
        final var actualMessage = message != null ? message : "null";
        client.log(String.format("[WARN-][%s] %s", moduleName, actualMessage));
    }

    @Override
    public void error(final @Nullable String message) {
        final var actualMessage = message != null ? message : "null";
        client.log(String.format("[ERROR][%s] %s", moduleName, actualMessage));
    }
}
