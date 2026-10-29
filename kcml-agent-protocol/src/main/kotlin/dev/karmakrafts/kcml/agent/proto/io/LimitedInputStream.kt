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

package dev.karmakrafts.kcml.agent.proto.io

import java.io.EOFException
import java.io.InputStream

internal class LimitedInputStream(
    private val input: InputStream, size: Int
) : InputStream() {
    private var remaining: Int = size

    override fun read(): Int {
        if (remaining == 0) return -1
        val value = input.read()
        if (value == -1) throw EOFException("Unexpected end of packet")
        remaining--
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (remaining == 0) return -1
        val readSize = input.read(buffer, offset, minOf(length, remaining))
        if (readSize == -1) throw EOFException("Unexpected end of packet")
        remaining -= readSize
        return readSize
    }

    fun skipRemaining() {
        while (remaining > 0) {
            val skipped = input.skip(remaining.toLong()).toInt()
            if (skipped > 0) {
                remaining -= skipped
            }
            else {
                read()
            }
        }
    }
}