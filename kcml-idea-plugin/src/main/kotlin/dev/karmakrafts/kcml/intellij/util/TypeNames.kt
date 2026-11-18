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

package dev.karmakrafts.kcml.intellij.util

internal object TypeNames {
    const val PLUGIN: String = "dev.karmakrafts.kcml.api.plugin.Plugin"
    const val COMPILER_PLUGIN: String = "dev.karmakrafts.kcml.api.plugin.CompilerPlugin"

    const val DIRECT_MIXIN: String = "dev.karmakrafts.kcml.api.mixin.DirectMixin"
    const val INDIRECT_MIXIN: String = "dev.karmakrafts.kcml.api.mixin.IndirectMixin"
    const val THIS_AWARE: String = "dev.karmakrafts.kcml.api.mixin.ThisAware"
    const val ONLY_INSIDE_MIXIN: String = "dev.karmakrafts.kcml.api.mixin.OnlyInsideMixin"
    const val INJECT: String = "dev.karmakrafts.kcml.api.mixin.Inject"
}