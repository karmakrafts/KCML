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

package dev.karmakrafts.kcml.agent.util;

import org.objectweb.asm.Type;

public final class ASMTypes {
    public static final Type CLASS = Type.getObjectType("java/lang/Class");
    public static final Type CLASS_ARRAY = Type.getType("[Ljava/lang/Class;");
    public static final Type OBJECT = Type.getObjectType("java/lang/Object");
    public static final Type OBJECT_ARRAY = Type.getType("[Ljava/lang/Object;");
    public static final Type STRING = Type.getObjectType("java/lang/String");
    public static final Type METHOD = Type.getObjectType("java/lang/reflect/Method");

    public static final class KCML {
        public static final Type TOP_LEVEL_PHASES_HOOKS = Type.getObjectType(
            "dev/karmakrafts/kcml/hooks/TopLevelPhasesHooks");
    }
}