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
    public static final Type STRING_ARRAY = Type.getType("[Ljava/lang/String;");
    public static final Type CLASS_LOADER = Type.getObjectType("java/lang/ClassLoader");
    public static final Type LIST = Type.getObjectType("java/util/List");
    public static final Type METHOD = Type.getObjectType("java/lang/reflect/Method");
    public static final Type URL = Type.getObjectType("java/net/URL");
    public static final Type URI = Type.getObjectType("java/net/URI");
    public static final Type PATH = Type.getObjectType("java/nio/file/Path");

    public static final class CInterop {
        public static final Type C_POINTER = Type.getObjectType("kotlinx/cinterop/CPointer");
    }

    public static final class Common {
        public static final Type IR_CALL = Type.getObjectType("org/jetbrains/kotlin/ir/expressions/IrCall");
        public static final Type IR_FUNCTION_ACCESS_EXPRESSION = Type.getObjectType(
            "org/jetbrains/kotlin/ir/expressions/IrFunctionAccessExpression");
        public static final Type IR_FILE = Type.getObjectType("org/jetbrains/kotlin/ir/declarations/IrFile");
        public static final Type CLI_COMPILER = Type.getObjectType("org/jetbrains/kotlin/cli/common/CLICompiler");
        public static final Type COMMON_COMPILER_ARGUMENTS = Type.getObjectType(
            "org/jetbrains/kotlin/cli/common/arguments/CommonCompilerArguments");
    }

    public static final class WASM {
        public static final Type WASM_BACKEND_CONTEXT = Type.getObjectType(
            "org/jetbrains/kotlin/backend/wasm/WasmBackendContext");

        public static final Type WASM_MODULE_FRAGMENT_GENERATOR_KT = Type.getObjectType(
            "org/jetbrains/kotlin/backend/wasm/ir2wasm/WasmModuleFragmentGeneratorKt");
        public static final Type BODY_GENERATOR = Type.getObjectType(
            "org/jetbrains/kotlin/backend/wasm/ir2wasm/BodyGenerator");
        public static final Type WASM_TYPE_CODEGEN_CONTEXT = Type.getObjectType(
            "org/jetbrains/kotlin/backend/wasm/ir2wasm/WasmTypeCodegenContext");
        public static final Type WASM_DECLARATION_CODEGEN_CONTEXT = Type.getObjectType(
            "org/jetbrains/kotlin/backend/wasm/ir2wasm/WasmDeclarationCodegenContext");
        public static final Type WASM_LINKER_DATA_CODEGEN_CONTEXT = Type.getObjectType(
            "org/jetbrains/kotlin/backend/wasm/ir2wasm/WasmLinkerDataCodegenContext");
    }

    public static final class Konan {
        public static final Type TOP_LEVEL_PHASES_KT = Type.getObjectType(
            "org/jetbrains/kotlin/backend/konan/driver/phases/TopLevelPhasesKt");

        public static final Type CODE_GENERATOR_VISITOR = Type.getObjectType(
            "org/jetbrains/kotlin/backend/konan/llvm/CodeGeneratorVisitor");
        public static final Type NATIVE_GENERATION_STATE = Type.getObjectType(
            "org/jetbrains/kotlin/backend/konan/llvm/NativeGenerationState");
    }

    public static final class KCML {
        public static final Type LLVM_HOOKS = Type.getObjectType("dev/karmakrafts/kcml/hooks/llvm/LLVMHooks");
        public static final Type WASM_HOOKS = Type.getObjectType("dev/karmakrafts/kcml/hooks/wasm/WASMHooks");
        public static final Type COMMON_HOOKS = Type.getObjectType("dev/karmakrafts/kcml/hooks/CommonHooks");
    }
}