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

package dev.karmakrafts.kcml.agent.asm

import org.objectweb.asm.ClassWriter

internal class NonLoadingClassWriter(flags: Int) : ClassWriter(flags) {
    /**
     * Frame calculation usually relies on class loading to determine common super classes,
     * which would cause major problems in an Instrumentation context.
     * We just stub the function to get rid of any class-loader dependant logic here.
     */
    override fun getCommonSuperClass(type1: String?, type2: String?): String {
        return Types.any.internalName
    }
}