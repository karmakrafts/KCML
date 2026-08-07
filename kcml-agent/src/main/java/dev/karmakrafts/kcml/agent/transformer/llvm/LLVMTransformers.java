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

package dev.karmakrafts.kcml.agent.transformer.llvm;

import dev.karmakrafts.kcml.agent.log.Logger;

import java.lang.instrument.Instrumentation;

public final class LLVMTransformers {
    // @formatter:off
    private LLVMTransformers() {}
    // @formatter:on

    public static void register(final Instrumentation instrumentation, final Logger logger) {
        instrumentation.addTransformer(new CodeGeneratorVisitorTransformer(logger));
        instrumentation.addTransformer(new TopLevelPhasesTransformer(logger));
    }
}
