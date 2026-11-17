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

package dev.karmakrafts.kcml.target

import org.jetbrains.kotlin.konan.properties.KonanPropertiesLoader
import org.jetbrains.kotlin.konan.target.ClangFlags
import org.jetbrains.kotlin.konan.target.Configurables
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.konan.util.ProgressCallback
import java.util.*

internal class CustomNativeConfigurables(
    target: KonanTarget, properties: Properties, dependenciesRoot: String?, progressCallback: ProgressCallback
) : KonanPropertiesLoader(
    target, properties, dependenciesRoot, progressCallback = progressCallback
), Configurables, ClangFlags {
    override val absoluteTargetToolchain: String
        get() = targetToolchain?.let(::absolute) ?: absoluteLlvmHome

    override val absoluteTargetSysRoot: String
        get() = targetSysRoot?.let(::absolute).orEmpty()
}