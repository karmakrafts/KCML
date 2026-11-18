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

package dev.karmakrafts.kcml.api.target

import org.jetbrains.kotlin.konan.target.BinaryFormat

/**
 * LLVM code generation settings, independent of the machine running the compiler.
 * The toolchain must include the LLVM backend and ABI-compatible Kotlin/Native runtime bitcode.
 */
public class NativeArchitectureConfiguration {
    /** LLVM target triple, for example `riscv64-unknown-linux-gnu`. */
    public var targetTriple: String? = null

    /** LLVM CPU name. An unset value leaves the Kotlin/Native default intact. */
    public var cpu: String? = null

    /** LLVM target feature string, for example `+m,+a,+f,+d,+c`. */
    public var features: String? = null

    /** LLVM data layout. An unset value preserves the layout of the supplied runtime bitcode. */
    public var dataLayout: String? = null

    /** Object file format for a custom family; defaults to ELF. */
    public var binaryFormat: BinaryFormat? = null

    /** Custom architectures conservatively disable 64-bit atomics unless enabled here. */
    public var supports64BitAtomics: Boolean? = null

    /** Custom architectures conservatively disable unaligned access unless enabled here. */
    public var supportsUnalignedAccess: Boolean? = null

    /** Additional exact `konan.properties` keys, including target/host suffixes where needed. */
    public val properties: MutableMap<String, String> = linkedMapOf()

    /** Validates explicit settings without imposing a fixed list of LLVM architectures. */
    public fun validate() {
        for ([key, value] in listOf(
            "targetTriple" to targetTriple, "cpu" to cpu, "features" to features, "dataLayout" to dataLayout
        )) {
            require(value == null || value.isNotBlank() || key == "features" && value.isEmpty()) {
                "Native architecture $key must not be blank"
            }
        }
        require(properties.keys.none(String::isBlank)) { "Native property names must not be blank" }
    }
}

/** Defines LLVM settings for a plugin-supplied [NativeCompileTarget]. */
public fun nativeArchitecture(configure: NativeArchitectureConfiguration.() -> Unit): NativeArchitectureConfiguration =
    NativeArchitectureConfiguration().apply(configure).also(NativeArchitectureConfiguration::validate)