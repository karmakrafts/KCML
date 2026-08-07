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

package dev.karmakrafts.kcml.agent.transformer;

import dev.karmakrafts.kcml.agent.log.Logger;

import java.lang.instrument.Instrumentation;
import java.nio.file.Path;
import java.util.Map;

public final class CommonTransformers {
    // @formatter:off
    private CommonTransformers() {}
    // @formatter:on

    public static void register(final Instrumentation instrumentation,
                                final Logger logger,
                                final Map<String, String> options) {
        final var loaderPath = Path.of(options.get("loader_path"));
        instrumentation.addTransformer(new CLICompilerTransformer(logger, loaderPath));
    }
}
