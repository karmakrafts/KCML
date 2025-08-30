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

package dev.karmakrafts.kcml.agent;

import dev.karmakrafts.kcml.monitor.protocol.C2SLogPacket;
import dev.karmakrafts.kcml.monitor.protocol.MonitorLogLevel;

final class Logger {
    public static void log(final MonitorLogLevel level, final String message) {
        MonitorClient.INSTANCE.sendPacket(new C2SLogPacket(MonitorClient.INSTANCE.id, level, message));
    }

    public static void debug(final String message) {
        log(MonitorLogLevel.DEBUG, message);
    }

    public static void info(final String message) {
        log(MonitorLogLevel.INFO, message);
    }

    public static void warn(final String message) {
        log(MonitorLogLevel.WARN, message);
    }

    public static void error(final String message) {
        log(MonitorLogLevel.ERROR, message);
    }

    public static void fatal(final String message) {
        log(MonitorLogLevel.FATAL, message);
    }
}
