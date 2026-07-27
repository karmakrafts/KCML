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
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public final class TopLevelPhasesTransformer extends AbstractClassTransformer {
    public TopLevelPhasesTransformer(final Logger logger) {
        super(logger);
        logger.info("Created TopLevelPhasesTransformer");
    }

    @Override
    protected boolean shouldTransform(final String className) {
        return className.equals("org/jetbrains/kotlin/backend/konan/driver/phases/TopLevelPhasesKt");
    }

    private void transformRunAllLowerings(final MethodNode methodNode) {
        logger.info(String.format("Transforming %s%s", methodNode.name, methodNode.desc));
    }

    @Override
    protected void transform(final ClassNode classNode) {
        for (final var methodNode : classNode.methods) {
            switch (methodNode.name) {
                case "runBackend$lambda$0$runAllLowerings" -> transformRunAllLowerings(methodNode);
            }
        }
    }
}
