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

package dev.karmakrafts.kcml.example.jvm

import dev.karmakrafts.kcml.api.target.CompileTargetType
import dev.karmakrafts.kcml.api.target.JvmCompileTarget

internal class ExampleJvmCompileTarget(override val isAndroid: Boolean) : JvmCompileTarget {
    companion object Type : CompileTargetType {
        override val aliases: Set<String> = setOf("exampleJvm")
    }

    object AndroidType : CompileTargetType {
        override val aliases: Set<String> = setOf("exampleAndroid")
    }

    override val type: CompileTargetType
        get() = if (isAndroid) AndroidType else Type

    override val name: String = if (isAndroid) "Example Android" else "Example JVM"
}