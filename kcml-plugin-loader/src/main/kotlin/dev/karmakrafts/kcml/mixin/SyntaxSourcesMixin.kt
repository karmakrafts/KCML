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

package dev.karmakrafts.kcml.mixin

import dev.karmakrafts.kcml.api.mixin.IndirectMixin
import dev.karmakrafts.kcml.api.mixin.Inject
import dev.karmakrafts.kcml.api.mixin.ReturnContext
import dev.karmakrafts.kcml.api.mixin.ThisAware
import dev.karmakrafts.kcml.hooks.KCMLHookApi
import dev.karmakrafts.kcml.hooks.SyntaxHooks
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.psi.KtFile

@OptIn(KCMLHookApi::class)
@IndirectMixin("org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment")
internal class SyntaxSourcesMixin : ThisAware<KotlinCoreEnvironment> {
    @Inject("getSourceFiles", "()Ljava/util/List;", order = Inject.Order.BEFORE)
    fun getSourceFiles(context: ReturnContext<List<KtFile>>) {
        val sources = SyntaxHooks.lowerSources(getThis()) ?: return
        context.returnFromTarget(sources)
    }
}