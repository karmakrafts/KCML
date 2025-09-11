/*
 * Copyright 2025 Karma Krafts & associates
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

package dev.karmakrafts.kcml.monitor.protocol.log;

import dev.karmakrafts.kcml.monitor.protocol.MonitorClient;
import dev.karmakrafts.kcml.monitor.protocol.c2s.C2SLogPacket;

import java.time.Instant;

public record RemoteLogger(MonitorClient client) implements Logger {
    @Override
    public void log(final MonitorLogLevel level, final String message) {
        client.sendPacket(new C2SLogPacket(client.id, Instant.now(), level, message));
    }
}
