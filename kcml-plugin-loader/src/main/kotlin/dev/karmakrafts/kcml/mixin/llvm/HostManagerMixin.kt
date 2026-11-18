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

package dev.karmakrafts.kcml.mixin.llvm

import dev.karmakrafts.kcml.api.mixin.DirectMixin
import dev.karmakrafts.kcml.api.mixin.Inject
import dev.karmakrafts.kcml.api.mixin.Inject.Order
import dev.karmakrafts.kcml.api.mixin.ReturnContext
import dev.karmakrafts.kcml.api.mixin.ThisAware
import dev.karmakrafts.kcml.hooks.KCMLHookApi
import dev.karmakrafts.kcml.hooks.llvm.NativeTargetHooks
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget

@OptIn(KCMLHookApi::class)
@DirectMixin(HostManager::class, -999)
internal class HostManagerMixin : ThisAware<HostManager> {
    @Inject(name = "getTargetValues", order = Order.BEFORE)
    fun getTargetValues(context: ReturnContext<List<KonanTarget>>) {
        val targets = NativeTargetHooks.onGetTargetValues() ?: return
        context.returnFromTarget(targets)
    }

    @Inject(name = "getEnabled", order = Order.BEFORE)
    fun getEnabled(context: ReturnContext<List<KonanTarget>>) {
        val targets = NativeTargetHooks.onGetEnabled(getThis()) ?: return
        context.returnFromTarget(targets)
    }
}