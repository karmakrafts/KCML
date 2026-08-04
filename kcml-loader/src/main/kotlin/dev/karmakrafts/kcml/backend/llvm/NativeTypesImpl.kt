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

package dev.karmakrafts.kcml.backend.llvm

import dev.karmakrafts.kcml.api.backend.llvm.NativeTypes
import kotlinx.cinterop.ExperimentalForeignApi
import llvm.LLVMContextRef
import llvm.LLVMDoubleTypeInContext
import llvm.LLVMFloatTypeInContext
import llvm.LLVMInt16TypeInContext
import llvm.LLVMInt1TypeInContext
import llvm.LLVMInt32TypeInContext
import llvm.LLVMInt64TypeInContext
import llvm.LLVMInt8TypeInContext
import llvm.LLVMPointerTypeInContext
import llvm.LLVMTypeRef

@OptIn(ExperimentalForeignApi::class)
class NativeTypesImpl( // @formatter:off
    private val context: LLVMContextRef
) : NativeTypes { // @formatter:on
    override val int1: LLVMTypeRef by lazy { LLVMInt1TypeInContext(context)!! }
    override val int8: LLVMTypeRef by lazy { LLVMInt8TypeInContext(context)!! }
    override val int16: LLVMTypeRef by lazy { LLVMInt16TypeInContext(context)!! }
    override val int32: LLVMTypeRef by lazy { LLVMInt32TypeInContext(context)!! }
    override val int64: LLVMTypeRef by lazy { LLVMInt64TypeInContext(context)!! }
    override val float32: LLVMTypeRef by lazy { LLVMFloatTypeInContext(context)!! }
    override val float64: LLVMTypeRef by lazy { LLVMDoubleTypeInContext(context)!! }
    override val ptr: LLVMTypeRef by lazy { LLVMPointerTypeInContext(context, 0)!! }
}