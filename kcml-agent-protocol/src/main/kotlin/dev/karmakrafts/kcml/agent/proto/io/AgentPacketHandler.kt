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
import kotlinx.serialization.json.decodeFromStream
import java.io.InputStream

@OptIn(ExperimentalSerializationApi::class)
class AgentPacketHandler<T>(
    val serializer: KSerializer<T>, private val handler: (T) -> Unit
) {
    val type: String
        get() = serializer.descriptor.serialName

    internal fun handle(input: InputStream) {
        handler(json.decodeFromStream(serializer, input))
    }

    operator fun plus(other: AgentPacketHandler<T>): AgentPacketHandler<T> = AgentPacketHandler(
        serializer = serializer, handler = { message ->
            handler(message)
            other.handler(message)
        })
}