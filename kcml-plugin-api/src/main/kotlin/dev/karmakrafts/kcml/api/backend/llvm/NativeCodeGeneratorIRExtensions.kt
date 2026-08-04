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

@file:OptIn(ExperimentalForeignApi::class) @file:Suppress("NOTHING_TO_INLINE")

package dev.karmakrafts.kcml.api.backend.llvm

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toCValues
import llvm.LLVMAtomicOrdering
import llvm.LLVMAtomicRMWBinOp
import llvm.LLVMBasicBlockRef
import llvm.LLVMBuildAShr
import llvm.LLVMBuildAdd
import llvm.LLVMBuildAddrSpaceCast
import llvm.LLVMBuildAggregateRet
import llvm.LLVMBuildAlloca
import llvm.LLVMBuildAnd
import llvm.LLVMBuildArrayAlloca
import llvm.LLVMBuildArrayMalloc
import llvm.LLVMBuildAtomicCmpXchg
import llvm.LLVMBuildAtomicCmpXchgSyncScope
import llvm.LLVMBuildAtomicRMW
import llvm.LLVMBuildAtomicRMWSyncScope
import llvm.LLVMBuildBinOp
import llvm.LLVMBuildBitCast
import llvm.LLVMBuildBr
import llvm.LLVMBuildCall2
import llvm.LLVMBuildCallBr
import llvm.LLVMBuildCallWithOperandBundles
import llvm.LLVMBuildCast
import llvm.LLVMBuildCatchPad
import llvm.LLVMBuildCatchRet
import llvm.LLVMBuildCatchSwitch
import llvm.LLVMBuildCleanupPad
import llvm.LLVMBuildCleanupRet
import llvm.LLVMBuildCondBr
import llvm.LLVMBuildExactSDiv
import llvm.LLVMBuildExactUDiv
import llvm.LLVMBuildExtractElement
import llvm.LLVMBuildExtractValue
import llvm.LLVMBuildFAdd
import llvm.LLVMBuildFCmp
import llvm.LLVMBuildFDiv
import llvm.LLVMBuildFMul
import llvm.LLVMBuildFNeg
import llvm.LLVMBuildFPCast
import llvm.LLVMBuildFPExt
import llvm.LLVMBuildFPToSI
import llvm.LLVMBuildFPToUI
import llvm.LLVMBuildFPTrunc
import llvm.LLVMBuildFRem
import llvm.LLVMBuildFSub
import llvm.LLVMBuildFence
import llvm.LLVMBuildFenceSyncScope
import llvm.LLVMBuildFree
import llvm.LLVMBuildFreeze
import llvm.LLVMBuildGEP2
import llvm.LLVMBuildGEPWithNoWrapFlags
import llvm.LLVMBuildGlobalString
import llvm.LLVMBuildGlobalStringPtr
import llvm.LLVMBuildICmp
import llvm.LLVMBuildInBoundsGEP2
import llvm.LLVMBuildIndirectBr
import llvm.LLVMBuildInsertElement
import llvm.LLVMBuildInsertValue
import llvm.LLVMBuildIntCast
import llvm.LLVMBuildIntCast2
import llvm.LLVMBuildIntToPtr
import llvm.LLVMBuildInvoke2
import llvm.LLVMBuildInvokeWithOperandBundles
import llvm.LLVMBuildIsNotNull
import llvm.LLVMBuildIsNull
import llvm.LLVMBuildLShr
import llvm.LLVMBuildLandingPad
import llvm.LLVMBuildLoad2
import llvm.LLVMBuildMalloc
import llvm.LLVMBuildMemCpy
import llvm.LLVMBuildMemMove
import llvm.LLVMBuildMemSet
import llvm.LLVMBuildMul
import llvm.LLVMBuildNSWAdd
import llvm.LLVMBuildNSWMul
import llvm.LLVMBuildNSWNeg
import llvm.LLVMBuildNSWSub
import llvm.LLVMBuildNUWAdd
import llvm.LLVMBuildNUWMul
import llvm.LLVMBuildNUWNeg
import llvm.LLVMBuildNUWSub
import llvm.LLVMBuildNeg
import llvm.LLVMBuildNot
import llvm.LLVMBuildOr
import llvm.LLVMBuildPhi
import llvm.LLVMBuildPointerCast
import llvm.LLVMBuildPtrDiff2
import llvm.LLVMBuildPtrToInt
import llvm.LLVMBuildResume
import llvm.LLVMBuildRet
import llvm.LLVMBuildRetVoid
import llvm.LLVMBuildSDiv
import llvm.LLVMBuildSExt
import llvm.LLVMBuildSExtOrBitCast
import llvm.LLVMBuildSIToFP
import llvm.LLVMBuildSRem
import llvm.LLVMBuildSelect
import llvm.LLVMBuildShl
import llvm.LLVMBuildShuffleVector
import llvm.LLVMBuildStore
import llvm.LLVMBuildStructGEP2
import llvm.LLVMBuildSub
import llvm.LLVMBuildSwitch
import llvm.LLVMBuildTrunc
import llvm.LLVMBuildTruncOrBitCast
import llvm.LLVMBuildUDiv
import llvm.LLVMBuildUIToFP
import llvm.LLVMBuildURem
import llvm.LLVMBuildUnreachable
import llvm.LLVMBuildVAArg
import llvm.LLVMBuildXor
import llvm.LLVMBuildZExt
import llvm.LLVMBuildZExtOrBitCast
import llvm.LLVMFunctionType
import llvm.LLVMIntPredicate
import llvm.LLVMOpcode
import llvm.LLVMOperandBundleRef
import llvm.LLVMRealPredicate
import llvm.LLVMTypeRef
import llvm.LLVMValueRef
import org.jetbrains.kotlin.ir.types.IrType

@PublishedApi
internal inline fun NativeCodeGenerator.requireInstruction(
    instruction: String, value: LLVMValueRef?
): LLVMValueRef = requireNotNull(value) {
    "Could not emit $instruction during custom code generation of plugin '${backend.pluginId}'"
}

/** Builds an [`alloca`](https://llvm.org/docs/LangRef.html#alloca-instruction) instruction for the given LLVM type. */
inline fun NativeCodeGenerator.alloca(type: LLVMTypeRef, name: String? = null): LLVMValueRef =
    requireInstruction("alloca", LLVMBuildAlloca(functionBuilder, type, name))

/** Builds an [`alloca`](https://llvm.org/docs/LangRef.html#alloca-instruction) instruction for the materialized IR type. */
inline fun NativeCodeGenerator.alloca(type: IrType, name: String? = null): LLVMValueRef =
    alloca(materializeType(type), name)

/** Builds an [aggregate return](https://llvm.org/doxygen/group__LLVMCCoreInstructionBuilder.html#ga01e641df042229d80ff54de0092597e9) from multiple values. */
inline fun NativeCodeGenerator.aggregateRet(values: List<LLVMValueRef>): LLVMValueRef =
    requireInstruction("aggregateRet", LLVMBuildAggregateRet(functionBuilder, values.toCValues(), values.size))

/** Builds an unconditional [`br`](https://llvm.org/docs/LangRef.html#br-instruction) to the destination block. */
inline fun NativeCodeGenerator.br(dest: LLVMBasicBlockRef): LLVMValueRef =
    requireInstruction("br", LLVMBuildBr(functionBuilder, dest))

/** Builds a conditional [`br`](https://llvm.org/docs/LangRef.html#br-instruction) to one of two blocks. */
inline fun NativeCodeGenerator.condBr(
    condition: LLVMValueRef, thenBlock: LLVMBasicBlockRef, elseBlock: LLVMBasicBlockRef
): LLVMValueRef = requireInstruction("condBr", LLVMBuildCondBr(functionBuilder, condition, thenBlock, elseBlock))

/** Builds a [`switch`](https://llvm.org/docs/LangRef.html#switch-instruction) with a default destination. */
inline fun NativeCodeGenerator.switch(
    value: LLVMValueRef, elseBlock: LLVMBasicBlockRef, caseCount: Int = 0
): LLVMValueRef = requireInstruction("switch", LLVMBuildSwitch(functionBuilder, value, elseBlock, caseCount))

/** Builds an [`indirectbr`](https://llvm.org/docs/LangRef.html#indirectbr-instruction) through a computed address. */
inline fun NativeCodeGenerator.indirectBr(address: LLVMValueRef, destCount: Int = 0): LLVMValueRef =
    requireInstruction("indirectBr", LLVMBuildIndirectBr(functionBuilder, address, destCount))

/** Builds an [`unreachable`](https://llvm.org/docs/LangRef.html#unreachable-instruction) terminator. */
inline fun NativeCodeGenerator.unreachable(): LLVMValueRef =
    requireInstruction("unreachable", LLVMBuildUnreachable(functionBuilder))

/** Builds a [`resume`](https://llvm.org/docs/LangRef.html#resume-instruction) that propagates an exception. */
inline fun NativeCodeGenerator.resume(exception: LLVMValueRef): LLVMValueRef =
    requireInstruction("resume", LLVMBuildResume(functionBuilder, exception))

/** Builds a [`cleanupret`](https://llvm.org/docs/LangRef.html#cleanupret-instruction) from a cleanup pad. */
inline fun NativeCodeGenerator.cleanupRet(
    cleanupPad: LLVMValueRef, unwindBlock: LLVMBasicBlockRef
): LLVMValueRef = requireInstruction("cleanupRet", LLVMBuildCleanupRet(functionBuilder, cleanupPad, unwindBlock))

/** Builds a [`catchret`](https://llvm.org/docs/LangRef.html#catchret-instruction) from a catch pad. */
inline fun NativeCodeGenerator.catchRet(catchPad: LLVMValueRef, successor: LLVMBasicBlockRef): LLVMValueRef =
    requireInstruction("catchRet", LLVMBuildCatchRet(functionBuilder, catchPad, successor))

/** Builds a [`catchpad`](https://llvm.org/docs/LangRef.html#catchpad-instruction) for the given exception arguments. */
inline fun NativeCodeGenerator.catchPad(
    parentPad: LLVMValueRef, args: List<LLVMValueRef> = emptyList(), name: String? = null
): LLVMValueRef =
    requireInstruction("catchPad", LLVMBuildCatchPad(functionBuilder, parentPad, args.toCValues(), args.size, name))

/** Builds a [`cleanuppad`](https://llvm.org/docs/LangRef.html#cleanuppad-instruction) for the given exception arguments. */
inline fun NativeCodeGenerator.cleanupPad(
    parentPad: LLVMValueRef, args: List<LLVMValueRef> = emptyList(), name: String? = null
): LLVMValueRef =
    requireInstruction("cleanupPad", LLVMBuildCleanupPad(functionBuilder, parentPad, args.toCValues(), args.size, name))

/** Builds a [`catchswitch`](https://llvm.org/docs/LangRef.html#catchswitch-instruction) for dispatching exceptions. */
inline fun NativeCodeGenerator.catchSwitch(
    parentPad: LLVMValueRef, unwindBlock: LLVMBasicBlockRef, handlerCount: Int = 0, name: String? = null
): LLVMValueRef =
    requireInstruction("catchSwitch", LLVMBuildCatchSwitch(functionBuilder, parentPad, unwindBlock, handlerCount, name))

/** Builds an integer [`add`](https://llvm.org/docs/LangRef.html#add-instruction). */
inline fun NativeCodeGenerator.add(
    lhs: LLVMValueRef, rhs: LLVMValueRef, name: String? = null
): LLVMValueRef = requireInstruction("add", LLVMBuildAdd(functionBuilder, lhs, rhs, name))

/** Builds an integer [`add`](https://llvm.org/docs/LangRef.html#add-instruction) with the `nsw` flag. */
inline fun NativeCodeGenerator.nswAdd(
    lhs: LLVMValueRef, rhs: LLVMValueRef, name: String? = null
): LLVMValueRef = requireInstruction("nswAdd", LLVMBuildNSWAdd(functionBuilder, lhs, rhs, name))

/** Builds an integer [`add`](https://llvm.org/docs/LangRef.html#add-instruction) with the `nuw` flag. */
inline fun NativeCodeGenerator.nuwAdd(
    lhs: LLVMValueRef, rhs: LLVMValueRef, name: String? = null
): LLVMValueRef = requireInstruction("nuwAdd", LLVMBuildNUWAdd(functionBuilder, lhs, rhs, name))

/** Builds a floating-point [`fadd`](https://llvm.org/docs/LangRef.html#fadd-instruction). */
inline fun NativeCodeGenerator.fAdd(
    lhs: LLVMValueRef, rhs: LLVMValueRef, name: String? = null
): LLVMValueRef = requireInstruction("fAdd", LLVMBuildFAdd(functionBuilder, lhs, rhs, name))

/** Builds an integer [`sub`](https://llvm.org/docs/LangRef.html#sub-instruction). */
inline fun NativeCodeGenerator.sub(
    lhs: LLVMValueRef, rhs: LLVMValueRef, name: String? = null
): LLVMValueRef = requireInstruction("sub", LLVMBuildSub(functionBuilder, lhs, rhs, name))

/** Builds an integer [`sub`](https://llvm.org/docs/LangRef.html#sub-instruction) with the `nsw` flag. */
inline fun NativeCodeGenerator.nswSub(
    lhs: LLVMValueRef, rhs: LLVMValueRef, name: String? = null
): LLVMValueRef = requireInstruction("nswSub", LLVMBuildNSWSub(functionBuilder, lhs, rhs, name))

/** Builds an integer [`sub`](https://llvm.org/docs/LangRef.html#sub-instruction) with the `nuw` flag. */
inline fun NativeCodeGenerator.nuwSub(
    lhs: LLVMValueRef, rhs: LLVMValueRef, name: String? = null
): LLVMValueRef = requireInstruction("nuwSub", LLVMBuildNUWSub(functionBuilder, lhs, rhs, name))

/** Builds a floating-point [`fsub`](https://llvm.org/docs/LangRef.html#fsub-instruction). */
inline fun NativeCodeGenerator.fSub(
    lhs: LLVMValueRef, rhs: LLVMValueRef, name: String? = null
): LLVMValueRef = requireInstruction("fSub", LLVMBuildFSub(functionBuilder, lhs, rhs, name))

/** Builds an integer [`mul`](https://llvm.org/docs/LangRef.html#mul-instruction). */
inline fun NativeCodeGenerator.mul(
    lhs: LLVMValueRef, rhs: LLVMValueRef, name: String? = null
): LLVMValueRef = requireInstruction("mul", LLVMBuildMul(functionBuilder, lhs, rhs, name))

/** Builds an integer [`mul`](https://llvm.org/docs/LangRef.html#mul-instruction) with the `nsw` flag. */
inline fun NativeCodeGenerator.nswMul(
    lhs: LLVMValueRef, rhs: LLVMValueRef, name: String? = null
): LLVMValueRef = requireInstruction("nswMul", LLVMBuildNSWMul(functionBuilder, lhs, rhs, name))

/** Builds an integer [`mul`](https://llvm.org/docs/LangRef.html#mul-instruction) with the `nuw` flag. */
inline fun NativeCodeGenerator.nuwMul(
    lhs: LLVMValueRef, rhs: LLVMValueRef, name: String? = null
): LLVMValueRef = requireInstruction("nuwMul", LLVMBuildNUWMul(functionBuilder, lhs, rhs, name))

/** Builds a floating-point [`fmul`](https://llvm.org/docs/LangRef.html#fmul-instruction). */
inline fun NativeCodeGenerator.fMul(
    lhs: LLVMValueRef, rhs: LLVMValueRef, name: String? = null
): LLVMValueRef = requireInstruction("fMul", LLVMBuildFMul(functionBuilder, lhs, rhs, name))

/** Builds an unsigned integer [`udiv`](https://llvm.org/docs/LangRef.html#udiv-instruction). */
inline fun NativeCodeGenerator.uDiv(
    lhs: LLVMValueRef, rhs: LLVMValueRef, name: String? = null
): LLVMValueRef = requireInstruction("uDiv", LLVMBuildUDiv(functionBuilder, lhs, rhs, name))

/** Builds an exact unsigned integer [`udiv`](https://llvm.org/docs/LangRef.html#udiv-instruction). */
inline fun NativeCodeGenerator.exactUDiv(
    lhs: LLVMValueRef, rhs: LLVMValueRef, name: String? = null
): LLVMValueRef = requireInstruction("exactUDiv", LLVMBuildExactUDiv(functionBuilder, lhs, rhs, name))

/** Builds a signed integer [`sdiv`](https://llvm.org/docs/LangRef.html#sdiv-instruction). */
inline fun NativeCodeGenerator.sDiv(
    lhs: LLVMValueRef, rhs: LLVMValueRef, name: String? = null
): LLVMValueRef = requireInstruction("sDiv", LLVMBuildSDiv(functionBuilder, lhs, rhs, name))

/** Builds an exact signed integer [`sdiv`](https://llvm.org/docs/LangRef.html#sdiv-instruction). */
inline fun NativeCodeGenerator.exactSDiv(
    lhs: LLVMValueRef, rhs: LLVMValueRef, name: String? = null
): LLVMValueRef = requireInstruction("exactSDiv", LLVMBuildExactSDiv(functionBuilder, lhs, rhs, name))

/** Builds a floating-point [`fdiv`](https://llvm.org/docs/LangRef.html#fdiv-instruction). */
inline fun NativeCodeGenerator.fDiv(
    lhs: LLVMValueRef, rhs: LLVMValueRef, name: String? = null
): LLVMValueRef = requireInstruction("fDiv", LLVMBuildFDiv(functionBuilder, lhs, rhs, name))

/** Builds an unsigned integer [`urem`](https://llvm.org/docs/LangRef.html#urem-instruction). */
inline fun NativeCodeGenerator.uRem(
    lhs: LLVMValueRef, rhs: LLVMValueRef, name: String? = null
): LLVMValueRef = requireInstruction("uRem", LLVMBuildURem(functionBuilder, lhs, rhs, name))

/** Builds a signed integer [`srem`](https://llvm.org/docs/LangRef.html#srem-instruction). */
inline fun NativeCodeGenerator.sRem(
    lhs: LLVMValueRef, rhs: LLVMValueRef, name: String? = null
): LLVMValueRef = requireInstruction("sRem", LLVMBuildSRem(functionBuilder, lhs, rhs, name))

/** Builds a floating-point [`frem`](https://llvm.org/docs/LangRef.html#frem-instruction). */
inline fun NativeCodeGenerator.fRem(
    lhs: LLVMValueRef, rhs: LLVMValueRef, name: String? = null
): LLVMValueRef = requireInstruction("fRem", LLVMBuildFRem(functionBuilder, lhs, rhs, name))

/** Builds a left-shift [`shl`](https://llvm.org/docs/LangRef.html#shl-instruction). */
inline fun NativeCodeGenerator.shl(
    lhs: LLVMValueRef, rhs: LLVMValueRef, name: String? = null
): LLVMValueRef = requireInstruction("shl", LLVMBuildShl(functionBuilder, lhs, rhs, name))

/** Builds a logical right-shift [`lshr`](https://llvm.org/docs/LangRef.html#lshr-instruction). */
inline fun NativeCodeGenerator.lShr(
    lhs: LLVMValueRef, rhs: LLVMValueRef, name: String? = null
): LLVMValueRef = requireInstruction("lShr", LLVMBuildLShr(functionBuilder, lhs, rhs, name))

/** Builds an arithmetic right-shift [`ashr`](https://llvm.org/docs/LangRef.html#ashr-instruction). */
inline fun NativeCodeGenerator.aShr(
    lhs: LLVMValueRef, rhs: LLVMValueRef, name: String? = null
): LLVMValueRef = requireInstruction("aShr", LLVMBuildAShr(functionBuilder, lhs, rhs, name))

/** Builds a bitwise [`and`](https://llvm.org/docs/LangRef.html#and-instruction). */
inline fun NativeCodeGenerator.and(
    lhs: LLVMValueRef, rhs: LLVMValueRef, name: String? = null
): LLVMValueRef = requireInstruction("and", LLVMBuildAnd(functionBuilder, lhs, rhs, name))

/** Builds a bitwise [`or`](https://llvm.org/docs/LangRef.html#or-instruction). */
inline fun NativeCodeGenerator.or(
    lhs: LLVMValueRef, rhs: LLVMValueRef, name: String? = null
): LLVMValueRef = requireInstruction("or", LLVMBuildOr(functionBuilder, lhs, rhs, name))

/** Builds a bitwise [`xor`](https://llvm.org/docs/LangRef.html#xor-instruction). */
inline fun NativeCodeGenerator.xor(
    lhs: LLVMValueRef, rhs: LLVMValueRef, name: String? = null
): LLVMValueRef = requireInstruction("xor", LLVMBuildXor(functionBuilder, lhs, rhs, name))

/** Builds the [binary instruction](https://llvm.org/doxygen/group__LLVMCCoreInstructionBuilder.html#ga82e1c8b572389e3c702f7e2543b11684) selected by the opcode. */
inline fun NativeCodeGenerator.binOp(
    opcode: LLVMOpcode, lhs: LLVMValueRef, rhs: LLVMValueRef, name: String? = null
): LLVMValueRef = requireInstruction("binOp", LLVMBuildBinOp(functionBuilder, opcode, lhs, rhs, name))

/** Builds integer negation as a [`sub`](https://llvm.org/docs/LangRef.html#sub-instruction) from zero. */
inline fun NativeCodeGenerator.neg(value: LLVMValueRef, name: String? = null): LLVMValueRef =
    requireInstruction("neg", LLVMBuildNeg(functionBuilder, value, name))

/** Builds integer negation as a [`sub`](https://llvm.org/docs/LangRef.html#sub-instruction) with the `nsw` flag. */
inline fun NativeCodeGenerator.nswNeg(value: LLVMValueRef, name: String? = null): LLVMValueRef =
    requireInstruction("nswNeg", LLVMBuildNSWNeg(functionBuilder, value, name))

/** Builds integer negation as a [`sub`](https://llvm.org/docs/LangRef.html#sub-instruction) with the `nuw` flag. */
inline fun NativeCodeGenerator.nuwNeg(value: LLVMValueRef, name: String? = null): LLVMValueRef =
    requireInstruction("nuwNeg", LLVMBuildNUWNeg(functionBuilder, value, name))

/** Builds floating-point negation with [`fneg`](https://llvm.org/docs/LangRef.html#fneg-instruction). */
inline fun NativeCodeGenerator.fNeg(value: LLVMValueRef, name: String? = null): LLVMValueRef =
    requireInstruction("fNeg", LLVMBuildFNeg(functionBuilder, value, name))

/** Builds bitwise complement as an [`xor`](https://llvm.org/docs/LangRef.html#xor-instruction) with all-one bits. */
inline fun NativeCodeGenerator.not(value: LLVMValueRef, name: String? = null): LLVMValueRef =
    requireInstruction("not", LLVMBuildNot(functionBuilder, value, name))

/** Builds a [`malloc`](https://llvm.org/doxygen/group__LLVMCCoreInstructionBuilder.html#gac222a183a7cca6dc0e16b74e9ce73f3d) call for one value of the LLVM type. */
inline fun NativeCodeGenerator.malloc(type: LLVMTypeRef, name: String? = null): LLVMValueRef =
    requireInstruction("malloc", LLVMBuildMalloc(functionBuilder, type, name))

/** Builds a [`malloc`](https://llvm.org/doxygen/group__LLVMCCoreInstructionBuilder.html#gac222a183a7cca6dc0e16b74e9ce73f3d) call for one value of the materialized IR type. */
inline fun NativeCodeGenerator.malloc(type: IrType, name: String? = null): LLVMValueRef =
    malloc(materializeType(type), name)

/** Builds a [`malloc`](https://llvm.org/doxygen/group__LLVMCCoreInstructionBuilder.html#ga9c21366bc30028f7080eb61e3c04e435) call for an array of the LLVM type. */
inline fun NativeCodeGenerator.arrayMalloc(
    type: LLVMTypeRef, count: LLVMValueRef, name: String? = null
): LLVMValueRef = requireInstruction("arrayMalloc", LLVMBuildArrayMalloc(functionBuilder, type, count, name))

/** Builds a [`malloc`](https://llvm.org/doxygen/group__LLVMCCoreInstructionBuilder.html#ga9c21366bc30028f7080eb61e3c04e435) call for an array of the materialized IR type. */
inline fun NativeCodeGenerator.arrayMalloc(type: IrType, count: LLVMValueRef, name: String? = null): LLVMValueRef =
    arrayMalloc(materializeType(type), count, name)

/** Builds an [`llvm.memset`](https://llvm.org/docs/LangRef.html#llvm-memset-intrinsics) intrinsic call. */
inline fun NativeCodeGenerator.memSet(
    pointer: LLVMValueRef, value: LLVMValueRef, size: LLVMValueRef, alignment: Int
): LLVMValueRef = requireInstruction("memSet", LLVMBuildMemSet(functionBuilder, pointer, value, size, alignment))

/** Builds an [`llvm.memcpy`](https://llvm.org/docs/LangRef.html#llvm-memcpy-intrinsic) intrinsic call. */
inline fun NativeCodeGenerator.memCpy(
    dest: LLVMValueRef, destAlignment: Int, source: LLVMValueRef, sourceAlignment: Int, size: LLVMValueRef
): LLVMValueRef =
    requireInstruction("memCpy", LLVMBuildMemCpy(functionBuilder, dest, destAlignment, source, sourceAlignment, size))

/** Builds an [`llvm.memmove`](https://llvm.org/docs/LangRef.html#llvm-memmove-intrinsic) intrinsic call. */
inline fun NativeCodeGenerator.memMove(
    dest: LLVMValueRef, destAlignment: Int, source: LLVMValueRef, sourceAlignment: Int, size: LLVMValueRef
): LLVMValueRef =
    requireInstruction("memMove", LLVMBuildMemMove(functionBuilder, dest, destAlignment, source, sourceAlignment, size))

/** Builds an [`alloca`](https://llvm.org/docs/LangRef.html#alloca-instruction) for an array of the LLVM type. */
inline fun NativeCodeGenerator.arrayAlloca(
    type: LLVMTypeRef, count: LLVMValueRef, name: String? = null
): LLVMValueRef = requireInstruction("arrayAlloca", LLVMBuildArrayAlloca(functionBuilder, type, count, name))

/** Builds an [`alloca`](https://llvm.org/docs/LangRef.html#alloca-instruction) for an array of the materialized IR type. */
inline fun NativeCodeGenerator.arrayAlloca(type: IrType, count: LLVMValueRef, name: String? = null): LLVMValueRef =
    arrayAlloca(materializeType(type), count, name)

/** Builds a [`free`](https://llvm.org/doxygen/group__LLVMCCoreInstructionBuilder.html#ga919d6fd695c14e5ddc917f5792462283) call for the pointer. */
inline fun NativeCodeGenerator.free(pointer: LLVMValueRef): LLVMValueRef =
    requireInstruction("free", LLVMBuildFree(functionBuilder, pointer))

/** Builds a [`load`](https://llvm.org/docs/LangRef.html#load-instruction) of the given LLVM type from memory. */
inline fun NativeCodeGenerator.load(
    type: LLVMTypeRef, pointer: LLVMValueRef, name: String? = null
): LLVMValueRef = requireInstruction("load", LLVMBuildLoad2(functionBuilder, type, pointer, name))

/** Builds a [`load`](https://llvm.org/docs/LangRef.html#load-instruction) of the materialized IR type from memory. */
inline fun NativeCodeGenerator.load(type: IrType, pointer: LLVMValueRef, name: String? = null): LLVMValueRef =
    load(materializeType(type), pointer, name)

/** Builds a [`store`](https://llvm.org/docs/LangRef.html#store-instruction) of a value to memory. */
inline fun NativeCodeGenerator.store(value: LLVMValueRef, pointer: LLVMValueRef): LLVMValueRef =
    requireInstruction("store", LLVMBuildStore(functionBuilder, value, pointer))

/** Builds a [`phi`](https://llvm.org/docs/LangRef.html#phi-instruction) node of the given LLVM type. */
inline fun NativeCodeGenerator.phi(type: LLVMTypeRef, name: String? = null): LLVMValueRef =
    requireInstruction("phi", LLVMBuildPhi(functionBuilder, type, name))

/** Builds a [`phi`](https://llvm.org/docs/LangRef.html#phi-instruction) node of the materialized IR type. */
inline fun NativeCodeGenerator.phi(type: IrType, name: String? = null): LLVMValueRef = phi(materializeType(type), name)

/** Builds a void [`ret`](https://llvm.org/docs/LangRef.html#ret-instruction) terminator. */
inline fun NativeCodeGenerator.ret(): LLVMValueRef = requireInstruction("ret", LLVMBuildRetVoid(functionBuilder))

/** Builds a [`ret`](https://llvm.org/docs/LangRef.html#ret-instruction) terminator for the given value. */
inline fun NativeCodeGenerator.ret(value: LLVMValueRef): LLVMValueRef =
    requireInstruction("ret", LLVMBuildRet(functionBuilder, value))

/** Builds a [`ret`](https://llvm.org/docs/LangRef.html#ret-instruction) terminator for the Kotlin `Unit` instance. */
inline fun NativeCodeGenerator.retUnit(): LLVMValueRef = ret(unitInstance)

/** Builds a [`getelementptr`](https://llvm.org/docs/LangRef.html#getelementptr-instruction) for the LLVM source type. */
inline fun NativeCodeGenerator.gep( // @formatter:off
    type: LLVMTypeRef,
    pointer: LLVMValueRef,
    indices: List<LLVMValueRef> = emptyList(),
    name: String? = null
): LLVMValueRef =
    requireInstruction(
        "gep",
        LLVMBuildGEP2(functionBuilder, type, pointer, indices.toCValues(), indices.size, name)
    ) // @formatter:on

/** Builds a [`getelementptr`](https://llvm.org/docs/LangRef.html#getelementptr-instruction) for the materialized IR source type. */
inline fun NativeCodeGenerator.gep( // @formatter:off
    type: IrType,
    pointer: LLVMValueRef,
    indices: List<LLVMValueRef> = emptyList(),
    name: String? = null
): LLVMValueRef = gep(materializeType(type), pointer, indices, name) // @formatter:on

/** Builds an `inbounds` [`getelementptr`](https://llvm.org/docs/LangRef.html#getelementptr-instruction) for the LLVM source type. */
inline fun NativeCodeGenerator.inBoundsGep( // @formatter:off
    type: LLVMTypeRef,
    pointer: LLVMValueRef,
    indices: List<LLVMValueRef> = emptyList(),
    name: String? = null
): LLVMValueRef =
    requireInstruction(
        "inBoundsGep",
        LLVMBuildInBoundsGEP2(functionBuilder, type, pointer, indices.toCValues(), indices.size, name)
    ) // @formatter:on

/** Builds an `inbounds` [`getelementptr`](https://llvm.org/docs/LangRef.html#getelementptr-instruction) for the materialized IR source type. */
inline fun NativeCodeGenerator.inBoundsGep( // @formatter:off
    type: IrType,
    pointer: LLVMValueRef,
    indices: List<LLVMValueRef> = emptyList(),
    name: String? = null
): LLVMValueRef = inBoundsGep(materializeType(type), pointer, indices, name) // @formatter:on

/** Builds a flagged [`getelementptr`](https://llvm.org/docs/LangRef.html#getelementptr-instruction) for the LLVM source type. */
inline fun NativeCodeGenerator.gepWithNoWrapFlags( // @formatter:off
    type: LLVMTypeRef,
    pointer: LLVMValueRef,
    indices: List<LLVMValueRef> = emptyList(),
    flags: Int,
    name: String? = null
): LLVMValueRef = requireInstruction("gepWithNoWrapFlags", LLVMBuildGEPWithNoWrapFlags(
    functionBuilder,
    type,
    pointer,
    indices.toCValues(),
    indices.size,
    name,
    flags
)
) // @formatter:on

/** Builds a flagged [`getelementptr`](https://llvm.org/docs/LangRef.html#getelementptr-instruction) for the materialized IR source type. */
inline fun NativeCodeGenerator.gepWithNoWrapFlags( // @formatter:off
    type: IrType,
    pointer: LLVMValueRef,
    indices: List<LLVMValueRef> = emptyList(),
    flags: Int,
    name: String? = null
): LLVMValueRef = gepWithNoWrapFlags(materializeType(type), pointer, indices, flags, name) // @formatter:on

/** Builds a [`getelementptr`](https://llvm.org/docs/LangRef.html#getelementptr-instruction) to a field of the LLVM structure type. */
inline fun NativeCodeGenerator.structGep( // @formatter:off
    type: LLVMTypeRef,
    pointer: LLVMValueRef,
    index: Int,
    name: String? = null
): LLVMValueRef =
    requireInstruction("structGep", LLVMBuildStructGEP2(functionBuilder, type, pointer, index, name)) // @formatter:on

/** Builds a [`getelementptr`](https://llvm.org/docs/LangRef.html#getelementptr-instruction) to a field of the materialized IR structure type. */
inline fun NativeCodeGenerator.structGep( // @formatter:off
    type: IrType,
    pointer: LLVMValueRef,
    index: Int,
    name: String? = null
): LLVMValueRef = structGep(materializeType(type), pointer, index, name) // @formatter:on

/** Builds a private null-terminated [global string](https://llvm.org/docs/LangRef.html#global-variables). */
inline fun NativeCodeGenerator.globalString(value: String, name: String? = null): LLVMValueRef =
    requireInstruction("globalString", LLVMBuildGlobalString(functionBuilder, value, name))

/** Builds a pointer to a private null-terminated [global string](https://llvm.org/docs/LangRef.html#global-variables). */
inline fun NativeCodeGenerator.globalStringPtr(value: String, name: String? = null): LLVMValueRef =
    requireInstruction("globalStringPtr", LLVMBuildGlobalStringPtr(functionBuilder, value, name))

/** Builds a [`trunc`](https://llvm.org/docs/LangRef.html#trunc-to-instruction) to the LLVM integer type. */
inline fun NativeCodeGenerator.trunc(
    value: LLVMValueRef, type: LLVMTypeRef, name: String? = null
): LLVMValueRef = requireInstruction("trunc", LLVMBuildTrunc(functionBuilder, value, type, name))

/** Builds a [`trunc`](https://llvm.org/docs/LangRef.html#trunc-to-instruction) to the materialized IR integer type. */
inline fun NativeCodeGenerator.trunc(value: LLVMValueRef, type: IrType, name: String? = null): LLVMValueRef =
    trunc(value, materializeType(type), name)

/** Builds a zero-extending [`zext`](https://llvm.org/docs/LangRef.html#zext-to-instruction) to the LLVM integer type. */
inline fun NativeCodeGenerator.zExt(
    value: LLVMValueRef, type: LLVMTypeRef, name: String? = null
): LLVMValueRef = requireInstruction("zExt", LLVMBuildZExt(functionBuilder, value, type, name))

/** Builds a zero-extending [`zext`](https://llvm.org/docs/LangRef.html#zext-to-instruction) to the materialized IR integer type. */
inline fun NativeCodeGenerator.zExt(value: LLVMValueRef, type: IrType, name: String? = null): LLVMValueRef =
    zExt(value, materializeType(type), name)

/** Builds a sign-extending [`sext`](https://llvm.org/docs/LangRef.html#sext-to-instruction) to the LLVM integer type. */
inline fun NativeCodeGenerator.sExt(
    value: LLVMValueRef, type: LLVMTypeRef, name: String? = null
): LLVMValueRef = requireInstruction("sExt", LLVMBuildSExt(functionBuilder, value, type, name))

/** Builds a sign-extending [`sext`](https://llvm.org/docs/LangRef.html#sext-to-instruction) to the materialized IR integer type. */
inline fun NativeCodeGenerator.sExt(value: LLVMValueRef, type: IrType, name: String? = null): LLVMValueRef =
    sExt(value, materializeType(type), name)

/** Builds an unsigned floating-point-to-integer [`fptoui`](https://llvm.org/docs/LangRef.html#fptoui-to-instruction) conversion. */
inline fun NativeCodeGenerator.fpToUI(
    value: LLVMValueRef, type: LLVMTypeRef, name: String? = null
): LLVMValueRef = requireInstruction("fpToUI", LLVMBuildFPToUI(functionBuilder, value, type, name))

/** Builds an unsigned [`fptoui`](https://llvm.org/docs/LangRef.html#fptoui-to-instruction) conversion to the materialized IR type. */
inline fun NativeCodeGenerator.fpToUI(value: LLVMValueRef, type: IrType, name: String? = null): LLVMValueRef =
    fpToUI(value, materializeType(type), name)

/** Builds a signed floating-point-to-integer [`fptosi`](https://llvm.org/docs/LangRef.html#fptosi-to-instruction) conversion. */
inline fun NativeCodeGenerator.fpToSI(
    value: LLVMValueRef, type: LLVMTypeRef, name: String? = null
): LLVMValueRef = requireInstruction("fpToSI", LLVMBuildFPToSI(functionBuilder, value, type, name))

/** Builds a signed [`fptosi`](https://llvm.org/docs/LangRef.html#fptosi-to-instruction) conversion to the materialized IR type. */
inline fun NativeCodeGenerator.fpToSI(value: LLVMValueRef, type: IrType, name: String? = null): LLVMValueRef =
    fpToSI(value, materializeType(type), name)

/** Builds an unsigned integer-to-floating-point [`uitofp`](https://llvm.org/docs/LangRef.html#uitofp-to-instruction) conversion. */
inline fun NativeCodeGenerator.uiToFP(
    value: LLVMValueRef, type: LLVMTypeRef, name: String? = null
): LLVMValueRef = requireInstruction("uiToFP", LLVMBuildUIToFP(functionBuilder, value, type, name))

/** Builds an unsigned [`uitofp`](https://llvm.org/docs/LangRef.html#uitofp-to-instruction) conversion to the materialized IR type. */
inline fun NativeCodeGenerator.uiToFP(value: LLVMValueRef, type: IrType, name: String? = null): LLVMValueRef =
    uiToFP(value, materializeType(type), name)

/** Builds a signed integer-to-floating-point [`sitofp`](https://llvm.org/docs/LangRef.html#sitofp-to-instruction) conversion. */
inline fun NativeCodeGenerator.siToFP(
    value: LLVMValueRef, type: LLVMTypeRef, name: String? = null
): LLVMValueRef = requireInstruction("siToFP", LLVMBuildSIToFP(functionBuilder, value, type, name))

/** Builds a signed [`sitofp`](https://llvm.org/docs/LangRef.html#sitofp-to-instruction) conversion to the materialized IR type. */
inline fun NativeCodeGenerator.siToFP(value: LLVMValueRef, type: IrType, name: String? = null): LLVMValueRef =
    siToFP(value, materializeType(type), name)

/** Builds an [`fptrunc`](https://llvm.org/docs/LangRef.html#fptrunc-to-instruction) to the LLVM floating-point type. */
inline fun NativeCodeGenerator.fpTrunc(
    value: LLVMValueRef, type: LLVMTypeRef, name: String? = null
): LLVMValueRef = requireInstruction("fpTrunc", LLVMBuildFPTrunc(functionBuilder, value, type, name))

/** Builds an [`fptrunc`](https://llvm.org/docs/LangRef.html#fptrunc-to-instruction) to the materialized IR type. */
inline fun NativeCodeGenerator.fpTrunc(value: LLVMValueRef, type: IrType, name: String? = null): LLVMValueRef =
    fpTrunc(value, materializeType(type), name)

/** Builds an [`fpext`](https://llvm.org/docs/LangRef.html#fpext-to-instruction) to the LLVM floating-point type. */
inline fun NativeCodeGenerator.fpExt(
    value: LLVMValueRef, type: LLVMTypeRef, name: String? = null
): LLVMValueRef = requireInstruction("fpExt", LLVMBuildFPExt(functionBuilder, value, type, name))

/** Builds an [`fpext`](https://llvm.org/docs/LangRef.html#fpext-to-instruction) to the materialized IR type. */
inline fun NativeCodeGenerator.fpExt(value: LLVMValueRef, type: IrType, name: String? = null): LLVMValueRef =
    fpExt(value, materializeType(type), name)

/** Builds a pointer-to-integer [`ptrtoint`](https://llvm.org/docs/LangRef.html#ptrtoint-to-instruction) conversion. */
inline fun NativeCodeGenerator.ptrToInt(
    value: LLVMValueRef, type: LLVMTypeRef, name: String? = null
): LLVMValueRef = requireInstruction("ptrToInt", LLVMBuildPtrToInt(functionBuilder, value, type, name))

/** Builds a [`ptrtoint`](https://llvm.org/docs/LangRef.html#ptrtoint-to-instruction) conversion to the materialized IR type. */
inline fun NativeCodeGenerator.ptrToInt(value: LLVMValueRef, type: IrType, name: String? = null): LLVMValueRef =
    ptrToInt(value, materializeType(type), name)

/** Builds an integer-to-pointer [`inttoptr`](https://llvm.org/docs/LangRef.html#inttoptr-to-instruction) conversion. */
inline fun NativeCodeGenerator.intToPtr(
    value: LLVMValueRef, type: LLVMTypeRef, name: String? = null
): LLVMValueRef = requireInstruction("intToPtr", LLVMBuildIntToPtr(functionBuilder, value, type, name))

/** Builds an [`inttoptr`](https://llvm.org/docs/LangRef.html#inttoptr-to-instruction) conversion to the materialized IR type. */
inline fun NativeCodeGenerator.intToPtr(value: LLVMValueRef, type: IrType, name: String? = null): LLVMValueRef =
    intToPtr(value, materializeType(type), name)

/** Builds a [`bitcast`](https://llvm.org/docs/LangRef.html#bitcast-to-instruction) to the LLVM type. */
inline fun NativeCodeGenerator.bitCast(
    value: LLVMValueRef, type: LLVMTypeRef, name: String? = null
): LLVMValueRef = requireInstruction("bitCast", LLVMBuildBitCast(functionBuilder, value, type, name))

/** Builds a [`bitcast`](https://llvm.org/docs/LangRef.html#bitcast-to-instruction) to the materialized IR type. */
inline fun NativeCodeGenerator.bitCast(value: LLVMValueRef, type: IrType, name: String? = null): LLVMValueRef =
    bitCast(value, materializeType(type), name)

/** Builds an [`addrspacecast`](https://llvm.org/docs/LangRef.html#addrspacecast-to-instruction) to the LLVM pointer type. */
inline fun NativeCodeGenerator.addrSpaceCast(
    value: LLVMValueRef, type: LLVMTypeRef, name: String? = null
): LLVMValueRef = requireInstruction("addrSpaceCast", LLVMBuildAddrSpaceCast(functionBuilder, value, type, name))

/** Builds an [`addrspacecast`](https://llvm.org/docs/LangRef.html#addrspacecast-to-instruction) to the materialized IR type. */
inline fun NativeCodeGenerator.addrSpaceCast(value: LLVMValueRef, type: IrType, name: String? = null): LLVMValueRef =
    addrSpaceCast(value, materializeType(type), name)

/** Builds the appropriate zero-extension or bitcast [conversion](https://llvm.org/doxygen/group__LLVMCCoreInstructionBuilder.html#ga70fa4a5ac3fdf1c5954b7c2cedc69492). */
inline fun NativeCodeGenerator.zExtOrBitCast(
    value: LLVMValueRef, type: LLVMTypeRef, name: String? = null
): LLVMValueRef = requireInstruction("zExtOrBitCast", LLVMBuildZExtOrBitCast(functionBuilder, value, type, name))

/** Builds the appropriate zero-extension or bitcast [conversion](https://llvm.org/doxygen/group__LLVMCCoreInstructionBuilder.html#ga70fa4a5ac3fdf1c5954b7c2cedc69492) to the materialized IR type. */
inline fun NativeCodeGenerator.zExtOrBitCast(value: LLVMValueRef, type: IrType, name: String? = null): LLVMValueRef =
    zExtOrBitCast(value, materializeType(type), name)

/** Builds the appropriate sign-extension or bitcast [conversion](https://llvm.org/doxygen/group__LLVMCCoreInstructionBuilder.html#ga4063e1e9444a46d1c03f29b4e63076b1). */
inline fun NativeCodeGenerator.sExtOrBitCast(
    value: LLVMValueRef, type: LLVMTypeRef, name: String? = null
): LLVMValueRef = requireInstruction("sExtOrBitCast", LLVMBuildSExtOrBitCast(functionBuilder, value, type, name))

/** Builds the appropriate sign-extension or bitcast [conversion](https://llvm.org/doxygen/group__LLVMCCoreInstructionBuilder.html#ga4063e1e9444a46d1c03f29b4e63076b1) to the materialized IR type. */
inline fun NativeCodeGenerator.sExtOrBitCast(value: LLVMValueRef, type: IrType, name: String? = null): LLVMValueRef =
    sExtOrBitCast(value, materializeType(type), name)

/** Builds the appropriate truncation or bitcast [conversion](https://llvm.org/doxygen/group__LLVMCCoreInstructionBuilder.html#ga7a1de55766a0d468c52ca85f1230d84f). */
inline fun NativeCodeGenerator.truncOrBitCast(
    value: LLVMValueRef, type: LLVMTypeRef, name: String? = null
): LLVMValueRef = requireInstruction("truncOrBitCast", LLVMBuildTruncOrBitCast(functionBuilder, value, type, name))

/** Builds the appropriate truncation or bitcast [conversion](https://llvm.org/doxygen/group__LLVMCCoreInstructionBuilder.html#ga7a1de55766a0d468c52ca85f1230d84f) to the materialized IR type. */
inline fun NativeCodeGenerator.truncOrBitCast(value: LLVMValueRef, type: IrType, name: String? = null): LLVMValueRef =
    truncOrBitCast(value, materializeType(type), name)

/** Builds the [conversion instruction](https://llvm.org/doxygen/group__LLVMCCoreInstructionBuilder.html#ga06564129a78e33346547ec2c86db39cb) selected by the opcode. */
inline fun NativeCodeGenerator.cast(
    opcode: LLVMOpcode, value: LLVMValueRef, type: LLVMTypeRef, name: String? = null
): LLVMValueRef = requireInstruction("cast", LLVMBuildCast(functionBuilder, opcode, value, type, name))

/** Builds the selected [conversion instruction](https://llvm.org/doxygen/group__LLVMCCoreInstructionBuilder.html#ga06564129a78e33346547ec2c86db39cb) to the materialized IR type. */
inline fun NativeCodeGenerator.cast(
    opcode: LLVMOpcode, value: LLVMValueRef, type: IrType, name: String? = null
): LLVMValueRef = cast(opcode, value, materializeType(type), name)

/** Builds the appropriate pointer [cast](https://llvm.org/doxygen/group__LLVMCCoreInstructionBuilder.html#gacc18ee2f7c2d1edfefe2c0e90e6012fb) to the LLVM type. */
inline fun NativeCodeGenerator.pointerCast(
    value: LLVMValueRef, type: LLVMTypeRef, name: String? = null
): LLVMValueRef = requireInstruction("pointerCast", LLVMBuildPointerCast(functionBuilder, value, type, name))

/** Builds the appropriate pointer [cast](https://llvm.org/doxygen/group__LLVMCCoreInstructionBuilder.html#gacc18ee2f7c2d1edfefe2c0e90e6012fb) to the materialized IR type. */
inline fun NativeCodeGenerator.pointerCast(value: LLVMValueRef, type: IrType, name: String? = null): LLVMValueRef =
    pointerCast(value, materializeType(type), name)

/** Builds a signed or unsigned integer [cast](https://llvm.org/doxygen/group__LLVMCCoreInstructionBuilder.html#ga67f7caf733730df8de9ba10f9f136abc) to the LLVM type. */
inline fun NativeCodeGenerator.intCast(
    value: LLVMValueRef, type: LLVMTypeRef, isSigned: Int, name: String? = null
): LLVMValueRef = requireInstruction("intCast", LLVMBuildIntCast2(functionBuilder, value, type, isSigned, name))

/** Builds a signed or unsigned integer [cast](https://llvm.org/doxygen/group__LLVMCCoreInstructionBuilder.html#ga67f7caf733730df8de9ba10f9f136abc) to the materialized IR type. */
inline fun NativeCodeGenerator.intCast(
    value: LLVMValueRef, type: IrType, isSigned: Int, name: String? = null
): LLVMValueRef = intCast(value, materializeType(type), isSigned, name)

/** Builds an integer [cast](https://llvm.org/doxygen/group__LLVMCCoreInstructionBuilder.html#ga52c827cd1e37f9dc8247d106333e4f1d) to the LLVM type. */
inline fun NativeCodeGenerator.intCast(
    value: LLVMValueRef, type: LLVMTypeRef, name: String? = null
): LLVMValueRef = requireInstruction("intCast", LLVMBuildIntCast(functionBuilder, value, type, name))

/** Builds an integer [cast](https://llvm.org/doxygen/group__LLVMCCoreInstructionBuilder.html#ga52c827cd1e37f9dc8247d106333e4f1d) to the materialized IR type. */
inline fun NativeCodeGenerator.intCast(value: LLVMValueRef, type: IrType, name: String? = null): LLVMValueRef =
    intCast(value, materializeType(type), name)

/** Builds the appropriate floating-point [cast](https://llvm.org/doxygen/group__LLVMCCoreInstructionBuilder.html#ga7459696d41d8a8206f1aeb09884d522d) to the LLVM type. */
inline fun NativeCodeGenerator.fpCast(
    value: LLVMValueRef, type: LLVMTypeRef, name: String? = null
): LLVMValueRef = requireInstruction("fpCast", LLVMBuildFPCast(functionBuilder, value, type, name))

/** Builds the appropriate floating-point [cast](https://llvm.org/doxygen/group__LLVMCCoreInstructionBuilder.html#ga7459696d41d8a8206f1aeb09884d522d) to the materialized IR type. */
inline fun NativeCodeGenerator.fpCast(value: LLVMValueRef, type: IrType, name: String? = null): LLVMValueRef =
    fpCast(value, materializeType(type), name)

/** Builds an integer [`icmp`](https://llvm.org/docs/LangRef.html#icmp-instruction) using the given predicate. */
inline fun NativeCodeGenerator.iCmp(
    predicate: LLVMIntPredicate, lhs: LLVMValueRef, rhs: LLVMValueRef, name: String? = null
): LLVMValueRef = requireInstruction("iCmp", LLVMBuildICmp(functionBuilder, predicate, lhs, rhs, name))

/** Builds a floating-point [`fcmp`](https://llvm.org/docs/LangRef.html#fcmp-instruction) using the given predicate. */
inline fun NativeCodeGenerator.fCmp(
    predicate: LLVMRealPredicate, lhs: LLVMValueRef, rhs: LLVMValueRef, name: String? = null
): LLVMValueRef = requireInstruction("fCmp", LLVMBuildFCmp(functionBuilder, predicate, lhs, rhs, name))

/** Builds a [`call`](https://llvm.org/docs/LangRef.html#call-instruction) with an LLVM function signature. */
inline fun NativeCodeGenerator.call( // @formatter:off
    address: LLVMValueRef,
    returnType: LLVMTypeRef,
    paramTypes: List<LLVMTypeRef> = emptyList(),
    args: List<LLVMValueRef> = emptyList(),
    name: String? = null
): LLVMValueRef { // @formatter:on
    val type = LLVMFunctionType(returnType, paramTypes.toCValues(), paramTypes.size, 0)
    return requireInstruction("call", LLVMBuildCall2(functionBuilder, type, address, args.toCValues(), args.size, name))
}

/** Builds a [`call`](https://llvm.org/docs/LangRef.html#call-instruction) with a materialized IR function signature. */
inline fun NativeCodeGenerator.call(
    address: LLVMValueRef,
    returnType: IrType = backend.irBuiltIns.unitType,
    paramTypes: List<IrType> = emptyList(),
    args: List<LLVMValueRef> = emptyList(),
    name: String? = null
): LLVMValueRef = call(
    address = address,
    returnType = materializeType(returnType),
    paramTypes = paramTypes.map(::materializeType),
    args = args,
    name = name
)

/** Builds a [`call`](https://llvm.org/docs/LangRef.html#call-instruction) with an LLVM signature and operand bundles. */
inline fun NativeCodeGenerator.callWithOperandBundles( // @formatter:off
    address: LLVMValueRef,
    returnType: LLVMTypeRef,
    paramTypes: List<LLVMTypeRef> = emptyList(),
    args: List<LLVMValueRef> = emptyList(),
    operandBundles: List<LLVMOperandBundleRef> = emptyList(),
    name: String? = null
): LLVMValueRef { // @formatter:on
    val type = LLVMFunctionType(returnType, paramTypes.toCValues(), paramTypes.size, 0)
    return requireInstruction(
        "callWithOperandBundles", LLVMBuildCallWithOperandBundles(
            functionBuilder,
            type,
            address,
            args.toCValues(),
            args.size,
            operandBundles.toCValues(),
            operandBundles.size,
            name
        )
    )
}

/** Builds a [`call`](https://llvm.org/docs/LangRef.html#call-instruction) with a materialized IR signature and operand bundles. */
inline fun NativeCodeGenerator.callWithOperandBundles( // @formatter:off
    address: LLVMValueRef,
    returnType: IrType = backend.irBuiltIns.unitType,
    paramTypes: List<IrType> = emptyList(),
    args: List<LLVMValueRef> = emptyList(),
    operandBundles: List<LLVMOperandBundleRef> = emptyList(),
    name: String? = null
): LLVMValueRef = callWithOperandBundles( // @formatter:on
    address = address,
    returnType = materializeType(returnType),
    paramTypes = paramTypes.map(::materializeType),
    args = args,
    operandBundles = operandBundles,
    name = name
)

/** Builds a [`callbr`](https://llvm.org/docs/LangRef.html#callbr-instruction) with an LLVM function signature. */
inline fun NativeCodeGenerator.callBr( // @formatter:off
    address: LLVMValueRef,
    fallthroughBlock: LLVMBasicBlockRef,
    indirectDestinations: List<LLVMBasicBlockRef> = emptyList(),
    returnType: LLVMTypeRef,
    paramTypes: List<LLVMTypeRef> = emptyList(),
    args: List<LLVMValueRef> = emptyList(),
    operandBundles: List<LLVMOperandBundleRef> = emptyList(),
    name: String? = null
): LLVMValueRef { // @formatter:on
    val type = LLVMFunctionType(returnType, paramTypes.toCValues(), paramTypes.size, 0)
    return requireInstruction(
        "callBr", LLVMBuildCallBr(
            functionBuilder,
            type,
            address,
            fallthroughBlock,
            indirectDestinations.toCValues(),
            indirectDestinations.size,
            args.toCValues(),
            args.size,
            operandBundles.toCValues(),
            operandBundles.size,
            name
        )
    )
}

/** Builds a [`callbr`](https://llvm.org/docs/LangRef.html#callbr-instruction) with a materialized IR function signature. */
inline fun NativeCodeGenerator.callBr( // @formatter:off
    address: LLVMValueRef,
    fallthroughBlock: LLVMBasicBlockRef,
    indirectDestinations: List<LLVMBasicBlockRef> = emptyList(),
    returnType: IrType = backend.irBuiltIns.unitType,
    paramTypes: List<IrType> = emptyList(),
    args: List<LLVMValueRef> = emptyList(),
    operandBundles: List<LLVMOperandBundleRef> = emptyList(),
    name: String? = null
): LLVMValueRef = callBr( // @formatter:on
    address = address,
    fallthroughBlock = fallthroughBlock,
    indirectDestinations = indirectDestinations,
    returnType = materializeType(returnType),
    paramTypes = paramTypes.map(::materializeType),
    args = args,
    operandBundles = operandBundles,
    name = name
)

/** Builds an [`invoke`](https://llvm.org/docs/LangRef.html#invoke-instruction) with an LLVM function signature. */
inline fun NativeCodeGenerator.invoke( // @formatter:off
    address: LLVMValueRef,
    thenBlock: LLVMBasicBlockRef,
    catchBlock: LLVMBasicBlockRef,
    returnType: LLVMTypeRef,
    paramTypes: List<LLVMTypeRef> = emptyList(),
    args: List<LLVMValueRef> = emptyList(),
    name: String? = null
): LLVMValueRef { // @formatter:on
    val type = LLVMFunctionType(returnType, paramTypes.toCValues(), paramTypes.size, 0)
    return requireInstruction(
        "invoke", LLVMBuildInvoke2(
            functionBuilder, type, address, args.toCValues(), args.size, thenBlock, catchBlock, name
        )
    )
}

/** Builds an [`invoke`](https://llvm.org/docs/LangRef.html#invoke-instruction) with a materialized IR function signature. */
inline fun NativeCodeGenerator.invoke( // @formatter:off
    address: LLVMValueRef,
    thenBlock: LLVMBasicBlockRef,
    catchBlock: LLVMBasicBlockRef,
    returnType: IrType = backend.irBuiltIns.unitType,
    paramTypes: List<IrType> = emptyList(),
    args: List<LLVMValueRef> = emptyList(),
    name: String? = null
): LLVMValueRef = invoke( // @formatter:on
    address = address,
    thenBlock = thenBlock,
    catchBlock = catchBlock,
    returnType = materializeType(returnType),
    paramTypes = paramTypes.map(::materializeType),
    args = args,
    name = name
)

/** Builds an [`invoke`](https://llvm.org/docs/LangRef.html#invoke-instruction) with an LLVM signature and operand bundles. */
inline fun NativeCodeGenerator.invokeWithOperandBundles( // @formatter:off
    address: LLVMValueRef,
    thenBlock: LLVMBasicBlockRef,
    catchBlock: LLVMBasicBlockRef,
    returnType: LLVMTypeRef,
    paramTypes: List<LLVMTypeRef> = emptyList(),
    args: List<LLVMValueRef> = emptyList(),
    operandBundles: List<LLVMOperandBundleRef> = emptyList(),
    name: String? = null
): LLVMValueRef { // @formatter:on
    val type = LLVMFunctionType(returnType, paramTypes.toCValues(), paramTypes.size, 0)
    return requireInstruction(
        "invokeWithOperandBundles", LLVMBuildInvokeWithOperandBundles(
            functionBuilder,
            type,
            address,
            args.toCValues(),
            args.size,
            thenBlock,
            catchBlock,
            operandBundles.toCValues(),
            operandBundles.size,
            name
        )
    )
}

/** Builds an [`invoke`](https://llvm.org/docs/LangRef.html#invoke-instruction) with a materialized IR signature and operand bundles. */
inline fun NativeCodeGenerator.invokeWithOperandBundles( // @formatter:off
    address: LLVMValueRef,
    thenBlock: LLVMBasicBlockRef,
    catchBlock: LLVMBasicBlockRef,
    returnType: IrType = backend.irBuiltIns.unitType,
    paramTypes: List<IrType> = emptyList(),
    args: List<LLVMValueRef> = emptyList(),
    operandBundles: List<LLVMOperandBundleRef> = emptyList(),
    name: String? = null
): LLVMValueRef = invokeWithOperandBundles( // @formatter:on
    address = address,
    thenBlock = thenBlock,
    catchBlock = catchBlock,
    returnType = materializeType(returnType),
    paramTypes = paramTypes.map(::materializeType),
    args = args,
    operandBundles = operandBundles,
    name = name
)

/** Builds a [`landingpad`](https://llvm.org/docs/LangRef.html#landingpad-instruction) of the LLVM result type. */
inline fun NativeCodeGenerator.landingPad(
    type: LLVMTypeRef, personalityFunction: LLVMValueRef, clauseCount: Int = 0, name: String? = null
): LLVMValueRef =
    requireInstruction("landingPad", LLVMBuildLandingPad(functionBuilder, type, personalityFunction, clauseCount, name))

/** Builds a [`landingpad`](https://llvm.org/docs/LangRef.html#landingpad-instruction) of the materialized IR result type. */
inline fun NativeCodeGenerator.landingPad(
    type: IrType, personalityFunction: LLVMValueRef, clauseCount: Int = 0, name: String? = null
): LLVMValueRef = landingPad(materializeType(type), personalityFunction, clauseCount, name)

/** Builds a [`select`](https://llvm.org/docs/LangRef.html#select-instruction) between two values. */
inline fun NativeCodeGenerator.select(
    condition: LLVMValueRef, thenValue: LLVMValueRef, elseValue: LLVMValueRef, name: String? = null
): LLVMValueRef = requireInstruction("select", LLVMBuildSelect(functionBuilder, condition, thenValue, elseValue, name))

/** Builds a [`va_arg`](https://llvm.org/docs/LangRef.html#va-arg-instruction) of the given LLVM type. */
inline fun NativeCodeGenerator.vaArg(
    list: LLVMValueRef, type: LLVMTypeRef, name: String? = null
): LLVMValueRef = requireInstruction("vaArg", LLVMBuildVAArg(functionBuilder, list, type, name))

/** Builds a [`va_arg`](https://llvm.org/docs/LangRef.html#va-arg-instruction) of the materialized IR type. */
inline fun NativeCodeGenerator.vaArg(list: LLVMValueRef, type: IrType, name: String? = null): LLVMValueRef =
    vaArg(list, materializeType(type), name)

/** Builds an [`extractelement`](https://llvm.org/docs/LangRef.html#extractelement-instruction) from a vector. */
inline fun NativeCodeGenerator.extractElement(
    vector: LLVMValueRef, index: LLVMValueRef, name: String? = null
): LLVMValueRef = requireInstruction("extractElement", LLVMBuildExtractElement(functionBuilder, vector, index, name))

/** Builds an [`insertelement`](https://llvm.org/docs/LangRef.html#insertelement-instruction) into a vector. */
inline fun NativeCodeGenerator.insertElement(
    vector: LLVMValueRef, element: LLVMValueRef, index: LLVMValueRef, name: String? = null
): LLVMValueRef =
    requireInstruction("insertElement", LLVMBuildInsertElement(functionBuilder, vector, element, index, name))

/** Builds a [`shufflevector`](https://llvm.org/docs/LangRef.html#shufflevector-instruction) from two vectors and a mask. */
inline fun NativeCodeGenerator.shuffleVector(
    lhs: LLVMValueRef, rhs: LLVMValueRef, mask: LLVMValueRef, name: String? = null
): LLVMValueRef = requireInstruction("shuffleVector", LLVMBuildShuffleVector(functionBuilder, lhs, rhs, mask, name))

/** Builds an [`extractvalue`](https://llvm.org/docs/LangRef.html#extractvalue-instruction) from an aggregate. */
inline fun NativeCodeGenerator.extractValue(
    aggregate: LLVMValueRef, index: Int, name: String? = null
): LLVMValueRef = requireInstruction("extractValue", LLVMBuildExtractValue(functionBuilder, aggregate, index, name))

/** Builds an [`insertvalue`](https://llvm.org/docs/LangRef.html#insertvalue-instruction) into an aggregate. */
inline fun NativeCodeGenerator.insertValue(
    aggregate: LLVMValueRef, element: LLVMValueRef, index: Int, name: String? = null
): LLVMValueRef =
    requireInstruction("insertValue", LLVMBuildInsertValue(functionBuilder, aggregate, element, index, name))

/** Builds a [`freeze`](https://llvm.org/docs/LangRef.html#freeze-instruction) that returns a fixed non-poison value. */
inline fun NativeCodeGenerator.freeze(value: LLVMValueRef, name: String? = null): LLVMValueRef =
    requireInstruction("freeze", LLVMBuildFreeze(functionBuilder, value, name))

/** Builds an [`icmp`](https://llvm.org/docs/LangRef.html#icmp-instruction) that tests whether a pointer is null. */
inline fun NativeCodeGenerator.isNull(value: LLVMValueRef, name: String? = null): LLVMValueRef =
    requireInstruction("isNull", LLVMBuildIsNull(functionBuilder, value, name))

/** Builds an [`icmp`](https://llvm.org/docs/LangRef.html#icmp-instruction) that tests whether a pointer is non-null. */
inline fun NativeCodeGenerator.isNotNull(value: LLVMValueRef, name: String? = null): LLVMValueRef =
    requireInstruction("isNotNull", LLVMBuildIsNotNull(functionBuilder, value, name))

/** Builds a [pointer difference](https://llvm.org/doxygen/group__LLVMCCoreInstructionBuilder.html#ga750e9b83bcf3254cf59330d6287615e5) in elements of the LLVM type. */
inline fun NativeCodeGenerator.ptrDiff(
    type: LLVMTypeRef, lhs: LLVMValueRef, rhs: LLVMValueRef, name: String? = null
): LLVMValueRef = requireInstruction("ptrDiff", LLVMBuildPtrDiff2(functionBuilder, type, lhs, rhs, name))

/** Builds a [pointer difference](https://llvm.org/doxygen/group__LLVMCCoreInstructionBuilder.html#ga750e9b83bcf3254cf59330d6287615e5) in elements of the materialized IR type. */
inline fun NativeCodeGenerator.ptrDiff(
    type: IrType, lhs: LLVMValueRef, rhs: LLVMValueRef, name: String? = null
): LLVMValueRef = ptrDiff(materializeType(type), lhs, rhs, name)

/** Builds a [`fence`](https://llvm.org/docs/LangRef.html#fence-instruction) with single-thread or system scope. */
inline fun NativeCodeGenerator.fence(
    ordering: LLVMAtomicOrdering, singleThread: Int, name: String? = null
): LLVMValueRef = requireInstruction("fence", LLVMBuildFence(functionBuilder, ordering, singleThread, name))

/** Builds a [`fence`](https://llvm.org/docs/LangRef.html#fence-instruction) with an explicit synchronization scope. */
inline fun NativeCodeGenerator.fenceSyncScope(
    ordering: LLVMAtomicOrdering, syncScopeId: Int, name: String? = null
): LLVMValueRef =
    requireInstruction("fenceSyncScope", LLVMBuildFenceSyncScope(functionBuilder, ordering, syncScopeId, name))

/** Builds an [`atomicrmw`](https://llvm.org/docs/LangRef.html#atomicrmw-instruction) with single-thread or system scope. */
inline fun NativeCodeGenerator.atomicRmw(
    operation: LLVMAtomicRMWBinOp,
    pointer: LLVMValueRef,
    value: LLVMValueRef,
    ordering: LLVMAtomicOrdering,
    singleThread: Int
): LLVMValueRef = requireInstruction(
    "atomicRmw", LLVMBuildAtomicRMW(functionBuilder, operation, pointer, value, ordering, singleThread)
)

/** Builds an [`atomicrmw`](https://llvm.org/docs/LangRef.html#atomicrmw-instruction) with an explicit synchronization scope. */
inline fun NativeCodeGenerator.atomicRmwSyncScope(
    operation: LLVMAtomicRMWBinOp,
    pointer: LLVMValueRef,
    value: LLVMValueRef,
    ordering: LLVMAtomicOrdering,
    syncScopeId: Int
): LLVMValueRef = requireInstruction(
    "atomicRmwSyncScope", LLVMBuildAtomicRMWSyncScope(functionBuilder, operation, pointer, value, ordering, syncScopeId)
)

/** Builds a [`cmpxchg`](https://llvm.org/docs/LangRef.html#cmpxchg-instruction) with single-thread or system scope. */
inline fun NativeCodeGenerator.atomicCmpXchg(
    pointer: LLVMValueRef,
    compare: LLVMValueRef,
    value: LLVMValueRef,
    successOrdering: LLVMAtomicOrdering,
    failureOrdering: LLVMAtomicOrdering,
    singleThread: Int
): LLVMValueRef = requireInstruction(
    "atomicCmpXchg", LLVMBuildAtomicCmpXchg(
        functionBuilder, pointer, compare, value, successOrdering, failureOrdering, singleThread
    )
)

/** Builds a [`cmpxchg`](https://llvm.org/docs/LangRef.html#cmpxchg-instruction) with an explicit synchronization scope. */
inline fun NativeCodeGenerator.atomicCmpXchgSyncScope(
    pointer: LLVMValueRef,
    compare: LLVMValueRef,
    value: LLVMValueRef,
    successOrdering: LLVMAtomicOrdering,
    failureOrdering: LLVMAtomicOrdering,
    syncScopeId: Int
): LLVMValueRef = requireInstruction(
    "atomicCmpXchgSyncScope", LLVMBuildAtomicCmpXchgSyncScope(
        functionBuilder, pointer, compare, value, successOrdering, failureOrdering, syncScopeId
    )
)