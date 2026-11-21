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

package dev.karmakrafts.kcml.util

import dev.karmakrafts.kcml.hacks.DiscouragedKcmlApi
import dev.karmakrafts.kcml.hacks.UnsafeUtils

object AgentAttachPermissions {
    @OptIn(DiscouragedKcmlApi::class)
    @Suppress("DEPRECATION")
    fun allowAttachSelf() {
        try {
            val unsafe = UnsafeUtils.unsafe
            val hotspotVirtualMachine = Class.forName("sun.tools.attach.HotSpotVirtualMachine")
            val allowAttachSelfField = hotspotVirtualMachine.getDeclaredField("ALLOW_ATTACH_SELF")
            val allowAttachSelfBase = unsafe.staticFieldBase(allowAttachSelfField)
            val allowAttachSelfOffset = unsafe.staticFieldOffset(allowAttachSelfField)
            unsafe.putBooleanVolatile(allowAttachSelfBase, allowAttachSelfOffset, true)
        } catch (_: ClassNotFoundException) {
            // Skip overwriting access flag if class is not found (J9/Graal)
        }
    }
}