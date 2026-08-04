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

package dev.karmakrafts.kcml.api.backend

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

inline fun NativeCodeGenerator.alloca(type: LLVMTypeRef, name: String? = null): LLVMValueRef? =
    LLVMBuildAlloca(functionBuilder, type, name)

inline fun NativeCodeGenerator.alloca(type: IrType, name: String? = null): LLVMValueRef? =
    alloca(materializeType(type), name)

inline fun NativeCodeGenerator.aggregateRet(values: List<LLVMValueRef>): LLVMValueRef? =
    LLVMBuildAggregateRet(functionBuilder, values.toCValues(), values.size)

inline fun NativeCodeGenerator.br(dest: LLVMBasicBlockRef): LLVMValueRef? = LLVMBuildBr(functionBuilder, dest)

inline fun NativeCodeGenerator.condBr(
    condition: LLVMValueRef, thenBlock: LLVMBasicBlockRef, elseBlock: LLVMBasicBlockRef
): LLVMValueRef? = LLVMBuildCondBr(functionBuilder, condition, thenBlock, elseBlock)

inline fun NativeCodeGenerator.switch(
    value: LLVMValueRef, elseBlock: LLVMBasicBlockRef, caseCount: Int = 0
): LLVMValueRef? = LLVMBuildSwitch(functionBuilder, value, elseBlock, caseCount)

inline fun NativeCodeGenerator.indirectBr(address: LLVMValueRef, destCount: Int = 0): LLVMValueRef? =
    LLVMBuildIndirectBr(functionBuilder, address, destCount)

inline fun NativeCodeGenerator.unreachable(): LLVMValueRef? = LLVMBuildUnreachable(functionBuilder)

inline fun NativeCodeGenerator.resume(exception: LLVMValueRef): LLVMValueRef? =
    LLVMBuildResume(functionBuilder, exception)

inline fun NativeCodeGenerator.cleanupRet(
    cleanupPad: LLVMValueRef, unwindBlock: LLVMBasicBlockRef
): LLVMValueRef? = LLVMBuildCleanupRet(functionBuilder, cleanupPad, unwindBlock)

inline fun NativeCodeGenerator.catchRet(catchPad: LLVMValueRef, successor: LLVMBasicBlockRef): LLVMValueRef? =
    LLVMBuildCatchRet(functionBuilder, catchPad, successor)

inline fun NativeCodeGenerator.catchPad(
    parentPad: LLVMValueRef, args: List<LLVMValueRef> = emptyList(), name: String? = null
): LLVMValueRef? = LLVMBuildCatchPad(functionBuilder, parentPad, args.toCValues(), args.size, name)

inline fun NativeCodeGenerator.cleanupPad(
    parentPad: LLVMValueRef, args: List<LLVMValueRef> = emptyList(), name: String? = null
): LLVMValueRef? = LLVMBuildCleanupPad(functionBuilder, parentPad, args.toCValues(), args.size, name)

inline fun NativeCodeGenerator.catchSwitch(
    parentPad: LLVMValueRef, unwindBlock: LLVMBasicBlockRef, handlerCount: Int = 0, name: String? = null
): LLVMValueRef? = LLVMBuildCatchSwitch(functionBuilder, parentPad, unwindBlock, handlerCount, name)

inline fun NativeCodeGenerator.add(
    lhs: LLVMValueRef, rhs: LLVMValueRef, name: String? = null
): LLVMValueRef? = LLVMBuildAdd(functionBuilder, lhs, rhs, name)

inline fun NativeCodeGenerator.nswAdd(
    lhs: LLVMValueRef, rhs: LLVMValueRef, name: String? = null
): LLVMValueRef? = LLVMBuildNSWAdd(functionBuilder, lhs, rhs, name)

inline fun NativeCodeGenerator.nuwAdd(
    lhs: LLVMValueRef, rhs: LLVMValueRef, name: String? = null
): LLVMValueRef? = LLVMBuildNUWAdd(functionBuilder, lhs, rhs, name)

inline fun NativeCodeGenerator.fAdd(
    lhs: LLVMValueRef, rhs: LLVMValueRef, name: String? = null
): LLVMValueRef? = LLVMBuildFAdd(functionBuilder, lhs, rhs, name)

inline fun NativeCodeGenerator.sub(
    lhs: LLVMValueRef, rhs: LLVMValueRef, name: String? = null
): LLVMValueRef? = LLVMBuildSub(functionBuilder, lhs, rhs, name)

inline fun NativeCodeGenerator.nswSub(
    lhs: LLVMValueRef, rhs: LLVMValueRef, name: String? = null
): LLVMValueRef? = LLVMBuildNSWSub(functionBuilder, lhs, rhs, name)

inline fun NativeCodeGenerator.nuwSub(
    lhs: LLVMValueRef, rhs: LLVMValueRef, name: String? = null
): LLVMValueRef? = LLVMBuildNUWSub(functionBuilder, lhs, rhs, name)

inline fun NativeCodeGenerator.fSub(
    lhs: LLVMValueRef, rhs: LLVMValueRef, name: String? = null
): LLVMValueRef? = LLVMBuildFSub(functionBuilder, lhs, rhs, name)

inline fun NativeCodeGenerator.mul(
    lhs: LLVMValueRef, rhs: LLVMValueRef, name: String? = null
): LLVMValueRef? = LLVMBuildMul(functionBuilder, lhs, rhs, name)

inline fun NativeCodeGenerator.nswMul(
    lhs: LLVMValueRef, rhs: LLVMValueRef, name: String? = null
): LLVMValueRef? = LLVMBuildNSWMul(functionBuilder, lhs, rhs, name)

inline fun NativeCodeGenerator.nuwMul(
    lhs: LLVMValueRef, rhs: LLVMValueRef, name: String? = null
): LLVMValueRef? = LLVMBuildNUWMul(functionBuilder, lhs, rhs, name)

inline fun NativeCodeGenerator.fMul(
    lhs: LLVMValueRef, rhs: LLVMValueRef, name: String? = null
): LLVMValueRef? = LLVMBuildFMul(functionBuilder, lhs, rhs, name)

inline fun NativeCodeGenerator.uDiv(
    lhs: LLVMValueRef, rhs: LLVMValueRef, name: String? = null
): LLVMValueRef? = LLVMBuildUDiv(functionBuilder, lhs, rhs, name)

inline fun NativeCodeGenerator.exactUDiv(
    lhs: LLVMValueRef, rhs: LLVMValueRef, name: String? = null
): LLVMValueRef? = LLVMBuildExactUDiv(functionBuilder, lhs, rhs, name)

inline fun NativeCodeGenerator.sDiv(
    lhs: LLVMValueRef, rhs: LLVMValueRef, name: String? = null
): LLVMValueRef? = LLVMBuildSDiv(functionBuilder, lhs, rhs, name)

inline fun NativeCodeGenerator.exactSDiv(
    lhs: LLVMValueRef, rhs: LLVMValueRef, name: String? = null
): LLVMValueRef? = LLVMBuildExactSDiv(functionBuilder, lhs, rhs, name)

inline fun NativeCodeGenerator.fDiv(
    lhs: LLVMValueRef, rhs: LLVMValueRef, name: String? = null
): LLVMValueRef? = LLVMBuildFDiv(functionBuilder, lhs, rhs, name)

inline fun NativeCodeGenerator.uRem(
    lhs: LLVMValueRef, rhs: LLVMValueRef, name: String? = null
): LLVMValueRef? = LLVMBuildURem(functionBuilder, lhs, rhs, name)

inline fun NativeCodeGenerator.sRem(
    lhs: LLVMValueRef, rhs: LLVMValueRef, name: String? = null
): LLVMValueRef? = LLVMBuildSRem(functionBuilder, lhs, rhs, name)

inline fun NativeCodeGenerator.fRem(
    lhs: LLVMValueRef, rhs: LLVMValueRef, name: String? = null
): LLVMValueRef? = LLVMBuildFRem(functionBuilder, lhs, rhs, name)

inline fun NativeCodeGenerator.shl(
    lhs: LLVMValueRef, rhs: LLVMValueRef, name: String? = null
): LLVMValueRef? = LLVMBuildShl(functionBuilder, lhs, rhs, name)

inline fun NativeCodeGenerator.lShr(
    lhs: LLVMValueRef, rhs: LLVMValueRef, name: String? = null
): LLVMValueRef? = LLVMBuildLShr(functionBuilder, lhs, rhs, name)

inline fun NativeCodeGenerator.aShr(
    lhs: LLVMValueRef, rhs: LLVMValueRef, name: String? = null
): LLVMValueRef? = LLVMBuildAShr(functionBuilder, lhs, rhs, name)

inline fun NativeCodeGenerator.and(
    lhs: LLVMValueRef, rhs: LLVMValueRef, name: String? = null
): LLVMValueRef? = LLVMBuildAnd(functionBuilder, lhs, rhs, name)

inline fun NativeCodeGenerator.or(
    lhs: LLVMValueRef, rhs: LLVMValueRef, name: String? = null
): LLVMValueRef? = LLVMBuildOr(functionBuilder, lhs, rhs, name)

inline fun NativeCodeGenerator.xor(
    lhs: LLVMValueRef, rhs: LLVMValueRef, name: String? = null
): LLVMValueRef? = LLVMBuildXor(functionBuilder, lhs, rhs, name)

inline fun NativeCodeGenerator.binOp(
    opcode: LLVMOpcode, lhs: LLVMValueRef, rhs: LLVMValueRef, name: String? = null
): LLVMValueRef? = LLVMBuildBinOp(functionBuilder, opcode, lhs, rhs, name)

inline fun NativeCodeGenerator.neg(value: LLVMValueRef, name: String? = null): LLVMValueRef? =
    LLVMBuildNeg(functionBuilder, value, name)

inline fun NativeCodeGenerator.nswNeg(value: LLVMValueRef, name: String? = null): LLVMValueRef? =
    LLVMBuildNSWNeg(functionBuilder, value, name)

inline fun NativeCodeGenerator.nuwNeg(value: LLVMValueRef, name: String? = null): LLVMValueRef? =
    LLVMBuildNUWNeg(functionBuilder, value, name)

inline fun NativeCodeGenerator.fNeg(value: LLVMValueRef, name: String? = null): LLVMValueRef? =
    LLVMBuildFNeg(functionBuilder, value, name)

inline fun NativeCodeGenerator.not(value: LLVMValueRef, name: String? = null): LLVMValueRef? =
    LLVMBuildNot(functionBuilder, value, name)

inline fun NativeCodeGenerator.malloc(type: LLVMTypeRef, name: String? = null): LLVMValueRef? =
    LLVMBuildMalloc(functionBuilder, type, name)

inline fun NativeCodeGenerator.malloc(type: IrType, name: String? = null): LLVMValueRef? =
    malloc(materializeType(type), name)

inline fun NativeCodeGenerator.arrayMalloc(
    type: LLVMTypeRef, count: LLVMValueRef, name: String? = null
): LLVMValueRef? = LLVMBuildArrayMalloc(functionBuilder, type, count, name)

inline fun NativeCodeGenerator.arrayMalloc(type: IrType, count: LLVMValueRef, name: String? = null): LLVMValueRef? =
    arrayMalloc(materializeType(type), count, name)

inline fun NativeCodeGenerator.memSet(
    pointer: LLVMValueRef, value: LLVMValueRef, size: LLVMValueRef, alignment: Int
): LLVMValueRef? = LLVMBuildMemSet(functionBuilder, pointer, value, size, alignment)

inline fun NativeCodeGenerator.memCpy(
    dest: LLVMValueRef, destAlignment: Int, source: LLVMValueRef, sourceAlignment: Int, size: LLVMValueRef
): LLVMValueRef? = LLVMBuildMemCpy(functionBuilder, dest, destAlignment, source, sourceAlignment, size)

inline fun NativeCodeGenerator.memMove(
    dest: LLVMValueRef, destAlignment: Int, source: LLVMValueRef, sourceAlignment: Int, size: LLVMValueRef
): LLVMValueRef? = LLVMBuildMemMove(functionBuilder, dest, destAlignment, source, sourceAlignment, size)

inline fun NativeCodeGenerator.arrayAlloca(
    type: LLVMTypeRef, count: LLVMValueRef, name: String? = null
): LLVMValueRef? = LLVMBuildArrayAlloca(functionBuilder, type, count, name)

inline fun NativeCodeGenerator.arrayAlloca(type: IrType, count: LLVMValueRef, name: String? = null): LLVMValueRef? =
    arrayAlloca(materializeType(type), count, name)

inline fun NativeCodeGenerator.free(pointer: LLVMValueRef): LLVMValueRef? = LLVMBuildFree(functionBuilder, pointer)

inline fun NativeCodeGenerator.load(
    type: LLVMTypeRef, pointer: LLVMValueRef, name: String? = null
): LLVMValueRef? = LLVMBuildLoad2(functionBuilder, type, pointer, name)

inline fun NativeCodeGenerator.load(type: IrType, pointer: LLVMValueRef, name: String? = null): LLVMValueRef? =
    load(materializeType(type), pointer, name)

inline fun NativeCodeGenerator.store(value: LLVMValueRef, pointer: LLVMValueRef): LLVMValueRef? =
    LLVMBuildStore(functionBuilder, value, pointer)

inline fun NativeCodeGenerator.phi(type: LLVMTypeRef, name: String? = null): LLVMValueRef? =
    LLVMBuildPhi(functionBuilder, type, name)

inline fun NativeCodeGenerator.phi(type: IrType, name: String? = null): LLVMValueRef? = phi(materializeType(type), name)

inline fun NativeCodeGenerator.ret() = LLVMBuildRetVoid(functionBuilder)

inline fun NativeCodeGenerator.ret(value: LLVMValueRef) = LLVMBuildRet(functionBuilder, value)

inline fun NativeCodeGenerator.retUnit() = ret(unitInstance)

inline fun NativeCodeGenerator.gep( // @formatter:off
    type: LLVMTypeRef,
    pointer: LLVMValueRef,
    indices: List<LLVMValueRef> = emptyList(),
    name: String? = null
): LLVMValueRef? =
    LLVMBuildGEP2(functionBuilder, type, pointer, indices.toCValues(), indices.size, name) // @formatter:on

inline fun NativeCodeGenerator.gep( // @formatter:off
    type: IrType,
    pointer: LLVMValueRef,
    indices: List<LLVMValueRef> = emptyList(),
    name: String? = null
): LLVMValueRef? = gep(materializeType(type), pointer, indices, name) // @formatter:on

inline fun NativeCodeGenerator.inBoundsGep( // @formatter:off
    type: LLVMTypeRef,
    pointer: LLVMValueRef,
    indices: List<LLVMValueRef> = emptyList(),
    name: String? = null
): LLVMValueRef? =
    LLVMBuildInBoundsGEP2(functionBuilder, type, pointer, indices.toCValues(), indices.size, name) // @formatter:on

inline fun NativeCodeGenerator.inBoundsGep( // @formatter:off
    type: IrType,
    pointer: LLVMValueRef,
    indices: List<LLVMValueRef> = emptyList(),
    name: String? = null
): LLVMValueRef? = inBoundsGep(materializeType(type), pointer, indices, name) // @formatter:on

inline fun NativeCodeGenerator.gepWithNoWrapFlags( // @formatter:off
    type: LLVMTypeRef,
    pointer: LLVMValueRef,
    indices: List<LLVMValueRef> = emptyList(),
    flags: Int,
    name: String? = null
): LLVMValueRef? = LLVMBuildGEPWithNoWrapFlags(
    functionBuilder,
    type,
    pointer,
    indices.toCValues(),
    indices.size,
    name,
    flags
) // @formatter:on

inline fun NativeCodeGenerator.gepWithNoWrapFlags( // @formatter:off
    type: IrType,
    pointer: LLVMValueRef,
    indices: List<LLVMValueRef> = emptyList(),
    flags: Int,
    name: String? = null
): LLVMValueRef? = gepWithNoWrapFlags(materializeType(type), pointer, indices, flags, name) // @formatter:on

inline fun NativeCodeGenerator.structGep( // @formatter:off
    type: LLVMTypeRef,
    pointer: LLVMValueRef,
    index: Int,
    name: String? = null
): LLVMValueRef? = LLVMBuildStructGEP2(functionBuilder, type, pointer, index, name) // @formatter:on

inline fun NativeCodeGenerator.structGep( // @formatter:off
    type: IrType,
    pointer: LLVMValueRef,
    index: Int,
    name: String? = null
): LLVMValueRef? = structGep(materializeType(type), pointer, index, name) // @formatter:on

inline fun NativeCodeGenerator.globalString(value: String, name: String? = null): LLVMValueRef? =
    LLVMBuildGlobalString(functionBuilder, value, name)

inline fun NativeCodeGenerator.globalStringPtr(value: String, name: String? = null): LLVMValueRef? =
    LLVMBuildGlobalStringPtr(functionBuilder, value, name)

inline fun NativeCodeGenerator.trunc(
    value: LLVMValueRef, type: LLVMTypeRef, name: String? = null
): LLVMValueRef? = LLVMBuildTrunc(functionBuilder, value, type, name)

inline fun NativeCodeGenerator.trunc(value: LLVMValueRef, type: IrType, name: String? = null): LLVMValueRef? =
    trunc(value, materializeType(type), name)

inline fun NativeCodeGenerator.zExt(
    value: LLVMValueRef, type: LLVMTypeRef, name: String? = null
): LLVMValueRef? = LLVMBuildZExt(functionBuilder, value, type, name)

inline fun NativeCodeGenerator.zExt(value: LLVMValueRef, type: IrType, name: String? = null): LLVMValueRef? =
    zExt(value, materializeType(type), name)

inline fun NativeCodeGenerator.sExt(
    value: LLVMValueRef, type: LLVMTypeRef, name: String? = null
): LLVMValueRef? = LLVMBuildSExt(functionBuilder, value, type, name)

inline fun NativeCodeGenerator.sExt(value: LLVMValueRef, type: IrType, name: String? = null): LLVMValueRef? =
    sExt(value, materializeType(type), name)

inline fun NativeCodeGenerator.fpToUI(
    value: LLVMValueRef, type: LLVMTypeRef, name: String? = null
): LLVMValueRef? = LLVMBuildFPToUI(functionBuilder, value, type, name)

inline fun NativeCodeGenerator.fpToUI(value: LLVMValueRef, type: IrType, name: String? = null): LLVMValueRef? =
    fpToUI(value, materializeType(type), name)

inline fun NativeCodeGenerator.fpToSI(
    value: LLVMValueRef, type: LLVMTypeRef, name: String? = null
): LLVMValueRef? = LLVMBuildFPToSI(functionBuilder, value, type, name)

inline fun NativeCodeGenerator.fpToSI(value: LLVMValueRef, type: IrType, name: String? = null): LLVMValueRef? =
    fpToSI(value, materializeType(type), name)

inline fun NativeCodeGenerator.uiToFP(
    value: LLVMValueRef, type: LLVMTypeRef, name: String? = null
): LLVMValueRef? = LLVMBuildUIToFP(functionBuilder, value, type, name)

inline fun NativeCodeGenerator.uiToFP(value: LLVMValueRef, type: IrType, name: String? = null): LLVMValueRef? =
    uiToFP(value, materializeType(type), name)

inline fun NativeCodeGenerator.siToFP(
    value: LLVMValueRef, type: LLVMTypeRef, name: String? = null
): LLVMValueRef? = LLVMBuildSIToFP(functionBuilder, value, type, name)

inline fun NativeCodeGenerator.siToFP(value: LLVMValueRef, type: IrType, name: String? = null): LLVMValueRef? =
    siToFP(value, materializeType(type), name)

inline fun NativeCodeGenerator.fpTrunc(
    value: LLVMValueRef, type: LLVMTypeRef, name: String? = null
): LLVMValueRef? = LLVMBuildFPTrunc(functionBuilder, value, type, name)

inline fun NativeCodeGenerator.fpTrunc(value: LLVMValueRef, type: IrType, name: String? = null): LLVMValueRef? =
    fpTrunc(value, materializeType(type), name)

inline fun NativeCodeGenerator.fpExt(
    value: LLVMValueRef, type: LLVMTypeRef, name: String? = null
): LLVMValueRef? = LLVMBuildFPExt(functionBuilder, value, type, name)

inline fun NativeCodeGenerator.fpExt(value: LLVMValueRef, type: IrType, name: String? = null): LLVMValueRef? =
    fpExt(value, materializeType(type), name)

inline fun NativeCodeGenerator.ptrToInt(
    value: LLVMValueRef, type: LLVMTypeRef, name: String? = null
): LLVMValueRef? = LLVMBuildPtrToInt(functionBuilder, value, type, name)

inline fun NativeCodeGenerator.ptrToInt(value: LLVMValueRef, type: IrType, name: String? = null): LLVMValueRef? =
    ptrToInt(value, materializeType(type), name)

inline fun NativeCodeGenerator.intToPtr(
    value: LLVMValueRef, type: LLVMTypeRef, name: String? = null
): LLVMValueRef? = LLVMBuildIntToPtr(functionBuilder, value, type, name)

inline fun NativeCodeGenerator.intToPtr(value: LLVMValueRef, type: IrType, name: String? = null): LLVMValueRef? =
    intToPtr(value, materializeType(type), name)

inline fun NativeCodeGenerator.bitCast(
    value: LLVMValueRef, type: LLVMTypeRef, name: String? = null
): LLVMValueRef? = LLVMBuildBitCast(functionBuilder, value, type, name)

inline fun NativeCodeGenerator.bitCast(value: LLVMValueRef, type: IrType, name: String? = null): LLVMValueRef? =
    bitCast(value, materializeType(type), name)

inline fun NativeCodeGenerator.addrSpaceCast(
    value: LLVMValueRef, type: LLVMTypeRef, name: String? = null
): LLVMValueRef? = LLVMBuildAddrSpaceCast(functionBuilder, value, type, name)

inline fun NativeCodeGenerator.addrSpaceCast(value: LLVMValueRef, type: IrType, name: String? = null): LLVMValueRef? =
    addrSpaceCast(value, materializeType(type), name)

inline fun NativeCodeGenerator.zExtOrBitCast(
    value: LLVMValueRef, type: LLVMTypeRef, name: String? = null
): LLVMValueRef? = LLVMBuildZExtOrBitCast(functionBuilder, value, type, name)

inline fun NativeCodeGenerator.zExtOrBitCast(value: LLVMValueRef, type: IrType, name: String? = null): LLVMValueRef? =
    zExtOrBitCast(value, materializeType(type), name)

inline fun NativeCodeGenerator.sExtOrBitCast(
    value: LLVMValueRef, type: LLVMTypeRef, name: String? = null
): LLVMValueRef? = LLVMBuildSExtOrBitCast(functionBuilder, value, type, name)

inline fun NativeCodeGenerator.sExtOrBitCast(value: LLVMValueRef, type: IrType, name: String? = null): LLVMValueRef? =
    sExtOrBitCast(value, materializeType(type), name)

inline fun NativeCodeGenerator.truncOrBitCast(
    value: LLVMValueRef, type: LLVMTypeRef, name: String? = null
): LLVMValueRef? = LLVMBuildTruncOrBitCast(functionBuilder, value, type, name)

inline fun NativeCodeGenerator.truncOrBitCast(value: LLVMValueRef, type: IrType, name: String? = null): LLVMValueRef? =
    truncOrBitCast(value, materializeType(type), name)

inline fun NativeCodeGenerator.cast(
    opcode: LLVMOpcode, value: LLVMValueRef, type: LLVMTypeRef, name: String? = null
): LLVMValueRef? = LLVMBuildCast(functionBuilder, opcode, value, type, name)

inline fun NativeCodeGenerator.cast(
    opcode: LLVMOpcode, value: LLVMValueRef, type: IrType, name: String? = null
): LLVMValueRef? = cast(opcode, value, materializeType(type), name)

inline fun NativeCodeGenerator.pointerCast(
    value: LLVMValueRef, type: LLVMTypeRef, name: String? = null
): LLVMValueRef? = LLVMBuildPointerCast(functionBuilder, value, type, name)

inline fun NativeCodeGenerator.pointerCast(value: LLVMValueRef, type: IrType, name: String? = null): LLVMValueRef? =
    pointerCast(value, materializeType(type), name)

inline fun NativeCodeGenerator.intCast(
    value: LLVMValueRef, type: LLVMTypeRef, isSigned: Int, name: String? = null
): LLVMValueRef? = LLVMBuildIntCast2(functionBuilder, value, type, isSigned, name)

inline fun NativeCodeGenerator.intCast(
    value: LLVMValueRef, type: IrType, isSigned: Int, name: String? = null
): LLVMValueRef? = intCast(value, materializeType(type), isSigned, name)

inline fun NativeCodeGenerator.intCast(
    value: LLVMValueRef, type: LLVMTypeRef, name: String? = null
): LLVMValueRef? = LLVMBuildIntCast(functionBuilder, value, type, name)

inline fun NativeCodeGenerator.intCast(value: LLVMValueRef, type: IrType, name: String? = null): LLVMValueRef? =
    intCast(value, materializeType(type), name)

inline fun NativeCodeGenerator.fpCast(
    value: LLVMValueRef, type: LLVMTypeRef, name: String? = null
): LLVMValueRef? = LLVMBuildFPCast(functionBuilder, value, type, name)

inline fun NativeCodeGenerator.fpCast(value: LLVMValueRef, type: IrType, name: String? = null): LLVMValueRef? =
    fpCast(value, materializeType(type), name)

inline fun NativeCodeGenerator.iCmp(
    predicate: LLVMIntPredicate, lhs: LLVMValueRef, rhs: LLVMValueRef, name: String? = null
): LLVMValueRef? = LLVMBuildICmp(functionBuilder, predicate, lhs, rhs, name)

inline fun NativeCodeGenerator.fCmp(
    predicate: LLVMRealPredicate, lhs: LLVMValueRef, rhs: LLVMValueRef, name: String? = null
): LLVMValueRef? = LLVMBuildFCmp(functionBuilder, predicate, lhs, rhs, name)

inline fun NativeCodeGenerator.call( // @formatter:off
    address: LLVMValueRef,
    returnType: LLVMTypeRef,
    paramTypes: List<LLVMTypeRef> = emptyList(),
    args: List<LLVMValueRef> = emptyList(),
    name: String? = null
): LLVMValueRef? { // @formatter:on
    val type = LLVMFunctionType(returnType, paramTypes.toCValues(), paramTypes.size, 0)
    return LLVMBuildCall2(functionBuilder, type, address, args.toCValues(), args.size, name)
}

inline fun NativeCodeGenerator.call(
    address: LLVMValueRef,
    returnType: IrType = irBuiltIns.unitType,
    paramTypes: List<IrType> = emptyList(),
    args: List<LLVMValueRef> = emptyList(),
    name: String? = null
): LLVMValueRef? = call(
    address = address,
    returnType = materializeType(returnType),
    paramTypes = paramTypes.map(::materializeType),
    args = args,
    name = name
)

inline fun NativeCodeGenerator.callWithOperandBundles( // @formatter:off
    address: LLVMValueRef,
    returnType: LLVMTypeRef,
    paramTypes: List<LLVMTypeRef> = emptyList(),
    args: List<LLVMValueRef> = emptyList(),
    operandBundles: List<LLVMOperandBundleRef> = emptyList(),
    name: String? = null
): LLVMValueRef? { // @formatter:on
    val type = LLVMFunctionType(returnType, paramTypes.toCValues(), paramTypes.size, 0)
    return LLVMBuildCallWithOperandBundles(
        functionBuilder,
        type,
        address,
        args.toCValues(),
        args.size,
        operandBundles.toCValues(),
        operandBundles.size,
        name
    )
}

inline fun NativeCodeGenerator.callWithOperandBundles( // @formatter:off
    address: LLVMValueRef,
    returnType: IrType = irBuiltIns.unitType,
    paramTypes: List<IrType> = emptyList(),
    args: List<LLVMValueRef> = emptyList(),
    operandBundles: List<LLVMOperandBundleRef> = emptyList(),
    name: String? = null
): LLVMValueRef? = callWithOperandBundles( // @formatter:on
    address = address,
    returnType = materializeType(returnType),
    paramTypes = paramTypes.map(::materializeType),
    args = args,
    operandBundles = operandBundles,
    name = name
)

inline fun NativeCodeGenerator.callBr( // @formatter:off
    address: LLVMValueRef,
    fallthroughBlock: LLVMBasicBlockRef,
    indirectDestinations: List<LLVMBasicBlockRef> = emptyList(),
    returnType: LLVMTypeRef,
    paramTypes: List<LLVMTypeRef> = emptyList(),
    args: List<LLVMValueRef> = emptyList(),
    operandBundles: List<LLVMOperandBundleRef> = emptyList(),
    name: String? = null
): LLVMValueRef? { // @formatter:on
    val type = LLVMFunctionType(returnType, paramTypes.toCValues(), paramTypes.size, 0)
    return LLVMBuildCallBr(
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
}

inline fun NativeCodeGenerator.callBr( // @formatter:off
    address: LLVMValueRef,
    fallthroughBlock: LLVMBasicBlockRef,
    indirectDestinations: List<LLVMBasicBlockRef> = emptyList(),
    returnType: IrType = irBuiltIns.unitType,
    paramTypes: List<IrType> = emptyList(),
    args: List<LLVMValueRef> = emptyList(),
    operandBundles: List<LLVMOperandBundleRef> = emptyList(),
    name: String? = null
): LLVMValueRef? = callBr( // @formatter:on
    address = address,
    fallthroughBlock = fallthroughBlock,
    indirectDestinations = indirectDestinations,
    returnType = materializeType(returnType),
    paramTypes = paramTypes.map(::materializeType),
    args = args,
    operandBundles = operandBundles,
    name = name
)

inline fun NativeCodeGenerator.invoke( // @formatter:off
    address: LLVMValueRef,
    thenBlock: LLVMBasicBlockRef,
    catchBlock: LLVMBasicBlockRef,
    returnType: LLVMTypeRef,
    paramTypes: List<LLVMTypeRef> = emptyList(),
    args: List<LLVMValueRef> = emptyList(),
    name: String? = null
): LLVMValueRef? { // @formatter:on
    val type = LLVMFunctionType(returnType, paramTypes.toCValues(), paramTypes.size, 0)
    return LLVMBuildInvoke2(
        functionBuilder, type, address, args.toCValues(), args.size, thenBlock, catchBlock, name
    )
}

inline fun NativeCodeGenerator.invoke( // @formatter:off
    address: LLVMValueRef,
    thenBlock: LLVMBasicBlockRef,
    catchBlock: LLVMBasicBlockRef,
    returnType: IrType = irBuiltIns.unitType,
    paramTypes: List<IrType> = emptyList(),
    args: List<LLVMValueRef> = emptyList(),
    name: String? = null
): LLVMValueRef? = invoke( // @formatter:on
    address = address,
    thenBlock = thenBlock,
    catchBlock = catchBlock,
    returnType = materializeType(returnType),
    paramTypes = paramTypes.map(::materializeType),
    args = args,
    name = name
)

inline fun NativeCodeGenerator.invokeWithOperandBundles( // @formatter:off
    address: LLVMValueRef,
    thenBlock: LLVMBasicBlockRef,
    catchBlock: LLVMBasicBlockRef,
    returnType: LLVMTypeRef,
    paramTypes: List<LLVMTypeRef> = emptyList(),
    args: List<LLVMValueRef> = emptyList(),
    operandBundles: List<LLVMOperandBundleRef> = emptyList(),
    name: String? = null
): LLVMValueRef? { // @formatter:on
    val type = LLVMFunctionType(returnType, paramTypes.toCValues(), paramTypes.size, 0)
    return LLVMBuildInvokeWithOperandBundles(
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
}

inline fun NativeCodeGenerator.invokeWithOperandBundles( // @formatter:off
    address: LLVMValueRef,
    thenBlock: LLVMBasicBlockRef,
    catchBlock: LLVMBasicBlockRef,
    returnType: IrType = irBuiltIns.unitType,
    paramTypes: List<IrType> = emptyList(),
    args: List<LLVMValueRef> = emptyList(),
    operandBundles: List<LLVMOperandBundleRef> = emptyList(),
    name: String? = null
): LLVMValueRef? = invokeWithOperandBundles( // @formatter:on
    address = address,
    thenBlock = thenBlock,
    catchBlock = catchBlock,
    returnType = materializeType(returnType),
    paramTypes = paramTypes.map(::materializeType),
    args = args,
    operandBundles = operandBundles,
    name = name
)

inline fun NativeCodeGenerator.landingPad(
    type: LLVMTypeRef, personalityFunction: LLVMValueRef, clauseCount: Int = 0, name: String? = null
): LLVMValueRef? = LLVMBuildLandingPad(functionBuilder, type, personalityFunction, clauseCount, name)

inline fun NativeCodeGenerator.landingPad(
    type: IrType, personalityFunction: LLVMValueRef, clauseCount: Int = 0, name: String? = null
): LLVMValueRef? = landingPad(materializeType(type), personalityFunction, clauseCount, name)

inline fun NativeCodeGenerator.select(
    condition: LLVMValueRef, thenValue: LLVMValueRef, elseValue: LLVMValueRef, name: String? = null
): LLVMValueRef? = LLVMBuildSelect(functionBuilder, condition, thenValue, elseValue, name)

inline fun NativeCodeGenerator.vaArg(
    list: LLVMValueRef, type: LLVMTypeRef, name: String? = null
): LLVMValueRef? = LLVMBuildVAArg(functionBuilder, list, type, name)

inline fun NativeCodeGenerator.vaArg(list: LLVMValueRef, type: IrType, name: String? = null): LLVMValueRef? =
    vaArg(list, materializeType(type), name)

inline fun NativeCodeGenerator.extractElement(
    vector: LLVMValueRef, index: LLVMValueRef, name: String? = null
): LLVMValueRef? = LLVMBuildExtractElement(functionBuilder, vector, index, name)

inline fun NativeCodeGenerator.insertElement(
    vector: LLVMValueRef, element: LLVMValueRef, index: LLVMValueRef, name: String? = null
): LLVMValueRef? = LLVMBuildInsertElement(functionBuilder, vector, element, index, name)

inline fun NativeCodeGenerator.shuffleVector(
    lhs: LLVMValueRef, rhs: LLVMValueRef, mask: LLVMValueRef, name: String? = null
): LLVMValueRef? = LLVMBuildShuffleVector(functionBuilder, lhs, rhs, mask, name)

inline fun NativeCodeGenerator.extractValue(
    aggregate: LLVMValueRef, index: Int, name: String? = null
): LLVMValueRef? = LLVMBuildExtractValue(functionBuilder, aggregate, index, name)

inline fun NativeCodeGenerator.insertValue(
    aggregate: LLVMValueRef, element: LLVMValueRef, index: Int, name: String? = null
): LLVMValueRef? = LLVMBuildInsertValue(functionBuilder, aggregate, element, index, name)

inline fun NativeCodeGenerator.freeze(value: LLVMValueRef, name: String? = null): LLVMValueRef? =
    LLVMBuildFreeze(functionBuilder, value, name)

inline fun NativeCodeGenerator.isNull(value: LLVMValueRef, name: String? = null): LLVMValueRef? =
    LLVMBuildIsNull(functionBuilder, value, name)

inline fun NativeCodeGenerator.isNotNull(value: LLVMValueRef, name: String? = null): LLVMValueRef? =
    LLVMBuildIsNotNull(functionBuilder, value, name)

inline fun NativeCodeGenerator.ptrDiff(
    type: LLVMTypeRef, lhs: LLVMValueRef, rhs: LLVMValueRef, name: String? = null
): LLVMValueRef? = LLVMBuildPtrDiff2(functionBuilder, type, lhs, rhs, name)

inline fun NativeCodeGenerator.ptrDiff(
    type: IrType, lhs: LLVMValueRef, rhs: LLVMValueRef, name: String? = null
): LLVMValueRef? = ptrDiff(materializeType(type), lhs, rhs, name)

inline fun NativeCodeGenerator.fence(
    ordering: LLVMAtomicOrdering, singleThread: Int, name: String? = null
): LLVMValueRef? = LLVMBuildFence(functionBuilder, ordering, singleThread, name)

inline fun NativeCodeGenerator.fenceSyncScope(
    ordering: LLVMAtomicOrdering, syncScopeId: Int, name: String? = null
): LLVMValueRef? = LLVMBuildFenceSyncScope(functionBuilder, ordering, syncScopeId, name)

inline fun NativeCodeGenerator.atomicRmw(
    operation: LLVMAtomicRMWBinOp,
    pointer: LLVMValueRef,
    value: LLVMValueRef,
    ordering: LLVMAtomicOrdering,
    singleThread: Int
): LLVMValueRef? = LLVMBuildAtomicRMW(functionBuilder, operation, pointer, value, ordering, singleThread)

inline fun NativeCodeGenerator.atomicRmwSyncScope(
    operation: LLVMAtomicRMWBinOp,
    pointer: LLVMValueRef,
    value: LLVMValueRef,
    ordering: LLVMAtomicOrdering,
    syncScopeId: Int
): LLVMValueRef? = LLVMBuildAtomicRMWSyncScope(functionBuilder, operation, pointer, value, ordering, syncScopeId)

inline fun NativeCodeGenerator.atomicCmpXchg(
    pointer: LLVMValueRef,
    compare: LLVMValueRef,
    value: LLVMValueRef,
    successOrdering: LLVMAtomicOrdering,
    failureOrdering: LLVMAtomicOrdering,
    singleThread: Int
): LLVMValueRef? = LLVMBuildAtomicCmpXchg(
    functionBuilder, pointer, compare, value, successOrdering, failureOrdering, singleThread
)

inline fun NativeCodeGenerator.atomicCmpXchgSyncScope(
    pointer: LLVMValueRef,
    compare: LLVMValueRef,
    value: LLVMValueRef,
    successOrdering: LLVMAtomicOrdering,
    failureOrdering: LLVMAtomicOrdering,
    syncScopeId: Int
): LLVMValueRef? = LLVMBuildAtomicCmpXchgSyncScope(
    functionBuilder, pointer, compare, value, successOrdering, failureOrdering, syncScopeId
)