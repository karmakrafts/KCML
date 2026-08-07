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

@file:Suppress("NOTHING_TO_INLINE")

package dev.karmakrafts.kcml.api.backend.wasm

import org.jetbrains.kotlin.wasm.ir.WasmElement
import org.jetbrains.kotlin.wasm.ir.WasmFunctionType
import org.jetbrains.kotlin.wasm.ir.WasmHeapType
import org.jetbrains.kotlin.wasm.ir.WasmImmediate
import org.jetbrains.kotlin.wasm.ir.WasmLocal
import org.jetbrains.kotlin.wasm.ir.WasmOp
import org.jetbrains.kotlin.wasm.ir.WasmSymbol
import org.jetbrains.kotlin.wasm.ir.WasmSymbolReadOnly
import org.jetbrains.kotlin.wasm.ir.WasmType
import org.jetbrains.kotlin.wasm.ir.source.location.SourceLocation
import org.jetbrains.kotlin.wasm.ir.source.location.SourceLocation.NoLocation

/**
 * Emits a WebAssembly instruction without immediates. See [MDN WebAssembly instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference#instructions).
 * @param op Instruction opcode to emit.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.instr(op: WasmOp, location: SourceLocation = NoLocation) {
    expressionBuilder.buildInstr(op, location)
}

/**
 * Emits a WebAssembly instruction with one immediate. See [MDN WebAssembly instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference#instructions).
 * @param op Instruction opcode to emit.
 * @param immediate Immediate operand encoded with the instruction.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.instr(
    op: WasmOp, immediate: WasmImmediate, location: SourceLocation = NoLocation
) {
    expressionBuilder.buildInstr(op, location, immediate)
}

/**
 * Emits a WebAssembly instruction with two immediates. See [MDN WebAssembly instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference#instructions).
 * @param op Instruction opcode to emit.
 * @param immediate1 First immediate operand.
 * @param immediate2 Second immediate operand.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.instr(
    op: WasmOp, immediate1: WasmImmediate, immediate2: WasmImmediate, location: SourceLocation = NoLocation
) {
    expressionBuilder.buildInstr(op, location, immediate1, immediate2)
}

/**
 * Emits a WebAssembly instruction with three immediates. See [MDN WebAssembly instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference#instructions).
 * @param op Instruction opcode to emit.
 * @param immediate1 First immediate operand.
 * @param immediate2 Second immediate operand.
 * @param immediate3 Third immediate operand.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.instr(
    op: WasmOp,
    immediate1: WasmImmediate,
    immediate2: WasmImmediate,
    immediate3: WasmImmediate,
    location: SourceLocation = NoLocation
) {
    expressionBuilder.buildInstr(op, location, immediate1, immediate2, immediate3)
}

/**
 * Emits a WebAssembly instruction with four immediates. See [MDN WebAssembly instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference#instructions).
 * @param op Instruction opcode to emit.
 * @param immediate1 First immediate operand.
 * @param immediate2 Second immediate operand.
 * @param immediate3 Third immediate operand.
 * @param immediate4 Fourth immediate operand.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.instr(
    op: WasmOp,
    immediate1: WasmImmediate,
    immediate2: WasmImmediate,
    immediate3: WasmImmediate,
    immediate4: WasmImmediate,
    location: SourceLocation = NoLocation
) {
    expressionBuilder.buildInstr(op, location, immediate1, immediate2, immediate3, immediate4)
}

/**
 * Emits [`drop`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Control_flow/Drop) to discard the top stack value.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.drop(location: SourceLocation = NoLocation) {
    expressionBuilder.buildDrop(location)
}

/**
 * Emits [`nop`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Control_flow/nop), which performs no operation.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.nop(location: SourceLocation = NoLocation) {
    expressionBuilder.buildNop(location)
}

/**
 * Emits [`unreachable`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Control_flow/unreachable), which traps when executed.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.unreachable(location: SourceLocation = NoLocation) {
    expressionBuilder.buildUnreachable(location)
}

/**
 * Emits an [`i32.const`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric/Constant) for a Boolean value.
 * @param value Boolean value encoded as zero or one.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.const(value: Boolean, location: SourceLocation = NoLocation) {
    expressionBuilder.buildConstI32(if (value) 1 else 0, location)
}

/**
 * Emits an [`i32.const`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric/Constant) for a byte value.
 * @param value Byte value to emit.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.const(value: Byte, location: SourceLocation = NoLocation) {
    expressionBuilder.buildConstI32(value.toInt(), location)
}

/**
 * Emits an [`i32.const`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric/Constant) for a short value.
 * @param value Short value to emit.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.const(value: Short, location: SourceLocation = NoLocation) {
    expressionBuilder.buildConstI32(value.toInt(), location)
}

/**
 * Emits an [`i32.const`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric/Constant) for an integer value.
 * @param value Integer value to emit.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.const(value: Int, location: SourceLocation = NoLocation) {
    expressionBuilder.buildConstI32(value, location)
}

/**
 * Emits an [`i64.const`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric/Constant) for a long value.
 * @param value Long value to emit.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.const(value: Long, location: SourceLocation = NoLocation) {
    expressionBuilder.buildConstI64(value, location)
}

/**
 * Emits an [`f32.const`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric/Constant) for a floating-point value.
 * @param value Single-precision value to emit.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.const(value: Float, location: SourceLocation = NoLocation) {
    expressionBuilder.buildConstF32(value, location)
}

/**
 * Emits an [`f64.const`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric/Constant) for a floating-point value.
 * @param value Double-precision value to emit.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.const(value: Double, location: SourceLocation = NoLocation) {
    expressionBuilder.buildConstF64(value, location)
}

/**
 * Emits a symbolic [`i32.const`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric/Constant).
 * @param value Symbol whose resolved integer value is emitted.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.const(value: WasmSymbol<Int>, location: SourceLocation = NoLocation) {
    expressionBuilder.buildConstI32Symbol(value, location)
}

/**
 * Emits a function-typed [`block`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Control_flow/block).
 * @param label Optional diagnostic label for the block.
 * @param resultType Symbolic function type describing block parameters and results.
 * @param body Callback that emits the block body and receives its absolute block level.
 */
inline fun WasmCodeGenerator.functionTypedBlock(
    label: String?, resultType: WasmSymbolReadOnly<WasmFunctionType>, body: (Int) -> Unit
) {
    expressionBuilder.buildFunctionTypedBlock(label, resultType, body)
}

/**
 * Emits a structured [`block`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Control_flow/block) and its body.
 * @param label Optional diagnostic label for the block.
 * @param resultType Optional value type produced by the block.
 * @param body Callback that emits the block body and receives its absolute block level.
 */
inline fun WasmCodeGenerator.block(
    label: String?, resultType: WasmType? = null, body: (Int) -> Unit
) {
    expressionBuilder.buildBlock(label, resultType, body)
}

/**
 * Starts a [`block`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Control_flow/block) and returns its absolute block level.
 * @param resultType Optional value type produced by the block.
 */
inline fun WasmCodeGenerator.block(resultType: WasmType? = null): Int = expressionBuilder.buildBlock(resultType)

/**
 * Emits a structured [`loop`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Control_flow/loop) and its body.
 * @param label Optional diagnostic label for the loop.
 * @param resultType Optional value type produced by the loop.
 * @param body Callback that emits the loop body and receives its absolute block level.
 */
inline fun WasmCodeGenerator.loop(
    label: String?, resultType: WasmType? = null, body: (Int) -> Unit
) {
    expressionBuilder.buildLoop(label, resultType, body)
}

/**
 * Starts an [`if`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Control_flow/if...else) block.
 * @param label Optional diagnostic label for the block.
 * @param resultType Optional value type produced by the block.
 */
inline fun WasmCodeGenerator.ifBlock(label: String?, resultType: WasmType? = null) {
    expressionBuilder.buildIf(label, resultType)
}

/**
 * Emits the [`else`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Control_flow/if...else) delimiter of an `if` block.
 * @param location Optional source location associated with the delimiter.
 */
inline fun WasmCodeGenerator.elseBlock(location: SourceLocation? = null) {
    expressionBuilder.buildElse(location)
}

/**
 * Emits [`end`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Control_flow/block) for the current structured instruction.
 * @param location Optional source location associated with the delimiter.
 */
inline fun WasmCodeGenerator.end(location: SourceLocation? = null) {
    expressionBuilder.buildEnd(location)
}

/**
 * Emits a branch instruction. See [MDN WebAssembly control flow](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Control_flow).
 * @param op Branch opcode to emit.
 * @param absoluteBlockLevel Absolute target block level.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.brInstr(
    op: WasmOp, absoluteBlockLevel: Int, location: SourceLocation = NoLocation
) {
    expressionBuilder.buildBrInstr(op, absoluteBlockLevel, location)
}

/**
 * Emits a typed reference branch. See [MDN WebAssembly control flow](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Control_flow).
 * @param op Reference branch opcode to emit.
 * @param absoluteBlockLevel Absolute target block level.
 * @param fromIsNullable Whether the source reference type is nullable.
 * @param toIsNullable Whether the target reference type is nullable.
 * @param from Source heap type.
 * @param to Target heap type tested by the branch.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.brOnCastInstr(
    op: WasmOp,
    absoluteBlockLevel: Int,
    fromIsNullable: Boolean,
    toIsNullable: Boolean,
    from: WasmHeapType,
    to: WasmHeapType,
    location: SourceLocation = NoLocation
) {
    expressionBuilder.buildBrOnCastInstr(
        op, absoluteBlockLevel, fromIsNullable, toIsNullable, from, to, location
    )
}

/**
 * Emits an unconditional [`br`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Control_flow/br).
 * @param absoluteBlockLevel Absolute target block level.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.br(absoluteBlockLevel: Int, location: SourceLocation = NoLocation) {
    expressionBuilder.buildBr(absoluteBlockLevel, location)
}

/**
 * Emits a conditional [`br_if`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Control_flow/br_if).
 * @param absoluteBlockLevel Absolute target block level.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.brIf(absoluteBlockLevel: Int, location: SourceLocation = NoLocation) {
    expressionBuilder.buildBrIf(absoluteBlockLevel, location)
}

/**
 * Emits `br_on_cast`, branching when a reference cast succeeds. See [MDN WebAssembly instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference#instructions).
 * @param absoluteBlockLevel Absolute target block level.
 * @param fromIsNullable Whether the source reference type is nullable.
 * @param toIsNullable Whether the target reference type is nullable.
 * @param from Source heap type.
 * @param to Target heap type tested by the cast.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.brOnCast(
    absoluteBlockLevel: Int,
    fromIsNullable: Boolean,
    toIsNullable: Boolean,
    from: WasmHeapType,
    to: WasmHeapType,
    location: SourceLocation = NoLocation
) {
    brOnCastInstr(
        WasmOp.BR_ON_CAST, absoluteBlockLevel, fromIsNullable, toIsNullable, from, to, location
    )
}

/**
 * Emits `br_on_cast_fail`, branching when a reference cast fails. See [MDN WebAssembly instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference#instructions).
 * @param absoluteBlockLevel Absolute target block level.
 * @param fromIsNullable Whether the source reference type is nullable.
 * @param toIsNullable Whether the target reference type is nullable.
 * @param from Source heap type.
 * @param to Target heap type tested by the cast.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.brOnCastFail(
    absoluteBlockLevel: Int,
    fromIsNullable: Boolean,
    toIsNullable: Boolean,
    from: WasmHeapType,
    to: WasmHeapType,
    location: SourceLocation = NoLocation
) {
    brOnCastInstr(
        WasmOp.BR_ON_CAST_FAIL, absoluteBlockLevel, fromIsNullable, toIsNullable, from, to, location
    )
}

/**
 * Emits a tagged WebAssembly `throw`. See [MDN WebAssembly instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference#instructions).
 * @param tagIndex Symbolic index of the exception tag.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.throwException(
    tagIndex: WasmSymbol<Int>, location: SourceLocation = NoLocation
) {
    expressionBuilder.buildThrow(tagIndex, location)
}

/**
 * Emits `throw_ref` for an exception reference. See [MDN WebAssembly instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference#instructions).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.throwRef(location: SourceLocation = NoLocation) {
    expressionBuilder.buildThrowRef(location)
}

/**
 * Emits a legacy WebAssembly `try` block. See [MDN WebAssembly instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference#instructions).
 * @param resultType Optional value type produced by the block.
 * @param body Callback that emits the protected body and receives its absolute block level.
 */
inline fun WasmCodeGenerator.tryBlock(resultType: WasmType? = null, noinline body: (Int) -> Unit) {
    expressionBuilder.buildTry(resultType, body)
}

/**
 * Emits a WebAssembly `try_table` block. See [MDN WebAssembly instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference#instructions).
 * @param catch1 First catch clause.
 * @param catch2 Optional second catch clause.
 * @param resultType Optional value type produced by the block.
 * @param body Callback that emits the protected body and receives its absolute block level.
 */
inline fun WasmCodeGenerator.tryTable(
    catch1: WasmImmediate.Catch,
    catch2: WasmImmediate.Catch? = null,
    resultType: WasmType? = null,
    noinline body: (Int) -> Unit
) {
    expressionBuilder.buildTryTable(catch1, catch2, resultType, body)
}

/**
 * Creates a tagged catch clause for `try_table`. See [MDN WebAssembly instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference#instructions).
 * @param tagIndex Symbolic index of the exception tag.
 * @param absoluteBlockLevel Absolute handler block level.
 */
inline fun WasmCodeGenerator.newCatch(
    tagIndex: WasmSymbol<Int>, absoluteBlockLevel: Int
): WasmImmediate.Catch = expressionBuilder.createNewCatch(tagIndex, absoluteBlockLevel)

/**
 * Creates a catch-all clause for `try_table`. See [MDN WebAssembly instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference#instructions).
 * @param absoluteBlockLevel Absolute handler block level.
 */
inline fun WasmCodeGenerator.newCatchAll(absoluteBlockLevel: Int): WasmImmediate.Catch =
    expressionBuilder.createNewCatchAll(absoluteBlockLevel)

/**
 * Creates a catch-all-reference clause for `try_table`. See [MDN WebAssembly instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference#instructions).
 * @param absoluteBlockLevel Absolute handler block level.
 */
inline fun WasmCodeGenerator.newCatchAllRef(absoluteBlockLevel: Int): WasmImmediate.Catch =
    expressionBuilder.createNewCatchAllRef(absoluteBlockLevel)

/**
 * Emits a tagged `catch` clause. See [MDN WebAssembly instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference#instructions).
 * @param tagIndex Symbolic index of the exception tag.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.catchException(
    tagIndex: WasmSymbol<Int>, location: SourceLocation = NoLocation
) {
    expressionBuilder.buildCatch(tagIndex, location)
}

/** Emits a `catch_all` clause. See [MDN WebAssembly instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference#instructions). */
inline fun WasmCodeGenerator.catchAll() {
    expressionBuilder.buildCatchAll()
}

/**
 * Emits a direct [`call`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Control_flow/call).
 * @param functionIndex Index of the function to invoke.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.call(
    functionIndex: WasmImmediate.FuncIdx, location: SourceLocation = NoLocation
) {
    expressionBuilder.buildCall(functionIndex, location)
}

/**
 * Emits Kotlin/Wasm's pure-call instruction. See [MDN WebAssembly function calls](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Control_flow/call).
 * @param functionIndex Index of the function to invoke.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.callPure(
    functionIndex: WasmImmediate.FuncIdx, location: SourceLocation = NoLocation
) {
    instr(WasmOp.CALL_PURE, functionIndex, location)
}

/**
 * Emits an indirect [`call_indirect`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Control_flow/call_indirect).
 * @param typeIndex Expected function type index.
 * @param tableIndex Symbolic index of the function table.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.callIndirect(
    typeIndex: WasmImmediate.TypeIdx,
    tableIndex: WasmSymbolReadOnly<Int> = WasmSymbol(0),
    location: SourceLocation = NoLocation
) {
    expressionBuilder.buildCallIndirect(typeIndex, tableIndex, location)
}

/**
 * Emits [`local.get`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Variables/Local_get).
 * @param local Local whose value is loaded.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.localGet(local: WasmLocal, location: SourceLocation = NoLocation) {
    expressionBuilder.buildGetLocal(local, location)
}

/**
 * Emits [`local.set`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Variables/Local_set).
 * @param local Local that receives the stack value.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.localSet(local: WasmLocal, location: SourceLocation = NoLocation) {
    expressionBuilder.buildSetLocal(local, location)
}

/**
 * Emits [`local.tee`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Variables/Local_tee).
 * @param local Local that receives the stack value.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.localTee(local: WasmLocal, location: SourceLocation = NoLocation) {
    expressionBuilder.buildTeeLocal(local, location)
}

/**
 * Emits [`global.get`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Variables/Global_get).
 * @param globalIndex Index of the global whose value is loaded.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.globalGet(
    globalIndex: WasmImmediate.GlobalIdx, location: SourceLocation = NoLocation
) {
    expressionBuilder.buildGetGlobal(globalIndex, location)
}

/**
 * Emits [`global.set`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Variables/Global_set).
 * @param globalIndex Index of the global that receives the stack value.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.globalSet(
    globalIndex: WasmImmediate.GlobalIdx, location: SourceLocation = NoLocation
) {
    expressionBuilder.buildSetGlobal(globalIndex, location)
}

/**
 * Emits `struct.get`. See [MDN WebAssembly instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference#instructions).
 * @param typeIndex Structure type index.
 * @param fieldIndex Field index to read.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.structGet(
    typeIndex: WasmImmediate.TypeIdx, fieldIndex: Int, location: SourceLocation = NoLocation
) {
    expressionBuilder.buildStructGet(typeIndex, fieldIndex, location)
}

/**
 * Emits `struct.new`. See [MDN WebAssembly instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference#instructions).
 * @param typeIndex Structure type index.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.structNew(
    typeIndex: WasmImmediate.TypeIdx, location: SourceLocation = NoLocation
) {
    expressionBuilder.buildStructNew(typeIndex, location)
}

/**
 * Emits `struct.set`. See [MDN WebAssembly instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference#instructions).
 * @param typeIndex Structure type index.
 * @param fieldIndex Field index to write.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.structSet(
    typeIndex: WasmImmediate.TypeIdx, fieldIndex: Int, location: SourceLocation = NoLocation
) {
    expressionBuilder.buildStructSet(typeIndex, fieldIndex, location)
}

/**
 * Emits nullable `ref.cast`. See [MDN WebAssembly instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference#instructions).
 * @param type Target heap type.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.refCastNull(
    type: WasmHeapType, location: SourceLocation = NoLocation
) {
    expressionBuilder.buildRefCastNullStatic(type, location)
}

/**
 * Emits `ref.cast`. See [MDN WebAssembly instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference#instructions).
 * @param type Target heap type.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.refCast(type: WasmHeapType, location: SourceLocation = NoLocation) {
    expressionBuilder.buildRefCastStatic(type, location)
}

/**
 * Emits `ref.test`. See [MDN WebAssembly instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference#instructions).
 * @param type Heap type to test against.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.refTest(type: WasmHeapType, location: SourceLocation = NoLocation) {
    expressionBuilder.buildRefTestStatic(type, location)
}

/**
 * Emits nullable `ref.test`. See [MDN WebAssembly instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference#instructions).
 * @param type Heap type to test against.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.refTestNull(type: WasmHeapType, location: SourceLocation = NoLocation) {
    instr(WasmOp.REF_TEST_NULL, WasmImmediate.HeapType(type), location)
}

/**
 * Emits [`ref.null`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Reference/null).
 * @param type Heap type of the null reference.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.refNull(type: WasmHeapType, location: SourceLocation = NoLocation) {
    expressionBuilder.buildRefNull(type, location)
}

/**
 * Adds a comment to the previously emitted instruction; see [MDN WebAssembly instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference#instructions) for the commented syntax.
 * @param text Lazily evaluated comment text.
 */
inline fun WasmCodeGenerator.commentPreviousInstr(text: () -> String) {
    expressionBuilder.commentPreviousInstr(text)
}

/**
 * Starts a comment group around WebAssembly instructions; see [MDN WebAssembly instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference#instructions).
 * @param text Lazily evaluated group comment text.
 */
inline fun WasmCodeGenerator.commentGroup(text: () -> String) {
    expressionBuilder.commentGroupStart(text)
}

/** Ends the current instruction comment group; see [MDN WebAssembly instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference#instructions). */
inline fun WasmCodeGenerator.endCommentGroup() {
    expressionBuilder.commentGroupEnd()
}

/**
 * Emits numeric [`i32.eqz`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric/Equal).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i32Eqz(location: SourceLocation = NoLocation) = instr(WasmOp.I32_EQZ, location)

/**
 * Emits numeric [`i64.eqz`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric/Equal).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i64Eqz(location: SourceLocation = NoLocation) = instr(WasmOp.I64_EQZ, location)

/**
 * Emits numeric `i32.clz`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i32Clz(location: SourceLocation = NoLocation) = instr(WasmOp.I32_CLZ, location)

/**
 * Emits numeric `i32.ctz`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i32Ctz(location: SourceLocation = NoLocation) = instr(WasmOp.I32_CTZ, location)

/**
 * Emits numeric `i32.popcnt`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i32Popcnt(location: SourceLocation = NoLocation) = instr(WasmOp.I32_POPCNT, location)

/**
 * Emits numeric `i64.clz`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i64Clz(location: SourceLocation = NoLocation) = instr(WasmOp.I64_CLZ, location)

/**
 * Emits numeric `i64.ctz`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i64Ctz(location: SourceLocation = NoLocation) = instr(WasmOp.I64_CTZ, location)

/**
 * Emits numeric `i64.popcnt`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i64Popcnt(location: SourceLocation = NoLocation) = instr(WasmOp.I64_POPCNT, location)

/**
 * Emits numeric `f32.abs`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.f32Abs(location: SourceLocation = NoLocation) = instr(WasmOp.F32_ABS, location)

/**
 * Emits numeric `f32.neg`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.f32Neg(location: SourceLocation = NoLocation) = instr(WasmOp.F32_NEG, location)

/**
 * Emits numeric `f32.ceil`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.f32Ceil(location: SourceLocation = NoLocation) = instr(WasmOp.F32_CEIL, location)

/**
 * Emits numeric `f32.floor`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.f32Floor(location: SourceLocation = NoLocation) = instr(WasmOp.F32_FLOOR, location)

/**
 * Emits numeric `f32.trunc`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.f32Trunc(location: SourceLocation = NoLocation) = instr(WasmOp.F32_TRUNC, location)

/**
 * Emits numeric `f32.nearest`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.f32Nearest(location: SourceLocation = NoLocation) = instr(WasmOp.F32_NEAREST, location)

/**
 * Emits numeric `f32.sqrt`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.f32Sqrt(location: SourceLocation = NoLocation) = instr(WasmOp.F32_SQRT, location)

/**
 * Emits numeric `f64.abs`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.f64Abs(location: SourceLocation = NoLocation) = instr(WasmOp.F64_ABS, location)

/**
 * Emits numeric `f64.neg`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.f64Neg(location: SourceLocation = NoLocation) = instr(WasmOp.F64_NEG, location)

/**
 * Emits numeric `f64.ceil`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.f64Ceil(location: SourceLocation = NoLocation) = instr(WasmOp.F64_CEIL, location)

/**
 * Emits numeric `f64.floor`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.f64Floor(location: SourceLocation = NoLocation) = instr(WasmOp.F64_FLOOR, location)

/**
 * Emits numeric `f64.trunc`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.f64Trunc(location: SourceLocation = NoLocation) = instr(WasmOp.F64_TRUNC, location)

/**
 * Emits numeric `f64.nearest`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.f64Nearest(location: SourceLocation = NoLocation) = instr(WasmOp.F64_NEAREST, location)

/**
 * Emits numeric `f64.sqrt`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.f64Sqrt(location: SourceLocation = NoLocation) = instr(WasmOp.F64_SQRT, location)

/**
 * Emits numeric `i32.wrap_i64`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i32WrapI64(location: SourceLocation = NoLocation) = instr(WasmOp.I32_WRAP_I64, location)

/**
 * Emits numeric `i32.trunc_f32_s`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i32TruncF32S(location: SourceLocation = NoLocation) =
    instr(WasmOp.I32_TRUNC_F32_S, location)

/**
 * Emits numeric `i32.trunc_f32_u`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i32TruncF32U(location: SourceLocation = NoLocation) =
    instr(WasmOp.I32_TRUNC_F32_U, location)

/**
 * Emits numeric `i32.trunc_f64_s`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i32TruncF64S(location: SourceLocation = NoLocation) =
    instr(WasmOp.I32_TRUNC_F64_S, location)

/**
 * Emits numeric `i32.trunc_f64_u`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i32TruncF64U(location: SourceLocation = NoLocation) =
    instr(WasmOp.I32_TRUNC_F64_U, location)

/**
 * Emits numeric `i64.extend_i32_s`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i64ExtendI32S(location: SourceLocation = NoLocation) =
    instr(WasmOp.I64_EXTEND_I32_S, location)

/**
 * Emits numeric `i64.extend_i32_u`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i64ExtendI32U(location: SourceLocation = NoLocation) =
    instr(WasmOp.I64_EXTEND_I32_U, location)

/**
 * Emits numeric `i64.trunc_f32_s`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i64TruncF32S(location: SourceLocation = NoLocation) =
    instr(WasmOp.I64_TRUNC_F32_S, location)

/**
 * Emits numeric `i64.trunc_f32_u`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i64TruncF32U(location: SourceLocation = NoLocation) =
    instr(WasmOp.I64_TRUNC_F32_U, location)

/**
 * Emits numeric `i64.trunc_f64_s`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i64TruncF64S(location: SourceLocation = NoLocation) =
    instr(WasmOp.I64_TRUNC_F64_S, location)

/**
 * Emits numeric `i64.trunc_f64_u`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i64TruncF64U(location: SourceLocation = NoLocation) =
    instr(WasmOp.I64_TRUNC_F64_U, location)

/**
 * Emits numeric `f32.convert_i32_s`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.f32ConvertI32S(location: SourceLocation = NoLocation) =
    instr(WasmOp.F32_CONVERT_I32_S, location)

/**
 * Emits numeric `f32.convert_i32_u`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.f32ConvertI32U(location: SourceLocation = NoLocation) =
    instr(WasmOp.F32_CONVERT_I32_U, location)

/**
 * Emits numeric `f32.convert_i64_s`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.f32ConvertI64S(location: SourceLocation = NoLocation) =
    instr(WasmOp.F32_CONVERT_I64_S, location)

/**
 * Emits numeric `f32.convert_i64_u`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.f32ConvertI64U(location: SourceLocation = NoLocation) =
    instr(WasmOp.F32_CONVERT_I64_U, location)

/**
 * Emits numeric `f32.demote_f64`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.f32DemoteF64(location: SourceLocation = NoLocation) =
    instr(WasmOp.F32_DEMOTE_F64, location)

/**
 * Emits numeric `f64.convert_i32_s`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.f64ConvertI32S(location: SourceLocation = NoLocation) =
    instr(WasmOp.F64_CONVERT_I32_S, location)

/**
 * Emits numeric `f64.convert_i32_u`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.f64ConvertI32U(location: SourceLocation = NoLocation) =
    instr(WasmOp.F64_CONVERT_I32_U, location)

/**
 * Emits numeric `f64.convert_i64_s`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.f64ConvertI64S(location: SourceLocation = NoLocation) =
    instr(WasmOp.F64_CONVERT_I64_S, location)

/**
 * Emits numeric `f64.convert_i64_u`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.f64ConvertI64U(location: SourceLocation = NoLocation) =
    instr(WasmOp.F64_CONVERT_I64_U, location)

/**
 * Emits numeric `f64.promote_f32`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.f64PromoteF32(location: SourceLocation = NoLocation) =
    instr(WasmOp.F64_PROMOTE_F32, location)

/**
 * Emits numeric `i32.reinterpret_f32`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i32ReinterpretF32(location: SourceLocation = NoLocation) =
    instr(WasmOp.I32_REINTERPRET_F32, location)

/**
 * Emits numeric `i64.reinterpret_f64`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i64ReinterpretF64(location: SourceLocation = NoLocation) =
    instr(WasmOp.I64_REINTERPRET_F64, location)

/**
 * Emits numeric `f32.reinterpret_i32`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.f32ReinterpretI32(location: SourceLocation = NoLocation) =
    instr(WasmOp.F32_REINTERPRET_I32, location)

/**
 * Emits numeric `f64.reinterpret_i64`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.f64ReinterpretI64(location: SourceLocation = NoLocation) =
    instr(WasmOp.F64_REINTERPRET_I64, location)

/**
 * Emits numeric `i32.extend8_s`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i32Extend8S(location: SourceLocation = NoLocation) = instr(WasmOp.I32_EXTEND8_S, location)

/**
 * Emits numeric `i32.extend16_s`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i32Extend16S(location: SourceLocation = NoLocation) =
    instr(WasmOp.I32_EXTEND16_S, location)

/**
 * Emits numeric `i64.extend8_s`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i64Extend8S(location: SourceLocation = NoLocation) = instr(WasmOp.I64_EXTEND8_S, location)

/**
 * Emits numeric `i64.extend16_s`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i64Extend16S(location: SourceLocation = NoLocation) =
    instr(WasmOp.I64_EXTEND16_S, location)

/**
 * Emits numeric `i64.extend32_s`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i64Extend32S(location: SourceLocation = NoLocation) =
    instr(WasmOp.I64_EXTEND32_S, location)

/**
 * Emits saturating `i32.trunc_sat_f32_s`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i32TruncSatF32S(location: SourceLocation = NoLocation) =
    instr(WasmOp.I32_TRUNC_SAT_F32_S, location)

/**
 * Emits saturating `i32.trunc_sat_f32_u`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i32TruncSatF32U(location: SourceLocation = NoLocation) =
    instr(WasmOp.I32_TRUNC_SAT_F32_U, location)

/**
 * Emits saturating `i32.trunc_sat_f64_s`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i32TruncSatF64S(location: SourceLocation = NoLocation) =
    instr(WasmOp.I32_TRUNC_SAT_F64_S, location)

/**
 * Emits saturating `i32.trunc_sat_f64_u`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i32TruncSatF64U(location: SourceLocation = NoLocation) =
    instr(WasmOp.I32_TRUNC_SAT_F64_U, location)

/**
 * Emits saturating `i64.trunc_sat_f32_s`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i64TruncSatF32S(location: SourceLocation = NoLocation) =
    instr(WasmOp.I64_TRUNC_SAT_F32_S, location)

/**
 * Emits saturating `i64.trunc_sat_f32_u`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i64TruncSatF32U(location: SourceLocation = NoLocation) =
    instr(WasmOp.I64_TRUNC_SAT_F32_U, location)

/**
 * Emits saturating `i64.trunc_sat_f64_s`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i64TruncSatF64S(location: SourceLocation = NoLocation) =
    instr(WasmOp.I64_TRUNC_SAT_F64_S, location)

/**
 * Emits saturating `i64.trunc_sat_f64_u`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i64TruncSatF64U(location: SourceLocation = NoLocation) =
    instr(WasmOp.I64_TRUNC_SAT_F64_U, location)

/**
 * Emits [`i32.eq`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric/Equal).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i32Eq(location: SourceLocation = NoLocation) = instr(WasmOp.I32_EQ, location)

/**
 * Emits [`i32.ne`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric/Not_equal).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i32Ne(location: SourceLocation = NoLocation) = instr(WasmOp.I32_NE, location)

/**
 * Emits signed `i32.lt`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i32LtS(location: SourceLocation = NoLocation) = instr(WasmOp.I32_LT_S, location)

/**
 * Emits unsigned `i32.lt`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i32LtU(location: SourceLocation = NoLocation) = instr(WasmOp.I32_LT_U, location)

/**
 * Emits signed `i32.gt`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i32GtS(location: SourceLocation = NoLocation) = instr(WasmOp.I32_GT_S, location)

/**
 * Emits unsigned `i32.gt`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i32GtU(location: SourceLocation = NoLocation) = instr(WasmOp.I32_GT_U, location)

/**
 * Emits signed `i32.le`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i32LeS(location: SourceLocation = NoLocation) = instr(WasmOp.I32_LE_S, location)

/**
 * Emits unsigned `i32.le`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i32LeU(location: SourceLocation = NoLocation) = instr(WasmOp.I32_LE_U, location)

/**
 * Emits signed `i32.ge`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i32GeS(location: SourceLocation = NoLocation) = instr(WasmOp.I32_GE_S, location)

/**
 * Emits unsigned `i32.ge`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i32GeU(location: SourceLocation = NoLocation) = instr(WasmOp.I32_GE_U, location)

/**
 * Emits [`i64.eq`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric/Equal).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i64Eq(location: SourceLocation = NoLocation) = instr(WasmOp.I64_EQ, location)

/**
 * Emits [`i64.ne`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric/Not_equal).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i64Ne(location: SourceLocation = NoLocation) = instr(WasmOp.I64_NE, location)

/**
 * Emits signed `i64.lt`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i64LtS(location: SourceLocation = NoLocation) = instr(WasmOp.I64_LT_S, location)

/**
 * Emits unsigned `i64.lt`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i64LtU(location: SourceLocation = NoLocation) = instr(WasmOp.I64_LT_U, location)

/**
 * Emits signed `i64.gt`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i64GtS(location: SourceLocation = NoLocation) = instr(WasmOp.I64_GT_S, location)

/**
 * Emits unsigned `i64.gt`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i64GtU(location: SourceLocation = NoLocation) = instr(WasmOp.I64_GT_U, location)

/**
 * Emits signed `i64.le`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i64LeS(location: SourceLocation = NoLocation) = instr(WasmOp.I64_LE_S, location)

/**
 * Emits unsigned `i64.le`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i64LeU(location: SourceLocation = NoLocation) = instr(WasmOp.I64_LE_U, location)

/**
 * Emits signed `i64.ge`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i64GeS(location: SourceLocation = NoLocation) = instr(WasmOp.I64_GE_S, location)

/**
 * Emits unsigned `i64.ge`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i64GeU(location: SourceLocation = NoLocation) = instr(WasmOp.I64_GE_U, location)

/**
 * Emits [`f32.eq`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric/Equal).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.f32Eq(location: SourceLocation = NoLocation) = instr(WasmOp.F32_EQ, location)

/**
 * Emits [`f32.ne`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric/Not_equal).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.f32Ne(location: SourceLocation = NoLocation) = instr(WasmOp.F32_NE, location)

/**
 * Emits `f32.lt`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.f32Lt(location: SourceLocation = NoLocation) = instr(WasmOp.F32_LT, location)

/**
 * Emits `f32.gt`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.f32Gt(location: SourceLocation = NoLocation) = instr(WasmOp.F32_GT, location)

/**
 * Emits `f32.le`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.f32Le(location: SourceLocation = NoLocation) = instr(WasmOp.F32_LE, location)

/**
 * Emits `f32.ge`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.f32Ge(location: SourceLocation = NoLocation) = instr(WasmOp.F32_GE, location)

/**
 * Emits [`f64.eq`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric/Equal).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.f64Eq(location: SourceLocation = NoLocation) = instr(WasmOp.F64_EQ, location)

/**
 * Emits [`f64.ne`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric/Not_equal).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.f64Ne(location: SourceLocation = NoLocation) = instr(WasmOp.F64_NE, location)

/**
 * Emits `f64.lt`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.f64Lt(location: SourceLocation = NoLocation) = instr(WasmOp.F64_LT, location)

/**
 * Emits `f64.gt`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.f64Gt(location: SourceLocation = NoLocation) = instr(WasmOp.F64_GT, location)

/**
 * Emits `f64.le`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.f64Le(location: SourceLocation = NoLocation) = instr(WasmOp.F64_LE, location)

/**
 * Emits `f64.ge`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.f64Ge(location: SourceLocation = NoLocation) = instr(WasmOp.F64_GE, location)

/**
 * Emits [`i32.add`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric/Addition).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i32Add(location: SourceLocation = NoLocation) = instr(WasmOp.I32_ADD, location)

/**
 * Emits [`i32.sub`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric/Subtraction).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i32Sub(location: SourceLocation = NoLocation) = instr(WasmOp.I32_SUB, location)

/**
 * Emits [`i32.mul`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric/Multiplication).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i32Mul(location: SourceLocation = NoLocation) = instr(WasmOp.I32_MUL, location)

/**
 * Emits signed [`i32.div`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric/Division).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i32DivS(location: SourceLocation = NoLocation) = instr(WasmOp.I32_DIV_S, location)

/**
 * Emits unsigned [`i32.div`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric/Division).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i32DivU(location: SourceLocation = NoLocation) = instr(WasmOp.I32_DIV_U, location)

/**
 * Emits signed `i32.rem`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i32RemS(location: SourceLocation = NoLocation) = instr(WasmOp.I32_REM_S, location)

/**
 * Emits unsigned `i32.rem`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i32RemU(location: SourceLocation = NoLocation) = instr(WasmOp.I32_REM_U, location)

/**
 * Emits bitwise `i32.and`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i32And(location: SourceLocation = NoLocation) = instr(WasmOp.I32_AND, location)

/**
 * Emits bitwise `i32.or`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i32Or(location: SourceLocation = NoLocation) = instr(WasmOp.I32_OR, location)

/**
 * Emits bitwise `i32.xor`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i32Xor(location: SourceLocation = NoLocation) = instr(WasmOp.I32_XOR, location)

/**
 * Emits `i32.shl`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i32Shl(location: SourceLocation = NoLocation) = instr(WasmOp.I32_SHL, location)

/**
 * Emits signed `i32.shr`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i32ShrS(location: SourceLocation = NoLocation) = instr(WasmOp.I32_SHR_S, location)

/**
 * Emits unsigned `i32.shr`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i32ShrU(location: SourceLocation = NoLocation) = instr(WasmOp.I32_SHR_U, location)

/**
 * Emits `i32.rotl`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i32Rotl(location: SourceLocation = NoLocation) = instr(WasmOp.I32_ROTL, location)

/**
 * Emits `i32.rotr`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i32Rotr(location: SourceLocation = NoLocation) = instr(WasmOp.I32_ROTR, location)

/**
 * Emits [`i64.add`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric/Addition).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i64Add(location: SourceLocation = NoLocation) = instr(WasmOp.I64_ADD, location)

/**
 * Emits [`i64.sub`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric/Subtraction).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i64Sub(location: SourceLocation = NoLocation) = instr(WasmOp.I64_SUB, location)

/**
 * Emits [`i64.mul`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric/Multiplication).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i64Mul(location: SourceLocation = NoLocation) = instr(WasmOp.I64_MUL, location)

/**
 * Emits signed [`i64.div`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric/Division).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i64DivS(location: SourceLocation = NoLocation) = instr(WasmOp.I64_DIV_S, location)

/**
 * Emits unsigned [`i64.div`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric/Division).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i64DivU(location: SourceLocation = NoLocation) = instr(WasmOp.I64_DIV_U, location)

/**
 * Emits signed `i64.rem`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i64RemS(location: SourceLocation = NoLocation) = instr(WasmOp.I64_REM_S, location)

/**
 * Emits unsigned `i64.rem`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i64RemU(location: SourceLocation = NoLocation) = instr(WasmOp.I64_REM_U, location)

/**
 * Emits bitwise `i64.and`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i64And(location: SourceLocation = NoLocation) = instr(WasmOp.I64_AND, location)

/**
 * Emits bitwise `i64.or`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i64Or(location: SourceLocation = NoLocation) = instr(WasmOp.I64_OR, location)

/**
 * Emits bitwise `i64.xor`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i64Xor(location: SourceLocation = NoLocation) = instr(WasmOp.I64_XOR, location)

/**
 * Emits `i64.shl`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i64Shl(location: SourceLocation = NoLocation) = instr(WasmOp.I64_SHL, location)

/**
 * Emits signed `i64.shr`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i64ShrS(location: SourceLocation = NoLocation) = instr(WasmOp.I64_SHR_S, location)

/**
 * Emits unsigned `i64.shr`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i64ShrU(location: SourceLocation = NoLocation) = instr(WasmOp.I64_SHR_U, location)

/**
 * Emits `i64.rotl`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i64Rotl(location: SourceLocation = NoLocation) = instr(WasmOp.I64_ROTL, location)

/**
 * Emits `i64.rotr`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i64Rotr(location: SourceLocation = NoLocation) = instr(WasmOp.I64_ROTR, location)

/**
 * Emits [`f32.add`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric/Addition).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.f32Add(location: SourceLocation = NoLocation) = instr(WasmOp.F32_ADD, location)

/**
 * Emits [`f32.sub`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric/Subtraction).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.f32Sub(location: SourceLocation = NoLocation) = instr(WasmOp.F32_SUB, location)

/**
 * Emits [`f32.mul`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric/Multiplication).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.f32Mul(location: SourceLocation = NoLocation) = instr(WasmOp.F32_MUL, location)

/**
 * Emits [`f32.div`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric/Division).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.f32Div(location: SourceLocation = NoLocation) = instr(WasmOp.F32_DIV, location)

/**
 * Emits `f32.min`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.f32Min(location: SourceLocation = NoLocation) = instr(WasmOp.F32_MIN, location)

/**
 * Emits `f32.max`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.f32Max(location: SourceLocation = NoLocation) = instr(WasmOp.F32_MAX, location)

/**
 * Emits `f32.copysign`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.f32Copysign(location: SourceLocation = NoLocation) = instr(WasmOp.F32_COPYSIGN, location)

/**
 * Emits [`f64.add`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric/Addition).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.f64Add(location: SourceLocation = NoLocation) = instr(WasmOp.F64_ADD, location)

/**
 * Emits [`f64.sub`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric/Subtraction).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.f64Sub(location: SourceLocation = NoLocation) = instr(WasmOp.F64_SUB, location)

/**
 * Emits [`f64.mul`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric/Multiplication).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.f64Mul(location: SourceLocation = NoLocation) = instr(WasmOp.F64_MUL, location)

/**
 * Emits [`f64.div`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric/Division).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.f64Div(location: SourceLocation = NoLocation) = instr(WasmOp.F64_DIV, location)

/**
 * Emits `f64.min`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.f64Min(location: SourceLocation = NoLocation) = instr(WasmOp.F64_MIN, location)

/**
 * Emits `f64.max`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.f64Max(location: SourceLocation = NoLocation) = instr(WasmOp.F64_MAX, location)

/**
 * Emits `f64.copysign`. See [MDN WebAssembly numeric instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Numeric).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.f64Copysign(location: SourceLocation = NoLocation) = instr(WasmOp.F64_COPYSIGN, location)

/**
 * Emits a WebAssembly memory instruction with a [`memarg`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Memory/Load).
 * @param op Memory instruction opcode to emit.
 * @param align Encoded alignment exponent.
 * @param offset Static byte offset added to the dynamic address.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.memoryInstr(
    op: WasmOp, align: UInt, offset: UInt = 0U, location: SourceLocation = NoLocation
) = instr(op, WasmImmediate.MemArg(align, offset), location)

/**
 * Emits [`i32.load`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Memory/Load).
 * @param align Encoded alignment exponent.
 * @param offset Static byte offset added to the dynamic address.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i32Load(align: UInt, offset: UInt = 0U, location: SourceLocation = NoLocation) =
    memoryInstr(WasmOp.I32_LOAD, align, offset, location)

/**
 * Emits [`i64.load`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Memory/Load).
 * @param align Encoded alignment exponent.
 * @param offset Static byte offset added to the dynamic address.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i64Load(align: UInt, offset: UInt = 0U, location: SourceLocation = NoLocation) =
    memoryInstr(WasmOp.I64_LOAD, align, offset, location)

/**
 * Emits [`f32.load`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Memory/Load).
 * @param align Encoded alignment exponent.
 * @param offset Static byte offset added to the dynamic address.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.f32Load(align: UInt, offset: UInt = 0U, location: SourceLocation = NoLocation) =
    memoryInstr(WasmOp.F32_LOAD, align, offset, location)

/**
 * Emits [`f64.load`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Memory/Load).
 * @param align Encoded alignment exponent.
 * @param offset Static byte offset added to the dynamic address.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.f64Load(align: UInt, offset: UInt = 0U, location: SourceLocation = NoLocation) =
    memoryInstr(WasmOp.F64_LOAD, align, offset, location)

/**
 * Emits sign-extending `i32.load8_s`. See [MDN WebAssembly loads](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Memory/Load).
 * @param align Encoded alignment exponent.
 * @param offset Static byte offset added to the dynamic address.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i32Load8S(align: UInt, offset: UInt = 0U, location: SourceLocation = NoLocation) =
    memoryInstr(WasmOp.I32_LOAD8_S, align, offset, location)

/**
 * Emits zero-extending `i32.load8_u`. See [MDN WebAssembly loads](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Memory/Load).
 * @param align Encoded alignment exponent.
 * @param offset Static byte offset added to the dynamic address.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i32Load8U(align: UInt, offset: UInt = 0U, location: SourceLocation = NoLocation) =
    memoryInstr(WasmOp.I32_LOAD8_U, align, offset, location)

/**
 * Emits sign-extending `i32.load16_s`. See [MDN WebAssembly loads](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Memory/Load).
 * @param align Encoded alignment exponent.
 * @param offset Static byte offset added to the dynamic address.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i32Load16S(align: UInt, offset: UInt = 0U, location: SourceLocation = NoLocation) =
    memoryInstr(WasmOp.I32_LOAD16_S, align, offset, location)

/**
 * Emits zero-extending `i32.load16_u`. See [MDN WebAssembly loads](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Memory/Load).
 * @param align Encoded alignment exponent.
 * @param offset Static byte offset added to the dynamic address.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i32Load16U(align: UInt, offset: UInt = 0U, location: SourceLocation = NoLocation) =
    memoryInstr(WasmOp.I32_LOAD16_U, align, offset, location)

/**
 * Emits sign-extending `i64.load8_s`. See [MDN WebAssembly loads](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Memory/Load).
 * @param align Encoded alignment exponent.
 * @param offset Static byte offset added to the dynamic address.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i64Load8S(align: UInt, offset: UInt = 0U, location: SourceLocation = NoLocation) =
    memoryInstr(WasmOp.I64_LOAD8_S, align, offset, location)

/**
 * Emits zero-extending `i64.load8_u`. See [MDN WebAssembly loads](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Memory/Load).
 * @param align Encoded alignment exponent.
 * @param offset Static byte offset added to the dynamic address.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i64Load8U(align: UInt, offset: UInt = 0U, location: SourceLocation = NoLocation) =
    memoryInstr(WasmOp.I64_LOAD8_U, align, offset, location)

/**
 * Emits sign-extending `i64.load16_s`. See [MDN WebAssembly loads](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Memory/Load).
 * @param align Encoded alignment exponent.
 * @param offset Static byte offset added to the dynamic address.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i64Load16S(align: UInt, offset: UInt = 0U, location: SourceLocation = NoLocation) =
    memoryInstr(WasmOp.I64_LOAD16_S, align, offset, location)

/**
 * Emits zero-extending `i64.load16_u`. See [MDN WebAssembly loads](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Memory/Load).
 * @param align Encoded alignment exponent.
 * @param offset Static byte offset added to the dynamic address.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i64Load16U(align: UInt, offset: UInt = 0U, location: SourceLocation = NoLocation) =
    memoryInstr(WasmOp.I64_LOAD16_U, align, offset, location)

/**
 * Emits sign-extending `i64.load32_s`. See [MDN WebAssembly loads](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Memory/Load).
 * @param align Encoded alignment exponent.
 * @param offset Static byte offset added to the dynamic address.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i64Load32S(align: UInt, offset: UInt = 0U, location: SourceLocation = NoLocation) =
    memoryInstr(WasmOp.I64_LOAD32_S, align, offset, location)

/**
 * Emits zero-extending `i64.load32_u`. See [MDN WebAssembly loads](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Memory/Load).
 * @param align Encoded alignment exponent.
 * @param offset Static byte offset added to the dynamic address.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i64Load32U(align: UInt, offset: UInt = 0U, location: SourceLocation = NoLocation) =
    memoryInstr(WasmOp.I64_LOAD32_U, align, offset, location)

/**
 * Emits [`i32.store`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Memory/Store).
 * @param align Encoded alignment exponent.
 * @param offset Static byte offset added to the dynamic address.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i32Store(align: UInt, offset: UInt = 0U, location: SourceLocation = NoLocation) =
    memoryInstr(WasmOp.I32_STORE, align, offset, location)

/**
 * Emits [`i64.store`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Memory/Store).
 * @param align Encoded alignment exponent.
 * @param offset Static byte offset added to the dynamic address.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i64Store(align: UInt, offset: UInt = 0U, location: SourceLocation = NoLocation) =
    memoryInstr(WasmOp.I64_STORE, align, offset, location)

/**
 * Emits [`f32.store`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Memory/Store).
 * @param align Encoded alignment exponent.
 * @param offset Static byte offset added to the dynamic address.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.f32Store(align: UInt, offset: UInt = 0U, location: SourceLocation = NoLocation) =
    memoryInstr(WasmOp.F32_STORE, align, offset, location)

/**
 * Emits [`f64.store`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Memory/Store).
 * @param align Encoded alignment exponent.
 * @param offset Static byte offset added to the dynamic address.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.f64Store(align: UInt, offset: UInt = 0U, location: SourceLocation = NoLocation) =
    memoryInstr(WasmOp.F64_STORE, align, offset, location)

/**
 * Emits truncating `i32.store8`. See [MDN WebAssembly stores](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Memory/Store).
 * @param align Encoded alignment exponent.
 * @param offset Static byte offset added to the dynamic address.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i32Store8(align: UInt, offset: UInt = 0U, location: SourceLocation = NoLocation) =
    memoryInstr(WasmOp.I32_STORE8, align, offset, location)

/**
 * Emits truncating `i32.store16`. See [MDN WebAssembly stores](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Memory/Store).
 * @param align Encoded alignment exponent.
 * @param offset Static byte offset added to the dynamic address.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i32Store16(align: UInt, offset: UInt = 0U, location: SourceLocation = NoLocation) =
    memoryInstr(WasmOp.I32_STORE16, align, offset, location)

/**
 * Emits truncating `i64.store8`. See [MDN WebAssembly stores](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Memory/Store).
 * @param align Encoded alignment exponent.
 * @param offset Static byte offset added to the dynamic address.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i64Store8(align: UInt, offset: UInt = 0U, location: SourceLocation = NoLocation) =
    memoryInstr(WasmOp.I64_STORE8, align, offset, location)

/**
 * Emits truncating `i64.store16`. See [MDN WebAssembly stores](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Memory/Store).
 * @param align Encoded alignment exponent.
 * @param offset Static byte offset added to the dynamic address.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i64Store16(align: UInt, offset: UInt = 0U, location: SourceLocation = NoLocation) =
    memoryInstr(WasmOp.I64_STORE16, align, offset, location)

/**
 * Emits truncating `i64.store32`. See [MDN WebAssembly stores](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Memory/Store).
 * @param align Encoded alignment exponent.
 * @param offset Static byte offset added to the dynamic address.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i64Store32(align: UInt, offset: UInt = 0U, location: SourceLocation = NoLocation) =
    memoryInstr(WasmOp.I64_STORE32, align, offset, location)

/**
 * Emits [`memory.size`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Memory/Size).
 * @param memoryIndex Index of the memory to query.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.memorySize(memoryIndex: Int = 0, location: SourceLocation = NoLocation) =
    instr(WasmOp.MEMORY_SIZE, WasmImmediate.MemoryIdx(memoryIndex), location)

/**
 * Emits [`memory.grow`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Memory/Grow).
 * @param memoryIndex Index of the memory to grow.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.memoryGrow(memoryIndex: Int = 0, location: SourceLocation = NoLocation) =
    instr(WasmOp.MEMORY_GROW, WasmImmediate.MemoryIdx(memoryIndex), location)

/**
 * Emits `memory.init`. See [MDN WebAssembly memory instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Memory).
 * @param dataIndex Symbolic index of the passive data segment.
 * @param memoryIndex Index of the destination memory.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.memoryInit(
    dataIndex: WasmSymbol<Int>, memoryIndex: Int = 0, location: SourceLocation = NoLocation
) = instr(WasmOp.MEMORY_INIT, WasmImmediate.DataIdx(dataIndex), WasmImmediate.MemoryIdx(memoryIndex), location)

/**
 * Emits `data.drop`. See [MDN WebAssembly memory instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Memory).
 * @param dataIndex Symbolic index of the passive data segment to discard.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.dataDrop(dataIndex: WasmSymbol<Int>, location: SourceLocation = NoLocation) =
    instr(WasmOp.DATA_DROP, WasmImmediate.DataIdx(dataIndex), location)

/**
 * Emits `memory.copy`. See [MDN WebAssembly memory instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Memory).
 * @param destinationMemoryIndex Index of the destination memory.
 * @param sourceMemoryIndex Index of the source memory.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.memoryCopy(
    destinationMemoryIndex: Int = 0, sourceMemoryIndex: Int = 0, location: SourceLocation = NoLocation
) = instr(
    WasmOp.MEMORY_COPY,
    WasmImmediate.MemoryIdx(destinationMemoryIndex),
    WasmImmediate.MemoryIdx(sourceMemoryIndex),
    location
)

/**
 * Emits `memory.fill`. See [MDN WebAssembly memory instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Memory).
 * @param memoryIndex Index of the memory to fill.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.memoryFill(memoryIndex: Int = 0, location: SourceLocation = NoLocation) =
    instr(WasmOp.MEMORY_FILL, WasmImmediate.MemoryIdx(memoryIndex), location)

/**
 * Emits [`table.get`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Table/Get).
 * @param tableIndex Symbolic index of the table to read.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.tableGet(
    tableIndex: WasmSymbolReadOnly<Int> = WasmSymbol(0), location: SourceLocation = NoLocation
) = instr(WasmOp.TABLE_GET, WasmImmediate.TableIdx(tableIndex), location)

/**
 * Emits [`table.set`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Table/Set).
 * @param tableIndex Symbolic index of the table to write.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.tableSet(
    tableIndex: WasmSymbolReadOnly<Int> = WasmSymbol(0), location: SourceLocation = NoLocation
) = instr(WasmOp.TABLE_SET, WasmImmediate.TableIdx(tableIndex), location)

/**
 * Emits [`table.grow`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Table/Grow).
 * @param tableIndex Symbolic index of the table to grow.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.tableGrow(
    tableIndex: WasmSymbolReadOnly<Int> = WasmSymbol(0), location: SourceLocation = NoLocation
) = instr(WasmOp.TABLE_GROW, WasmImmediate.TableIdx(tableIndex), location)

/**
 * Emits [`table.size`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Table/Size).
 * @param tableIndex Symbolic index of the table to query.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.tableSize(
    tableIndex: WasmSymbolReadOnly<Int> = WasmSymbol(0), location: SourceLocation = NoLocation
) = instr(WasmOp.TABLE_SIZE, WasmImmediate.TableIdx(tableIndex), location)

/**
 * Emits `table.fill`. See [MDN WebAssembly table instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Table).
 * @param tableIndex Symbolic index of the table to fill.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.tableFill(
    tableIndex: WasmSymbolReadOnly<Int> = WasmSymbol(0), location: SourceLocation = NoLocation
) = instr(WasmOp.TABLE_FILL, WasmImmediate.TableIdx(tableIndex), location)

/**
 * Emits `table.init`. See [MDN WebAssembly table instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Table).
 * @param element Element segment used to initialize the table.
 * @param tableIndex Symbolic index of the destination table.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.tableInit(
    element: WasmElement, tableIndex: WasmSymbolReadOnly<Int> = WasmSymbol(0), location: SourceLocation = NoLocation
) = instr(WasmOp.TABLE_INIT, WasmImmediate.ElemIdx(element), WasmImmediate.TableIdx(tableIndex), location)

/**
 * Emits `elem.drop`. See [MDN WebAssembly table instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Table).
 * @param element Passive element segment to discard.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.elemDrop(element: WasmElement, location: SourceLocation = NoLocation) =
    instr(WasmOp.ELEM_DROP, WasmImmediate.ElemIdx(element), location)

/**
 * Emits `table.copy`. See [MDN WebAssembly table instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Table).
 * @param destinationTableIndex Symbolic index of the destination table.
 * @param sourceTableIndex Symbolic index of the source table.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.tableCopy(
    destinationTableIndex: WasmSymbolReadOnly<Int> = WasmSymbol(0),
    sourceTableIndex: WasmSymbolReadOnly<Int> = WasmSymbol(0),
    location: SourceLocation = NoLocation
) = instr(
    WasmOp.TABLE_COPY, WasmImmediate.TableIdx(destinationTableIndex), WasmImmediate.TableIdx(sourceTableIndex), location
)

/**
 * Emits [`br_table`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Control_flow/br_table).
 * @param labels Absolute block levels selected by the operand.
 * @param defaultLabel Absolute fallback block level.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.brTable(
    labels: List<Int>, defaultLabel: Int, location: SourceLocation = NoLocation
) = instr(
    WasmOp.BR_TABLE, WasmImmediate.LabelIdxVector(labels), WasmImmediate.LabelIdx.get(defaultLabel), location
)

/**
 * Emits [`return`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Control_flow/return).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.ret(location: SourceLocation = NoLocation) = instr(WasmOp.RETURN, location)

/**
 * Emits exception-handling `delegate`. See [MDN WebAssembly instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference#instructions).
 * @param label Target label index.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.delegate(label: Int, location: SourceLocation = NoLocation) =
    instr(WasmOp.DELEGATE, WasmImmediate.LabelIdx.get(label), location)

/**
 * Emits exception-handling `rethrow`. See [MDN WebAssembly instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference#instructions).
 * @param label Target catch label index.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.rethrow(label: Int, location: SourceLocation = NoLocation) =
    instr(WasmOp.RETHROW, WasmImmediate.LabelIdx.get(label), location)

/**
 * Emits untyped [`select`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Control_flow/Select).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.select(location: SourceLocation = NoLocation) = instr(WasmOp.SELECT, location)

/**
 * Emits typed [`select`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Control_flow/Select).
 * @param types Result value types selected by the instruction.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.select(types: List<WasmType>, location: SourceLocation = NoLocation) =
    instr(WasmOp.SELECT_TYPED, WasmImmediate.ValTypeVector(types), location)

/**
 * Emits [`ref.is_null`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Reference/is_null).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.refIsNull(location: SourceLocation = NoLocation) = instr(WasmOp.REF_IS_NULL, location)

/**
 * Emits [`ref.func`](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Reference/Function).
 * @param functionIndex Index of the referenced function.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.refFunc(
    functionIndex: WasmImmediate.FuncIdx, location: SourceLocation = NoLocation
) = instr(WasmOp.REF_FUNC, functionIndex, location)

/**
 * Emits `call_ref`. See [MDN WebAssembly function calls](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Control_flow/call).
 * @param typeIndex Expected function type index.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.callRef(typeIndex: WasmImmediate.TypeIdx, location: SourceLocation = NoLocation) =
    instr(WasmOp.CALL_REF, typeIndex, location)

/**
 * Emits tail-call `return_call_ref`. See [MDN WebAssembly function calls](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Control_flow/call).
 * @param typeIndex Expected function type index.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.returnCallRef(
    typeIndex: WasmImmediate.TypeIdx, location: SourceLocation = NoLocation
) = instr(WasmOp.RETURN_CALL_REF, typeIndex, location)

/**
 * Emits `ref.as_non_null`. See [MDN WebAssembly reference instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Reference).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.refAsNotNull(location: SourceLocation = NoLocation) =
    instr(WasmOp.REF_AS_NOT_NULL, location)

/**
 * Emits `br_on_null`. See [MDN WebAssembly reference instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Reference).
 * @param absoluteBlockLevel Absolute target block level.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.brOnNull(absoluteBlockLevel: Int, location: SourceLocation = NoLocation) =
    brInstr(WasmOp.BR_ON_NULL, absoluteBlockLevel, location)

/**
 * Emits `br_on_non_null`. See [MDN WebAssembly reference instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Reference).
 * @param absoluteBlockLevel Absolute target block level.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.brOnNonNull(absoluteBlockLevel: Int, location: SourceLocation = NoLocation) =
    brInstr(WasmOp.BR_ON_NON_NULL, absoluteBlockLevel, location)

/**
 * Emits `struct.new_default`. See [MDN WebAssembly instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference#instructions).
 * @param typeIndex Structure type index.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.structNewDefault(
    typeIndex: WasmImmediate.TypeIdx, location: SourceLocation = NoLocation
) = instr(WasmOp.STRUCT_NEW_DEFAULT, typeIndex, location)

/**
 * Emits sign-extending `struct.get_s`. See [MDN WebAssembly instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference#instructions).
 * @param typeIndex Structure type index.
 * @param fieldIndex Packed field index to read.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.structGetS(
    typeIndex: WasmImmediate.TypeIdx, fieldIndex: Int, location: SourceLocation = NoLocation
) = instr(WasmOp.STRUCT_GET_S, typeIndex, WasmImmediate.StructFieldIdx.get(fieldIndex), location)

/**
 * Emits zero-extending `struct.get_u`. See [MDN WebAssembly instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference#instructions).
 * @param typeIndex Structure type index.
 * @param fieldIndex Packed field index to read.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.structGetU(
    typeIndex: WasmImmediate.TypeIdx, fieldIndex: Int, location: SourceLocation = NoLocation
) = instr(WasmOp.STRUCT_GET_U, typeIndex, WasmImmediate.StructFieldIdx.get(fieldIndex), location)

/**
 * Emits `array.new`. See [MDN WebAssembly instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference#instructions).
 * @param typeIndex Array type index.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.arrayNew(typeIndex: WasmImmediate.TypeIdx, location: SourceLocation = NoLocation) =
    instr(WasmOp.ARRAY_NEW, typeIndex, location)

/**
 * Emits `array.new_default`. See [MDN WebAssembly instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference#instructions).
 * @param typeIndex Array type index.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.arrayNewDefault(
    typeIndex: WasmImmediate.TypeIdx, location: SourceLocation = NoLocation
) = instr(WasmOp.ARRAY_NEW_DEFAULT, typeIndex, location)

/**
 * Emits `array.get`. See [MDN WebAssembly instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference#instructions).
 * @param typeIndex Array type index.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.arrayGet(typeIndex: WasmImmediate.TypeIdx, location: SourceLocation = NoLocation) =
    instr(WasmOp.ARRAY_GET, typeIndex, location)

/**
 * Emits sign-extending `array.get_s`. See [MDN WebAssembly instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference#instructions).
 * @param typeIndex Array type index.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.arrayGetS(typeIndex: WasmImmediate.TypeIdx, location: SourceLocation = NoLocation) =
    instr(WasmOp.ARRAY_GET_S, typeIndex, location)

/**
 * Emits zero-extending `array.get_u`. See [MDN WebAssembly instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference#instructions).
 * @param typeIndex Array type index.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.arrayGetU(typeIndex: WasmImmediate.TypeIdx, location: SourceLocation = NoLocation) =
    instr(WasmOp.ARRAY_GET_U, typeIndex, location)

/**
 * Emits `array.set`. See [MDN WebAssembly instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference#instructions).
 * @param typeIndex Array type index.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.arraySet(typeIndex: WasmImmediate.TypeIdx, location: SourceLocation = NoLocation) =
    instr(WasmOp.ARRAY_SET, typeIndex, location)

/**
 * Emits `array.len`. See [MDN WebAssembly instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference#instructions).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.arrayLen(location: SourceLocation = NoLocation) = instr(WasmOp.ARRAY_LEN, location)

/**
 * Emits `array.copy`. See [MDN WebAssembly instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference#instructions).
 * @param destinationTypeIndex Destination array type index.
 * @param sourceTypeIndex Source array type index.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.arrayCopy(
    destinationTypeIndex: WasmImmediate.TypeIdx,
    sourceTypeIndex: WasmImmediate.TypeIdx,
    location: SourceLocation = NoLocation
) = instr(WasmOp.ARRAY_COPY, destinationTypeIndex, sourceTypeIndex, location)

/**
 * Emits `array.new_data`. See [MDN WebAssembly instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference#instructions).
 * @param typeIndex Array type index.
 * @param dataIndex Symbolic index of the source data segment.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.arrayNewData(
    typeIndex: WasmImmediate.TypeIdx, dataIndex: WasmSymbol<Int>, location: SourceLocation = NoLocation
) = instr(WasmOp.ARRAY_NEW_DATA, typeIndex, WasmImmediate.DataIdx(dataIndex), location)

/**
 * Emits `array.new_fixed`. See [MDN WebAssembly instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference#instructions).
 * @param typeIndex Array type index.
 * @param size Number of element values consumed from the stack.
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.arrayNewFixed(
    typeIndex: WasmImmediate.TypeIdx, size: Int, location: SourceLocation = NoLocation
) = instr(WasmOp.ARRAY_NEW_FIXED, typeIndex, WasmImmediate.ConstI32(size), location)

/**
 * Emits `i31.new`. See [MDN WebAssembly instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference#instructions).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i31New(location: SourceLocation = NoLocation) = instr(WasmOp.I31_NEW, location)

/**
 * Emits signed `i31.get_s`. See [MDN WebAssembly instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference#instructions).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i31GetS(location: SourceLocation = NoLocation) = instr(WasmOp.I31_GET_S, location)

/**
 * Emits unsigned `i31.get_u`. See [MDN WebAssembly instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference#instructions).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.i31GetU(location: SourceLocation = NoLocation) = instr(WasmOp.I31_GET_U, location)

/**
 * Emits `ref.eq`. See [MDN WebAssembly reference instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Reference).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.refEq(location: SourceLocation = NoLocation) = instr(WasmOp.REF_EQ, location)

/**
 * Emits `extern.internalize`. See [MDN WebAssembly reference instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Reference).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.externInternalize(location: SourceLocation = NoLocation) =
    instr(WasmOp.EXTERN_INTERNALIZE, location)

/**
 * Emits `extern.externalize`. See [MDN WebAssembly reference instructions](https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/Reference).
 * @param location Source location associated with the instruction.
 */
inline fun WasmCodeGenerator.externExternalize(location: SourceLocation = NoLocation) =
    instr(WasmOp.EXTERN_EXTERNALIZE, location)