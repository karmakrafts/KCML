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

package dev.karmakrafts.kcml.api.extension.syntax

import dev.karmakrafts.kcml.api.ExperimentalKcmlApi
import org.jetbrains.kotlin.com.intellij.openapi.util.TextRange

/**
 * An editor coloring override in original source coordinates.
 *
 * @param range nonempty range within the source file; IDE hosts ignore out-of-bounds ranges.
 * @param textAttributesKey external name of an IDE color-scheme key, such as `KOTLIN_KEYWORD`,
 * `KOTLIN_NUMBER` or `DEFAULT_KEYWORD`. Custom keys must be registered by an IDE integration.
 */
@ExperimentalKcmlApi
public data class Highlighting( // @formatter:off
    public val range: TextRange,
    public val textAttributesKey: String
) { // @formatter:on
    init {
        require(!range.isEmpty) { "Highlighting range must not be empty" }
        require(textAttributesKey.isNotBlank()) { "Text attributes key must not be blank" }
    }
}