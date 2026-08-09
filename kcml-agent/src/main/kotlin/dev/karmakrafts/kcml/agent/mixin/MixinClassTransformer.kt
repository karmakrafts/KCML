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

package dev.karmakrafts.kcml.agent.mixin

import dev.karmakrafts.kcml.agent.log.Logger
import java.lang.instrument.ClassFileTransformer
import java.security.ProtectionDomain

internal class MixinClassTransformer(
    private val logger: Logger, private val loader: MixinLoader
) : ClassFileTransformer {
    override fun transform(
        module: Module?,
        loader: ClassLoader?,
        className: String?,
        classBeingRedefined: Class<*>?,
        protectionDomain: ProtectionDomain?,
        classfileBuffer: ByteArray?
    ): ByteArray {
        if (className.isNullOrBlank() || classfileBuffer == null) return ByteArray(0)
        // TODO: raise an error when a Mixin class is being instantiated
        return classfileBuffer
    }
}