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

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.encodeToStream
import java.io.DataOutputStream
import java.io.OutputStream

@OptIn(ExperimentalSerializationApi::class)
class AgentPacketWriter(output: OutputStream) {
    private val output: DataOutputStream = DataOutputStream(output)

    fun <T> write(serializer: KSerializer<T>, message: T) {
        val counter = CountingOutputStream()
        json.encodeToStream(serializer, message, counter)
        require(counter.count <= MAX_PACKET_SIZE) { "Packet exceeds maximum size of $MAX_PACKET_SIZE bytes" }
        output.writeUTF(serializer.descriptor.serialName)
        output.writeInt(counter.count)
        json.encodeToStream(serializer, message, output)
        output.flush()
    }

    private class CountingOutputStream : OutputStream() {
        var count: Int = 0
            private set

        override fun write(value: Int) {
            count = Math.addExact(count, 1)
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            count = Math.addExact(count, length)
        }
    }
}