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

import java.util.Arrays;
import java.util.Optional;

public enum LoggingMode {
    // @formatter:off
    NONE,
    FILE,
    REMOTE;
    // @formatter:on

    public static Optional<LoggingMode> byName(final String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(mode -> mode.name().equalsIgnoreCase(name)).findFirst();
    }
}
