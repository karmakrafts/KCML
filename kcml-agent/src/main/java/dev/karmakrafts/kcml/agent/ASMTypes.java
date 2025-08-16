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

import org.objectweb.asm.Type;

final class ASMTypes {
    // Hooks class defined in the loader runtime
    public static final Type COMPILER_HOOKS = Type.getObjectType("dev/karmakrafts/kcml/CompilerHooks");

    public static final Type ARGUMENTS_PIPELINE_ARTIFACT = Type.getObjectType("org/jetbrains/kotlin/cli/pipeline/ArgumentsPipelineArtifact");
    public static final Type COMPILER_CONFIGURATION = Type.getObjectType("org/jetbrains/kotlin/config/CompilerConfiguration");
}
