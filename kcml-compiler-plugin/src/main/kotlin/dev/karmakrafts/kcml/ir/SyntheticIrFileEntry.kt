/*
 * Copyright 2025 Karma Krafts & associates
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

package dev.karmakrafts.kcml.ir

import dev.karmakrafts.kcml.util.UsedByAgent
import org.jetbrains.kotlin.ir.IrFileEntry
import org.jetbrains.kotlin.ir.LineAndColumn
import org.jetbrains.kotlin.ir.SourceRangeInfo

@UsedByAgent
class SyntheticIrFileEntry(
    override val name: String
) : IrFileEntry {
    override fun getColumnNumber(offset: Int): Int = 0
    override fun getLineAndColumnNumbers(offset: Int): LineAndColumn = LineAndColumn(0, 0)
    override fun getLineNumber(offset: Int): Int = 0

    override fun getSourceRangeInfo(
        beginOffset: Int, endOffset: Int
    ): SourceRangeInfo = SourceRangeInfo(name, beginOffset, 0, 0, endOffset, 0, 0)

    override val maxOffset: Int = 0
}