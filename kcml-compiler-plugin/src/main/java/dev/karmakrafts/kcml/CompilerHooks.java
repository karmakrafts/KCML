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

package dev.karmakrafts.kcml;

import org.jetbrains.kotlin.cli.pipeline.ArgumentsPipelineArtifact;
import org.jetbrains.kotlin.config.CompilerConfiguration;
import org.jetbrains.kotlin.config.CompilerConfigurationKey;

import java.nio.file.Files;
import java.nio.file.Path;

@SuppressWarnings("unused") // Functions invoked by modified compiler code
public final class CompilerHooks {
    public static final CompilerConfigurationKey<String[]> PLUGIN_CLASSPATHS = new CompilerConfigurationKey<>(
        "kcml_plugin_classpaths");

    // Steal plugin classpaths from artifact and insert into compiler config so we can access it for our own CL
    public static void onLoadCompilerPlugins(final ArgumentsPipelineArtifact<?> artifact,
                                             final CompilerConfiguration configuration) {
        try {
            Files.writeString(Path.of("/home/fux/Schreibtisch/HELLOU.txt"), "HELLOU, SCHEISE GAYN");
        }
        catch (Throwable error) {
            // ---
        }
        var classpaths = artifact.getArguments().getPluginClasspaths();
        if (classpaths == null) {
            // Ensure the array is never null
            classpaths = new String[0];
        }
        configuration.put(PLUGIN_CLASSPATHS, classpaths);
    }
}