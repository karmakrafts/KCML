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

import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;

public final class RemoteLogger implements Logger {
    private final Socket socket;
    private final PrintWriter writer;
    private final String moduleName;

    public RemoteLogger(final int port, final String moduleName) throws IOException {
        socket = new Socket(InetAddress.getLoopbackAddress(), port);
        writer = new PrintWriter(socket.getOutputStream(), true);
        this.moduleName = moduleName;
    }

    @Override
    public void debug(final @Nullable String message) {
        try {
            final var actualMessage = message != null ? message : "null";
            writer.println(String.format("[DEBUG][%s] %s", moduleName, actualMessage));
        }
        catch (Throwable error) {
            // Cannot do anything
        }
    }

    @Override
    public void info(final @Nullable String message) {
        try {
            final var actualMessage = message != null ? message : "null";
            writer.println(String.format("[INFO-][%s] %s", moduleName, actualMessage));
        }
        catch (Throwable error) {
            // Cannot do anything
        }
    }

    @Override
    public void warn(final @Nullable String message) {
        try {
            final var actualMessage = message != null ? message : "null";
            writer.println(String.format("[WARN-][%s] %s", moduleName, actualMessage));
        }
        catch (Throwable error) {
            // Cannot do anything
        }
    }

    @Override
    public void error(final @Nullable String message) {
        try {
            final var actualMessage = message != null ? message : "null";
            writer.println(String.format("[ERROR][%s] %s", moduleName, actualMessage));
        }
        catch (Throwable error) {
            // Cannot do anything
        }
    }

    @Override
    public void close() throws Exception {
        writer.close();
        socket.close();
    }
}
