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

package dev.karmakrafts.kcml.agent.asm;

import org.objectweb.asm.Type;

public final class Types {
    public static final Type CLASS = Type.getObjectType("java/lang/Class");
    public static final Type CLASS_ARRAY = Type.getType("[Ljava/lang/Class;");
    public static final Type OBJECT = Type.getObjectType("java/lang/Object");
    public static final Type OBJECT_ARRAY = Type.getType("[Ljava/lang/Object;");
    public static final Type STRING = Type.getObjectType("java/lang/String");
    public static final Type METHOD = Type.getObjectType("java/lang/reflect/Method");
    public static final Type LIST = Type.getObjectType("java/util/List");

    public static final class CInterop {
        public static final Type C_POINTER = Type.getObjectType("kotlinx/cinterop/CPointer");
    }

    public static final class Common {
        public static final Type IR_CALL = Type.getObjectType("org/jetbrains/kotlin/ir/expressions/IrCall");
    }

    public static final class Konan {
        public static final Type NATIVE_GENERATION_STATE = Type.getObjectType(
            "org/jetbrains/kotlin/backend/konan/llvm/NativeGenerationState");
        public static final Type FUNCTION_GENERATION_CONTEXT = Type.getObjectType(
            "org/jetbrains/kotlin/backend/konan/llvm/FunctionGenerationContext");
    }

    public static final class KCML {
        public static final Type KCML_HOOKS = Type.getObjectType("dev/karmakrafts/kcml/hooks/KCMLHooks");
    }
}