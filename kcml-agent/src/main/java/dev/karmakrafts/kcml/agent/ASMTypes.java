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
    public static final Type CLASS = Type.getObjectType("java/lang/Class");
    public static final Type CLASS_ARRAY = Type.getType("[Ljava/lang/Class;");
    public static final Type OBJECT = Type.getObjectType("java/lang/Object");
    public static final Type OBJECT_ARRAY = Type.getType("[Ljava/lang/Object;");
    public static final Type STRING = Type.getObjectType("java/lang/String");
    public static final Type CHAR_SEQUENCE = Type.getObjectType("java/lang/CharSequence");
    public static final Type LIST = Type.getObjectType("java/util/List");
    public static final Type MAP = Type.getObjectType("java/util/Map");
    public static final Type ITERATOR = Type.getObjectType("java/util/Iterator");
    public static final Type METHOD = Type.getObjectType("java/lang/reflect/Method");
    public static final Type PAIR = Type.getObjectType("kotlin/Pair");

    public static final Type SYNTHETIC_IR_FILE_ENTRY = Type.getObjectType("dev/karmakrafts/kcml/ir/SyntheticIrFileEntry");
    public static final Type NATIVE_IR_DECLARATION_ORIGIN = Type.getObjectType(
        "dev/karmakrafts/kcml/ir/NativeIrDeclarationOrigin");

    public static final Type FUNCTION_GENERATION_CONTEXT = Type.getObjectType(
        "org/jetbrains/kotlin/backend/konan/llvm/FunctionGenerationContext");
    public static final Type LLVM_CALLABLE = Type.getObjectType("org/jetbrains/kotlin/backend/konan/llvm");
    public static final Type C_POINTER = Type.getObjectType("kotlinx/cinterop/CPointer");
    public static final Type FIR_2_IR_ACTUALIZED_RESULT = Type.getObjectType(
        "org/jetbrains/kotlin/fir/pipeline/Fir2IrActualizedResult");
    public static final Type MODULE_COMPILER_ANALYZED_OUTPUT = Type.getObjectType(
        "org/jetbrains/kotlin/fir/pipeline/ModuleCompilerAnalyzedOutput");
    public static final Type SCOPE_SESSION = Type.getObjectType("org/jetbrains/kotlin/fir/resolve/ScopeSession");
    public static final Type FQ_NAME = Type.getObjectType("org/jetbrains/kotlin/name/FqName");
    public static final Type METADATA_SOURCE = Type.getObjectType("org/jetbrains/kotlin/ir/declarations/MetadataSource");
    public static final Type KT_SOURCE_FILE = Type.getObjectType("org/jetbrains/kotlin/KtSourceFile");
    public static final Type KT_IN_MEMORY_SOURCE_FILE = Type.getObjectType(
        "org/jetbrains/kotlin/KtInMemoryTextSourceFile");

    public static final Type IR_DECLARATION_ORIGIN = Type.getObjectType(
        "org/jetbrains/kotlin/ir/declarations/IrDeclarationOrigin");
    public static final Type IR_MODULE_FRAGMENT = Type.getObjectType(
        "org/jetbrains/kotlin/ir/declarations/IrModuleFragment");
    public static final Type IR_FILE = Type.getObjectType("org/jetbrains/kotlin/ir/declarations/IrFile");
    public static final Type IR_FILE_ENTRY = Type.getObjectType("org/jetbrains/kotlin/ir/IrFileEntry");
    public static final Type IR_CALL = Type.getObjectType("org/jetbrains/kotlin/ir/expressions/IrCall");
    public static final Type IR_SIMPLE_FUNCTION = Type.getObjectType(
        "org/jetbrains/kotlin/ir/declarations/IrSimpleFunction");

    public static final Type FIR_MODULE_DATA = Type.getObjectType("org/jetbrains/kotlin/fir/FirModuleData");
    public static final Type FIR_MODULE_DATA_KT = Type.getObjectType("org/jetbrains/kotlin/fir/FirModuleDataKt");
    public static final Type FIR_SESSION = Type.getObjectType("org/jetbrains/kotlin/fir/FirSession");
    public static final Type FIR_FILE_BUILDER = Type.getObjectType(
        "org/jetbrains/kotlin/fir/declarations/builder/FirFileBuilder");
    public static final Type FIR_PACKAGE_DIRECTIVE_BUILDER = Type.getObjectType(
        "org/jetbrains/kotlin/fir/builder/FirPackageDirectiveBuilder");
    public static final Type FIR_PACKAGE_DIRECTIVE = Type.getObjectType("org/jetbrains/kotlin/fir/FirPackageDirective");
    public static final Type FIR_FILE = Type.getObjectType("org/jetbrains/kotlin/fir/declarations/FirFile");
    public static final Type FIR_DECLARATION_ORIGIN = Type.getObjectType(
        "org/jetbrains/kotlin/fir/declarations/FirDeclarationOrigin");
    public static final Type FIR_SYNTHETIC_PLUGIN_FILE = Type.getObjectType(
        "org/jetbrains/kotlin/fir/declarations/FirDeclarationOrigin$Synthetic$PluginFile");
    public static final Type FIR_FILE_METADATA_SOURCE = Type.getObjectType(
        "org/jetbrains/kotlin/fir/backend/FirMetadataSource$File");
}