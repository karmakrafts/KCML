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

/** Describes a Kotlin/JVM compilation target. */
public interface JvmCompileTarget : CompileTarget {
    /** Type descriptor for JVM and Android compilation targets. */
    public companion object Type : CompileTargetType {
        /** Aliases used to select JVM or Android compilation targets. */
        override val aliases: Set<String> = setOf("jvm", "android")
    }

    /** The JVM compilation target type. */
    override val type: CompileTargetType
        get() = Type

    /** Whether this target compiles for Android rather than a standard JVM runtime. */
    public val isAndroid: Boolean
}