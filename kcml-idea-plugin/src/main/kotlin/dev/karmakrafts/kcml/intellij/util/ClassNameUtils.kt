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

internal object ClassNameUtils {
    fun lookupNames(binaryName: String): Set<String> {
        val className = binaryName.substringAfterLast('.')
        val names = linkedSetOf(binaryName, className, className.replace('$', '.'))
        var index = className.indexOf('$')
        while (index >= 0) {
            val nestedName = className.substring(index + 1)
            names.add(nestedName)
            names.add(nestedName.replace('$', '.'))
            index = className.indexOf('$', index + 1)
        }
        return names
    }
}