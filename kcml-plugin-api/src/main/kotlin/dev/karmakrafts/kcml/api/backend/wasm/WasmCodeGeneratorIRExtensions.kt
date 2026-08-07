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

import org.jetbrains.kotlin.wasm.ir.source.location.SourceLocation
import org.jetbrains.kotlin.wasm.ir.source.location.SourceLocation.NoLocation

// TODO: document this
inline fun WasmCodeGenerator.drop(location: SourceLocation = NoLocation) {
    expressionBuilder.buildDrop(location)
}

// TODO: document this
inline fun WasmCodeGenerator.const(value: Boolean, location: SourceLocation = NoLocation) {
    expressionBuilder.buildConstI32(if (value) 1 else 0, location)
}

// TODO: document this
inline fun WasmCodeGenerator.const(value: Byte, location: SourceLocation = NoLocation) {
    expressionBuilder.buildConstI32(value.toInt(), location)
}

// TODO: document this
inline fun WasmCodeGenerator.const(value: Short, location: SourceLocation = NoLocation) {
    expressionBuilder.buildConstI32(value.toInt(), location)
}

// TODO: document this
inline fun WasmCodeGenerator.const(value: Int, location: SourceLocation = NoLocation) {
    expressionBuilder.buildConstI32(value, location)
}

// TODO: document this
inline fun WasmCodeGenerator.const(value: Long, location: SourceLocation = NoLocation) {
    expressionBuilder.buildConstI64(value, location)
}

// TODO: document this
inline fun WasmCodeGenerator.const(value: Float, location: SourceLocation = NoLocation) {
    expressionBuilder.buildConstF32(value, location)
}

// TODO: document this
inline fun WasmCodeGenerator.const(value: Double, location: SourceLocation = NoLocation) {
    expressionBuilder.buildConstF64(value, location)
}