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

package dev.karmakrafts.kcml.target.proto

import kotlinx.serialization.Serializable

@Serializable
public data class NativeLlvmDescriptor(
    public val targetTriple: String? = null,
    public val cpu: String? = null,
    public val features: String? = null,
    public val dataLayout: String? = null,
    public val binaryFormat: String? = null,
    public val supports64BitAtomics: Boolean? = null,
    public val supportsUnalignedAccess: Boolean? = null,
    public val properties: Map<String, String> = emptyMap()
)